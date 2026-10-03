package crm_imobiliario.back.model.dto;

import java.time.LocalDate;

import crm_imobiliario.back.util.validacao.DataNascimento;
import crm_imobiliario.back.util.validacao.Documentos;
import crm_imobiliario.back.util.validacao.Telefone;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PerfilDTO(
        @NotBlank(message = "Informe o seu nome.")
        @Size(min = 2, max = 255, message = "O nome deve ter entre 2 e 255 caracteres.")
        @Pattern(regexp = "(?s).*\\p{L}.*", message = "O nome deve conter letras.")
        String nome,
        @NotBlank(message = "Informe o seu e-mail.")
        @Email(regexp = Documentos.EMAIL_REGEX, message = "O e-mail informado não é válido. Use o formato nome@dominio.com.")
        @Size(max = 255, message = "O e-mail deve ter no máximo 255 caracteres.")
        String email,
        @NotBlank(message = "Selecione o gênero.")
        @Pattern(regexp = "^[MFO]$", message = "Selecione o gênero: Masculino, Feminino ou Outro.")
        String genero,
        @NotBlank(message = "Informe o seu telefone.")
        @Telefone
        String telefone,
        @NotNull(message = "Informe a data de nascimento.")
        @DataNascimento
        LocalDate dataNascimento
) {}
