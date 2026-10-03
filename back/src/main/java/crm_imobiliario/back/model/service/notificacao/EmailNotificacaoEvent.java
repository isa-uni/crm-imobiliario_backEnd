package crm_imobiliario.back.model.service.notificacao;

/**
 * Pedido de e-mail gerado junto com uma notificação da plataforma (mesmo evento de origem).
 * É publicado dentro da transação e só enviado depois do commit (ver {@link EmailNotificacaoListener}):
 * se a operação falhar e for desfeita, nenhum e-mail sai.
 */
public record EmailNotificacaoEvent(
        String destinatarioEmail,
        String destinatarioNome,
        String assunto,
        String mensagem,
        String acao,
        String link
) {}
