package crm_imobiliario.back.model.service.empreendimento.extracao;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

/**
 * Extração de texto e tabelas de documentos Word — Apache POI (§4.3 do spec).
 * DOC (formato binário legado) e DOCX (OOXML) usam modelos internos
 * diferentes e não são tratados como equivalentes: DOC é extraído apenas como
 * texto corrido (POI não oferece um modelo de tabela confiável para o
 * formato antigo); DOCX extrai texto e tabelas estruturadas.
 */
@Component
public class WordTextExtractor {

    public static class Resultado {
        public String texto = "";
        public final List<TabelaBruta> tabelas = new ArrayList<>();
        public final List<String> alertas = new ArrayList<>();
    }

    public Resultado extrair(byte[] bytes, String extensao) throws IOException {
        if ("docx".equalsIgnoreCase(extensao)) {
            return extrairDocx(bytes);
        }
        return extrairDoc(bytes);
    }

    private Resultado extrairDocx(byte[] bytes) throws IOException {
        Resultado resultado = new Resultado();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            StringBuilder texto = new StringBuilder();
            for (XWPFParagraph p : doc.getParagraphs()) {
                texto.append(p.getText()).append('\n');
            }
            resultado.texto = texto.toString();

            for (XWPFTable table : doc.getTables()) {
                List<XWPFTableRow> linhas = table.getRows();
                if (linhas.isEmpty()) continue;
                TabelaBruta tabela = new TabelaBruta();
                tabela.cabecalhos = celulasDaLinha(linhas.get(0));
                int linhaOrigem = 0;
                for (int i = 1; i < linhas.size(); i++) {
                    List<String> celulas = celulasDaLinha(linhas.get(i));
                    if (celulas.stream().allMatch(String::isBlank)) continue;
                    TabelaBruta.Linha l = new TabelaBruta.Linha();
                    l.celulas = celulas;
                    l.linhaOrigem = ++linhaOrigem;
                    tabela.linhas.add(l);
                }
                if (!tabela.linhas.isEmpty()) resultado.tabelas.add(tabela);
            }
        }
        return resultado;
    }

    private List<String> celulasDaLinha(XWPFTableRow row) {
        List<String> celulas = new ArrayList<>();
        for (XWPFTableCell cell : row.getTableCells()) celulas.add(cell.getText().trim());
        return celulas;
    }

    private Resultado extrairDoc(byte[] bytes) throws IOException {
        Resultado resultado = new Resultado();
        try (HWPFDocument doc = new HWPFDocument(new ByteArrayInputStream(bytes));
             WordExtractor extractor = new WordExtractor(doc)) {
            resultado.texto = String.join("\n", extractor.getParagraphText());
            resultado.alertas.add("Formato .doc (legado) — apenas texto foi extraído; tabelas eventuais não são interpretadas nesta versão. Considere reenviar como .docx.");
        }
        return resultado;
    }
}
