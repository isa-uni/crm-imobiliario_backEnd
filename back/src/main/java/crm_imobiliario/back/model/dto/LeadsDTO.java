package crm_imobiliario.back.model.dto;

import crm_imobiliario.back.util.validacao.Documentos;
import crm_imobiliario.back.util.validacao.Telefone;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter
public class LeadsDTO {

    @NotBlank(message = "Informe o nome do lead.")
    @Size(min = 2, max = 255, message = "O nome deve ter entre 2 e 255 caracteres.")
    @Pattern(regexp = "(?s).*\\p{L}.*", message = "O nome deve conter letras.")
    private String nome;

    // opcional (vazio é aceito); se preenchido, precisa ser um e-mail válido
    @Email(regexp = Documentos.EMAIL_REGEX, message = "O e-mail informado não é válido. Use o formato nome@dominio.com.")
    @Size(max = 255, message = "O e-mail deve ter no máximo 255 caracteres.")
    private String email;

    @Telefone
    @NotBlank(message = "Informe o telefone do lead.")
    private String telefone;

    @NotBlank(message = "Selecione a origem do lead.")
    private String origem;

    @NotBlank(message = "Selecione o histórico do lead.")
    private String historico;

    @NotBlank(message = "Selecione o status do lead.")
    private String status;

    @NotNull(message = "Informe o valor de interesse.")
    @PositiveOrZero(message = "O valor de interesse não pode ser negativo.")
    private Long valorInteresse;

    @Size(max = 255, message = "As observações devem ter no máximo 255 caracteres.")
    private String observacao;

    private Long empreendimentoId;
}
