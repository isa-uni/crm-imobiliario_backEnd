package crm_imobiliario.back.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter
public class ImovelDTO {

    @NotBlank(message = "Informe o título do imóvel.")
    @Size(max = 255, message = "O título deve ter no máximo 255 caracteres.")
    private String titulo;

    @NotBlank(message = "Selecione o status do imóvel.")
    @Pattern(regexp = "^(disponivel|vendido)$", message = "Selecione o status do imóvel: Disponível ou Vendido.")
    private String status;

    @NotBlank(message = "Informe o endereço do imóvel.")
    @Size(max = 255, message = "O endereço deve ter no máximo 255 caracteres.")
    private String endereco;

    @NotBlank(message = "Informe o bairro do imóvel.")
    @Size(max = 255, message = "O bairro deve ter no máximo 255 caracteres.")
    private String bairro;

    @NotBlank(message = "Informe a cidade do imóvel.")
    @Size(max = 255, message = "A cidade deve ter no máximo 255 caracteres.")
    private String cidade;

    @Size(max = 255, message = "A descrição deve ter no máximo 255 caracteres.")
    private String descricao;

    @NotNull(message = "Informe o valor de venda do imóvel.")
    @Positive(message = "Informe um valor de venda maior que zero.")
    private Long valorVenda;

    @PositiveOrZero(message = "A quantidade de quartos não pode ser negativa.")
    private Long quartos;

    @PositiveOrZero(message = "A quantidade de banheiros não pode ser negativa.")
    private Long banheiros;

    @PositiveOrZero(message = "A quantidade de vagas não pode ser negativa.")
    private Long vagas;

    @PositiveOrZero(message = "A área não pode ser negativa.")
    private Long area;
}
