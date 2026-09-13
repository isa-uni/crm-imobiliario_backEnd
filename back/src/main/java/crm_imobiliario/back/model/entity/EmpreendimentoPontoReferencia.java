package crm_imobiliario.back.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Entity @Table(name = "empreendimento_ponto_referencia")
public class EmpreendimentoPontoReferencia {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "empreendimento_id", nullable = false)
    private Long empreendimentoId;
    @Column(nullable = false)
    private String nome;
    private String categoria;
    private Double distancia;
    @Builder.Default
    private String unidade = "m";
    private Integer tempo;
    private Double lat;
    private Double lng;
    @Builder.Default
    private Integer ordem = 0;
}
