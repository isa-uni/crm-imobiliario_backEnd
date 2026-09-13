package crm_imobiliario.back.model.service.empreendimento.extracao;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Mapeia uma {@link TabelaBruta} (célula a célula) para linhas de
 * {@link UnidadeExtraida}, usando {@link UnidadeHeaderNormalizer} para
 * reconhecer cada coluna por nome — nunca por posição fixa (§6 do spec).
 *
 * Regras de tipo por campo (área, valor monetário, situação) ficam aqui;
 * quando uma célula não pode ser interpretada com segurança, o valor original
 * é preservado e a confiança é reduzida, nunca inventado.
 */
@Component
@RequiredArgsConstructor
public class UnidadeTableMapper {

    private final UnidadeHeaderNormalizer normalizer;

    private static final Set<String> PLACEHOLDERS_VAZIOS = Set.of("-", "--", "- -", "—", "n/a", "na");

    private static final Pattern AREA = Pattern.compile("([0-9]+(?:[.,][0-9]+)?)\\s*m[²2]?", Pattern.CASE_INSENSITIVE);
    private static final Pattern MOEDA = Pattern.compile("R\\$\\s*([0-9.,]+)", Pattern.CASE_INSENSITIVE);

    public static class Resultado {
        public final List<UnidadeExtraida> unidades = new ArrayList<>();
        public final List<String> alertas = new ArrayList<>();
    }

    public Resultado mapear(TabelaBruta tabela, Long documentoId, String documentoNome) {
        Resultado resultado = new Resultado();
        Map<Integer, CampoUnidade> colunaParaCampo = new LinkedHashMap<>();
        for (int i = 0; i < tabela.cabecalhos.size(); i++) {
            String bruto = tabela.cabecalhos.get(i);
            Optional<UnidadeHeaderNormalizer.Reconhecimento> rec = normalizer.reconhecer(bruto);
            if (rec.isPresent()) {
                colunaParaCampo.put(i, rec.get().campo);
            } else if (bruto != null && !bruto.isBlank()) {
                resultado.alertas.add("Coluna não reconhecida: \"" + bruto.trim() + "\" — requer mapeamento manual.");
            }
        }

        boolean temIdentificador = colunaParaCampo.containsValue(CampoUnidade.UNIDADE);
        if (!temIdentificador) {
            resultado.alertas.add("Nenhuma coluna de identificação de unidade (\"Unidade\") foi reconhecida — tabela ignorada para importação de unidades.");
            return resultado;
        }

        for (TabelaBruta.Linha linha : tabela.linhas) {
            UnidadeExtraida u = new UnidadeExtraida();
            u.documentoId = documentoId;
            u.documentoNome = documentoNome;
            u.pagina = tabela.pagina;
            u.aba = tabela.aba;
            u.linhaOrigem = linha.linhaOrigem;

            for (Map.Entry<Integer, CampoUnidade> e : colunaParaCampo.entrySet()) {
                int idx = e.getKey();
                if (idx >= linha.celulas.size()) continue;
                String bruto = linha.celulas.get(idx);
                mapearCelula(u, e.getValue(), bruto);
            }

            if (u.chaveIdentidade() == null) continue; // linha sem identificação de unidade — ignora (provável linha de rodapé/nota)
            resultado.unidades.add(u);
        }
        return resultado;
    }

    private void mapearCelula(UnidadeExtraida u, CampoUnidade campo, String bruto) {
        if (bruto == null) return;
        String texto = bruto.trim();
        if (texto.isEmpty() || PLACEHOLDERS_VAZIOS.contains(texto.toLowerCase(Locale.ROOT))) return;

        switch (campo) {
            case AREA_PRIVATIVA, AREA_COMUM, OUTRAS_AREAS -> {
                Matcher m = AREA.matcher(texto);
                if (m.find()) {
                    String normalizado = m.group(1).replace(".", "").replace(",", ".");
                    u.set(campo, normalizado, 95, texto);
                } else {
                    u.set(campo, texto, 40, texto);
                    u.alertas.add("Área não reconhecida em \"" + texto + "\" para " + campo + " — revisar.");
                }
            }
            case VALOR_TOTAL, ATO, SUBSIDIO_COHAPAR, FINANCIAMENTO, VALOR_AVALIACAO -> {
                Matcher m = MOEDA.matcher(texto);
                if (m.find()) {
                    String normalizado = m.group(1).replace(".", "").replace(",", ".");
                    u.set(campo, normalizado, 95, texto);
                } else {
                    u.set(campo, texto, 40, texto);
                    u.alertas.add("Valor monetário não reconhecido em \"" + texto + "\" para " + campo + " — revisar.");
                }
            }
            case SITUACAO -> {
                String canonico = canonicalizarSituacao(texto);
                u.set(campo, canonico != null ? canonico : texto, canonico != null ? 90 : 50, texto);
            }
            default -> u.set(campo, texto, 85, texto);
        }
    }

    private String canonicalizarSituacao(String texto) {
        String n = normalizer.normalizar(texto);
        return switch (n) {
            case "disponivel" -> "disponivel";
            case "reservada", "reservado" -> "reservada";
            case "vendida", "vendido" -> "vendida";
            default -> n.contains("processo") ? "em_processo" : null;
        };
    }
}
