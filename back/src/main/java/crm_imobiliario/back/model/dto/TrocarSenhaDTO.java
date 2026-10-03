package crm_imobiliario.back.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TrocarSenhaDTO(
        @NotBlank(message = "Informe a sua senha atual.")
        String senhaAtual,
        @NotBlank(message = "Informe a nova senha.")
        @Size(min = 8, message = "A nova senha deve ter pelo menos 8 caracteres.")
        String novaSenha
) {}
