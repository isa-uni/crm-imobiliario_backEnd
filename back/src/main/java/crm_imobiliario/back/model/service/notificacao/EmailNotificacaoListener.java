package crm_imobiliario.back.model.service.notificacao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Envia o e-mail das notificações depois que a transação foi gravada, fora da requisição (@Async):
 * uma falha de SMTP não desfaz a inativação/redistribuição nem atrasa a resposta da tela.
 *
 * O SMTP é configurado por variáveis de ambiente (spring.mail.host, spring.mail.port,
 * spring.mail.username, spring.mail.password). Sem spring.mail.host o Spring não cria o JavaMailSender
 * e o envio é apenas registrado no log — a notificação na plataforma continua funcionando.
 */
@Component
public class EmailNotificacaoListener {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificacaoListener.class);

    private final ObjectProvider<JavaMailSender> mailSender;

    @Value("${app.mail.remetente:CRM Imóveis <nao-responda@crm-imoveis.local>}")
    private String remetente;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    public EmailNotificacaoListener(ObjectProvider<JavaMailSender> mailSender) {
        this.mailSender = mailSender;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void enviar(EmailNotificacaoEvent e) {
        if (e.destinatarioEmail() == null || e.destinatarioEmail().isBlank()) return;
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.info("E-mail não enviado (SMTP não configurado): para={} assunto=\"{}\"", e.destinatarioEmail(), e.assunto());
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(remetente);
            msg.setTo(e.destinatarioEmail());
            msg.setSubject("[CRM Imóveis] " + e.assunto());
            msg.setText(corpo(e));
            sender.send(msg);
            log.info("E-mail de notificação enviado: para={} assunto=\"{}\"", e.destinatarioEmail(), e.assunto());
        } catch (Exception ex) {
            // a notificação na plataforma já foi gravada; o e-mail é um canal adicional
            log.warn("Falha ao enviar e-mail de notificação para {}: {}", e.destinatarioEmail(), ex.getMessage());
        }
    }

    String corpo(EmailNotificacaoEvent e) {
        StringBuilder sb = new StringBuilder();
        sb.append("Olá, ").append(e.destinatarioNome() != null ? e.destinatarioNome() : "").append(".\n\n");
        sb.append(e.mensagem()).append("\n");
        if (e.acao() != null && !e.acao().isBlank()) sb.append("\nO que fazer: ").append(e.acao()).append("\n");
        if (e.link() != null && !e.link().isBlank()) sb.append("\nAcesse: ").append(frontendUrl).append(e.link()).append("\n");
        sb.append("\nEsta mensagem também está disponível no ícone de notificações do CRM Imóveis.\n");
        sb.append("E-mail automático, não responda.");
        return sb.toString();
    }
}
