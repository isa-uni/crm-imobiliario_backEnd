package crm_imobiliario.back.model.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/** Atualização parcial: campo ausente (null) não é alterado; se enviado, precisa ser válido. */
@AllArgsConstructor
@Getter
@Setter
public class ImovelAtualizacaoDTO {
    @Pattern(regexp = "(?s).*\\S.*", message = "Informe o título do imóvel.")
    @Size(max = 255, message = "O título deve ter no máximo 255 caracteres.")
    private String titulo;
    @Pattern(regexp = "^(disponivel|vendido)$", message = "Selecione o status do imóvel: Disponível ou Vendido.")
    private String status;
    @Pattern(regexp = "(?s).*\\S.*", message = "Informe o endereço do imóvel.")
    @Size(max = 255, message = "O endereço deve ter no máximo 255 caracteres.")
    private String endereco;
    @Pattern(regexp = "(?s).*\\S.*", message = "Informe o bairro do imóvel.")
    @Size(max = 255, message = "O bairro deve ter no máximo 255 caracteres.")
    private String bairro;
    @Pattern(regexp = "(?s).*\\S.*", message = "Informe a cidade do imóvel.")
    @Size(max = 255, message = "A cidade deve ter no máximo 255 caracteres.")
    private String cidade;
    @Positive(message = "Informe um valor de venda maior que zero.")
    private Long valorVenda;
    @PositiveOrZero(message = "A área não pode ser negativa.")
    private Long area;
    @PositiveOrZero(message = "A quantidade de quartos não pode ser negativa.")
    private Long quartos;
    @PositiveOrZero(message = "A quantidade de banheiros não pode ser negativa.")
    private Long banheiros;
    @PositiveOrZero(message = "A quantidade de vagas não pode ser negativa.")
    private Long vagas;
    @Size(max = 255, message = "A descrição deve ter no máximo 255 caracteres.")
    private String descricao;
}
