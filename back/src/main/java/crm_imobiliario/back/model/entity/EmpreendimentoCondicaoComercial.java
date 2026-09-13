package crm_imobiliario.back.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Entity @Table(name = "empreendimento_condicao_comercial")
public class EmpreendimentoCondicaoComercial {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empreendimento_id", nullable = false, unique = true)
    private Long empreendimentoId;

    private Double entrada;
    private Double ato;
    private Integer parcelas;
    private Double valorParcela;
    @Column(columnDefinition = "TEXT")
    private String baloes;
    @Column(columnDefinition = "TEXT")
    private String financiamento;
    private Double subsidio;
    private Boolean fgts;
    private String correcao;
    @Column(columnDefinition = "TEXT")
    private String condicoesEspeciais;
    @Column(columnDefinition = "TEXT")
    private String observacoes;
}
