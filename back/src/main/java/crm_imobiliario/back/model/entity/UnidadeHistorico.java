package crm_imobiliario.back.model.entity;

import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import jakarta.persistence.*;
import lombok.*;

/** Histórico de alteração de campo de uma unidade entre uploads/confirmações (§15 do spec de importação). */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Entity @Table(name = "unidade_historico", indexes = {
    @Index(name = "idx_unid_hist_unid_campo", columnList = "unidade_id, campo")
})
public class UnidadeHistorico {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "unidade_id", nullable = false)
    private Long unidadeId;

    @Column(nullable = false)
    private String campo;

    @Column(columnDefinition = "TEXT")
    private String valorAnterior;

    @Column(columnDefinition = "TEXT")
    private String valorNovo;

    private Long usuario;

    @Column(name = "documento_origem_id")
    private Long documentoOrigemId;

    /** extracao | usuario */
    @Builder.Default
    private String origem = "usuario";

    @CreationTimestamp
    private Instant criadoEm;
}
