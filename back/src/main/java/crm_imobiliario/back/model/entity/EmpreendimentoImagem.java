package crm_imobiliario.back.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Entity @Table(name = "empreendimento_imagem", indexes = @Index(name = "idx_img_emp_tipo", columnList = "empreendimento_id,tipo"))
public class EmpreendimentoImagem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "empreendimento_id", nullable = false)
    private Long empreendimentoId;
    private Long arquivoId;
    private String tipo;
    private String legenda;
    @Builder.Default
    private Integer ordem = 0;
    @Builder.Default
    private Boolean destaque = false;
    @Column(columnDefinition = "TEXT")
    private String url;
}
