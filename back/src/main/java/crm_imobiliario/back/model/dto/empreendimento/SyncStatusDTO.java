package crm_imobiliario.back.model.dto.empreendimento;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncStatusDTO {
    private Instant montadoEm;
    private Integer totalCatalogo;
    private Long totalDisponiveis;
    private Long capasProntas;
    private Long totalUnidades;
    private Boolean emBuild;
    private String ultimoErro;
    private Instant ultimaSincronizacao;
}
