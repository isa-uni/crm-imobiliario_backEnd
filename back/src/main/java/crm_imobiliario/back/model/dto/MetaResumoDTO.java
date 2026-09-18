package crm_imobiliario.back.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Meta própria (definida pelo corretor) e meta atribuída pelo gestor, lado a lado, junto da
 * meta que efetivamente vale: a própria quando existe, senão a do gestor.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class MetaResumoDTO {
    private MetaDTO metaPropria;
    private MetaDTO metaGestor;
    private MetaDTO metaEfetiva;
}
