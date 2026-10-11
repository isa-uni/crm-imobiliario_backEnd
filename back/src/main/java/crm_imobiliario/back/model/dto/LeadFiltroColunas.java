package crm_imobiliario.back.model.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Filtros dos cabeçalhos da tabela de Leads (GET /leads). Campo nulo ou lista vazia = sem filtro.
 *
 * @param nome        parte do nome
 * @param contato     parte do e-mail ou do telefone (o telefone compara só os dígitos)
 * @param etapas      status do funil (lead, oportunidade, ..., contrato, descarte)
 * @param origens     códigos de origem (espontaneo, digital_lead, ...)
 * @param valorMin    valor de interesse mínimo
 * @param valorMax    valor de interesse máximo
 * @param responsavel parte do nome do corretor responsável
 * @param dataDe      atualizado a partir deste dia
 * @param dataAte     atualizado até este dia (inclusive)
 */
public record LeadFiltroColunas(
        String nome,
        String contato,
        List<String> etapas,
        List<String> origens,
        Long valorMin,
        Long valorMax,
        String responsavel,
        LocalDate dataDe,
        LocalDate dataAte
) {
    public static final LeadFiltroColunas VAZIO = new LeadFiltroColunas(null, null, null, null, null, null, null, null, null);
}
