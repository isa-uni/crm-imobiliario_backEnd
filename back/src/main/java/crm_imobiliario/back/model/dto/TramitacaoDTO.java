package crm_imobiliario.back.model.dto;

import java.time.LocalDateTime;

import crm_imobiliario.back.model.entity.Tramitacao;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class TramitacaoDTO {

    private Long id;
    private Long leadId;
    private String statusAnterior;
    private String statusAtual;
    private LocalDateTime dataMovimentacao;
    private Long usuarioId;

    public static TramitacaoDTO from(Tramitacao tramitacao) {
        TramitacaoDTO dto = new TramitacaoDTO();
        dto.setId(tramitacao.getId());
        dto.setLeadId(tramitacao.getLead() != null ? tramitacao.getLead().getId() : null);
        dto.setStatusAnterior(tramitacao.getStatus_anterior());
        dto.setStatusAtual(tramitacao.getStatus_atual());
        dto.setDataMovimentacao(tramitacao.getDataMovimentacao());
        dto.setUsuarioId(tramitacao.getUsuario() != null ? tramitacao.getUsuario().getId() : null);
        return dto;
    }
}