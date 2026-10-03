package crm_imobiliario.back.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginDTO(
        @Email(regexp = crm_imobiliario.back.util.validacao.Documentos.EMAIL_REGEX, message = "O e-mail informado não é válido. Use o formato nome@dominio.com.")
        @NotBlank(message = "Informe o seu e-mail.")
        String email,
        @NotBlank(message = "Informe a sua senha.")
        String senha
) {}
