package crm_imobiliario.back.model.dto;

import java.time.LocalDate;

import crm_imobiliario.back.util.validacao.Cadastro;
import crm_imobiliario.back.util.validacao.Cpf;
import crm_imobiliario.back.util.validacao.DataNascimento;
import crm_imobiliario.back.util.validacao.Documentos;
import crm_imobiliario.back.util.validacao.Telefone;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@AllArgsConstructor
@Getter
@Setter
public class UsuarioDTO {
    @NotBlank(message = "Informe o nome do usuário.")
    @Size(min = 2, max = 255, message = "O nome deve ter entre 2 e 255 caracteres.")
    @Pattern(regexp = "(?s).*\\p{L}.*", message = "O nome deve conter letras.")
    private String nome;
    @Email(regexp = Documentos.EMAIL_REGEX, message = "O e-mail informado não é válido. Use o formato nome@dominio.com.")
    @Size(max = 255, message = "O e-mail deve ter no máximo 255 caracteres.")
    @NotBlank(message = "Informe o e-mail do usuário.")
    private String email;
    // o CPF não pode ser alterado na edição; por isso a validação de conteúdo vale só no cadastro
    @NotBlank(message = "Informe o CPF do usuário.")
    @Cpf(groups = Cadastro.class)
    private String cpf;
    @NotBlank(message = "Selecione o gênero.")
    @Pattern(regexp = "^[MFO]$", message = "Selecione o gênero: Masculino, Feminino ou Outro.")
    private String genero;
    @Telefone
    @NotBlank(message = "Informe o telefone do usuário.")
    private String telefone;
    @NotNull(message = "Informe a data de nascimento.")
    @DataNascimento
    private LocalDate dataNascimento;
    @NotNull(message = "Selecione o papel do usuário.")
    private Long papelId;
    private Long gestorId;
}
