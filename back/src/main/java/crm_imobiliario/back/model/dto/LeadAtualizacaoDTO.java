package crm_imobiliario.back.model.dto;

import crm_imobiliario.back.util.validacao.Documentos;
import crm_imobiliario.back.util.validacao.Telefone;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * Atualização parcial: campo ausente (null) não é alterado. Mas, se enviado, precisa ser válido —
 * antes era possível gravar nome vazio, telefone com letras ou valor negativo pela edição.
 */
@AllArgsConstructor
@Getter
@Setter
public class LeadAtualizacaoDTO {

    @Pattern(regexp = "(?s).*\\S.*", message = "Informe o nome do lead.")
    @Size(min = 2, max = 255, message = "O nome deve ter entre 2 e 255 caracteres.")
    private String nome;
    @Email(regexp = Documentos.EMAIL_REGEX, message = "O e-mail informado não é válido. Use o formato nome@dominio.com.")
    @Size(max = 255, message = "O e-mail deve ter no máximo 255 caracteres.")
    private String email;
    @Pattern(regexp = "(?s).*\\S.*", message = "Informe o telefone do lead.")
    @Telefone
    private String telefone;
    @Pattern(regexp = "(?s).*\\S.*", message = "Selecione a origem do lead.")
    private String origem;
    @Pattern(regexp = "(?s).*\\S.*", message = "Selecione o histórico do lead.")
    private String historico;
    private String status;
    @PositiveOrZero(message = "O valor de interesse não pode ser negativo.")
    private Long valorInteresse;
    private Long empreendimentoId;
    // true = remover o empreendimento já vinculado; ausente/false = não mexer no vínculo atual.
    // Necessário porque este DTO também é usado em atualizações parciais (ex.: só trocar o status),
    // que não devem apagar o empreendimento só por não terem enviado o campo.
    private Boolean limparEmpreendimento;
    @Size(max = 255, message = "As observações devem ter no máximo 255 caracteres.")
    private String observacao;
    @Size(max = 255, message = "O motivo do descarte deve ter no máximo 255 caracteres.")
    private String motivoDescarte;
}
