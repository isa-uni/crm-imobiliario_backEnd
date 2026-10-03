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
    @NotNull(message = "Informe o lead a ser redistribuído.")
    private Long leadId;
    @NotNull(message = "Selecione o novo corretor responsável.")
    private Long novoCorretorId;
}
