package crm_imobiliario.back.model.service.empreendimento.extracao;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import crm_imobiliario.back.model.entity.EmpreendimentoDocumento;
import crm_imobiliario.back.model.service.empreendimento.EmpreendimentoExtractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Orquestrador de extração determinística: identifica o formato de cada
 * documento, encaminha para o extrator adequado, normaliza cabeçalhos e
 * monta o resultado estruturado para revisão — sem IA generativa (§5 do spec).
 *
 * Extração de unidades (tabelas de preço) é a parte confiável e testada desta
 * versão. Extração de campos gerais do empreendimento (identificação,
 * localização, características) a partir de texto livre é heurística e
 * conservadora: só preenche o que reconhece com um padrão explícito, marca
 * tudo mais como não encontrado para preenchimento manual — nunca inventa.
 */
@Slf4j
@Service("documentExtractionOrchestrator")
@RequiredArgsConstructor
public class DocumentExtractionOrchestrator implements EmpreendimentoExtractionService {

    private final PdfTableExtractor pdfTableExtractor;
    private final SpreadsheetTableExtractor spreadsheetTableExtractor;
    private final WordTextExtractor wordTextExtractor;
    private final UnidadeTableMapper unidadeTableMapper;
    private final MemorialTextExtractor memorialTextExtractor;

    @Value("${crm.extracao.max-pdf-pages:40}")
    private int maxPdfPages;

    private static final Set<String> EXT_PDF = Set.of("pdf");
    private static final Set<String> EXT_PLANILHA = Set.of("xlsx", "xls", "csv");
    private static final Set<String> EXT_WORD = Set.of("doc", "docx");

    @Override
    public ExtractionResult extract(List<EmpreendimentoDocumento> documentos) {
        List<EmpreendimentoFonteData> fontes = new ArrayList<>();
        List<String> alertas = new ArrayList<>();
        List<UnidadeExtraida> todasUnidades = new ArrayList<>();
        Map<String, Object> camposGerais = new LinkedHashMap<>();

        for (EmpreendimentoDocumento doc : documentos) {
            String ext = doc.getTipo() != null ? doc.getTipo().toLowerCase(Locale.ROOT) : "";
            try {
                byte[] bytes = Files.readAllBytes(Paths.get(doc.getCaminho()));
                if (EXT_PDF.contains(ext)) {
                    processarPdf(doc, bytes, todasUnidades, camposGerais, alertas);
                } else if (EXT_PLANILHA.contains(ext)) {
                    processarPlanilha(doc, bytes, ext, todasUnidades, alertas);
                } else if (EXT_WORD.contains(ext)) {
                    processarWord(doc, bytes, ext, todasUnidades, camposGerais, alertas);
                } else {
                    alertas.add("Formato \"" + ext + "\" (" + doc.getNomeOriginal() + ") não é suportado para extração automática — envie os dados manualmente.");
                    marcarRevisao(doc);
                }
            } catch (Exception e) {
                log.warn("Falha ao processar documento {} ({}): {}", doc.getId(), doc.getNomeOriginal(), e.getMessage(), e);
                alertas.add("Não foi possível ler o arquivo \"" + doc.getNomeOriginal() + "\": " + e.getMessage());
                marcarRevisao(doc);
            }
        }

        Map<String, List<UnidadeExtraida>> porUnidade = new LinkedHashMap<>();
        for (UnidadeExtraida u : todasUnidades) {
            porUnidade.computeIfAbsent(u.chaveIdentidade(), k -> new ArrayList<>()).add(u);
        }

        List<Map<String, Object>> unidadesJson = new ArrayList<>();
        for (Map.Entry<String, List<UnidadeExtraida>> entry : porUnidade.entrySet()) {
            Map<String, Object> unidadeJson = new LinkedHashMap<>();
            unidadeJson.put("chave", entry.getKey());
            // rastreabilidade da linha (§12 do spec): de qual documento/linha de tabela esta
            // unidade veio, usada pela tela de revisão para preencher Unidade.documentoOrigemId
            UnidadeExtraida primeiraOcorrencia = entry.getValue().get(0);
            unidadeJson.put("documentoOrigemId", primeiraOcorrencia.documentoId);
            unidadeJson.put("linhaOrigem", primeiraOcorrencia.linhaOrigem);
            for (CampoUnidade campo : CampoUnidade.values()) {
                // usa o primeiro valor não nulo encontrado; se documentos divergirem, todos viram fontes
                // (mesmo mecanismo de conflito já existente para campos do empreendimento)
                CampoEvidencia primeira = null;
                for (UnidadeExtraida u : entry.getValue()) {
                    CampoEvidencia ev = u.campos.get(campo);
                    if (ev == null || ev.valor == null) continue;
                    if (primeira == null) primeira = ev;
                    EmpreendimentoFonteData f = new EmpreendimentoFonteData();
                    f.campo = "unidades[" + entry.getKey() + "]." + campo.name();
                    f.valorExtraido = ev.valor;
                    f.confianca = ev.confianca;
                    f.pagina = ev.pagina;
                    f.trecho = ev.trechoOriginal;
                    f.documentoNome = ev.documentoNome;
                    f.documentoId = u.documentoId;
                    fontes.add(f);
                }
                if (primeira != null) unidadeJson.put(campo.name(), campoJson(primeira));
            }
            for (UnidadeExtraida u : entry.getValue()) alertas.addAll(u.alertas);
            unidadesJson.add(unidadeJson);
        }
        camposGerais.put("unidades", unidadesJson);

        for (Map.Entry<String, Object> topo : camposGerais.entrySet()) {
            if ("unidades".equals(topo.getKey())) continue;
            coletarFontes(topo.getValue(), topo.getKey(), fontes);
        }

        for (String alerta : alertas) {
            EmpreendimentoFonteData f = new EmpreendimentoFonteData();
            f.campo = "_alerta";
            f.valorExtraido = alerta;
            f.confianca = 100;
            f.documentoNome = "sistema";
            fontes.add(f);
        }

        ExtractionResult r = new ExtractionResult();
        r.resultadoJson = camposGerais;
        r.fontes = fontes;
        r.modelo = "regras-deterministicas";
        r.avisosTruncamento = alertas;
        return r;
    }

    private void processarPdf(EmpreendimentoDocumento doc, byte[] bytes, List<UnidadeExtraida> todasUnidades,
                               Map<String, Object> camposGerais, List<String> alertas) throws IOException {
        PdfTableExtractor.Resultado extraido = pdfTableExtractor.extrair(bytes, maxPdfPages);
        alertas.addAll(prefixarAlertas(extraido.alertas, doc.getNomeOriginal()));

        for (TabelaBruta tabela : extraido.tabelas) {
            UnidadeTableMapper.Resultado mapeado = unidadeTableMapper.mapear(tabela, doc.getId(), doc.getNomeOriginal());
            todasUnidades.addAll(mapeado.unidades);
            alertas.addAll(prefixarAlertas(mapeado.alertas, doc.getNomeOriginal()));
        }
        // texto livre é processado página a página (não como um único bloco) para preservar de qual
        // página cada campo veio (§5/§15 do spec) — livros comerciais reais espalham cada informação
        // (endereço, diferenciais, lazer) numa página diferente.
        if (extraido.tabelas.isEmpty()) {
            for (int i = 0; i < extraido.textoPorPagina.size(); i++) {
                String textoDaPagina = extraido.textoPorPagina.get(i);
                if (textoDaPagina == null || textoDaPagina.isBlank()) continue;
                memorialTextExtractor.extrair(textoDaPagina, doc, i + 1, camposGerais, alertas);
            }
        }
    }

    private void processarPlanilha(EmpreendimentoDocumento doc, byte[] bytes, String ext,
                                    List<UnidadeExtraida> todasUnidades, List<String> alertas) throws IOException {
        SpreadsheetTableExtractor.Resultado extraido = spreadsheetTableExtractor.extrair(bytes, ext);
        alertas.addAll(prefixarAlertas(extraido.alertas, doc.getNomeOriginal()));
        for (TabelaBruta tabela : extraido.tabelas) {
            UnidadeTableMapper.Resultado mapeado = unidadeTableMapper.mapear(tabela, doc.getId(), doc.getNomeOriginal());
            todasUnidades.addAll(mapeado.unidades);
            alertas.addAll(prefixarAlertas(mapeado.alertas, doc.getNomeOriginal()));
        }
    }

    private void processarWord(EmpreendimentoDocumento doc, byte[] bytes, String ext, List<UnidadeExtraida> todasUnidades,
                                Map<String, Object> camposGerais, List<String> alertas) throws IOException {
        WordTextExtractor.Resultado extraido = wordTextExtractor.extrair(bytes, ext);
        alertas.addAll(prefixarAlertas(extraido.alertas, doc.getNomeOriginal()));
        for (TabelaBruta tabela : extraido.tabelas) {
            UnidadeTableMapper.Resultado mapeado = unidadeTableMapper.mapear(tabela, doc.getId(), doc.getNomeOriginal());
            todasUnidades.addAll(mapeado.unidades);
            alertas.addAll(prefixarAlertas(mapeado.alertas, doc.getNomeOriginal()));
        }
        if (extraido.texto != null && !extraido.texto.isBlank()) {
            // DOCX/DOC não têm paginação confiável via POI — página fica nula (nunca inventada) e a
            // rastreabilidade continua garantida pelo nome do documento e pelo trecho original.
            memorialTextExtractor.extrair(extraido.texto, doc, null, camposGerais, alertas);
        }
    }

    private List<String> prefixarAlertas(List<String> alertas, String nomeDocumento) {
        return alertas.stream().map(a -> "[" + nomeDocumento + "] " + a).toList();
    }

    private void marcarRevisao(EmpreendimentoDocumento doc) {
        doc.setStatusProcessamento("revisao");
    }

    @SuppressWarnings("unchecked")
    private void coletarFontes(Object node, String prefixo, List<EmpreendimentoFonteData> out) {
        if (node instanceof Map) {
            Map<String, Object> mapa = (Map<String, Object>) node;
            if (mapa.containsKey("valor") && mapa.containsKey("confianca")) {
                Object valor = mapa.get("valor");
                if (valor == null || String.valueOf(valor).isBlank()) return;
                EmpreendimentoFonteData f = new EmpreendimentoFonteData();
                f.campo = prefixo;
                f.valorExtraido = String.valueOf(valor);
                Object conf = mapa.get("confianca");
                f.confianca = conf instanceof Number n ? n.intValue() : null;
                Object pag = mapa.get("pagina");
                f.pagina = pag instanceof Number n ? n.intValue() : null;
                Object trecho = mapa.get("trecho_original");
                f.trecho = trecho != null ? String.valueOf(trecho) : null;
                Object docNome = mapa.get("documento_nome");
                f.documentoNome = docNome != null ? String.valueOf(docNome) : null;
                out.add(f);
                return;
            }
            for (Map.Entry<String, Object> e : mapa.entrySet()) {
                coletarFontes(e.getValue(), prefixo + "." + e.getKey(), out);
            }
        } else if (node instanceof List<?> lista) {
            for (int i = 0; i < lista.size(); i++) coletarFontes(lista.get(i), prefixo + "[" + i + "]", out);
        }
    }

    private Map<String, Object> campoJson(CampoEvidencia ev) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("valor", ev.valor);
        m.put("confianca", ev.confianca);
        m.put("pagina", ev.pagina);
        m.put("trecho_original", ev.trechoOriginal);
        m.put("documento_nome", ev.documentoNome);
        return m;
    }
}
