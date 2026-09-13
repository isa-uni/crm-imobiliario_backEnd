package crm_imobiliario.back.model.entity;

import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import jakarta.persistence.*;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Entity @Table(name = "empreendimento_documento", indexes = {
    @Index(name = "idx_doc_emp", columnList = "empreendimento_id"),
    @Index(name = "idx_doc_hash", columnList = "hash")
})
public class EmpreendimentoDocumento {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empreendimento_id")
    private Long empreendimentoId;

    @Column(nullable = false)
    private String nomeOriginal;
    @Column(nullable = false)
    private String nomeArmazenado;
    private String tipo;
    private Long tamanho;
    @Column(nullable = false)
    private String caminho;
    @Column(nullable = false)
    private String hash;
    private String mime;

    @CreationTimestamp
    private Instant dataUpload;

    @Column(name = "usuario_upload")
    private Long usuarioUpload;

    @Builder.Default
    private String statusProcessamento = "pendente";
}
