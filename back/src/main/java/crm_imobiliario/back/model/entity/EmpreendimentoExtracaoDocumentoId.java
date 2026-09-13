package crm_imobiliario.back.model.entity;

import java.io.Serializable;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
public class EmpreendimentoExtracaoDocumentoId implements Serializable {
    private Long extracaoId;
    private Long documentoId;
}
