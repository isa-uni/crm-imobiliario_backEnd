package crm_imobiliario.back.model.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class MetaCreateDTO {
    @NotNull
    private Long usuarioId;
    @NotNull
    private LocalDate mesReferencia;
    @NotNull @Min(0)
    private Integer metaContratos;
}
