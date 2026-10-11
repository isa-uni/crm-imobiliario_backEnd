package crm_imobiliario.back.model.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardCorretorTimelineDTO {
    private List<PontoMensalDTO> timeline;
    /** meus | equipe | todos */
    private String escopo;
    /** Gestor pode alternar entre os próprios clientes e os da equipe. */
    private boolean podeVerEquipe;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class PontoMensalDTO {
        private String mes; // yyyy-MM
        private String label; // MMM yyyy ex: jan 2026
        private long leadsRecebidos; // cadastrados no mês
        private long contratosFechados;
    }
}
