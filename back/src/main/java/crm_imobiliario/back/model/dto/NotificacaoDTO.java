package crm_imobiliario.back.model.dto;

import java.time.LocalDateTime;

import crm_imobiliario.back.model.entity.Notificacao;

public record NotificacaoDTO(
        Long id,
        String tipo,
        String mensagem,
        Long leadId,
        String leadNome,
        Boolean lida,
        LocalDateTime dataCriacao
) {
    public static NotificacaoDTO from(Notificacao n) {
        return new NotificacaoDTO(
                n.getId(),
                n.getTipo(),
                n.getMensagem(),
                n.getLeadId(),
                n.getLeadNome(),
                n.getLida(),
                n.getDataCriacao()
        );
    }
}
