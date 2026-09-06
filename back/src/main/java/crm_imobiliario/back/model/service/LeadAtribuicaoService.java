package crm_imobiliario.back.model.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
        if (u == null) return null;
        if (u.getEquipe() != null) return u.getEquipe();
        if (u.getGestor() != null) {
            // tenta via gestor
            if (u.getGestor().getEquipe() != null) return u.getGestor().getEquipe();
            return equipeRepository.findByGestorId(u.getGestor().getId()).orElse(null);
        }
        // se é gestor sem equipe direta, tenta via equipe onde é gestor
        return equipeRepository.findByGestorId(u.getId()).orElse(null);
    }

    @Transactional
    public Lead atribuirInicial(Lead lead, Usuario corretor, Usuario criador) {
        if (corretor != null) {
            lead.setCorretor(corretor);
            lead.setCorretor_responsavel(corretor.getNome());
            Equipe eq = resolverEquipeUsuario(corretor);
            if (eq == null) eq = equipeRepository.findByNome("Equipe Geral").orElse(null);
            lead.setEquipe(eq);
            lead.setStatusAtribuicao("ATRIBUIDO");
        }
        Lead salvo = leadRepository.save(lead);
        LeadResponsavelHistorico h = LeadResponsavelHistorico.builder()
                .lead(salvo)
                .corretor(corretor)
                .equipe(salvo.getEquipe())
                .gestor(corretor != null && corretor.getGestor() != null ? corretor.getGestor() : null)
                .dataInicio(LocalDateTime.now())
                .motivo("ATRIBUICAO_INICIAL")
                .usuarioResponsavel(criador)
                .build();
        historicoRepository.save(h);
        return salvo;
    }

    @Transactional
    public void desligamentoCorretor(Long corretorId, Usuario solicitante) {
        Usuario corretor = usuarioRepository.findById(corretorId).orElseThrow(() -> new RuntimeException("Corretor não encontrado"));
        List<Lead> leads = leadRepository.findAll().stream()
                .filter(l -> l.getCorretor() != null && l.getCorretor().getId().equals(corretorId))
                .filter(l -> "ATRIBUIDO".equals(l.getStatusAtribuicao()))
                .toList();
        for (Lead lead : leads) {
            // encerra histórico aberto
            List<LeadResponsavelHistorico> hist = historicoRepository.findByLeadIdOrderByDataInicioDesc(lead.getId());
            for (LeadResponsavelHistorico h : hist) {
                if (h.getDataFim() == null) {
                    h.setDataFim(LocalDateTime.now());
                    historicoRepository.save(h);
                    break;
                }
            }
            Usuario anterior = lead.getCorretor();
            lead.setCorretor(null);
            // corretor_responsavel mantido como null para indicar aguardo? mantemos string vazia
            lead.setCorretor_responsavel(null);
            lead.setStatusAtribuicao("AGUARDANDO_REDISTRIBUICAO");
            // equipe permanece
            leadRepository.save(lead);

            LeadResponsavelHistorico novo = LeadResponsavelHistorico.builder()
                    .lead(lead)
                    .corretor(null)
                    .equipe(lead.getEquipe())
                    .gestor(null)
                    .dataInicio(LocalDateTime.now())
                    .motivo("DESLIGAMENTO_CORRETOR")
                    .usuarioResponsavel(solicitante)
                    .build();
            historicoRepository.save(novo);

            // notifica apenas se anterior estava ativo - mas corretor está sendo inativado, então não notifica removido (regra)
            // porém se houver transição, o anterior já é inativo, não notifica
            // notificações de recebimento serão feitas na redistribuição
        }
    }

    @Transactional
    public Lead redistribuir(Long leadId, Long novoCorretorId, Usuario solicitante) {
        Lead lead = leadRepository.findById(leadId).orElseThrow(() -> new RuntimeException("Cliente não encontrado"));
        Usuario novoCorretor = usuarioRepository.findById(novoCorretorId).orElseThrow(() -> new RuntimeException("Corretor destino não encontrado"));

        // validações §10
        if (!novoCorretor.isAtivo()) throw new RuntimeException("Corretor de destino está inativo");
        String papel = novoCorretor.getPapel() != null ? novoCorretor.getPapel().getPapel() : "";
        if (!"corretor".equals(papel) && !"gestor".equals(papel) && !"admin".equals(papel)) throw new RuntimeException("Destino não é corretor válido");

        // equipe compatível
        if (lead.getEquipe() != null && novoCorretor.getEquipe() != null && !lead.getEquipe().getId().equals(novoCorretor.getEquipe().getId())) {
            // admin pode cruzar equipes, gestor não
            String solicitantePapel = solicitante.getPapel() != null ? solicitante.getPapel().getPapel() : "";
            if (!"admin".equals(solicitantePapel)) {
                throw new RuntimeException("Corretor pertence à equipe " + novoCorretor.getEquipe().getNome() + ", cliente pertence à equipe " + lead.getEquipe().getNome());
            }
        }

        // permissão
        validarPermissao(solicitante, lead);

        String statusAnterior = lead.getStatusAtribuicao();
        Usuario corretorAnterior = lead.getCorretor();

        // encerra histórico aberto
        List<LeadResponsavelHistorico> hist = historicoRepository.findByLeadIdOrderByDataInicioDesc(lead.getId());
        for (LeadResponsavelHistorico h : hist) {
            if (h.getDataFim() == null) {
                h.setDataFim(LocalDateTime.now());
                historicoRepository.save(h);
                break;
            }
        }

        Equipe eqNovo = resolverEquipeUsuario(novoCorretor);
        Equipe equipeFinal = eqNovo != null ? eqNovo : lead.getEquipe();
        if (equipeFinal == null) {
            equipeFinal = equipeRepository.findByNome("Equipe Geral").orElse(null);
        }
        lead.setCorretor(novoCorretor);
        lead.setCorretor_responsavel(novoCorretor.getNome());
        lead.setEquipe(equipeFinal);
        lead.setStatusAtribuicao("ATRIBUIDO");
        Lead salvo = leadRepository.save(lead);

        String motivo = corretorAnterior == null ? "REDISTRIBUICAO" : "ALTERACAO_MANUAL";
        if ("AGUARDANDO_REDISTRIBUICAO".equals(statusAnterior) || corretorAnterior == null) motivo = "REDISTRIBUICAO";

        LeadResponsavelHistorico novoHist = LeadResponsavelHistorico.builder()
                .lead(salvo)
                .corretor(novoCorretor)
                .equipe(salvo.getEquipe())
                .gestor(novoCorretor.getGestor())
                .dataInicio(LocalDateTime.now())
                .motivo(motivo)
                .usuarioResponsavel(solicitante)
                .build();
        historicoRepository.save(novoHist);

        // notificações
        notificacaoService.notificarTransferencia(salvo, corretorAnterior, novoCorretor);

        return salvo;
    }

    public List<Lead> listarAguardando(Long equipeId, Usuario solicitante) {
        String papel = solicitante.getPapel() != null ? solicitante.getPapel().getPapel() : "";
        if ("admin".equals(papel)) {
            if (equipeId != null) {
                return leadRepository.findAll().stream().filter(l -> "AGUARDANDO_REDISTRIBUICAO".equals(l.getStatusAtribuicao()) && l.getEquipe() != null && l.getEquipe().getId().equals(equipeId)).toList();
            }
            return leadRepository.findAll().stream().filter(l -> "AGUARDANDO_REDISTRIBUICAO".equals(l.getStatusAtribuicao())).toList();
        }
        if ("gestor".equals(papel)) {
            Equipe equipe = resolverEquipeUsuario(solicitante);
            if (equipe == null) return List.of();
            return leadRepository.findAll().stream().filter(l -> "AGUARDANDO_REDISTRIBUICAO".equals(l.getStatusAtribuicao()) && l.getEquipe() != null && l.getEquipe().getId().equals(equipe.getId())).toList();
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
            Equipe equipe = resolverEquipeUsuario(solicitante);
            if (equipe == null) return new PageImpl<>(List.of(), pr, 0);
            leadsPage = leadRepository.findByStatusAtribuicaoAndEquipeId("AGUARDANDO_REDISTRIBUICAO", equipe.getId(), pr);
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
            Equipe eqSolicitante = resolverEquipeUsuario(solicitante);
            if (eqSolicitante == null || lead.getEquipe() == null || !eqSolicitante.getId().equals(lead.getEquipe().getId())) {
                throw new RuntimeException("Gestor só pode redistribuir clientes da sua equipe");
            }
            return;
        }
        throw new RuntimeException("Sem permissão para redistribuir");
    }
}
