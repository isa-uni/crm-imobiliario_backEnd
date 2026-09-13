package crm_imobiliario.back.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Entity @Table(name = "empreendimento_planta", indexes = @Index(name = "idx_planta_emp", columnList = "empreendimento_id"))
public class EmpreendimentoPlanta {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empreendimento_id", nullable = false)
    private Long empreendimentoId;

    private String nome;
    private String tipo;
    private Double metragem;
    private Integer quartos;
    private Integer suites;
    private Integer banheiros;
    private Integer vagas;
    @Column(columnDefinition = "TEXT")
    private String descricao;
    private Long arquivoId;
    @Builder.Default
    private Integer ordem = 0;
}
