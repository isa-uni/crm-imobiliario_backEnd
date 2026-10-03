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
    @NotBlank(message = "Informe o nome da equipe.")
    @jakarta.validation.constraints.Size(max = 100, message = "O nome da equipe deve ter no máximo 100 caracteres.")
    private String nome;
    private String descricao;
    private Long gestorId;
}
