package crm_imobiliario.back.model.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import crm_imobiliario.back.model.dto.LeadAguardandoDTO;
import crm_imobiliario.back.model.entity.Equipe;
import crm_imobiliario.back.model.entity.Lead;
import crm_imobiliario.back.model.entity.LeadResponsavelHistorico;
import crm_imobiliario.back.model.entity.Usuario;
import crm_imobiliario.back.model.repository.EquipeRepository;
import crm_imobiliario.back.model.repository.LeadRepository;
import crm_imobiliario.back.model.repository.LeadResponsavelHistoricoRepository;
import crm_imobiliario.back.model.repository.UsuarioRepository;
import crm_imobiliario.back.util.RecursoNaoEncontradoException;
import crm_imobiliario.back.util.RegraNegocioException;

@Service
public class LeadAtribuicaoService {

    @Autowired
    private LeadRepository leadRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private EquipeRepository equipeRepository;
    @Autowired
    private LeadResponsavelHistoricoRepository historicoRepository;
    @Autowired
    private NotificacaoService notificacaoService;

    private Equipe resolverEquipeUsuario(Usuario u) {
        return EquipeResolver.resolver(u, equipeRepository);
    }

    /** Leads hoje atribuídos ao corretor — os que irão para a redistribuição se ele for inativado. */
    public long contarLeadsAtribuidos(Long corretorId) {
        return leadRepository.findByCorretorId(corretorId).stream().filter(l -> "ATRIBUIDO".equals(l.getStatusAtribuicao())).count();
    }

    /** Limite de leads por atribuição em massa (mesmo tamanho máximo de página da tela de redistribuição). */
    public static final int MAX_EM_MASSA = 100;

    /**
     * Gestor que responde pelos leads do corretor: o gestor vinculado, se existir e estiver ativo.
     * Retorna null quando o corretor não tem gestor (ou o gestor está inativo).
     */
    public static Usuario gestorAtivo(Usuario corretor) {
        Usuario g = corretor != null ? corretor.getGestor() : null;
        if (g == null || !g.isAtivo() || (corretor.getId() != null && corretor.getId().equals(g.getId()))) return null;
        return g;
    }

    /**
     * Inativação de corretor: os leads atribuídos a ele passam a aguardar redistribuição e ganham um
     * responsável — o gestor do corretor ou, sem gestor, quem fez a inativação. O responsável é notificado
     * (plataforma + e-mail). Retorna o responsável definido (null se não havia leads).
     */
    @Transactional
    public Usuario desligamentoCorretor(Long corretorId, Usuario solicitante) {
        Usuario corretor = usuarioRepository.findById(corretorId).orElseThrow(() -> new RecursoNaoEncontradoException("O corretor informado não foi encontrado."));
        List<Lead> leads = leadRepository.findByCorretorId(corretorId).stream()
                .filter(l -> "ATRIBUIDO".equals(l.getStatusAtribuicao()))
                .toList();
        if (leads.isEmpty()) return null;

        Usuario gestor = gestorAtivo(corretor);
        // nunca o próprio corretor inativado: sem gestor, responde quem fez a inativação
        Usuario responsavel = gestor != null ? gestor
                : (solicitante != null && !solicitante.getId().equals(corretor.getId()) ? solicitante : null);

        for (Lead lead : leads) {
            encerrarHistoricoAberto(lead);
            lead.setCorretor(null);
            lead.setCorretor_responsavel(null);
            lead.setStatusAtribuicao("AGUARDANDO_REDISTRIBUICAO");
            lead.setResponsavelRedistribuicao(responsavel);
            // equipe permanece
            leadRepository.save(lead);

            historicoRepository.save(LeadResponsavelHistorico.builder()
                    .lead(lead)
                    .corretor(null)
                    .equipe(lead.getEquipe())
                    .gestor(gestor)
                    .dataInicio(LocalDateTime.now())
                    .motivo("DESLIGAMENTO_CORRETOR")
                    .usuarioResponsavel(solicitante)
                    .build());
        }
        notificacaoService.notificarRedistribuicaoPendente(responsavel, corretor, leads, gestor != null);
        return responsavel;
    }

    @Transactional
    public Lead redistribuir(Long leadId, Long novoCorretorId, Usuario solicitante) {
        Lead lead = leadRepository.findById(leadId).orElseThrow(() -> new RecursoNaoEncontradoException("O lead solicitado não foi encontrado. Ele pode ter sido removido."));
        Usuario novoCorretor = buscarCorretorDestino(novoCorretorId);
        Usuario corretorAnterior = lead.getCorretor();
        Lead salvo = atribuir(lead, novoCorretor, solicitante);
        notificacaoService.notificarTransferencia(salvo, corretorAnterior, novoCorretor);
        notificacaoService.notificarLeadsRecebidos(novoCorretor, List.of(salvo), solicitante);
        return salvo;
    }

    /**
     * Atribuição em massa (tela de redistribuição): todos os leads selecionados vão para o mesmo corretor
     * numa única transação. Cada lead passa pelas mesmas regras da atribuição individual; se qualquer um
     * for inválido, nenhum é atribuído e a mensagem diz qual lead e por quê. O corretor recebe uma única
     * notificação (e um único e-mail) com todos os leads.
     */
    @Transactional
    public List<Lead> redistribuirEmMassa(List<Long> leadIds, Long novoCorretorId, Usuario solicitante) {
        if (leadIds == null || leadIds.isEmpty()) {
            throw new RegraNegocioException("Selecione pelo menos um lead para atribuir.", "leadIds", "EMPTY_SELECTION");
        }
        List<Long> ids = leadIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.size() > MAX_EM_MASSA) {
            throw new RegraNegocioException("Selecione no máximo " + MAX_EM_MASSA + " leads por vez.", "leadIds", "TOO_MANY_LEADS");
        }
        Usuario novoCorretor = buscarCorretorDestino(novoCorretorId);

        List<Lead> leads = leadRepository.findAllById(ids);
        if (leads.size() != ids.size()) {
            int faltam = ids.size() - leads.size();
            throw new RecursoNaoEncontradoException((faltam == 1 ? "1 lead selecionado não foi encontrado" : faltam + " leads selecionados não foram encontrados")
                    + ". Atualize a lista e selecione novamente.");
        }
        // mantém a ordem da seleção
        Map<Long, Lead> porId = leads.stream().collect(Collectors.toMap(Lead::getId, l -> l));
        List<Lead> ordenados = ids.stream().map(porId::get).toList();

        for (Lead lead : ordenados) {
            if (!"AGUARDANDO_REDISTRIBUICAO".equals(lead.getStatusAtribuicao())) {
                String atual = lead.getCorretor() != null ? " a " + lead.getCorretor().getNome() : "";
                throw new RegraNegocioException("O lead " + lead.getNome() + " já foi atribuído" + atual
                        + " e não está mais aguardando redistribuição. Atualize a lista e tente novamente. Nenhum lead foi atribuído.",
                        "leadIds", "LEAD_NOT_WAITING");
            }
        }
        List<Lead> atribuidos = new ArrayList<>();
        for (Lead lead : ordenados) {
            atribuidos.add(atribuir(lead, novoCorretor, solicitante));
        }
        notificacaoService.notificarLeadsRecebidos(novoCorretor, atribuidos, solicitante);
        return atribuidos;
    }

    private Usuario buscarCorretorDestino(Long novoCorretorId) {
        if (novoCorretorId == null) throw new RegraNegocioException("Selecione o novo corretor responsável.", "novoCorretorId", "MISSING_TARGET");
        return usuarioRepository.findById(novoCorretorId).orElseThrow(() -> new RecursoNaoEncontradoException("O corretor selecionado não foi encontrado. Atualize a página e escolha outro."));
    }

    /** Regras e registro de uma atribuição (usadas pela individual e pela em massa). Não notifica. */
    private Lead atribuir(Lead lead, Usuario novoCorretor, Usuario solicitante) {
        // validações §10
        if (!novoCorretor.isAtivo()) throw new RegraNegocioException("Não é possível atribuir o lead a " + novoCorretor.getNome() + " porque o usuário está inativo. Escolha um corretor ativo.", "novoCorretorId", "TARGET_INACTIVE");
        String papel = novoCorretor.getPapel() != null ? novoCorretor.getPapel().getPapel() : "";
        if (!"corretor".equals(papel) && !"gestor".equals(papel) && !"admin".equals(papel)) throw new RegraNegocioException("Não é possível atribuir o lead a " + novoCorretor.getNome() + " porque o papel \"" + papel + "\" não pode receber leads.", "novoCorretorId", "TARGET_INVALID_ROLE");

        // permissão primeiro: quem não pode mexer no lead recebe esse motivo, não o de equipe
        validarPermissao(solicitante, lead);

        // equipe compatível
        if (lead.getEquipe() != null && novoCorretor.getEquipe() != null && !lead.getEquipe().getId().equals(novoCorretor.getEquipe().getId())) {
            // admin pode cruzar equipes, gestor não
            String solicitantePapel = solicitante.getPapel() != null ? solicitante.getPapel().getPapel() : "";
            if (!"admin".equals(solicitantePapel)) {
                throw new RegraNegocioException("Não é possível atribuir o lead " + lead.getNome() + " a " + novoCorretor.getNome() + " porque ele é da equipe " + novoCorretor.getEquipe().getNome() + " e o lead é da equipe " + lead.getEquipe().getNome() + ". Somente administradores podem transferir leads entre equipes.", "novoCorretorId", "TEAM_MISMATCH");
            }
        }

        String statusAnterior = lead.getStatusAtribuicao();
        Usuario corretorAnterior = lead.getCorretor();

        encerrarHistoricoAberto(lead);

        Equipe eqNovo = resolverEquipeUsuario(novoCorretor);
        Equipe equipeFinal = eqNovo != null ? eqNovo : lead.getEquipe();
        if (equipeFinal == null) {
            equipeFinal = equipeRepository.findByNome("Equipe Geral").orElse(null);
        }
        lead.setCorretor(novoCorretor);
        lead.setCorretor_responsavel(novoCorretor.getNome());
        lead.setEquipe(equipeFinal);
        lead.setStatusAtribuicao("ATRIBUIDO");
        lead.setResponsavelRedistribuicao(null); // a pendência foi resolvida
        Lead salvo = leadRepository.save(lead);

        String motivo = corretorAnterior == null ? "REDISTRIBUICAO" : "ALTERACAO_MANUAL";
        if ("AGUARDANDO_REDISTRIBUICAO".equals(statusAnterior) || corretorAnterior == null) motivo = "REDISTRIBUICAO";

        historicoRepository.save(LeadResponsavelHistorico.builder()
                .lead(salvo)
                .corretor(novoCorretor)
                .equipe(salvo.getEquipe())
                .gestor(novoCorretor.getGestor())
                .dataInicio(LocalDateTime.now())
                .motivo(motivo)
                .usuarioResponsavel(solicitante)
                .build());
        return salvo;
    }

    private void encerrarHistoricoAberto(Lead lead) {
        List<LeadResponsavelHistorico> hist = historicoRepository.findByLeadIdOrderByDataInicioDesc(lead.getId());
        for (LeadResponsavelHistorico h : hist) {
            if (h.getDataFim() == null) {
                h.setDataFim(LocalDateTime.now());
                historicoRepository.save(h);
                break;
            }
        }
    }

    public List<Lead> listarAguardando(Long equipeId, Usuario solicitante) {
        String papel = solicitante.getPapel() != null ? solicitante.getPapel().getPapel() : "";
        if ("admin".equals(papel)) {
            if (equipeId != null) {
                return leadRepository.findByStatusAtribuicao("AGUARDANDO_REDISTRIBUICAO").stream().filter(l -> l.getEquipe() != null && l.getEquipe().getId().equals(equipeId)).toList();
            }
            return leadRepository.findByStatusAtribuicao("AGUARDANDO_REDISTRIBUICAO");
        }
        if ("gestor".equals(papel)) {
            Equipe equipe = resolverEquipeUsuario(solicitante);
            return leadRepository.findByStatusAtribuicao("AGUARDANDO_REDISTRIBUICAO").stream()
                    .filter(l -> (equipe != null && l.getEquipe() != null && l.getEquipe().getId().equals(equipe.getId()))
                            || (l.getResponsavelRedistribuicao() != null && solicitante.getId().equals(l.getResponsavelRedistribuicao().getId())))
                    .toList();
        }
        return List.of();
    }

    public Page<LeadAguardandoDTO> listarAguardandoComResumo(Long equipeId, Usuario solicitante, int page, int size) {
        String papel = solicitante.getPapel() != null ? solicitante.getPapel().getPapel() : "";
        int p = Math.max(0, page);
        int s = Math.min(Math.max(1, size), 100);
        PageRequest pr = PageRequest.of(p, s, Sort.by(Sort.Direction.DESC, "dataAtualizacao"));
        Page<Lead> leadsPage;
        if ("admin".equals(papel)) {
            if (equipeId != null) leadsPage = leadRepository.findByStatusAtribuicaoAndEquipeId("AGUARDANDO_REDISTRIBUICAO", equipeId, pr);
            else leadsPage = leadRepository.findByStatusAtribuicao("AGUARDANDO_REDISTRIBUICAO", pr);
        } else if ("gestor".equals(papel)) {
            // leads da equipe do gestor + os que ficaram sob responsabilidade dele
            Equipe equipe = resolverEquipeUsuario(solicitante);
            leadsPage = leadRepository.findAguardandoDaEquipeOuDoResponsavel(equipe != null ? equipe.getId() : -1L, solicitante.getId(), pr);
        } else {
            return new PageImpl<>(List.of(), pr, 0);
        }
        if (leadsPage.isEmpty()) return new PageImpl<>(List.of(), pr, leadsPage.getTotalElements());
        List<Long> ids = leadsPage.getContent().stream().map(Lead::getId).toList();
        List<LeadResponsavelHistorico> hists = historicoRepository.findByLeadIdInOrderByDataInicioAsc(ids);
        Map<Long, List<LeadResponsavelHistorico>> byLead = hists.stream().collect(Collectors.groupingBy(h -> h.getLead().getId()));
        List<LeadAguardandoDTO> dtos = new ArrayList<>();
        for (Lead lead : leadsPage.getContent()) {
            List<LeadResponsavelHistorico> hist = byLead.getOrDefault(lead.getId(), List.of());
            // hist já ordenado ASC, último é desligamento, penúltimo é anterior
            LeadResponsavelHistorico ultimo = hist.isEmpty() ? null : hist.get(hist.size() - 1);
            LeadResponsavelHistorico anterior = hist.size() >= 2 ? hist.get(hist.size() - 2) : null;
            // valida que último é realmente DESLIGAMENTO, senão busca último com corretor null
            if (ultimo != null && ultimo.getCorretor() != null && hist.size() >= 2) {
                // procura último com corretor null
                for (int i = hist.size() - 1; i >= 0; i--) {
                    if (hist.get(i).getCorretor() == null) { ultimo = hist.get(i); break; }
                }
            }
            dtos.add(LeadAguardandoDTO.from(lead, ultimo, anterior));
        }
        return new PageImpl<>(dtos, pr, leadsPage.getTotalElements());
    }

    private void validarPermissao(Usuario solicitante, Lead lead) {
        String papel = solicitante.getPapel() != null ? solicitante.getPapel().getPapel() : "";
        if ("admin".equals(papel)) return;
        if ("gestor".equals(papel)) {
            if (lead.getResponsavelRedistribuicao() != null && solicitante.getId().equals(lead.getResponsavelRedistribuicao().getId())) return;
            Equipe eqSolicitante = resolverEquipeUsuario(solicitante);
            if (eqSolicitante == null || lead.getEquipe() == null || !eqSolicitante.getId().equals(lead.getEquipe().getId())) {
                throw new AccessDeniedException("Você só pode redistribuir leads da sua equipe.");
            }
            return;
        }
        throw new AccessDeniedException("Somente administradores e gestores podem redistribuir leads.");
    }
}
