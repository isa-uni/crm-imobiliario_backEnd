package crm_imobiliario.back.model.service.empreendimento.extracao;

import java.util.EnumMap;
import java.util.Map;

import crm_imobiliario.back.model.service.empreendimento.EmpreendimentoExtractionService.CampoEvidencia;

/**
 * Uma linha de unidade extraída de uma tabela (PDF/planilha), antes da revisão
 * do usuário. Cada campo carrega sua evidência (valor, confiança, origem) —
 * nunca um valor "cru" sem rastreabilidade (§12 do spec).
 */
public class UnidadeExtraida {

    public final Map<CampoUnidade, CampoEvidencia> campos = new EnumMap<>(CampoUnidade.class);

    /** Documento de origem desta linha. */
    public Long documentoId;
    public String documentoNome;

    /** Linha na tabela de origem (1-based) — para rastreabilidade. */
    public Integer linhaOrigem;

    /** Página (PDF) ou aba (planilha) de origem, quando aplicável. */
    public Integer pagina;
    public String aba;

    /** Alertas específicos desta linha (ex.: coluna ambígua, valor não interpretado). */
    public final java.util.List<String> alertas = new java.util.ArrayList<>();

    public void set(CampoUnidade campo, String valor, Integer confianca, String trecho) {
        CampoEvidencia ev = new CampoEvidencia(valor, confianca, pagina, trecho, documentoNome);
        campos.put(campo, ev);
    }

    public String valor(CampoUnidade campo) {
        CampoEvidencia ev = campos.get(campo);
        return ev != null ? ev.valor : null;
    }

    /** Chave de identidade da unidade dentro do empreendimento (bloco/torre + unidade). */
    public String chaveIdentidade() {
        String unidade = valor(CampoUnidade.UNIDADE);
        return unidade != null ? unidade.trim().toUpperCase() : null;
    }
}
