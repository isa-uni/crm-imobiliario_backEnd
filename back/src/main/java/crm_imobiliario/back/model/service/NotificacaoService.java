package crm_imobiliario.back.model.service;

import crm_imobiliario.back.util.RecursoNaoEncontradoException;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import crm_imobiliario.back.model.entity.Lead;
import crm_imobiliario.back.model.entity.Notificacao;
import crm_imobiliario.back.model.entity.Usuario;
import crm_imobiliario.back.model.repository.NotificacaoRepository;
import crm_imobiliario.back.model.service.notificacao.EmailNotificacaoEvent;

/**
 * Notificações geradas pelos eventos do sistema. Cada notificação vai somente para quem participa do
 * processo e diz o que aconteceu, por que a pessoa está recebendo e o que fazer. As que pedem ação
 * também saem por e-mail, a partir do mesmo evento (enviado após o commit).
 */
@Service
public class NotificacaoService {

    public static final String TIPO_REMOVIDO = "CLIENTE_REMOVIDO";
    public static final String TIPO_LEADS_RECEBIDOS = "LEADS_RECEBIDOS";
    public static final String TIPO_REDISTRIBUICAO_PENDENTE = "REDISTRIBUICAO_PENDENTE";

    /** Quantos nomes de leads aparecem no texto antes de "e mais N". */
    private static final int MAX_NOMES = 5;

    @Autowired
    private NotificacaoRepository notificacaoRepository;

    @Autowired
    private ApplicationEventPublisher eventos;

    /** Aviso (só na plataforma) ao corretor que perdeu o lead numa troca manual de responsável. */
    public void notificarTransferencia(Lead lead, Usuario corretorAnterior, Usuario corretorNovo) {
        if (corretorAnterior == null || !corretorAnterior.isAtivo()) return;
        if (corretorNovo != null && corretorAnterior.getId().equals(corretorNovo.getId())) return;
        salvar(corretorAnterior, TIPO_REMOVIDO, "Lead transferido",
                "O lead " + lead.getNome() + " foi transferido para " + (corretorNovo != null ? corretorNovo.getNome() : "redistribuição") + ".",
                "/leads", lead);
    }

    /**
     * Corretor recebeu um ou mais leads (redistribuição individual ou em massa): uma única notificação e um
     * único e-mail por operação. Não notifica quem atribuiu os leads a si mesmo.
     */
    public void notificarLeadsRecebidos(Usuario corretor, List<Lead> leads, Usuario solicitante) {
        if (corretor == null || !corretor.isAtivo() || leads == null || leads.isEmpty()) return;
        if (solicitante != null && solicitante.getId() != null && solicitante.getId().equals(corretor.getId())) return;

        int n = leads.size();
        String quem = solicitante != null ? solicitante.getNome() : "Um gestor";
        String titulo = n == 1 ? "Novo lead atribuído a você" : n + " novos leads atribuídos a você";
        String mensagem = n == 1
                ? quem + " atribuiu a você o lead " + leads.get(0).getNome() + "."
                : quem + " atribuiu a você " + n + " leads: " + listarNomes(leads) + ".";
        String acao = n == 1
                ? "Entre em contato com o lead e atualize o status dele na tela Leads."
                : "Entre em contato com os leads e atualize o status deles na tela Leads.";
        salvar(corretor, TIPO_LEADS_RECEBIDOS, titulo, mensagem + " " + acao, "/leads", n == 1 ? leads.get(0) : null);
        email(corretor, titulo, mensagem, acao, "/leads");
    }

    /**
     * Corretor inativado deixou leads sem responsável: avisa quem deve redistribuí-los — o gestor dele ou,
     * se ele não tiver gestor, o administrador que fez a inativação.
     */
    public void notificarRedistribuicaoPendente(Usuario responsavel, Usuario corretorInativado, List<Lead> leads, boolean responsavelEhGestor) {
        if (responsavel == null || leads == null || leads.isEmpty()) return;
        int n = leads.size();
        String titulo = n == 1 ? "1 lead aguardando redistribuição" : n + " leads aguardando redistribuição";
        String motivo = responsavelEhGestor
                ? "Você recebeu este aviso porque é o gestor de " + corretorInativado.getNome() + "."
                : "Você recebeu este aviso porque fez a inativação e " + corretorInativado.getNome() + " não tinha gestor vinculado.";
        String mensagem = corretorInativado.getNome() + " foi inativado(a) e "
                + (n == 1 ? "o lead " + leads.get(0).getNome() + " ficou" : n + " leads ficaram") + " sem corretor responsável"
                + (n == 1 ? "" : ": " + listarNomes(leads)) + ". " + motivo;
        String acao = n == 1
                ? "Acesse a tela Redistribuição e atribua o lead a um corretor ativo."
                : "Acesse a tela Redistribuição e atribua esses leads a corretores ativos (é possível atribuir vários de uma vez).";
        salvar(responsavel, TIPO_REDISTRIBUICAO_PENDENTE, titulo, mensagem + " " + acao, "/redistribuicao", null);
        email(responsavel, titulo, mensagem, acao, "/redistribuicao");
    }

    private void salvar(Usuario destino, String tipo, String titulo, String mensagem, String link, Lead lead) {
        notificacaoRepository.save(Notificacao.builder()
                .usuario(destino)
                .tipo(tipo)
                .titulo(titulo)
                .mensagem(mensagem)
                .link(link)
                .leadId(lead != null ? lead.getId() : null)
                .leadNome(lead != null ? lead.getNome() : null)
                .lida(false)
                .dataCriacao(LocalDateTime.now())
                .build());
    }

    private void email(Usuario destino, String assunto, String mensagem, String acao, String link) {
        eventos.publishEvent(new EmailNotificacaoEvent(destino.getEmail(), destino.getNome(), assunto, mensagem, acao, link));
    }

    static String listarNomes(List<Lead> leads) {
        String nomes = leads.stream().limit(MAX_NOMES).map(Lead::getNome).collect(Collectors.joining(", "));
        return leads.size() > MAX_NOMES ? nomes + " e mais " + (leads.size() - MAX_NOMES) : nomes;
    }

    public List<crm_imobiliario.back.model.dto.NotificacaoDTO> listarPorUsuario(Long usuarioId) {
        return notificacaoRepository.findByUsuarioIdOrderByDataCriacaoDesc(usuarioId)
                .stream().map(crm_imobiliario.back.model.dto.NotificacaoDTO::from).toList();
    }

    public long contarNaoLidas(Long usuarioId) {
        return notificacaoRepository.countByUsuarioIdAndLidaFalse(usuarioId);
    }

    public void marcarComoLida(Long notificacaoId, Long usuarioId) {
        Notificacao n = notificacaoRepository.findById(notificacaoId).orElseThrow(() -> new RecursoNaoEncontradoException("A notificação não foi encontrada. Ela pode ter sido removida."));
        if (!n.getUsuario().getId().equals(usuarioId)) throw new AccessDeniedException("Você só pode alterar as suas próprias notificações.");
        n.setLida(true);
        notificacaoRepository.save(n);
    }

    public void marcarTodasComoLidas(Long usuarioId) {
        List<Notificacao> lista = notificacaoRepository.findByUsuarioIdAndLidaFalse(usuarioId);
        for (Notificacao n : lista) n.setLida(true);
        notificacaoRepository.saveAll(lista);
    }
}
