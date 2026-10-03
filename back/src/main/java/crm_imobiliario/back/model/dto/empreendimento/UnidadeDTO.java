package crm_imobiliario.back.model.dto.empreendimento;

import lombok.*;

/**
 * Representa uma unidade tanto na listagem de detalhes (valores já
 * confirmados) quanto no payload de confirmação da extração (valores que o
 * usuário revisou e aceitou) — mesmo formato achatado nas duas direções.
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UnidadeDTO {
    private Long id;
    @jakarta.validation.constraints.Size(max = 255, message = "O nome da unidade deve ter no máximo 255 caracteres.")
    private String nomeUnidade;
    private String bloco;
    private String tipologia;
    @jakarta.validation.constraints.PositiveOrZero(message = "A área privativa não pode ser negativa.")
    private Double areaPrivativa;
    @jakarta.validation.constraints.PositiveOrZero(message = "A área comum não pode ser negativa.")
    private Double areaComum;
    @jakarta.validation.constraints.PositiveOrZero(message = "Outras áreas não pode ser negativo.")
    private Double outrasAreas;
    @jakarta.validation.constraints.Size(max = 50, message = "A garagem deve ter no máximo 50 caracteres.")
    private String garagem;
    private String situacao;
    @jakarta.validation.constraints.PositiveOrZero(message = "O valor total não pode ser negativo.")
    private Long preco;
    @jakarta.validation.constraints.PositiveOrZero(message = "O valor do ato não pode ser negativo.")
    private Long ato;
    @jakarta.validation.constraints.PositiveOrZero(message = "O subsídio não pode ser negativo.")
    private Long subsidioCohapar;
    @jakarta.validation.constraints.PositiveOrZero(message = "O financiamento não pode ser negativo.")
    private Long financiamento;
    @jakarta.validation.constraints.PositiveOrZero(message = "O valor de avaliação não pode ser negativo.")
    private Long valorAvaliacao;
    private String observacoes;
    private Long documentoOrigemId;
    private Integer linhaOrigem;
    private String statusValidacao;
}
