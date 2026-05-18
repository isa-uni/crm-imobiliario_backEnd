package crm_imobiliario.back.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class MetricsDTO {
    private long totalLeads;
    private long totalOportunidades;
    private long totalVisitasAgen;
    private long totalVisitasReal;
    private long totalPastas;
    private long totalAprovados;
    private long totalContratos;
    private long totalDescartes;

    private double taxaConversaoGeral;
    private double valorTotalFechado;
}
