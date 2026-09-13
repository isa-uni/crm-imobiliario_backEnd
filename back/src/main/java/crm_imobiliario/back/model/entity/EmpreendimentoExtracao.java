package crm_imobiliario.back.model.entity;

import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import jakarta.persistence.*;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Entity @Table(name = "empreendimento_extracao", indexes = {
    @Index(name = "idx_extracao_emp", columnList = "empreendimento_id"),
    @Index(name = "idx_extracao_status", columnList = "status")
})
public class EmpreendimentoExtracao {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empreendimento_id")
    private Long empreendimentoId;

    @Builder.Default
    private String status = "pendente";

    private String modeloIa;
    private Instant dataProcessamento;
    private Instant dataConclusao;

    @Column(columnDefinition = "TEXT")
    private String erro;

    @Column(columnDefinition = "TEXT")
    private String resultado;

    private Long usuario;

    @CreationTimestamp
    private Instant criadoEm;
}
