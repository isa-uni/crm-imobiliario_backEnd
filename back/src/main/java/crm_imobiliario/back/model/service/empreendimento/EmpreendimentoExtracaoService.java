package crm_imobiliario.back.model.service.empreendimento;

import crm_imobiliario.back.util.RecursoNaoEncontradoException;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import crm_imobiliario.back.model.dto.empreendimento.EmpreendimentoExtracaoDTO;
import crm_imobiliario.back.model.entity.*;
import crm_imobiliario.back.model.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmpreendimentoExtracaoService {

    private final EmpreendimentoExtracaoRepository extracaoRepository;
    private final EmpreendimentoDocumentoRepository documentoRepository;
    private final EmpreendimentoFonteRepository fonteRepository;
    private final EmpreendimentoExtracaoDocumentoRepository extracaoDocumentoRepository;
    private final crm_imobiliario.back.model.service.empreendimento.extracao.DocumentExtractionOrchestrator extractionOrchestrator;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public EmpreendimentoExtracao criarExtracao(List<Long> documentoIds, Long usuarioId, Long empreendimentoId) {
        if (documentoIds == null || documentoIds.isEmpty()) throw new IllegalArgumentException("Selecione pelo menos um documento para iniciar a extração.");
        if (documentoIds.size() > 5) throw new IllegalArgumentException("Envie no máximo 5 documentos por extração. Foram informados " + documentoIds.size() + ".");
        List<EmpreendimentoDocumento> docs = documentoRepository.findAllById(documentoIds);//procurando docs no banco
        if (docs.size() != documentoIds.size()) {//Conferindo se todos os documentos foram encontrados
            Set<Long> encontrados = docs.stream().map(EmpreendimentoDocumento::getId).collect(Collectors.toSet());
            //Conferindo se todos os documentos foram encontrados
            List<Long> faltando = documentoIds.stream().filter(id -> !encontrados.contains(id)).toList();
            //compara os IDs enviados pelo usuário com os IDs encontrados no banco. Para descobrindo quais IDs estão faltando
            throw new RecursoNaoEncontradoException("Não foi possível iniciar a extração porque "
                + (faltando.size() == 1 ? "o documento " + faltando.get(0) + " não foi encontrado." : "os documentos " + faltando + " não foram encontrados.")
                + " Envie os arquivos novamente.");
            //Informando qual documento não foi encontrado
        }

        EmpreendimentoExtracao extracao = EmpreendimentoExtracao.builder()//Criando o registro da extração no banco
            .empreendimentoId(empreendimentoId)
            .status("pendente")
            .modeloIa("regras-deterministicas")
            .dataProcessamento(Instant.now())
            .usuario(usuarioId)
            .build();
        extracao = extracaoRepository.save(extracao);
        // vincula documentos via entity
        for (Long docId : documentoIds) {//Para cada ID de documento dentro da lista
            EmpreendimentoExtracaoDocumento link = EmpreendimentoExtracaoDocumento.builder()
                .extracaoId(extracao.getId()).documentoId(docId).build();
            extracaoDocumentoRepository.save(link);
            //Salva essa relação do docs com a extracao
            // Encontra o docs e atualiza status para processando
            documentoRepository.findById(docId).ifPresent(d -> { d.setStatusProcessamento("processando"); documentoRepository.save(d); });
        }
        // processamento em segundo plano(ver ExtracaoAsyncListener)
        //responsável por publicar eventos
        //Ele está enviando uma informação que permite que outro componente encontre a extração e seus documentos.
        eventPublisher.publishEvent(new ExtracaoSolicitadaEvent(extracao.getId()));
        return extracao;
    }

    /** Evento publicado ao criar/reprocessar uma extração; consumido após o commit. */
    public record ExtracaoSolicitadaEvent(Long extracaoId) {}

    /**
     * Executa a extração. Não é chamado diretamente por este bean: antes, criarExtracao chamava
     * this.processarAsync(...), e como @Async/@Transactional funcionam por proxy, a chamada interna
     * rodava de forma síncrona dentro da requisição de upload. Agora o ExtracaoAsyncListener (outro
     * bean, em thread do TaskExecutor) chama este método através do proxy.
     */
    @Transactional
    public void processar(Long extracaoId) {
        EmpreendimentoExtracao extracao = extracaoRepository.findById(extracaoId).orElse(null);//encontrando a extracao no banco
        if (extracao == null) return;//encerra o metodo
        try {
            extracao.setStatus("processando");//Atualizando o status para processando
            extracao.setDataProcessamento(Instant.now());//hora atual
            extracaoRepository.save(extracao);

            //Buscando os documentos da extração(na tabela de vinculo)
            List<Long> docIds = extracaoDocumentoRepository.findByExtracaoId(extracaoId).stream()
                .map(EmpreendimentoExtracaoDocumento::getDocumentoId).toList();
            List<EmpreendimentoDocumento> docs = documentoRepository.findAllById(docIds);//busca as infos completas dos docs

            EmpreendimentoExtractionService.ExtractionResult result = extractionOrchestrator.extract(docs);//resultado da extracao

            String json = objectMapper.writeValueAsString(result.resultadoJson);//converte em json
            extracao.setResultado(json);
            extracao.setModeloIa(result.modelo);
            extracao.setDataConclusao(Instant.now());

            // salva fontes
            fonteRepository.deleteAll(fonteRepository.findByExtracaoId(extracaoId));//busca as fontes que já estavam registradas para aquela extração e exclui esses registros.
            if (result.fontes != null) {//verifica se a lista de fontes não é nula.
                for (var f : result.fontes) {//percorre cada fonte individualmente.
                    EmpreendimentoFonte fonte = EmpreendimentoFonte.builder()
                        .extracaoId(extracaoId)
                        .documentoId(f.documentoId)
                        .empreendimentoId(extracao.getEmpreendimentoId())
                        .campo(f.campo)//Qual informação foi identificada
                        .valorExtraido(f.valorExtraido)
                        .pagina(f.pagina)
                        .trecho(f.trecho)
                        .confianca(f.confianca)
                        .documentoNome(f.documentoNome)
                        .build();
                    fonteRepository.save(fonte);//salva no banco
                }
            }

            // detecta conflitos, baixa confiança e alertas (documento não suportado, coluna não reconhecida etc.)
            boolean temConflito = detectarConflitos(result.fontes);
            boolean baixaConfianca = result.fontes != null && result.fontes.stream().anyMatch(f -> f.confianca != null && f.confianca < 60);
            //se pelo menos uma fonte possui confiança inferior a 60.

            boolean temAlerta = result.fontes != null && result.fontes.stream().anyMatch(f -> "_alerta".equals(f.campo));
            //procura alguma fonte cujo campo seja _alerta.

            extracao.setStatus(temConflito || baixaConfianca || temAlerta ? "revisao" : "concluido");//"Existe conflito OU baixa confiança OU alerta = 'revisao'
            // atualiza doc status — mantém "revisao" nos documentos que o extrator sinalizou (ex.: formato não suportado)
            for (EmpreendimentoDocumento d : docs) {
                //percorre todos os documentos utilizados na extração e se o status não for revisão, altera para concluido.
                if (!"revisao".equals(d.getStatusProcessamento())) d.setStatusProcessamento("concluido");
                documentoRepository.save(d);
            }

            extracaoRepository.save(extracao);
            log.info("Extracao {} concluida status={}", extracaoId, extracao.getStatus());
            //salva e registra log
        } catch (Exception e) {
            log.error("Extracao {} falhou: {}", extracaoId, e.getMessage(), e);
            try {
                extracao.setStatus("erro");
                extracao.setErro(e.getMessage());//registra a mensagem de erro
                extracao.setDataConclusao(Instant.now());
                extracaoRepository.save(extracao);
                List<Long> docIds = extracaoDocumentoRepository.findByExtracaoId(extracaoId).stream()
                    .map(EmpreendimentoExtracaoDocumento::getDocumentoId).toList();
                //Busca os IDs dos documentos relacionados à extração.
                for (Long docId : docIds) documentoRepository.findById(docId).ifPresent(d -> { d.setStatusProcessamento("erro"); documentoRepository.save(d); });
                //e atualiza o status para erro.
            } catch (Exception ex) { log.error("Falha ao marcar erro extracao", ex); }
        }
    }

    private boolean detectarConflitos(List<EmpreendimentoExtractionService.EmpreendimentoFonteData> fontes) {
        if (fontes == null || fontes.isEmpty()) return false;
        Map<String, Set<String>> porCampo = new HashMap<>();
        for (var f : fontes) {
            if (f.valorExtraido == null || f.valorExtraido.isBlank()) continue;
            porCampo.computeIfAbsent(f.campo, k -> new HashSet<>()).add(f.valorExtraido.trim().toLowerCase());
        }
        return porCampo.values().stream().anyMatch(set -> set.size() > 1);
    }

    @Transactional(readOnly = true)
    public EmpreendimentoExtracaoDTO obter(Long id) {
        EmpreendimentoExtracao e = extracaoRepository.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("A extração solicitada não foi encontrada. Envie os documentos novamente."));
        //Buscando a extração no banco

        List<Long> docIds = extracaoDocumentoRepository.findByExtracaoId(id).stream()
            .map(EmpreendimentoExtracaoDocumento::getDocumentoId).toList();
        //Buscando os documentos relacionados

        List<EmpreendimentoFonte> fontes = fonteRepository.findByExtracaoId(id);
        //Buscando as fontes encontradas

        // conflitos
        Map<String, List<EmpreendimentoFonte>> porCampo = fontes.stream().collect(Collectors.groupingBy(EmpreendimentoFonte::getCampo));
        //o código agrupa as fontes pelo campo para identificar conflitos entre os documentos

        List<EmpreendimentoExtracaoDTO.ConflitoDTO> conflitos = new ArrayList<>();
        //cria a lista de conflitos

        for (Map.Entry<String, List<EmpreendimentoFonte>> entry : porCampo.entrySet()) {//Percorrendo os campos agrupados
            Set<String> vals = entry.getValue().stream().map(f -> f.getValorExtraido()!=null?f.getValorExtraido().toLowerCase():"").collect(Collectors.toSet());
            //Identificando os valores diferentes

            if (vals.size() > 1) {//existem valores diferentes, então há conflito.
                conflitos.add(EmpreendimentoExtracaoDTO.ConflitoDTO.builder()
                    .campo(entry.getKey()) //Montando o objeto de conflito
                    .valores(entry.getValue().stream().map(f -> EmpreendimentoExtracaoDTO.FonteDTO.builder()
                        .id(f.getId()).documentoId(f.getDocumentoId()).documentoNome(f.getDocumentoNome())
                        .campo(f.getCampo()).valorExtraido(f.getValorExtraido()).pagina(f.getPagina()).trecho(f.getTrecho()).confianca(f.getConfianca()).build()).toList())
                    .build());
                    //Aqui ele percorre todas as fontes daquele campo e transforma cada uma em um FonteDTO. Pois precisa mostrar de onde vieram as informações divergentes.
            }
        }
        Object resultadoJson = null;
        try { if (e.getResultado()!=null) resultadoJson = objectMapper.readValue(e.getResultado(), Object.class); } catch (Exception ex) { resultadoJson = e.getResultado(); }
        //Convertendo o resultado JSON

        //Montando o DTO final
        return EmpreendimentoExtracaoDTO.builder()
            .id(e.getId()).empreendimentoId(e.getEmpreendimentoId()).status(e.getStatus()).modeloIa(e.getModeloIa())
            .dataProcessamento(e.getDataProcessamento()).dataConclusao(e.getDataConclusao()).erro(e.getErro())
            .resultado(resultadoJson).documentoIds(docIds)
            .fontes(fontes.stream().map(f -> EmpreendimentoExtracaoDTO.FonteDTO.builder()
                .id(f.getId()).documentoId(f.getDocumentoId()).documentoNome(f.getDocumentoNome())
                .campo(f.getCampo()).valorExtraido(f.getValorExtraido()).pagina(f.getPagina()).trecho(f.getTrecho()).confianca(f.getConfianca()).build()).toList())
            .conflitos(conflitos)
            .build();
    }

    @Transactional
    public EmpreendimentoExtracao reprocessar(Long id) {
        EmpreendimentoExtracao e = extracaoRepository.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("A extração solicitada não foi encontrada. Envie os documentos novamente."));
        e.setStatus("pendente");
        e.setErro(null);
        e.setDataProcessamento(Instant.now());
        extracaoRepository.save(e);
        eventPublisher.publishEvent(new ExtracaoSolicitadaEvent(id));
        return e;
    }

    /**
     * Vincula os documentos enviados nesta extração ao empreendimento recém-confirmado —
     * documentos de um upload inicial (sem empreendimento ainda existente) ficam com
     * empreendimentoId nulo até a confirmação (§17/§19 do spec de importação: "Documentos —
     * Arquivos originais").
     */
    @Transactional
    public void vincularEmpreendimento(Long extracaoId, Long empreendimentoId) {
        extracaoRepository.findById(extracaoId).ifPresent(e -> {
            if (e.getEmpreendimentoId() == null) {
                e.setEmpreendimentoId(empreendimentoId);
                extracaoRepository.save(e);
            }
        });
        List<Long> docIds = extracaoDocumentoRepository.findByExtracaoId(extracaoId).stream()
            .map(EmpreendimentoExtracaoDocumento::getDocumentoId).toList();
        for (Long docId : docIds) {
            documentoRepository.findById(docId).ifPresent(d -> {
                if (d.getEmpreendimentoId() == null) {
                    d.setEmpreendimentoId(empreendimentoId);
                    documentoRepository.save(d);
                }
            });
        }
        // as fontes (rastreabilidade) são gravadas durante o processamento assíncrono, quando o
        // empreendimento normalmente ainda não existe — sem este backfill, ficariam com
        // empreendimentoId nulo para sempre e a aba de "origem dos dados" nunca as encontraria.
        for (EmpreendimentoFonte fonte : fonteRepository.findByExtracaoId(extracaoId)) {
            if (fonte.getEmpreendimentoId() == null) {
                fonte.setEmpreendimentoId(empreendimentoId);
                fonteRepository.save(fonte);
            }
        }
    }
}
