package crm_imobiliario.back.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Entity @Table(name = "empreendimento_area_comum", indexes = @Index(name = "idx_ac_emp_ordem", columnList = "empreendimento_id,ordem"))
public class EmpreendimentoAreaComum {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "empreendimento_id", nullable = false)
    private Long empreendimentoId;
    @Column(nullable = false)
    private String nome;
    @Column(columnDefinition = "TEXT")
    private String descricao;
    private String icone;
    @Builder.Default
    private Integer ordem = 0;
}
