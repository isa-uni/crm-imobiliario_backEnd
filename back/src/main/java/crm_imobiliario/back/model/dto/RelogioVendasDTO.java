package crm_imobiliario.back.model.dto;

import java.util.List;

/**
 * Contagem do Relógio de Vendas do usuário logado no mês atual.
 *
 * @param mesReferencia      mês da contagem (yyyy-MM)
 * @param clientesNaContagem clientes considerados: em andamento (de qualquer mês) + contratos e descartes do mês
 * @param etapas             uma linha por etapa do funil, na ordem do funil
 */
public record RelogioVendasDTO(
        String mesReferencia,
        ProporcaoFunilDTO proporcoes,
        long clientesNaContagem,
        List<Etapa> etapas
) {
    /**
     * @param realizado            clientes que chegaram até esta etapa ou além (contagem cumulativa)
     * @param emAndamento          dos realizados, quantos seguem em andamento
     * @param contratosNoMes       dos realizados, quantos fecharam contrato neste mês
     * @param descartadosNoMes     dos realizados, quantos foram descartados neste mês
     * @param descartesNestaEtapa  descartes do mês em que o cliente estava nesta etapa
     * @param porMesCadastro       composição dos realizados por mês de cadastro do cliente
     */
    public record Etapa(
            String etapa,
            long realizado,
            long emAndamento,
            long contratosNoMes,
            long descartadosNoMes,
            long descartesNestaEtapa,
            List<MesCadastro> porMesCadastro
    ) {}

    public record MesCadastro(String mes, long quantidade) {}
}
