package crm_imobiliario.back.model.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import crm_imobiliario.back.model.dto.LeadListaDTO;

/**
 * Gera o relatório Excel de leads do corretor: aba de dados brutos + funil de vendas,
 * origem dos leads e histórico dos leads, cada um com sua agregação e gráfico nativo.
 */
@Service
public class LeadExportService {

    @Autowired
    private LeadsService leadsService;

    // mesma ordem usada no funil do dashboard (components/FunilDashboard.tsx) — precisa
    // ficar em sincronia com aquele arquivo para o relatório bater com a tela.
    static final List<String> FUNIL_ORDEM = List.of(
            "lead", "oportunidade", "visita-agendada", "visita-realizada", "pasta", "aprovado", "contrato");

    public byte[] exportarExcel(String search, String status, String month, String origem, String historico) {
        List<LeadListaDTO> leads = leadsService.findAllForExport(search, status, month, origem, historico)
                .stream().map(LeadListaDTO::from).toList();

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            LeadExportExcelBuilder.buildLeadsSheet(wb, leads);
            LeadExportExcelBuilder.buildFunilSheet(wb, contagemFunilCumulativa(leads, FUNIL_ORDEM), FUNIL_ORDEM);
            LeadExportExcelBuilder.buildOrigemSheet(wb, contagemPorCampo(leads, LeadListaDTO::origem, "Sem origem"));
            LeadExportExcelBuilder.buildHistoricoSheet(wb, contagemPorCampo(leads, LeadListaDTO::historico, "Sem histórico"));

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Falha ao gerar relatório Excel", e);
        }
    }

    /**
     * Tradução literal do algoritmo cumulativo do funil (components/FunilDashboard.tsx): a
     * primeira etapa conta todos os leads, independente do status; as demais contam leads cujo
     * status esteja na própria etapa ou em qualquer etapa posterior — um lead em "pasta" conta em
     * lead/oportunidade/visita-agendada/visita-realizada/pasta, mas não em aprovado/contrato.
     */
    static Map<String, Long> contagemFunilCumulativa(List<LeadListaDTO> leads, List<String> ordem) {
        Map<String, Long> contagens = new LinkedHashMap<>();
        for (int i = 0; i < ordem.size(); i++) {
            String etapa = ordem.get(i);
            if (i == 0) {
                contagens.put(etapa, (long) leads.size());
            } else {
                Set<String> validos = new HashSet<>(ordem.subList(i, ordem.size()));
                contagens.put(etapa, leads.stream().filter(l -> validos.contains(l.status())).count());
            }
        }
        return contagens;
    }

    /** Agrupa e conta, ordenado do maior para o menor — mesma ordenação usada em LeadOrigemChart.tsx. */
    static Map<String, Long> contagemPorCampo(List<LeadListaDTO> leads, Function<LeadListaDTO, String> campo, String valorPadrao) {
        Map<String, Long> contagens = leads.stream().collect(Collectors.groupingBy(
                l -> {
                    String v = campo.apply(l);
                    return (v == null || v.isBlank()) ? valorPadrao : v;
                },
                Collectors.counting()));
        return contagens.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));
    }
}
