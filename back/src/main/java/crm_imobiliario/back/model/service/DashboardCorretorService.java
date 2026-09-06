package crm_imobiliario.back.model.service;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import crm_imobiliario.back.model.dto.DashboardCorretorTimelineDTO;
import crm_imobiliario.back.model.entity.Usuario;
import crm_imobiliario.back.model.repository.LeadRepository;
import crm_imobiliario.back.model.repository.TramitacaoRepository;

@Service
public class DashboardCorretorService {

    @Autowired
    private LeadRepository leadRepository;
    @Autowired
    private TramitacaoRepository tramitacaoRepository;
    @Autowired
    private UsuarioService usuarioService;

    private static final DateTimeFormatter LABEL_FMT = DateTimeFormatter.ofPattern("MMM yyyy", new Locale("pt", "BR"));

    public DashboardCorretorTimelineDTO getTimeline(String email, LocalDateTime inicio, LocalDateTime fim) {
        Usuario solicitante = usuarioService.buscarPorEmail(email);
        Long corretorId = solicitante.getId();

        // agregações no banco
        List<Object[]> leadsPorMes = leadRepository.contarLeadsPorMes(corretorId, inicio, fim);
        List<Object[]> contratosPorMes = tramitacaoRepository.contarContratosPorMes(corretorId, inicio, fim);

        Map<String, Long> mapLeads = new HashMap<>();
        for (Object[] row : leadsPorMes) {
            String mes = (String) row[0];
            Number cnt = (Number) row[1];
            mapLeads.put(mes, cnt.longValue());
        }
        Map<String, Long> mapContratos = new HashMap<>();
        for (Object[] row : contratosPorMes) {
            String mes = (String) row[0];
            Number cnt = (Number) row[1];
            mapContratos.put(mes, cnt.longValue());
        }

        // gerar série completa mês a mês com 0 onde não há dados, ordem cronológica
        YearMonth ymInicio = YearMonth.from(inicio);
        YearMonth ymFim = YearMonth.from(fim);
        List<DashboardCorretorTimelineDTO.PontoMensalDTO> pontos = new ArrayList<>();
        YearMonth cursor = ymInicio;
        while (!cursor.isAfter(ymFim)) {
            String mesKey = cursor.toString(); // yyyy-MM
            // label pt-BR ex: jan 2026 -> capitaliza primeira letra
            String rawLabel = cursor.format(LABEL_FMT); // ex: jan. 2026
            // limpa ponto e capitaliza
            String label = rawLabel.replace(".", "");
            if (!label.isEmpty()) label = Character.toUpperCase(label.charAt(0)) + label.substring(1);

            long leads = mapLeads.getOrDefault(mesKey, 0L);
            long contratos = mapContratos.getOrDefault(mesKey, 0L);
            pontos.add(DashboardCorretorTimelineDTO.PontoMensalDTO.builder()
                    .mes(mesKey)
                    .label(label)
                    .leadsRecebidos(leads)
                    .contratosFechados(contratos)
                    .build());
            cursor = cursor.plusMonths(1);
        }

        return DashboardCorretorTimelineDTO.builder().timeline(pontos).build();
    }
}
