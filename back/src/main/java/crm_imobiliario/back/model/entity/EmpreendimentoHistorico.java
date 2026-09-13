package crm_imobiliario.back.model.entity;

import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import jakarta.persistence.*;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Entity @Table(name = "empreendimento_historico", indexes = @Index(name = "idx_hist_emp_campo", columnList = "empreendimento_id,campo"))
public class EmpreendimentoHistorico {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "empreendimento_id", nullable = false)
    private Long empreendimentoId;
    @Column(nullable = false)
    private String campo;
    @Column(columnDefinition = "TEXT")
    private String valorAnterior;
    @Column(columnDefinition = "TEXT")
    private String valorNovo;
    private Long usuario;
    @Builder.Default
    private String origem = "usuario";
    @CreationTimestamp
    private Instant criadoEm;
}
