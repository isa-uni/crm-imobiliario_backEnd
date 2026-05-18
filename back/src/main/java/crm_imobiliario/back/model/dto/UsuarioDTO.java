package crm_imobiliario.back.model.dto;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@AllArgsConstructor
@Getter
@Setter
public class UsuarioDTO {
    @NotBlank(message = "O nome é obrigatória")
    private String nome;
    @Email
    @NotBlank(message = "O email é obrigatória")
    private String email;
    @NotBlank(message = "O cpf é obrigatória")
    private String cpf;

    @NotBlank(message = "O genero é obrigatória")
    private String genero;
    @Pattern(
        regexp = "^\\(?\\d{2}\\)?\\s?9?\\d{4}-?\\d{4}$",
        message = "Telefone inválido"
    )
    @NotBlank(message = "O telefone é obrigatória")
    private String telefone;
    @NotNull(message = "A data de nascimento é obrigatória")
    private LocalDate dataNascimento;
    @NotNull(message = "O papel é obrigatória")
    private Long papelId;
}
