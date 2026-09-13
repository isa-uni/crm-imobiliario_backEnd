package crm_imobiliario.back.model.service.empreendimento.extracao;

import java.text.Normalizer.Form;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Reconhece o cabeçalho de uma coluna de tabela de unidades e o mapeia para um
 * {@link CampoUnidade} canônico — por nome/sinônimo, nunca por posição (§6-7 do spec).
 *
 * Cabeçalhos reais (Pride/CVCRM) trazem multiplicador e data embutidos, ex.:
 * "ATO (1x)\n10/10/2026", "SUBSÍDIO COHAPAR (1x)\n30/04/2028" — esses são
 * extraídos à parte e não interferem no reconhecimento do rótulo.
 */
@Component
public class UnidadeHeaderNormalizer {

    private static final Pattern DATA = Pattern.compile("(\\d{2}/\\d{2}/\\d{4})");
    private static final Pattern MULTIPLICADOR = Pattern.compile("\\((\\d+)\\s*x\\)", Pattern.CASE_INSENSITIVE);

    /** sinônimo normalizado -> campo canônico. Ordem não importa; busca é por igualdade após normalização. */
    private static final Map<String, CampoUnidade> SINONIMOS = new LinkedHashMap<>();
    static {
        put(CampoUnidade.BLOCO, "bloco");
        put(CampoUnidade.TORRE, "torre");
        put(CampoUnidade.UNIDADE, "unidade", "unid", "apto", "apartamento");
        put(CampoUnidade.AREA_PRIVATIVA, "area privativa", "area util", "metragem privativa");
        put(CampoUnidade.TIPOLOGIA, "tipologia", "tipo");
        put(CampoUnidade.AREA_COMUM, "area comum");
        put(CampoUnidade.OUTRAS_AREAS, "outras areas", "area adicional", "areas adicionais");
        put(CampoUnidade.GARAGEM, "garagem", "vaga", "vagas");
        put(CampoUnidade.SITUACAO, "situacao", "status", "disponibilidade");
        put(CampoUnidade.VALOR_TOTAL, "valor total", "valor", "preco total");
        put(CampoUnidade.ATO, "ato", "entrada", "sinal");
        put(CampoUnidade.SUBSIDIO_COHAPAR, "subsidio cohapar", "subsidio");
        put(CampoUnidade.FINANCIAMENTO, "financiamento");
        put(CampoUnidade.VALOR_AVALIACAO, "valor avaliacao", "valor de avaliacao", "avaliacao");
    }

    private static void put(CampoUnidade campo, String... sinonimos) {
        for (String s : sinonimos) SINONIMOS.put(s, campo);
    }

    public static class Reconhecimento {
        public final CampoUnidade campo;
        public final String rotuloOriginal;
        public final Integer multiplicador;
        public final String dataReferencia;

        Reconhecimento(CampoUnidade campo, String rotuloOriginal, Integer multiplicador, String dataReferencia) {
            this.campo = campo;
            this.rotuloOriginal = rotuloOriginal;
            this.multiplicador = multiplicador;
            this.dataReferencia = dataReferencia;
        }
    }

    /** Remove acentos, baixa para minúsculas, colapsa espaços. Não decide significado — só compara texto. */
    public String normalizar(String texto) {
        if (texto == null) return "";
        String n = java.text.Normalizer.normalize(texto, Form.NFD);
        n = n.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        n = n.toLowerCase(Locale.ROOT);
        n = n.replaceAll("[\\r\\n]+", " ");
        n = n.replaceAll("\\((\\d+)\\s*x\\)", " ");
        n = n.replaceAll("\\d{2}/\\d{2}/\\d{4}", " ");
        n = n.replaceAll("[^a-z0-9\\s]", " ");
        n = n.replaceAll("\\s+", " ").trim();
        return n;
    }

    /**
     * Tenta reconhecer um cabeçalho. Retorna vazio se o texto não corresponder a
     * nenhum sinônimo cadastrado — nesse caso a coluna deve ser apresentada ao
     * usuário como "não reconhecida" para mapeamento manual (§7.1).
     */
    public Optional<Reconhecimento> reconhecer(String cabecalhoOriginal) {
        if (cabecalhoOriginal == null || cabecalhoOriginal.isBlank()) return Optional.empty();
        String normalizado = normalizar(cabecalhoOriginal);
        CampoUnidade campo = SINONIMOS.get(normalizado);
        if (campo == null) {
            // tolera rótulo com uma palavra extra colada (ex.: "valor total r$") tentando prefixo
            for (Map.Entry<String, CampoUnidade> e : SINONIMOS.entrySet()) {
                if (normalizado.startsWith(e.getKey() + " ") || normalizado.equals(e.getKey())) {
                    campo = e.getValue();
                    break;
                }
            }
        }
        if (campo == null) return Optional.empty();

        Integer multiplicador = null;
        Matcher mm = MULTIPLICADOR.matcher(cabecalhoOriginal);
        if (mm.find()) multiplicador = Integer.parseInt(mm.group(1));

        String data = null;
        Matcher md = DATA.matcher(cabecalhoOriginal);
        if (md.find()) data = md.group(1);

        return Optional.of(new Reconhecimento(campo, cabecalhoOriginal.trim(), multiplicador, data));
    }
}
