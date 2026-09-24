package crm_imobiliario.back.model.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import crm_imobiliario.back.model.entity.Lead;
import crm_imobiliario.back.model.entity.Usuario;

/**
 * Gera o workbook de verdade (POI real) e lê os bytes de volta, no mesmo estilo já usado para
 * ler planilhas em SpreadsheetTableExtractor.java — confirma que as 4 abas existem, que os dados
 * batem com o esperado e que os gráficos foram efetivamente criados (não só a tabela de apoio).
 */
@ExtendWith(MockitoExtension.class)
class LeadExportServiceIntegrationTest {

    @Mock
    private LeadsService leadsService;

    private LeadExportService exportService;

    private void setUp() throws Exception {
        exportService = new LeadExportService();
        var field = LeadExportService.class.getDeclaredField("leadsService");
        field.setAccessible(true);
        field.set(exportService, leadsService);
    }

    private Lead leadEntity(Long id, String status, String origem, String historico) {
        Lead lead = new Lead();
        lead.setId(id);
        lead.setNome("Lead " + id);
        lead.setTelefone("11999999999");
        lead.setEmail("lead" + id + "@teste.com");
        lead.setStatus(status);
        lead.setOrigem(origem);
        lead.setHistorico(historico);
        lead.setValorInteresse(100000L);
        lead.setAtivo(true);
        lead.setDataCriacao(LocalDateTime.now());
        lead.setDataAtualizacao(LocalDateTime.now());
        return lead;
    }

    @Test
    void geraWorkbookComQuatroAbasEDadosCorretos() throws Exception {
        setUp();
        when(leadsService.findAllForExport(any(), any(), any(), any(), any())).thenReturn(List.of(
                leadEntity(1L, "lead", "digital_lead", "novo"),
                leadEntity(2L, "pasta", "espontaneo", "reaquecido")));

        byte[] bytes = exportService.exportarExcel(null, null, null, null, null);
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);

        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            assertEquals(4, wb.getNumberOfSheets());
            assertNotNull(wb.getSheet("Leads"));
            assertNotNull(wb.getSheet("Funil de Vendas"));
            assertNotNull(wb.getSheet("Origem dos Leads"));
            assertNotNull(wb.getSheet("Histórico dos Leads"));

            Sheet leadsSheet = wb.getSheet("Leads");
            assertEquals(2, leadsSheet.getLastRowNum(), "cabeçalho + 2 linhas de dados => lastRowNum == 2");

            Sheet funilSheet = wb.getSheet("Funil de Vendas");
            assertEquals(2.0, valorNumerico(funilSheet, 1)); // "lead": todos os 2 leads contam
            assertEquals(1.0, valorNumerico(funilSheet, 5)); // "pasta": só o lead 2
            assertEquals(0.0, valorNumerico(funilSheet, 6)); // "aprovado": ninguém chegou lá

            XSSFSheet xssfFunil = (XSSFSheet) funilSheet;
            List<XSSFChart> charts = xssfFunil.getDrawingPatriarch().getCharts();
            assertEquals(1, charts.size(), "aba do funil deve ter exatamente um gráfico");

            XSSFSheet xssfOrigem = (XSSFSheet) wb.getSheet("Origem dos Leads");
            assertEquals(1, xssfOrigem.getDrawingPatriarch().getCharts().size());
        }
    }

    @Test
    void listaVaziaAindaGeraWorkbookValidoComQuatroAbas() throws Exception {
        setUp();
        when(leadsService.findAllForExport(any(), any(), any(), any(), any())).thenReturn(List.of());

        byte[] bytes = exportService.exportarExcel("nada", "all", "all", "all", "all");

        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            assertEquals(4, wb.getNumberOfSheets());
            assertEquals(0, wb.getSheet("Leads").getLastRowNum(), "só o cabeçalho, sem dados");
        }
    }

    private double valorNumerico(Sheet sheet, int linha) {
        Row row = sheet.getRow(linha);
        return row.getCell(1).getNumericCellValue();
    }
}
