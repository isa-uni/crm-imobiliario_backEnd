package crm_imobiliario.back.model.dto;

/**
 * Números do mês do Dashboard (tela inicial), pela regra única do painel (ContagemFunil).
 *
 * @param mes                  yyyy-MM
 * @param clientesNoMes        em andamento (qualquer mês de cadastro) + contratos e descartes do mês
 * @param emAndamento          clientes em andamento agora (qualquer mês de cadastro)
 * @param emAndamentoMesAnterior em andamento no fim do mês anterior (para a variação)
 * @param contratos            contratos fechados no mês
 * @param valorContratos       soma do valor de interesse desses contratos
 * @param conversao            contratos ÷ clientesNoMes × 100
 * @param contratosMesAnterior contratos fechados no mês anterior (para a variação)
 * @param escopo               meus | equipe | todos
 */
public record DashboardCorretorResumoDTO(
        String mes,
        long clientesNoMes,
        long emAndamento,
        long emAndamentoMesAnterior,
        long contratos,
        double valorContratos,
        double conversao,
        long contratosMesAnterior,
        String escopo
) {}
