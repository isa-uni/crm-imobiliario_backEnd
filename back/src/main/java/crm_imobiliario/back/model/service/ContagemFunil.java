package crm_imobiliario.back.model.service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import crm_imobiliario.back.model.dto.RelogioVendasDTO;
import crm_imobiliario.back.model.entity.Lead;
import crm_imobiliario.back.model.entity.Tramitacao;

/**
 * Regra única de contagem do funil, usada pelo Relógio de Vendas e pelo Dashboard do Gestor.
 *
 * Num mês, entram na contagem: clientes em andamento (de qualquer mês de cadastro), contratos fechados
 * no mês e clientes descartados no mês. Contratos e descartes de meses anteriores ficam de fora.
 * Cada cliente conta na etapa mais avançada a que chegou e em todas as anteriores (inclusive as que
 * pulou). Em mês passado, a situação é reconstruída pela tramitação como estava no fim daquele mês.
 */
final class ContagemFunil {

    static final List<String> ETAPAS = LeadExportService.FUNIL_ORDEM;

    enum Tipo { ANDAMENTO, CONTRATO, DESCARTE }

    /**
     * @param existe        o cliente já estava cadastrado na data de referência
     * @param entra         entra na contagem do mês
     * @param maxIdx        índice (em ETAPAS) da etapa mais avançada alcançada até a referência
     * @param etapaAtual    status na data de referência
     * @param etapaDescarte etapa em que estava quando foi descartado (só para DESCARTE)
     * @param quando        data do contrato ou do descarte (só para CONTRATO/DESCARTE)
     */
    record Situacao(boolean existe, boolean entra, Tipo tipo, int maxIdx, String etapaAtual, String etapaDescarte, LocalDateTime quando) {
        static final Situacao INEXISTENTE = new Situacao(false, false, Tipo.ANDAMENTO, -1, null, null, null);
    }

    record Avaliado(Lead lead, Situacao situacao) {}

    private ContagemFunil() {}

    /** Mês atual: agora. Mês passado: último instante do mês. */
    static LocalDateTime referencia(YearMonth mes, LocalDateTime agora) {
        return mes.equals(YearMonth.from(agora)) ? agora : mes.atEndOfMonth().atTime(LocalTime.MAX);
    }

    /** @param trams tramitações do lead em ordem de data */
    static Situacao avaliar(Lead l, List<Tramitacao> trams, YearMonth mes, LocalDateTime ref, boolean mesCorrente) {
        if (l.getDataCriacao() != null && l.getDataCriacao().isAfter(ref)) return Situacao.INEXISTENTE;
        List<Tramitacao> ate = trams.stream()
                .filter(t -> t.getDataMovimentacao() == null || !t.getDataMovimentacao().isAfter(ref)).toList();

        String status = !ate.isEmpty() ? ate.get(ate.size() - 1).getStatus_atual()
                : !trams.isEmpty() ? trams.get(0).getStatus_atual() : l.getStatus();
        int maxIdx = Math.max(ETAPAS.indexOf(status),
                ate.stream().mapToInt(t -> ETAPAS.indexOf(t.getStatus_atual())).max().orElse(-1));
        if (maxIdx < 0) maxIdx = 0; // descartado sem histórico: ao menos foi um lead

        if ("contrato".equals(status) || "descarte".equals(status)) {
            Tramitacao ultima = null;
            for (Tramitacao t : ate) if (status.equals(t.getStatus_atual())) ultima = t;
            LocalDateTime quando = ultima != null && ultima.getDataMovimentacao() != null ? ultima.getDataMovimentacao() : dataAlternativa(l, ref);
            Tipo tipo = "contrato".equals(status) ? Tipo.CONTRATO : Tipo.DESCARTE;
            return new Situacao(true, YearMonth.from(quando).equals(mes), tipo, maxIdx, status,
                    tipo == Tipo.DESCARTE && ultima != null ? ultima.getStatus_anterior() : null, quando);
        }
        // lead inativado sem descarte não está em andamento (só se sabe disso para o momento atual)
        boolean emAndamento = !mesCorrente || !Boolean.FALSE.equals(l.getAtivo());
        return new Situacao(true, emAndamento, Tipo.ANDAMENTO, maxIdx, status, null, null);
    }

    /** Data de contrato/descarte de um cliente sem tramitação desse status (não deveria ocorrer). */
    private static LocalDateTime dataAlternativa(Lead l, LocalDateTime ref) {
        LocalDateTime d = l.getDataAtualizacao() != null ? l.getDataAtualizacao() : l.getDataCriacao();
        return d == null || d.isAfter(ref) ? ref : d;
    }

    static String mesCadastro(Lead l, LocalDateTime ref) {
        return YearMonth.from(l.getDataCriacao() != null ? l.getDataCriacao() : ref).toString();
    }

    /** Uma linha por etapa: realizado (cumulativo), composição por situação e por mês de cadastro. */
    static List<RelogioVendasDTO.Etapa> etapas(List<Avaliado> naContagem, LocalDateTime ref) {
        int n = ETAPAS.size();
        long[] realizado = new long[n], andamento = new long[n], contratos = new long[n], descartados = new long[n], descartesNaEtapa = new long[n];
        List<Map<String, Long>> porMes = new ArrayList<>();
        for (int i = 0; i < n; i++) porMes.add(new TreeMap<>());

        for (Avaliado a : naContagem) {
            Situacao s = a.situacao();
            String origem = mesCadastro(a.lead(), ref);
            for (int k = 0; k <= s.maxIdx(); k++) {
                realizado[k]++;
                porMes.get(k).merge(origem, 1L, Long::sum);
                if (s.tipo() == Tipo.CONTRATO) contratos[k]++;
                else if (s.tipo() == Tipo.DESCARTE) descartados[k]++;
                else andamento[k]++;
            }
            int idxDescarte = s.etapaDescarte() != null ? ETAPAS.indexOf(s.etapaDescarte()) : -1;
            if (s.tipo() == Tipo.DESCARTE && idxDescarte >= 0) descartesNaEtapa[idxDescarte]++;
        }

        List<RelogioVendasDTO.Etapa> etapas = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            List<RelogioVendasDTO.MesCadastro> meses = porMes.get(i).entrySet().stream()
                    .map(e -> new RelogioVendasDTO.MesCadastro(e.getKey(), e.getValue())).toList();
            etapas.add(new RelogioVendasDTO.Etapa(ETAPAS.get(i), realizado[i], andamento[i], contratos[i], descartados[i], descartesNaEtapa[i], meses));
        }
        return etapas;
    }
}
