package crm_imobiliario.back.model.service.empreendimento.extracao;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import crm_imobiliario.back.model.entity.EmpreendimentoDocumento;
import crm_imobiliario.back.model.service.empreendimento.EmpreendimentoExtractionService;

/**
 * Testes do orquestrador ponta-a-ponta (arquivo real em disco -> resultado
 * estruturado), cobrindo os cenários do §22 do spec de importação: conflito
 * entre documentos, formato não suportado, falha de leitura, tipologia
 * Garden, subsídio COHAPAR e ordem de colunas diferente entre documentos.
 */
class DocumentExtractionOrchestratorTest {

    @TempDir
    Path tempDir;

    private final DocumentExtractionOrchestrator orchestrator = new DocumentExtractionOrchestrator(
            new PdfTableExtractor(new UnidadeHeaderNormalizer()),
            new SpreadsheetTableExtractor(new UnidadeHeaderNormalizer()),
            new WordTextExtractor(),
            new UnidadeTableMapper(new UnidadeHeaderNormalizer()),
            new MemorialTextExtractor());

    private EmpreendimentoDocumento documento(long id, String nomeOriginal, Path caminho) {
        EmpreendimentoDocumento d = new EmpreendimentoDocumento();
        d.setId(id);
        d.setNomeOriginal(nomeOriginal);
        String ext = nomeOriginal.substring(nomeOriginal.lastIndexOf('.') + 1);
        d.setTipo(ext);
        d.setCaminho(caminho.toString());
        d.setStatusProcessamento("pendente");
        return d;
    }

    private Path escreverPdf(String nomeArquivo, String... colunas) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                escreverLinha(cs, 700, colunas);
            }
            Path p = tempDir.resolve(nomeArquivo);
            doc.save(p.toFile());
            return p;
        }
    }

    private Path escreverPdfComLinhas(String nomeArquivo, String[] cabecalho, String[]... linhas) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = 700;
                escreverLinha(cs, y, cabecalho);
                for (String[] linha : linhas) {
                    y -= 30;
                    escreverLinha(cs, y, linha);
                }
            }
            Path p = tempDir.resolve(nomeArquivo);
            doc.save(p.toFile());
            return p;
        }
    }

    private void escreverLinha(PDPageContentStream cs, float y, String... colunas) throws IOException {
        float x = 50;
        for (String coluna : colunas) {
            cs.beginText();
            cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
            cs.newLineAtOffset(x, y);
            cs.showText(coluna);
            cs.endText();
            x += 120;
        }
    }

    @Test
    void extraiUnidadesDeUmUnicoPdf() throws IOException {
        Path pdf = escreverPdfComLinhas("tabela.pdf",
                new String[]{"BLOCO", "UNIDADE", "TIPOLOGIA", "SITUAÇÃO", "VALOR TOTAL"},
                new String[]{"BLOCO 01", "B01.101", "2Q", "Disponível", "R$ 227.200,00"});

        EmpreendimentoExtractionService.ExtractionResult r = orchestrator.extract(List.of(documento(1, "tabela.pdf", pdf)));

        @SuppressWarnings("unchecked")
        var resultado = (java.util.Map<String, Object>) r.resultadoJson;
        @SuppressWarnings("unchecked")
        var unidades = (List<java.util.Map<String, Object>>) resultado.get("unidades");
        assertEquals(1, unidades.size());
        assertEquals("B01.101", unidades.get(0).get("chave"));
    }

    @Test
    void detectaConflitoEntreDoisDocumentosParaAMesmaUnidade() throws IOException {
        Path pdf1 = escreverPdfComLinhas("doc1.pdf",
                new String[]{"BLOCO", "UNIDADE", "SITUAÇÃO", "VALOR TOTAL"},
                new String[]{"BLOCO 01", "B01.101", "Disponível", "R$ 227.200,00"});
        Path pdf2 = escreverPdfComLinhas("doc2.pdf",
                new String[]{"BLOCO", "UNIDADE", "SITUAÇÃO", "VALOR TOTAL"},
                new String[]{"BLOCO 01", "B01.101", "Disponível", "R$ 235.000,00"});

        var docs = List.of(documento(1, "doc1.pdf", pdf1), documento(2, "doc2.pdf", pdf2));
        EmpreendimentoExtractionService.ExtractionResult r = orchestrator.extract(docs);

        boolean temDoisValoresDistintosParaMesmoCampo = r.fontes.stream()
                .filter(f -> f.campo.equals("unidades[B01.101].VALOR_TOTAL"))
                .map(f -> f.valorExtraido)
                .distinct().count() > 1;
        assertTrue(temDoisValoresDistintosParaMesmoCampo, "deveria preservar os dois valores divergentes como fontes distintas");
    }

    @Test
    void ordemDeColunasDiferenteEntreDocumentosNaoAfetaOResultado() throws IOException {
        // um documento tem VALOR TOTAL antes de SITUAÇÃO, outro depois — mapeamento é por nome, não posição
        Path pdf1 = escreverPdfComLinhas("ordemA.pdf",
                new String[]{"BLOCO", "UNIDADE", "VALOR TOTAL", "SITUAÇÃO"},
                new String[]{"BLOCO 01", "B01.101", "R$ 227.200,00", "Disponível"});
        Path pdf2 = escreverPdfComLinhas("ordemB.pdf",
                new String[]{"BLOCO", "UNIDADE", "SITUAÇÃO", "VALOR TOTAL"},
                new String[]{"BLOCO 02", "B02.101", "Disponível", "R$ 250.000,00"});

        var docs = List.of(documento(1, "ordemA.pdf", pdf1), documento(2, "ordemB.pdf", pdf2));
        EmpreendimentoExtractionService.ExtractionResult r = orchestrator.extract(docs);

        boolean b01ValorCorreto = r.fontes.stream().anyMatch(f -> f.campo.equals("unidades[B01.101].VALOR_TOTAL") && "227200.00".equals(f.valorExtraido));
        boolean b02ValorCorreto = r.fontes.stream().anyMatch(f -> f.campo.equals("unidades[B02.101].VALOR_TOTAL") && "250000.00".equals(f.valorExtraido));
        assertTrue(b01ValorCorreto);
        assertTrue(b02ValorCorreto);
    }

    @Test
    void unidadeGardenComOutrasAreasEExtraidaCorretamente() throws IOException {
        Path pdf = escreverPdfComLinhas("garden.pdf",
                new String[]{"BLOCO", "UNIDADE", "TIPOLOGIA", "OUTRAS ÁREAS"},
                new String[]{"BLOCO 01", "B01.101", "2Q - Garden", "12,85 m²"});

        EmpreendimentoExtractionService.ExtractionResult r = orchestrator.extract(List.of(documento(1, "garden.pdf", pdf)));
        boolean tipologiaGarden = r.fontes.stream().anyMatch(f -> f.campo.equals("unidades[B01.101].TIPOLOGIA") && f.valorExtraido.contains("Garden"));
        boolean outrasAreas = r.fontes.stream().anyMatch(f -> f.campo.equals("unidades[B01.101].OUTRAS_AREAS") && "12.85".equals(f.valorExtraido));
        assertTrue(tipologiaGarden);
        assertTrue(outrasAreas);
    }

    @Test
    void subsidioCohaparEReconhecidoQuandoPresente() throws IOException {
        Path pdf = escreverPdfComLinhas("cohapar.pdf",
                new String[]{"BLOCO", "UNIDADE", "SUBSÍDIO COHAPAR"},
                new String[]{"BLOCO 01", "B01.101", "R$ 20.000,00"});

        EmpreendimentoExtractionService.ExtractionResult r = orchestrator.extract(List.of(documento(1, "cohapar.pdf", pdf)));
        assertTrue(r.fontes.stream().anyMatch(f -> f.campo.equals("unidades[B01.101].SUBSIDIO_COHAPAR") && "20000.00".equals(f.valorExtraido)));
    }

    @Test
    void formatoNaoSuportadoGeraAlertaEMarcaDocumentoParaRevisao() throws IOException {
        Path arquivo = tempDir.resolve("planta.dwg");
        Files.writeString(arquivo, "conteudo binario simulado");
        EmpreendimentoDocumento doc = documento(1, "planta.dwg", arquivo);

        EmpreendimentoExtractionService.ExtractionResult r = orchestrator.extract(List.of(doc));

        assertTrue(r.fontes.stream().anyMatch(f -> "_alerta".equals(f.campo) && f.valorExtraido.contains("não é suportado")));
        assertEquals("revisao", doc.getStatusProcessamento());
    }

    @Test
    void arquivoIlegivelNaoQuebraExtracaoDosDemais() throws IOException {
        Path corrompido = tempDir.resolve("corrompido.pdf");
        Files.writeString(corrompido, "isto nao e um pdf valido");
        Path valido = escreverPdfComLinhas("valido.pdf",
                new String[]{"BLOCO", "UNIDADE", "VALOR TOTAL"},
                new String[]{"BLOCO 01", "B01.101", "R$ 200.000,00"});

        var docs = List.of(documento(1, "corrompido.pdf", corrompido), documento(2, "valido.pdf", valido));
        EmpreendimentoExtractionService.ExtractionResult r = orchestrator.extract(docs);

        assertTrue(r.fontes.stream().anyMatch(f -> "_alerta".equals(f.campo) && f.valorExtraido.contains("corrompido.pdf")));
        assertTrue(r.fontes.stream().anyMatch(f -> f.campo.equals("unidades[B01.101].VALOR_TOTAL")));
    }

    @Test
    void csvEPdfCombinadosSaoMescladosPorChaveDeUnidade() throws IOException {
        Path pdf = escreverPdfComLinhas("tabela.pdf",
                new String[]{"BLOCO", "UNIDADE", "VALOR TOTAL"},
                new String[]{"BLOCO 01", "B01.101", "R$ 200.000,00"});
        Path csv = tempDir.resolve("extra.csv");
        Files.writeString(csv, "Bloco;Unidade;Situação\nBloco 01;B01.101;Disponível\n", StandardCharsets.UTF_8);

        var docs = List.of(documento(1, "tabela.pdf", pdf), documento(2, "extra.csv", csv));
        EmpreendimentoExtractionService.ExtractionResult r = orchestrator.extract(docs);

        assertTrue(r.fontes.stream().anyMatch(f -> f.campo.equals("unidades[B01.101].VALOR_TOTAL")));
        assertTrue(r.fontes.stream().anyMatch(f -> f.campo.equals("unidades[B01.101].SITUACAO")));
    }
}
