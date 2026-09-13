package crm_imobiliario.back.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Entity @Table(name = "empreendimento_caracteristica")
public class EmpreendimentoCaracteristica {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empreendimento_id", nullable = false, unique = true)
    private Long empreendimentoId;

    private Double metragemMin;
    private Double metragemMax;
    private Integer quartosMin;
    private Integer quartosMax;
    private Integer suitesMin;
    private Integer suitesMax;
    private Integer banheirosMin;
    private Integer banheirosMax;
    private Integer vagasMin;
    private Integer vagasMax;
    private Integer pavimentos;
    private Integer unidadesPorAndar;
    private Integer qtdTorres;
    private Boolean possuiElevador;
}
