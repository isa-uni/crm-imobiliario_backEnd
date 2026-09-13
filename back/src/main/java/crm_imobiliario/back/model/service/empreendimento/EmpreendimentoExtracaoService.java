package crm_imobiliario.back.model.service.empreendimento;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Async;
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

    @Transactional
    public EmpreendimentoExtracao criarExtracao(List<Long> documentoIds, Long usuarioId, Long empreendimentoId) {
        if (documentoIds == null || documentoIds.isEmpty()) throw new IllegalArgumentException("Nenhum documento informado");
        if (documentoIds.size() > 5) throw new IllegalArgumentException("Máximo 5 arquivos");
        List<EmpreendimentoDocumento> docs = documentoRepository.findAllById(documentoIds);
        if (docs.size() != documentoIds.size()) throw new RuntimeException("Algum documento não encontrado");

        EmpreendimentoExtracao extracao = EmpreendimentoExtracao.builder()
            .empreendimentoId(empreendimentoId)
            .status("pendente")
            .modeloIa("regras-deterministicas")
            .dataProcessamento(Instant.now())
            .usuario(usuarioId)
            .build();
        extracao = extracaoRepository.save(extracao);
        // vincula documentos via entity
        for (Long docId : documentoIds) {
            EmpreendimentoExtracaoDocumento link = EmpreendimentoExtracaoDocumento.builder()
                .extracaoId(extracao.getId()).documentoId(docId).build();
            extracaoDocumentoRepository.save(link);
            // atualiza status doc
            documentoRepository.findById(docId).ifPresent(d -> { d.setStatusProcessamento("processando"); documentoRepository.save(d); });
        }
        // dispara async
        processarAsync(extracao.getId());
        return extracao;
    }

    @Async
    @Transactional
    public void processarAsync(Long extracaoId) {
        EmpreendimentoExtracao extracao = extracaoRepository.findById(extracaoId).orElse(null);
        if (extracao == null) return;
        try {
            extracao.setStatus("processando");
            extracao.setDataProcessamento(Instant.now());
            extracaoRepository.save(extracao);

            List<Long> docIds = extracaoDocumentoRepository.findByExtracaoId(extracaoId).stream()
                .map(EmpreendimentoExtracaoDocumento::getDocumentoId).toList();
            List<EmpreendimentoDocumento> docs = documentoRepository.findAllById(docIds);

            EmpreendimentoExtractionService.ExtractionResult result = extractionOrchestrator.extract(docs);

            String json = objectMapper.writeValueAsString(result.resultadoJson);
            extracao.setResultado(json);
            extracao.setModeloIa(result.modelo);
            extracao.setDataConclusao(Instant.now());

            // salva fontes
            fonteRepository.deleteAll(fonteRepository.findByExtracaoId(extracaoId));
            if (result.fontes != null) {
                for (var f : result.fontes) {
                    EmpreendimentoFonte fonte = EmpreendimentoFonte.builder()
                        .extracaoId(extracaoId)
                        .documentoId(f.documentoId)
                        .empreendimentoId(extracao.getEmpreendimentoId())
                        .campo(f.campo)
                        .valorExtraido(f.valorExtraido)
                        .pagina(f.pagina)
                        .trecho(f.trecho)
                        .confianca(f.confianca)
                        .documentoNome(f.documentoNome)
                        .build();
                    fonteRepository.save(fonte);
                }
            }

            // detecta conflitos, baixa confiança e alertas (documento não suportado, coluna não reconhecida etc.)
            boolean temConflito = detectarConflitos(result.fontes);
            boolean baixaConfianca = result.fontes != null && result.fontes.stream().anyMatch(f -> f.confianca != null && f.confianca < 60);
            boolean temAlerta = result.fontes != null && result.fontes.stream().anyMatch(f -> "_alerta".equals(f.campo));
            extracao.setStatus(temConflito || baixaConfianca || temAlerta ? "revisao" : "concluido");
            // atualiza doc status — mantém "revisao" nos documentos que o extrator sinalizou (ex.: formato não suportado)
            for (EmpreendimentoDocumento d : docs) {
                if (!"revisao".equals(d.getStatusProcessamento())) d.setStatusProcessamento("concluido");
                documentoRepository.save(d);
            }

            extracaoRepository.save(extracao);
            log.info("Extracao {} concluida status={}", extracaoId, extracao.getStatus());
        } catch (Exception e) {
            log.error("Extracao {} falhou: {}", extracaoId, e.getMessage(), e);
            try {
                extracao.setStatus("erro");
                extracao.setErro(e.getMessage());
                extracao.setDataConclusao(Instant.now());
                extracaoRepository.save(extracao);
                List<Long> docIds = extracaoDocumentoRepository.findByExtracaoId(extracaoId).stream()
                    .map(EmpreendimentoExtracaoDocumento::getDocumentoId).toList();
                for (Long docId : docIds) documentoRepository.findById(docId).ifPresent(d -> { d.setStatusProcessamento("erro"); documentoRepository.save(d); });
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
        EmpreendimentoExtracao e = extracaoRepository.findById(id).orElseThrow(() -> new RuntimeException("Extração não encontrada"));
        List<Long> docIds = extracaoDocumentoRepository.findByExtracaoId(id).stream()
            .map(EmpreendimentoExtracaoDocumento::getDocumentoId).toList();
        List<EmpreendimentoFonte> fontes = fonteRepository.findByExtracaoId(id);
        // conflitos
        Map<String, List<EmpreendimentoFonte>> porCampo = fontes.stream().collect(Collectors.groupingBy(EmpreendimentoFonte::getCampo));
        List<EmpreendimentoExtracaoDTO.ConflitoDTO> conflitos = new ArrayList<>();
        for (Map.Entry<String, List<EmpreendimentoFonte>> entry : porCampo.entrySet()) {
            Set<String> vals = entry.getValue().stream().map(f -> f.getValorExtraido()!=null?f.getValorExtraido().toLowerCase():"").collect(Collectors.toSet());
            if (vals.size() > 1) {
                conflitos.add(EmpreendimentoExtracaoDTO.ConflitoDTO.builder()
                    .campo(entry.getKey())
                    .valores(entry.getValue().stream().map(f -> EmpreendimentoExtracaoDTO.FonteDTO.builder()
                        .id(f.getId()).documentoId(f.getDocumentoId()).documentoNome(f.getDocumentoNome())
                        .campo(f.getCampo()).valorExtraido(f.getValorExtraido()).pagina(f.getPagina()).trecho(f.getTrecho()).confianca(f.getConfianca()).build()).toList())
                    .build());
            }
        }
        Object resultadoJson = null;
        try { if (e.getResultado()!=null) resultadoJson = objectMapper.readValue(e.getResultado(), Object.class); } catch (Exception ex) { resultadoJson = e.getResultado(); }

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
        EmpreendimentoExtracao e = extracaoRepository.findById(id).orElseThrow(() -> new RuntimeException("Extração não encontrada"));
        e.setStatus("pendente");
        e.setErro(null);
        e.setDataProcessamento(Instant.now());
        extracaoRepository.save(e);
        processarAsync(id);
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
