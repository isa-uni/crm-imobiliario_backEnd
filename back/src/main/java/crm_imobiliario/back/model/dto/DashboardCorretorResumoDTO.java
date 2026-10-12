package crm_imobiliario.back.model.dto;

import java.util.List;

/**
 * Números do mês do Dashboard (tela inicial), pela regra única do painel (ContagemFunil).
 *
 * @param mes                  yyyy-MM
 * @param clientesNoMes        em andamento (qualquer mês de cadastro) + contratos e descartes do mês
 * @param emAndamento          clientes em andamento agora (qualquer mês de cadastro)
 * @param emAndamentoMesAnterior em andamento no fim do mês anterior (para a variação)
 * @param contratos            contratos fechados no mês
 * @param descartes            clientes descartados no mês
 * @param valorContratos       soma do valor de interesse desses contratos
 * @param conversao            contratos ÷ clientesNoMes × 100
 * @param contratosMesAnterior contratos fechados no mês anterior (para a variação)
 * @param funil                etapas do funil, na ordem: clientes em andamento + contratos do mês (sem
 *                             descartes nem contratos de meses anteriores), cada um na etapa mais
 *                             avançada que alcançou e nas anteriores
 * @param escopo               meus | equipe | todos
 */
public record DashboardCorretorResumoDTO(
        String mes,
        long clientesNoMes,
        long emAndamento,
        long emAndamentoMesAnterior,
        long contratos,
        long descartes,
        double valorContratos,
        double conversao,
        long contratosMesAnterior,
        List<EtapaFunil> funil,
        String escopo
) {
    public record EtapaFunil(String etapa, long quantidade) {}
}
