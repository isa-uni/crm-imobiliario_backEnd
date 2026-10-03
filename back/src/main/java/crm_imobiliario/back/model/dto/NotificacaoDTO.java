package crm_imobiliario.back.model.dto;

import java.time.LocalDateTime;

import crm_imobiliario.back.model.entity.Notificacao;

public record NotificacaoDTO(
        Long id,
        String tipo,
        String titulo,
        String mensagem,
        String link,
        Long leadId,
        String leadNome,
        Boolean lida,
        LocalDateTime dataCriacao
) {
    public static NotificacaoDTO from(Notificacao n) {
        return new NotificacaoDTO(
                n.getId(),
                n.getTipo(),
                n.getTitulo(),
                n.getMensagem(),
                n.getLink(),
                n.getLeadId(),
                n.getLeadNome(),
                n.getLida(),
                n.getDataCriacao()
        );
    }
}
