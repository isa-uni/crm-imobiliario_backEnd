package crm_imobiliario.back.model.service.empreendimento.extracao;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Extração de tabelas de planilhas (XLSX, XLS, CSV) — Apache POI, sem IA
 * generativa (§4.2 do spec). Lê múltiplas abas, detecta a linha de cabeçalho
 * por conteúdo (não assume que é sempre a primeira linha — cabeçalhos
 * deslocados são um caso citado no spec) e preserva os dados originais como
 * texto exibido (DataFormatter), sem reinterpretar valores.
 *
 * Limitação conhecida: células mescladas fora da linha de cabeçalho podem
 * aparecer em branco nas linhas de continuação (comportamento nativo do
 * modelo de planilha do Apache POI) — casos assim ficam com confiança
 * reduzida em vez de inventar o valor ausente.
 */
@Component
@RequiredArgsConstructor
public class SpreadsheetTableExtractor {

    private final UnidadeHeaderNormalizer headerNormalizer;

    private static final int LINHAS_PARA_PROCURAR_CABECALHO = 8;

    public static class Resultado {
        public final List<TabelaBruta> tabelas = new ArrayList<>();
        public final List<String> alertas = new ArrayList<>();
    }

    public Resultado extrair(byte[] bytes, String extensao) throws IOException {
        if ("csv".equalsIgnoreCase(extensao)) {
            return extrairCsv(bytes);
        }
        return extrairExcel(bytes);
    }

    private Resultado extrairExcel(byte[] bytes) throws IOException {
        Resultado resultado = new Resultado();
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            DataFormatter formatter = new DataFormatter();
            for (Sheet sheet : wb) {
                List<List<String>> linhasTexto = new ArrayList<>();
                int ultimaLinha = sheet.getLastRowNum();
                for (int i = 0; i <= ultimaLinha; i++) {
                    Row row = sheet.getRow(i);
                    List<String> celulas = new ArrayList<>();
                    if (row != null) {
                        int ultimaCelula = row.getLastCellNum();
                        for (int c = 0; c < Math.max(ultimaCelula, 0); c++) {
                            Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                            celulas.add(cell != null ? formatter.formatCellValue(cell).trim() : "");
                        }
                    }
                    linhasTexto.add(celulas);
                }

                int idxCabecalho = encontrarLinhaCabecalho(linhasTexto);
                if (idxCabecalho < 0) continue; // aba sem tabela de unidades reconhecível — ignora silenciosamente

                TabelaBruta tabela = new TabelaBruta();
                tabela.aba = sheet.getSheetName();
                tabela.cabecalhos = linhasTexto.get(idxCabecalho);

                int linhaOrigem = 0;
                for (int i = idxCabecalho + 1; i < linhasTexto.size(); i++) {
                    List<String> celulas = linhasTexto.get(i);
                    if (celulas.stream().allMatch(String::isBlank)) continue;
                    TabelaBruta.Linha l = new TabelaBruta.Linha();
                    l.celulas = celulas;
                    l.linhaOrigem = ++linhaOrigem;
                    tabela.linhas.add(l);
                }
                if (!tabela.linhas.isEmpty()) resultado.tabelas.add(tabela);
            }
        }
        if (resultado.tabelas.isEmpty()) {
            resultado.alertas.add("Nenhuma tabela de unidades foi reconhecida nas abas da planilha.");
        }
        return resultado;
    }

    private Resultado extrairCsv(byte[] bytes) {
        Resultado resultado = new Resultado();
        String conteudo = new String(bytes, StandardCharsets.UTF_8);
        String[] linhasBrutas = conteudo.split("\\r?\\n");
        List<List<String>> linhasTexto = new ArrayList<>();
        for (String linha : linhasBrutas) {
            if (linha.isBlank()) { linhasTexto.add(List.of()); continue; }
            linhasTexto.add(parseLinhaCsv(linha));
        }

        int idxCabecalho = encontrarLinhaCabecalho(linhasTexto);
        if (idxCabecalho < 0) {
            resultado.alertas.add("Nenhuma tabela de unidades foi reconhecida no CSV.");
            return resultado;
        }

        TabelaBruta tabela = new TabelaBruta();
        tabela.cabecalhos = linhasTexto.get(idxCabecalho);
        int linhaOrigem = 0;
        for (int i = idxCabecalho + 1; i < linhasTexto.size(); i++) {
            List<String> celulas = linhasTexto.get(i);
            if (celulas.isEmpty() || celulas.stream().allMatch(String::isBlank)) continue;
            TabelaBruta.Linha l = new TabelaBruta.Linha();
            l.celulas = celulas;
            l.linhaOrigem = ++linhaOrigem;
            tabela.linhas.add(l);
        }
        if (!tabela.linhas.isEmpty()) resultado.tabelas.add(tabela);
        return resultado;
    }

    private List<String> parseLinhaCsv(String linha) {
        List<String> campos = new ArrayList<>();
        StringBuilder atual = new StringBuilder();
        boolean entreAspas = false;
        for (int i = 0; i < linha.length(); i++) {
            char c = linha.charAt(i);
            if (c == '"') {
                entreAspas = !entreAspas;
            } else if ((c == ',' || c == ';') && !entreAspas) {
                campos.add(atual.toString().trim());
                atual = new StringBuilder();
            } else {
                atual.append(c);
            }
        }
        campos.add(atual.toString().trim());
        return campos;
    }

    /** Procura, nas primeiras linhas, aquela com mais colunas reconhecidas — cabeçalho pode estar deslocado. */
    private int encontrarLinhaCabecalho(List<List<String>> linhas) {
        int melhorIndice = -1;
        int melhorPontuacao = 0;
        int limite = Math.min(LINHAS_PARA_PROCURAR_CABECALHO, linhas.size());
        for (int i = 0; i < limite; i++) {
            List<String> linha = linhas.get(i);
            int pontuacao = 0;
            boolean temIdentificacao = false;
            for (String celula : linha) {
                var rec = headerNormalizer.reconhecer(celula);
                if (rec.isPresent()) {
                    pontuacao++;
                    if (rec.get().campo == CampoUnidade.UNIDADE) temIdentificacao = true;
                }
            }
            if (temIdentificacao && pontuacao > melhorPontuacao) {
                melhorPontuacao = pontuacao;
                melhorIndice = i;
            }
        }
        return melhorPontuacao >= 2 ? melhorIndice : -1;
    }
}
