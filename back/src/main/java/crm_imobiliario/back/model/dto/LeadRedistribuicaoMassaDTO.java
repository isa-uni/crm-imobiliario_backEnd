package crm_imobiliario.back.model.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LeadRedistribuicaoMassaDTO {
    @NotEmpty(message = "Selecione pelo menos um lead para atribuir.")
    @Size(max = 100, message = "Selecione no máximo 100 leads por vez.")
    private List<Long> leadIds;

    @NotNull(message = "Selecione o novo corretor responsável.")
    private Long novoCorretorId;
}
