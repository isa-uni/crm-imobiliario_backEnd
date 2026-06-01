package crm_imobiliario.back.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter
public class LeadsDTO {

    @NotBlank(message = "O nome é obrigatória")
    private String nome;
    @Email
    @NotBlank(message = "O email é obrigatória")
    private String email;
    @Pattern(
        regexp = "^\\d{10,11}$",
        message = "Telefone inválido"
    )
    @NotBlank(message = "O telefone é obrigatória")
    private String telefone;

    @NotNull(message = "A origem é obrigatória")
    private String origem;

    @NotNull(message = "O status é obrigatória")
    private String status;

    @NotNull(message = "O Valor de interesse é obrigatória")
    private Long valorInteresse;

    // @NotBlank(message = "A observação é obrigatória")
    private String observacao;
    
    // @NotNull(message = "O papel é obrigatória")
    // private Long papelId;
    // @NotNull(message = "O imóvel é obrigatória")
    private Long imovelId;
}
