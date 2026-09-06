package crm_imobiliario.back.model.service.empreendimento;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Parser de linhas de Tabela de Preço em PDF — porta de extrator_cvcrm.py:277-315.
 * Linha típica: "Etapa Única BLOCO 01 B01.101 35,74 m² 2Q - Garden Disponível R$ 227.200,00"
 */
@Component
public class PdfPrecoParser {

    // Grupo 1: nome-unidade (B01.101), 2: área (35,74), 3: tipologia (2Q - Garden), 4: situação, 5: valor
    private static final Pattern RE_LINHA_PRECO = Pattern.compile(
            "^\\s*Etapa\\s+[A-Za-zÀ-ú0-9]+\\s+BLOCO\\s+\\S+\\s+" +
            "([A-Z]{1,3}[0-9]{2}\\.[0-9]{3})\\s+" +
            "([0-9.,]+)\\s*m²\\s+" +
            "(.+?)\\s+" +
            "(Disponível|Reservada|Vendida|Em [Pp]rocesso)\\s+" +
            "R\\$\\s*([0-9.,]+)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern RE_QUARTOS = Pattern.compile("(?:^|\\D)(\\d)\\s*(?:q|quart|dorm)", Pattern.CASE_INSENSITIVE);

    public Map<String, PrecoLinha> parse(String texto) {
        Map<String, PrecoLinha> out = new HashMap<>();
        if (texto == null) return out;
        for (String linha : texto.split("\\R")) {
            Matcher m = RE_LINHA_PRECO.matcher(linha);
            if (!m.find()) continue;
            String nome = m.group(1);
            String area = m.group(2);
            String tipologia = m.group(3).replaceAll("\\s+", " ").trim();
            String situacao = m.group(4).trim();
            String valor = m.group(5);
            Double areaVal = parseArea(area);
            Long precoVal = parsePreco(valor);
            out.put(nome, new PrecoLinha(nome, areaVal, tipologia, situacao, precoVal));
        }
        return out;
    }

    public Integer quartosDeTipologia(String tipologia) {
        if (tipologia == null) return null;
        String t = tipologia.toLowerCase();
        Matcher m = RE_QUARTOS.matcher(t);
        if (m.find()) return Integer.parseInt(m.group(1));
        if (t.contains("studio") || t.contains("cobertura") || t.contains("kitnet")) return 0;
        return null;
    }

    private Double parseArea(String s) {
        try {
            return Double.parseDouble(s.replace(".", "").replace(",", "."));
        } catch (Exception e) { return null; }
    }

    private Long parsePreco(String s) {
        try {
            String n = s.replace(".", "").replace(",", ".");
            double d = Double.parseDouble(n);
            return Math.round(d);
        } catch (Exception e) { return null; }
    }

    public record PrecoLinha(String nomeUnidade, Double area, String tipologia, String situacao, Long preco) {}

    public List<Long> precosDisponiveis(Map<String, PrecoLinha> map) {
        List<Long> out = new ArrayList<>();
        for (PrecoLinha p : map.values()) {
            if (p.situacao().equalsIgnoreCase("Disponível") && p.preco() != null) out.add(p.preco());
        }
        return out;
    }
}
