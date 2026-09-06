package crm_imobiliario.back.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class LeadRedistribuicaoDTO {
    @NotNull
    private Long leadId;
    @NotNull
    private Long novoCorretorId;
}
