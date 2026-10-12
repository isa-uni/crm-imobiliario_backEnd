package crm_imobiliario.back.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import crm_imobiliario.back.model.dto.DashboardCorretorResumoDTO;
import crm_imobiliario.back.model.dto.DashboardCorretorTimelineDTO;
import crm_imobiliario.back.model.service.DashboardCorretorService;

@RestController
@RequestMapping("/dashboard/corretor")
public class DashboardCorretorController {

    @Autowired
    private DashboardCorretorService dashboardCorretorService;

    /** Cards do mês do Dashboard: contratos, valor e conversão pela regra única. Gestor: escopo "meus" (padrão) ou "equipe". */
    @GetMapping("/resumo")
    public ResponseEntity<DashboardCorretorResumoDTO> getResumo(Authentication auth, @RequestParam(required = false) String escopo) {
        return ResponseEntity.ok(dashboardCorretorService.getResumo(auth.getName(), escopo, LocalDateTime.now()));
    }

    @GetMapping("/timeline")
    public ResponseEntity<DashboardCorretorTimelineDTO> getTimeline(
            Authentication auth,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            // gestor: "meus" (padrão) ou "equipe"; corretor sempre vê os seus e admin, todos
            @RequestParam(required = false) String escopo) {
        String email = auth.getName();
        LocalDateTime ini;
        LocalDateTime fi;
        if (inicio != null && fim != null && inicio.isAfter(fim)) {
            throw new crm_imobiliario.back.util.RegraNegocioException("A data inicial deve ser igual ou anterior à data final.", "inicio", "INVALID_PERIOD");
        }
        if (inicio != null && fim != null) {
            ini = inicio.atStartOfDay();
            fi = fim.atTime(LocalTime.MAX);
        } else {
            // padrão: últimos 6 meses incluindo atual, com período completo
            LocalDate now = LocalDate.now();
            LocalDate start = now.minusMonths(5).withDayOfMonth(1);
            LocalDate end = now.withDayOfMonth(now.lengthOfMonth());
            ini = start.atStartOfDay();
            fi = end.atTime(LocalTime.MAX);
        }
        // garante ordem cronológica
        if (ini.isAfter(fi)) {
            LocalDateTime tmp = ini;
            ini = fi.withHour(0).withMinute(0).withSecond(0).withNano(0);
            fi = tmp.withHour(23).withMinute(59).withSecond(59).withNano(999999999);
        }
        DashboardCorretorTimelineDTO dto = dashboardCorretorService.getTimeline(email, ini, fi, escopo, LocalDateTime.now());
        return ResponseEntity.ok(dto);
    }
}
