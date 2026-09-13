package crm_imobiliario.back.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Entity @Table(name = "empreendimento_diferencial")
public class EmpreendimentoDiferencial {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "empreendimento_id", nullable = false)
    private Long empreendimentoId;
    @Column(nullable = false)
    private String titulo;
    @Column(columnDefinition = "TEXT")
    private String descricao;
    @Builder.Default
    private Integer ordem = 0;
}
