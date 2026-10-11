package crm_imobiliario.back.model.dto;

import crm_imobiliario.back.model.entity.ProporcaoFunil;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Quantos clientes em cada etapa equivalem a 1 contrato da meta. */
public record ProporcaoFunilDTO(
        @NotNull(message = "Informe a proporção de leads.") @Min(value = 1, message = "A proporção de leads deve ser pelo menos 1.") @Max(value = 1000, message = "A proporção de leads deve ser no máximo 1000.")
        Integer leads,
        @NotNull(message = "Informe a proporção de oportunidades.") @Min(value = 1, message = "A proporção de oportunidades deve ser pelo menos 1.") @Max(value = 1000, message = "A proporção de oportunidades deve ser no máximo 1000.")
        Integer oportunidades,
        @NotNull(message = "Informe a proporção de visitas agendadas.") @Min(value = 1, message = "A proporção de visitas agendadas deve ser pelo menos 1.") @Max(value = 1000, message = "A proporção de visitas agendadas deve ser no máximo 1000.")
        Integer visitasAgendadas,
        @NotNull(message = "Informe a proporção de visitas realizadas.") @Min(value = 1, message = "A proporção de visitas realizadas deve ser pelo menos 1.") @Max(value = 1000, message = "A proporção de visitas realizadas deve ser no máximo 1000.")
        Integer visitasRealizadas,
        @NotNull(message = "Informe a proporção de pastas.") @Min(value = 1, message = "A proporção de pastas deve ser pelo menos 1.") @Max(value = 1000, message = "A proporção de pastas deve ser no máximo 1000.")
        Integer pastas,
        @NotNull(message = "Informe a proporção de aprovados.") @Min(value = 1, message = "A proporção de aprovados deve ser pelo menos 1.") @Max(value = 1000, message = "A proporção de aprovados deve ser no máximo 1000.")
        Integer aprovados
) {
    public static ProporcaoFunilDTO from(ProporcaoFunil p) {
        return new ProporcaoFunilDTO(p.getLeads(), p.getOportunidades(), p.getVisitasAgendadas(),
                p.getVisitasRealizadas(), p.getPastas(), p.getAprovados());
    }
}
