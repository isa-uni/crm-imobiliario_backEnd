package crm_imobiliario.back.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Entity @Table(name = "empreendimento_extracao_documento")
@IdClass(EmpreendimentoExtracaoDocumentoId.class)
public class EmpreendimentoExtracaoDocumento {

    @Id
    @Column(name = "extracao_id", nullable = false)
    private Long extracaoId;

    @Id
    @Column(name = "documento_id", nullable = false)
    private Long documentoId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "extracao_id", insertable = false, updatable = false)
    private EmpreendimentoExtracao extracao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "documento_id", insertable = false, updatable = false)
    private EmpreendimentoDocumento documento;
}
