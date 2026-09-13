package crm_imobiliario.back.model.entity;

import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import jakarta.persistence.*;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Entity @Table(name = "empreendimento_fonte", indexes = {
    @Index(name = "idx_fonte_extracao", columnList = "extracao_id"),
    @Index(name = "idx_fonte_campo", columnList = "campo")
})
public class EmpreendimentoFonte {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "extracao_id")
    private Long extracaoId;

    @Column(name = "documento_id")
    private Long documentoId;

    @Column(name = "empreendimento_id")
    private Long empreendimentoId;

    @Column(nullable = false)
    private String campo;

    @Column(columnDefinition = "TEXT")
    private String valorExtraido;

    private Integer pagina;

    @Column(columnDefinition = "TEXT")
    private String trecho;

    private Integer confianca;

    private String documentoNome;

    @CreationTimestamp
    private Instant criadoEm;
}
