package crm_imobiliario.back.model.service.empreendimento.extracao;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class SpreadsheetTableExtractorTest {

    private final SpreadsheetTableExtractor extractor = new SpreadsheetTableExtractor(new UnidadeHeaderNormalizer());

    @Test
    void extraiTabelaComCabecalhoDeslocado() throws Exception {
        // título do empreendimento na linha 0, cabeçalho só na linha 2 — "cabeçalho deslocado" (§6.2)
        byte[] xlsx = gerarXlsx(sheet -> {
            linha(sheet, 0, "RESIDENCIAL EXEMPLO");
            linha(sheet, 1);
            linha(sheet, 2, "Bloco", "Unidade", "Área Privativa", "Situação", "Valor Total");
            linha(sheet, 3, "Bloco 01", "B01.101", "35,74", "Disponível", "227200");
            linha(sheet, 4, "Bloco 01", "B01.102", "39,50", "Vendida", "242500");
        });

        SpreadsheetTableExtractor.Resultado r = extractor.extrair(xlsx, "xlsx");
        assertEquals(1, r.tabelas.size());
        TabelaBruta tabela = r.tabelas.get(0);
        assertEquals(2, tabela.linhas.size());
        assertEquals("B01.101", tabela.linhas.get(0).celulas.get(1));

        UnidadeTableMapper mapper = new UnidadeTableMapper(new UnidadeHeaderNormalizer());
        var mapeado = mapper.mapear(tabela, 1L, "planilha.xlsx");
        assertEquals(2, mapeado.unidades.size());
    }

    @Test
    void abaSemTabelaDeUnidadesEIgnoradaSemErro() throws Exception {
        byte[] xlsx = gerarXlsx(sheet -> linha(sheet, 0, "apenas texto solto, sem tabela"));
        SpreadsheetTableExtractor.Resultado r = extractor.extrair(xlsx, "xlsx");
        assertTrue(r.tabelas.isEmpty());
        assertFalse(r.alertas.isEmpty());
    }

    @Test
    void csvComPontoEVirgulaEValoresEntreAspas() {
        String csv = "Bloco;Unidade;Situação;Valor Total\n"
                + "Bloco 01;B01.101;Disponível;\"227.200,00\"\n";
        SpreadsheetTableExtractor.Resultado r = null;
        try {
            r = extractor.extrair(csv.getBytes(StandardCharsets.UTF_8), "csv");
        } catch (Exception e) {
            fail(e);
        }
        assertEquals(1, r.tabelas.size());
        assertEquals("B01.101", r.tabelas.get(0).linhas.get(0).celulas.get(1));
    }

    private byte[] gerarXlsx(java.util.function.Consumer<Sheet> preencher) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Unidades");
            preencher.accept(sheet);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    private void linha(Sheet sheet, int indice, String... valores) {
        Row row = sheet.createRow(indice);
        for (int i = 0; i < valores.length; i++) row.createCell(i).setCellValue(valores[i]);
    }
}
