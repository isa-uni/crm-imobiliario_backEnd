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
    private String nomeUnidade;
    private String bloco;
    private String tipologia;
    private Double areaPrivativa;
    private Double areaComum;
    private Double outrasAreas;
    private String garagem;
    private String situacao;
    private Long preco;
    private Long ato;
    private Long subsidioCohapar;
    private Long financiamento;
    private Long valorAvaliacao;
    private String observacoes;
    private Long documentoOrigemId;
    private Integer linhaOrigem;
    private String statusValidacao;
}
