package crm_imobiliario.back.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter
public class ImovelDTO {
    @NotBlank(message = "O titulo é obrigatória")
    private String titulo;
    @NotBlank(message = "O status é obrigatória")
    private String status;
    @NotBlank(message = "O endereco é obrigatória")
    private String endereco;
    @NotBlank(message = "O bairro é obrigatória")
    private String bairro;
    @NotBlank(message = "O cidade é obrigatória")
    private String cidade;
    @NotBlank(message = "O descricao é obrigatória")
    private String descricao;

    @NotNull(message = "O Valor de venda é obrigatória")
    private Long valorVenda;
    @NotNull(message = "O quartos é obrigatória")
    private Long quartos;
    @NotNull(message = "O banheiro é obrigatória")
    private Long banheiros;
    @NotNull(message = "O vaga é obrigatória")
    private Long vagas;
    @NotNull(message = "A área é obrigatória")
    private Long area;
}
