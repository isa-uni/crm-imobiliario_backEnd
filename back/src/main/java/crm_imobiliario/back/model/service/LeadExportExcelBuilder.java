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
        XSSFSheet sheet = wb.createSheet("Leads");//cria uma aba leads
        CellStyle header = headerStyle(wb);
        String[] colunas = {//titulo das colunas
                "Nome", "Telefone", "Email", "Status", "Histórico", "Origem",
                "Valor de Interesse", "Empreendimento", "Data de Criação", "Data de Atualização"
        };
        Row cabecalho = sheet.createRow(0);//primeira linha da planilha
        for (int c = 0; c < colunas.length; c++) {//percorre todos os titulos
            Cell cell = cabecalho.createCell(c);//cria uma celula na posição atual
            cell.setCellValue(colunas[c]);//coloca o nome da coluna na celula
            cell.setCellStyle(header);//aplica formatacao do cabecalho
            sheet.setColumnWidth(c, 22 * 256);
        }

        int r = 1;//controle para linha atual, começa em 1 pois 0 é o cabecalho
        for (LeadListaDTO lead : leads) {//percorre cada cliente
            Row row = sheet.createRow(r++);//nova linha para o cliente
            //Cada linha pega uma informação do cliente e coloca em uma coluna.
            row.createCell(0).setCellValue(nvl(lead.nome()));
            row.createCell(1).setCellValue(nvl(lead.telefone()));
            row.createCell(2).setCellValue(nvl(lead.email()));
            row.createCell(3).setCellValue(nvl(lead.status()));
            row.createCell(4).setCellValue(historicoLabel(lead.historico()));//pega o histórico e transforma o código técnico em um texto amigável.
            row.createCell(5).setCellValue(origemLabel(lead.origem()));//mesma coisa com a origem
            row.createCell(6).setCellValue(lead.valorInteresse() != null ? lead.valorInteresse() : 0);//Se o cliente possuir um valor de interesse, utiliza esse valor.
            row.createCell(7).setCellValue(nvl(lead.empreendimentoNome()));
            row.createCell(8).setCellValue(lead.dataCriacao() != null ? lead.dataCriacao().format(DATA_HORA) : "");
            row.createCell(9).setCellValue(lead.dataAtualizacao() != null ? lead.dataAtualizacao().format(DATA_HORA) : "");
        }
    }

    static void buildFunilSheet(XSSFWorkbook wb, Map<String, Long> contagens, java.util.List<String> ordem) {
        //wb = arquivo excel, contagens é a quantidade de clientes em cada etapa, e ordem do funil
        XSSFSheet sheet = wb.createSheet("Funil de Vendas");//cria a aba
        int ultimaLinha = escreverTabelaContagem(sheet, headerStyle(wb), "Etapa", ordem, contagens, LeadExportExcelBuilder::funilLabel);
        //escreve a tabela com as etapas e suas quantidades e depois troca por um nome amigavel
        writeBarChart(sheet, "Funil de Vendas", 1, ultimaLinha);//cria o grafico de barras utilizando a tabela
    }

    // usado só quando não há nenhum lead no filtro — garante ao menos uma linha de dados,
    // sem a qual o intervalo de células do gráfico fica inválido (lastRow < firstRow)
    private static final String SEM_DADOS = "Nenhum lead encontrado";

    static void buildOrigemSheet(XSSFWorkbook wb, Map<String, Long> contagens) {
        XSSFSheet sheet = wb.createSheet("Origem dos Leads");
        java.util.List<String> chaves = chavesOuPlaceholder(contagens);//verifica se existem informações para preencher a tabela.
        int ultimaLinha = escreverTabelaContagem(sheet, headerStyle(wb), "Origem", chaves, contagens, LeadExportExcelBuilder::origemLabel);
        //Cria a tabela com as origens e suas quantidades.
        writePieChart(sheet, "Origem dos Leads", 1, ultimaLinha);//Cria um gráfico de pizza.
    }

    static void buildHistoricoSheet(XSSFWorkbook wb, Map<String, Long> contagens) {
        XSSFSheet sheet = wb.createSheet("Histórico dos Leads");
        java.util.List<String> chaves = chavesOuPlaceholder(contagens);//verifica se existe os dados
        int ultimaLinha = escreverTabelaContagem(sheet, headerStyle(wb), "Histórico", chaves, contagens, LeadExportExcelBuilder::historicoLabel);
        // Cria uma tabela com os históricos e suas quantidades.
        writeBarChart(sheet, "Histórico dos Leads", 1, ultimaLinha);//Cria um gráfico de barras.
    }

    private static java.util.List<String> chavesOuPlaceholder(Map<String, Long> contagens) {
        return contagens.isEmpty() ? java.util.List.of(SEM_DADOS) : java.util.List.copyOf(contagens.keySet());
        //Se o mapa estiver vazio, cria uma lista contendo Nenhum lead encontrado. Se houver dados, pega as chaves do mapa e transforma em uma lista.
    }

    /** Escreve cabeçalho + uma linha por chave (na ordem dada), retorna o índice (0-based) da última linha de dados. */
    private static int escreverTabelaContagem(XSSFSheet sheet, CellStyle headerStyle, String colunaLabel,
            java.util.List<String> ordemChaves, Map<String, Long> contagens, java.util.function.Function<String, String> label) {
        //escrever tabelas de resumo.
        //sheet	é aba onde a tabela será criada. headerStyle é formatação do cabeçalho. colunaLabel	é o nome da primeira coluna.
        //ordemChaves é a lista dos itens que serão apresentados. contagens	é a quantidade de cada item. label é a função que transforma o nome técnico em nome amigável.

        Row cabecalho = sheet.createRow(0);//criando a primeira linha
        Cell c0 = cabecalho.createCell(0);//criando primeira coluna
        c0.setCellValue(colunaLabel);//coloca o nome recebido em colunaLabel.
        c0.setCellStyle(headerStyle);
        Cell c1 = cabecalho.createCell(1);
        c1.setCellValue("Quantidade");
        c1.setCellStyle(headerStyle);
        //Cria a segunda coluna com o nome Quantidade.
        sheet.setColumnWidth(0, 26 * 256);
        sheet.setColumnWidth(1, 14 * 256);
        //Define a largura da primeira e da segunda coluna.

        int r = 1;
        for (String chave : ordemChaves) {//começa na segunda linha e percorre todas as chaves.
            Row row = sheet.createRow(r);//cria linha
            row.createCell(0).setCellValue(label.apply(chave));//Coloca o nome amigável na primeira coluna.
            row.createCell(1).setCellValue(contagens.getOrDefault(chave, 0L));//Pega a quantidade daquela chave.
            r++;//proxima
        }
        return r - 1;//Devolve o índice da última linha de dados.
    }

    private static void writeBarChart(XSSFSheet sheet, String titulo, int primeiraLinhaDados, int ultimaLinhaDados) {
        XSSFDrawing drawing = sheet.createDrawingPatriarch();//Essa linha cria uma área de desenho dentro da aba.   
        XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0, 3, 0, 12, 20);//Define a posição e o tamanho do gráfico na planilha.
        XSSFChart chart = drawing.createChart(anchor);//Cria o gráfico naquela posição.
        chart.setTitleText(titulo);
        chart.setTitleOverlay(false);
        //Define o título e informa que ele não deve ficar sobreposto à área do gráfico.

        XDDFChartLegend legend = chart.getOrAddLegend();
        legend.setPosition(LegendPosition.BOTTOM);
        //Adiciona a legenda e posiciona na parte inferior.

        XDDFCategoryAxis eixoCategorias = chart.createCategoryAxis(AxisPosition.BOTTOM);//Cria o eixo das categorias.
        XDDFValueAxis eixoValores = chart.createValueAxis(AxisPosition.LEFT);//Cria o eixo dos valores numéricos.
        eixoValores.setCrosses(AxisCrosses.AUTO_ZERO);

        XDDFCategoryDataSource categorias = XDDFDataSourcesFactory.fromStringCellRange(
                sheet, new CellRangeAddress(primeiraLinhaDados, ultimaLinhaDados, 0, 0));
        //Pegue os textos da primeira coluna, entre a primeira e a última linha de dados

        XDDFNumericalDataSource<Double> valores = XDDFDataSourcesFactory.fromNumericCellRange(
                sheet, new CellRangeAddress(primeiraLinhaDados, ultimaLinhaDados, 1, 1));
        //Agora ele pega os números da segunda coluna.

        XDDFChartData data = chart.createData(ChartTypes.BAR, eixoCategorias, eixoValores);//informa que o tipo de gráfico será de barras.
        XDDFChartData.Series series = data.addSeries(categorias, valores);//Adiciona os dados ao gráfico.
        series.setTitle("Quantidade");//Define o nome da série.
        ((XDDFBarChartData) data).setBarDirection(BarDirection.COL);//Define a orientação das barras como colunas.

        chart.plot(data);//Desenha o gráfico utilizando os dados preparados.
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
        XDDFChartData.Series series = data.addSeries(categorias, valores);//Adiciona os dados.
        series.setTitle("Quantidade");

        chart.plot(data);//Desenha o gráfico.

        XDDFChartLegend legend = chart.getOrAddLegend();
        legend.setPosition(LegendPosition.RIGHT);//A legenda fica na direita
    }

    private static CellStyle headerStyle(XSSFWorkbook wb) {//cria a aparência dos títulos das tabelas.
        Font negrito = wb.createFont();
        negrito.setBold(true);
        //Cria uma fonte e coloca em negrito.

        CellStyle estilo = wb.createCellStyle();
        estilo.setFont(negrito);
        //Cria um estilo de célula e aplica a fonte criada.

        estilo.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());//Define a cor de preenchimento como cinza claro.
        estilo.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);//Faz com que essa cor seja aplicada como preenchimento sólido.
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
