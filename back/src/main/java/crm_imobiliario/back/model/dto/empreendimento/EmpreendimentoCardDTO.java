package crm_imobiliario.back.model.dto.empreendimento;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EmpreendimentoCardDTO {
    private Long id;
    private String nome;
    private String slug;
    private String codigoExterno;
    private String codigoCrm;
    private String status;
    private String cidade;
    private String bairro;
    private String uf;
    private Double metragemMin;
    private Double metragemMax;
    private Integer quartosMin;
    private Integer quartosMax;
    private Integer vagasMin;
    private Integer vagasMax;
    private Long precoMin;
    private Long precoMax;
    private String imagemUrl;
    private Integer disponiveis;
    private Boolean ativo;
}
