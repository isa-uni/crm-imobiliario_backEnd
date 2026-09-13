package crm_imobiliario.back.model.entity;

import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.CreationTimestamp;
import jakarta.persistence.*;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Entity @Table(name = "empreendimento_preco", indexes = @Index(name = "idx_preco_emp_data", columnList = "empreendimento_id,data_referencia"))
public class EmpreendimentoPreco {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empreendimento_id", nullable = false)
    private Long empreendimentoId;

    @Builder.Default
    private String tipo = "venda";
    private Long valorMin;
    private Long valorMax;
    @Builder.Default
    private String moeda = "BRL";
    private LocalDate dataReferencia;
    @Column(columnDefinition = "TEXT")
    private String observacao;
    private Long criadoPor;
    @CreationTimestamp
    private Instant criadoEm;
}
