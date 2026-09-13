package crm_imobiliario.back.model.service.empreendimento.extracao;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

/**
 * Valida o {@link PdfTableExtractor} contra PDFs sintéticos gerados no próprio
 * teste (sem embutir documentos reais de cliente no repositório). O layout de
 * cabeçalho de duas linhas empilhadas ("ÁREA" sobre "PRIVATIVA") replica o
 * padrão observado nos documentos reais da Pride/CVCRM usados para calibrar
 * este extrator durante o desenvolvimento.
 */
class PdfTableExtractorTest {

    private final PdfTableExtractor extractor = new PdfTableExtractor(new UnidadeHeaderNormalizer());

    @Test
    void extraiTabelaComCabecalhoDeUmaLinha() throws Exception {
        byte[] pdf = gerarPdf(cs -> {
            escrever(cs, 50, 700, 10, "BLOCO", "UNIDADE", "SITUAÇÃO", "VALOR TOTAL");
            escrever(cs, 50, 670, 10, "BLOCO 01", "B01.101", "Disponível", "R$ 227.200,00");
            escrever(cs, 50, 655, 10, "BLOCO 01", "B01.102", "Vendida", "R$ 242.500,00");
        });

        PdfTableExtractor.Resultado r = extractor.extrair(pdf, 0);
        assertEquals(1, r.tabelas.size());
        TabelaBruta tabela = r.tabelas.get(0);
        assertEquals(2, tabela.linhas.size());

        UnidadeTableMapper mapper = new UnidadeTableMapper(new UnidadeHeaderNormalizer());
        var mapeado = mapper.mapear(tabela, 1L, "teste.pdf");
        assertEquals(2, mapeado.unidades.size());
        assertEquals("227200.00", mapeado.unidades.get(0).valor(CampoUnidade.VALOR_TOTAL));
        assertEquals("disponivel", mapeado.unidades.get(0).valor(CampoUnidade.SITUACAO));
    }

    @Test
    void rodapeNaoEhConfundidoComLinhaDeDados() throws Exception {
        byte[] pdf = gerarPdf(cs -> {
            escrever(cs, 50, 700, 10, "BLOCO", "UNIDADE", "VALOR TOTAL");
            escrever(cs, 50, 670, 10, "BLOCO 01", "B01.101", "R$ 227.200,00");
            escrever(cs, 50, 650, 8, "1. Valores sujeitos a alteracao, consulte o especialista.");
            escrever(cs, 50, 635, 8, "Gerado dia 13/09/2026 por Fulano de Tal");
        });

        TabelaBruta tabela = extractor.extrair(pdf, 0).tabelas.get(0);
        assertEquals(1, tabela.linhas.size());
    }

    @Test
    void cabecalhoDeDuasLinhasEmpilhadasEReconstituido() throws Exception {
        // replica o layout real: "ÁREA" numa sub-linha e "PRIVATIVA" logo abaixo, na mesma coluna,
        // enquanto "UNIDADE" (rótulo de uma linha só) fica centralizado entre as duas sub-linhas.
        byte[] pdf = gerarPdf(cs -> {
            escrever(cs, 50, 706, 9, "", "", "ÁREA");
            escreverEm(cs, 50, 700, 9, "BLOCO", "UNIDADE");
            escrever(cs, 50, 694, 9, "", "", "PRIVATIVA");
            escrever(cs, 50, 660, 10, "BLOCO 01", "B01.101", "35,74 m²");
        });

        TabelaBruta tabela = extractor.extrair(pdf, 0).tabelas.get(0);
        assertTrue(tabela.cabecalhos.stream().anyMatch(h -> h.replace("Á", "A").toUpperCase().contains("PRIVATIVA")),
                "cabecalhos encontrados: " + tabela.cabecalhos);
        assertEquals(1, tabela.linhas.size());
        assertEquals("35,74 m²", tabela.linhas.get(0).celulas.get(2));
    }

    @Test
    void pdfSemTextoEExtraiveNaoQuebraEGeraAlerta() throws Exception {
        byte[] pdfVazio;
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage(PDRectangle.A4));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            pdfVazio = out.toByteArray();
        }
        PdfTableExtractor.Resultado r = extractor.extrair(pdfVazio, 0);
        assertTrue(r.tabelas.isEmpty());
        assertFalse(r.alertas.isEmpty());
    }

    // ---- helpers de geração de PDF sintético ----

    private byte[] gerarPdf(EscritorConteudo escritor) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                escritor.escrever(cs);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    /** Escreve colunas com espaçamento amplo e fixo (simula separação real entre colunas de tabela). */
    private void escrever(PDPageContentStream cs, float x, float y, float fontSize, String... colunas) {
        try {
            float cursor = x;
            for (String coluna : colunas) {
                if (!coluna.isEmpty()) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), fontSize);
                    cs.newLineAtOffset(cursor, y);
                    cs.showText(coluna);
                    cs.endText();
                }
                cursor += 120;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** Escreve só nas colunas 0 e 1 (para simular um rótulo de cabeçalho de uma linha só entre duas sub-linhas). */
    private void escreverEm(PDPageContentStream cs, float x, float y, float fontSize, String... colunas) {
        escrever(cs, x, y, fontSize, colunas);
    }

    @FunctionalInterface
    private interface EscritorConteudo {
        void escrever(PDPageContentStream cs) throws IOException;
    }
}
