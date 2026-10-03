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
    @NotNull(message = "Selecione o usuário da meta.")
    private Long usuarioId;
    @NotNull(message = "Informe o mês de referência da meta.")
    private LocalDate mesReferencia;
    @NotNull(message = "Informe a quantidade de contratos da meta.") @Min(value = 0, message = "A meta de contratos não pode ser negativa.")
    private Integer metaContratos;
}
