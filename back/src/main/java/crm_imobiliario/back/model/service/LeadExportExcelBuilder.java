package crm_imobiliario.back.model.service;

import java.time.format.DateTimeFormatter;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xddf.usermodel.chart.AxisCrosses;
import org.apache.poi.xddf.usermodel.chart.AxisPosition;
import org.apache.poi.xddf.usermodel.chart.BarDirection;
import org.apache.poi.xddf.usermodel.chart.ChartTypes;
import org.apache.poi.xddf.usermodel.chart.LegendPosition;
import org.apache.poi.xddf.usermodel.chart.XDDFBarChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFCategoryAxis;
import org.apache.poi.xddf.usermodel.chart.XDDFCategoryDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFChartLegend;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSourcesFactory;
import org.apache.poi.xddf.usermodel.chart.XDDFNumericalDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFValueAxis;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import crm_imobiliario.back.model.dto.LeadListaDTO;

/**
 * Mecânica pura do POI (planilhas + gráficos nativos) para o relatório de exportação de leads.
 * Não contém nenhuma agregação/regra de negócio — isso fica em {@link LeadExportService}.
 */
final class LeadExportExcelBuilder {

    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // rótulos de exibição das etapas do funil — precisam ficar em sincronia com
    // components/FunilDashboard.tsx (ETAPAS)
    private static final Map<String, String> FUNIL_LABELS = Map.of(
            "lead", "Leads",
            "oportunidade", "Oportunidades",
            "visita-agendada", "Visitas Agendadas",
            "visita-realizada", "Visitas Realizadas",
            "pasta", "Pastas",
            "aprovado", "Aprovados",
            "contrato", "Contratos");

    // precisa ficar em sincronia com service/origemOptions.ts
    private static final Map<String, String> ORIGEM_LABELS = Map.of(
            "acao_externa", "Ação Externa",
            "captacao_corretor", "Captação Corretor",
            "captacao_gerente", "Captação Gerente",
            "digital_lead", "Digital Lead",
            "espontaneo", "Espontâneo",
            "ganhe_mais", "Ganhe+",
            "oferta_ativa", "Oferta Ativa");

    // precisa ficar em sincronia com service/historicoOptions.ts
    private static final Map<String, String> HISTORICO_LABELS = Map.of(
            "novo", "Novo",
            "reaquecido", "Reaquecido",
            "vencido", "Vencido");

    private LeadExportExcelBuilder() {}

    static void buildLeadsSheet(XSSFWorkbook wb, java.util.List<LeadListaDTO> leads) {
        XSSFSheet sheet = wb.createSheet("Leads");
        CellStyle header = headerStyle(wb);
        String[] colunas = {
                "Nome", "Telefone", "Email", "Status", "Histórico", "Origem",
                "Valor de Interesse", "Empreendimento", "Data de Criação", "Data de Atualização"
        };
        Row cabecalho = sheet.createRow(0);
        for (int c = 0; c < colunas.length; c++) {
            Cell cell = cabecalho.createCell(c);
            cell.setCellValue(colunas[c]);
            cell.setCellStyle(header);
            sheet.setColumnWidth(c, 22 * 256);
        }

        int r = 1;
        for (LeadListaDTO lead : leads) {
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(nvl(lead.nome()));
            row.createCell(1).setCellValue(nvl(lead.telefone()));
            row.createCell(2).setCellValue(nvl(lead.email()));
            row.createCell(3).setCellValue(nvl(lead.status()));
            row.createCell(4).setCellValue(historicoLabel(lead.historico()));
            row.createCell(5).setCellValue(origemLabel(lead.origem()));
            row.createCell(6).setCellValue(lead.valorInteresse() != null ? lead.valorInteresse() : 0);
            row.createCell(7).setCellValue(nvl(lead.empreendimentoNome()));
            row.createCell(8).setCellValue(lead.dataCriacao() != null ? lead.dataCriacao().format(DATA_HORA) : "");
            row.createCell(9).setCellValue(lead.dataAtualizacao() != null ? lead.dataAtualizacao().format(DATA_HORA) : "");
        }
    }

    static void buildFunilSheet(XSSFWorkbook wb, Map<String, Long> contagens, java.util.List<String> ordem) {
        XSSFSheet sheet = wb.createSheet("Funil de Vendas");
        int ultimaLinha = escreverTabelaContagem(sheet, headerStyle(wb), "Etapa", ordem, contagens, LeadExportExcelBuilder::funilLabel);
        writeBarChart(sheet, "Funil de Vendas", 1, ultimaLinha);
    }

    // usado só quando não há nenhum lead no filtro — garante ao menos uma linha de dados,
    // sem a qual o intervalo de células do gráfico fica inválido (lastRow < firstRow)
    private static final String SEM_DADOS = "Nenhum lead encontrado";

    static void buildOrigemSheet(XSSFWorkbook wb, Map<String, Long> contagens) {
        XSSFSheet sheet = wb.createSheet("Origem dos Leads");
        java.util.List<String> chaves = chavesOuPlaceholder(contagens);
        int ultimaLinha = escreverTabelaContagem(sheet, headerStyle(wb), "Origem", chaves, contagens, LeadExportExcelBuilder::origemLabel);
        writePieChart(sheet, "Origem dos Leads", 1, ultimaLinha);
    }

    static void buildHistoricoSheet(XSSFWorkbook wb, Map<String, Long> contagens) {
        XSSFSheet sheet = wb.createSheet("Histórico dos Leads");
        java.util.List<String> chaves = chavesOuPlaceholder(contagens);
        int ultimaLinha = escreverTabelaContagem(sheet, headerStyle(wb), "Histórico", chaves, contagens, LeadExportExcelBuilder::historicoLabel);
        // só 3 categorias — barra é mais legível que uma pizza de 3 fatias, e reaproveita o
        // mesmo helper do gráfico do funil.
        writeBarChart(sheet, "Histórico dos Leads", 1, ultimaLinha);
    }

    private static java.util.List<String> chavesOuPlaceholder(Map<String, Long> contagens) {
        return contagens.isEmpty() ? java.util.List.of(SEM_DADOS) : java.util.List.copyOf(contagens.keySet());
    }

    /** Escreve cabeçalho + uma linha por chave (na ordem dada), retorna o índice (0-based) da última linha de dados. */
    private static int escreverTabelaContagem(XSSFSheet sheet, CellStyle headerStyle, String colunaLabel,
            java.util.List<String> ordemChaves, Map<String, Long> contagens, java.util.function.Function<String, String> label) {
        Row cabecalho = sheet.createRow(0);
        Cell c0 = cabecalho.createCell(0);
        c0.setCellValue(colunaLabel);
        c0.setCellStyle(headerStyle);
        Cell c1 = cabecalho.createCell(1);
        c1.setCellValue("Quantidade");
        c1.setCellStyle(headerStyle);
        sheet.setColumnWidth(0, 26 * 256);
        sheet.setColumnWidth(1, 14 * 256);

        int r = 1;
        for (String chave : ordemChaves) {
            Row row = sheet.createRow(r);
            row.createCell(0).setCellValue(label.apply(chave));
            row.createCell(1).setCellValue(contagens.getOrDefault(chave, 0L));
            r++;
        }
        return r - 1;
    }

    private static void writeBarChart(XSSFSheet sheet, String titulo, int primeiraLinhaDados, int ultimaLinhaDados) {
        XSSFDrawing drawing = sheet.createDrawingPatriarch();
        XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0, 3, 0, 12, 20);
        XSSFChart chart = drawing.createChart(anchor);
        chart.setTitleText(titulo);
        chart.setTitleOverlay(false);

        XDDFChartLegend legend = chart.getOrAddLegend();
        legend.setPosition(LegendPosition.BOTTOM);

        XDDFCategoryAxis eixoCategorias = chart.createCategoryAxis(AxisPosition.BOTTOM);
        XDDFValueAxis eixoValores = chart.createValueAxis(AxisPosition.LEFT);
        eixoValores.setCrosses(AxisCrosses.AUTO_ZERO);

        XDDFCategoryDataSource categorias = XDDFDataSourcesFactory.fromStringCellRange(
                sheet, new CellRangeAddress(primeiraLinhaDados, ultimaLinhaDados, 0, 0));
        XDDFNumericalDataSource<Double> valores = XDDFDataSourcesFactory.fromNumericCellRange(
                sheet, new CellRangeAddress(primeiraLinhaDados, ultimaLinhaDados, 1, 1));

        XDDFChartData data = chart.createData(ChartTypes.BAR, eixoCategorias, eixoValores);
        XDDFChartData.Series series = data.addSeries(categorias, valores);
        series.setTitle("Quantidade");
        ((XDDFBarChartData) data).setBarDirection(BarDirection.COL);

        chart.plot(data);
    }

    private static void writePieChart(XSSFSheet sheet, String titulo, int primeiraLinhaDados, int ultimaLinhaDados) {
        XSSFDrawing drawing = sheet.createDrawingPatriarch();
        XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0, 3, 0, 10, 18);
        XSSFChart chart = drawing.createChart(anchor);
        chart.setTitleText(titulo);
        chart.setTitleOverlay(false);

        XDDFCategoryDataSource categorias = XDDFDataSourcesFactory.fromStringCellRange(
                sheet, new CellRangeAddress(primeiraLinhaDados, ultimaLinhaDados, 0, 0));
        XDDFNumericalDataSource<Double> valores = XDDFDataSourcesFactory.fromNumericCellRange(
                sheet, new CellRangeAddress(primeiraLinhaDados, ultimaLinhaDados, 1, 1));

        // gráfico de pizza não usa eixos — createData espera null para categoria/valor
        XDDFChartData data = chart.createData(ChartTypes.PIE, null, null);
        XDDFChartData.Series series = data.addSeries(categorias, valores);
        series.setTitle("Quantidade");

        chart.plot(data);

        XDDFChartLegend legend = chart.getOrAddLegend();
        legend.setPosition(LegendPosition.RIGHT);
    }

    private static CellStyle headerStyle(XSSFWorkbook wb) {
        Font negrito = wb.createFont();
        negrito.setBold(true);
        CellStyle estilo = wb.createCellStyle();
        estilo.setFont(negrito);
        estilo.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        estilo.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
        return estilo;
    }

    private static String funilLabel(String etapa) {
        return FUNIL_LABELS.getOrDefault(etapa, etapa);
    }

    private static String origemLabel(String origem) {
        if (origem == null || origem.isBlank()) return "Sem origem";
        return ORIGEM_LABELS.getOrDefault(origem, origem);
    }

    private static String historicoLabel(String historico) {
        if (historico == null || historico.isBlank()) return "Sem histórico";
        return HISTORICO_LABELS.getOrDefault(historico, historico);
    }

    private static String nvl(String valor) {
        return valor != null ? valor : "";
    }
}
