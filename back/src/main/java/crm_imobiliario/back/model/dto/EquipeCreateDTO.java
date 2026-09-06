package crm_imobiliario.back.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class EquipeCreateDTO {
    @NotBlank(message = "Nome da equipe é obrigatório")
    private String nome;
    private String descricao;
    private Long gestorId;
}
