package crm_imobiliario.back.model.service.empreendimento.extracao;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;

class WordTextExtractorTest {

    private final WordTextExtractor extractor = new WordTextExtractor();

    @Test
    void extraiTextoEATabelaDeUmDocx() throws Exception {
        byte[] docx = gerarDocxComTabela();
        WordTextExtractor.Resultado r = extractor.extrair(docx, "docx");

        assertTrue(r.texto.contains("Memorial descritivo"));
        assertEquals(1, r.tabelas.size());
        TabelaBruta tabela = r.tabelas.get(0);
        assertEquals(List_("BLOCO", "UNIDADE", "SITUAÇÃO"), tabela.cabecalhos);
        assertEquals(1, tabela.linhas.size());
        assertEquals("B01.101", tabela.linhas.get(0).celulas.get(1));

        UnidadeTableMapper mapper = new UnidadeTableMapper(new UnidadeHeaderNormalizer());
        var mapeado = mapper.mapear(tabela, 1L, "memorial.docx");
        assertEquals(1, mapeado.unidades.size());
        assertEquals("disponivel", mapeado.unidades.get(0).valor(CampoUnidade.SITUACAO));
    }

    @Test
    void docxSemTabelaRetornaSoTexto() throws Exception {
        byte[] docx = gerarDocxSemTabela();
        WordTextExtractor.Resultado r = extractor.extrair(docx, "docx");
        assertTrue(r.texto.contains("Diferenciais: piscina, academia"));
        assertTrue(r.tabelas.isEmpty());
    }

    private java.util.List<String> List_(String... valores) {
        return java.util.List.of(valores);
    }

    private byte[] gerarDocxComTabela() throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph p = doc.createParagraph();
            p.createRun().setText("Memorial descritivo do empreendimento.");

            XWPFTable table = doc.createTable(2, 3);
            String[] cabecalho = {"BLOCO", "UNIDADE", "SITUAÇÃO"};
            for (int i = 0; i < cabecalho.length; i++) table.getRow(0).getCell(i).setText(cabecalho[i]);
            String[] linha = {"BLOCO 01", "B01.101", "Disponível"};
            for (int i = 0; i < linha.length; i++) table.getRow(1).getCell(i).setText(linha[i]);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            return out.toByteArray();
        }
    }

    private byte[] gerarDocxSemTabela() throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {
            doc.createParagraph().createRun().setText("Diferenciais: piscina, academia, playground.");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            return out.toByteArray();
        }
    }
}
