package crm_imobiliario.back.model.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import crm_imobiliario.back.model.entity.Lead;
import crm_imobiliario.back.model.entity.Notificacao;
import crm_imobiliario.back.model.entity.Usuario;
import crm_imobiliario.back.model.repository.NotificacaoRepository;

@Service
public class NotificacaoService {

    @Autowired
    private NotificacaoRepository notificacaoRepository;

    public void notificarTransferencia(Lead lead, Usuario corretorAnterior, Usuario corretorNovo) {
        if (corretorAnterior != null && corretorAnterior.isAtivo()) {
            Notificacao n = Notificacao.builder()
                    .usuario(corretorAnterior)
                    .tipo("CLIENTE_REMOVIDO")
                    .mensagem("Cliente " + lead.getNome() + " foi transferido para " + (corretorNovo != null ? corretorNovo.getNome() : "aguardando redistribuição"))
                    .leadId(lead.getId())
                    .leadNome(lead.getNome())
                    .lida(false)
                    .dataCriacao(LocalDateTime.now())
                    .build();
            notificacaoRepository.save(n);
        }
        if (corretorNovo != null && corretorNovo.isAtivo()) {
            String origem = corretorAnterior != null ? " de " + corretorAnterior.getNome() : "";
            Notificacao n = Notificacao.builder()
                    .usuario(corretorNovo)
                    .tipo("CLIENTE_RECEBIDO")
                    .mensagem("Você recebeu o cliente " + lead.getNome() + origem)
                    .leadId(lead.getId())
                    .leadNome(lead.getNome())
                    .lida(false)
                    .dataCriacao(LocalDateTime.now())
                    .build();
            notificacaoRepository.save(n);
        }
    }

    public List<crm_imobiliario.back.model.dto.NotificacaoDTO> listarPorUsuario(Long usuarioId) {
        return notificacaoRepository.findByUsuarioIdOrderByDataCriacaoDesc(usuarioId)
                .stream().map(crm_imobiliario.back.model.dto.NotificacaoDTO::from).toList();
    }

    public long contarNaoLidas(Long usuarioId) {
        return notificacaoRepository.countByUsuarioIdAndLidaFalse(usuarioId);
    }

    public void marcarComoLida(Long notificacaoId, Long usuarioId) {
        Notificacao n = notificacaoRepository.findById(notificacaoId).orElseThrow(() -> new RuntimeException("Notificação não encontrada"));
        if (!n.getUsuario().getId().equals(usuarioId)) throw new RuntimeException("Sem permissão");
        n.setLida(true);
        notificacaoRepository.save(n);
    }

    public void marcarTodasComoLidas(Long usuarioId) {
        List<Notificacao> lista = notificacaoRepository.findByUsuarioIdAndLidaFalse(usuarioId);
        for (Notificacao n : lista) n.setLida(true);
        notificacaoRepository.saveAll(lista);
    }
}
