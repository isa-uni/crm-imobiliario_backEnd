package crm_imobiliario.back.model.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import crm_imobiliario.back.model.dto.LeadAtualizacaoDTO;
import crm_imobiliario.back.model.dto.LeadListaDTO;
import crm_imobiliario.back.model.dto.LeadsDTO;
import crm_imobiliario.back.model.dto.MetricsDTO;
import crm_imobiliario.back.model.entity.Equipe;
import crm_imobiliario.back.model.entity.Imovel;
import crm_imobiliario.back.model.entity.Lead;
import crm_imobiliario.back.model.entity.Tramitacao;
import crm_imobiliario.back.model.entity.Usuario;
import crm_imobiliario.back.model.repository.ImovelRepository;
import crm_imobiliario.back.model.repository.LeadRepository;
import crm_imobiliario.back.model.repository.TramitacaoRepository;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;

@Service
public class LeadsService {
    
    @Autowired
    private LeadRepository leadRepository;

    @Autowired
    private ImovelRepository imovelRepository;

    @Autowired
    private TramitacaoRepository tramitacaoRepository;

    @Autowired
    private UsuarioService usuarioService;
    @Autowired
    private crm_imobiliario.back.model.repository.LeadResponsavelHistoricoRepository historicoRepository;
    @Autowired
    private crm_imobiliario.back.model.repository.EquipeRepository equipeRepository;

    public Lead cadastrarLeads(LeadsDTO dto) {

        Lead lead = new Lead();

        lead.setNome(dto.getNome());
        lead.setEmail(dto.getEmail());
        lead.setTelefone(dto.getTelefone());
        lead.setOrigem(dto.getOrigem());
        lead.setHistorico(dto.getHistorico());
        lead.setStatus(dto.getStatus());
        lead.setValorInteresse(dto.getValorInteresse());
        lead.setObservacao(dto.getObservacao());
        lead.setAtivo(true);
        lead.setStatusAtribuicao("ATRIBUIDO");
        Usuario logado = usuarioLogado();
        if (logado != null) {
            lead.setCorretor(logado);
            lead.setCorretor_responsavel(logado.getNome());
            lead.setEquipe(logado.getEquipe());
        }
        if (lead.getEquipe() == null) {
            equipeRepository.findByNome("Equipe Geral").ifPresent(lead::setEquipe);
        }
        
        if (dto.getImovelId() != null) {
            Imovel imovel = imovelRepository.findById(dto.getImovelId())
                    .orElseThrow(() -> new RuntimeException("Imóvel não encontrado"));

            lead.setImovel(imovel);
        } else {
            lead.setImovel(null);
        }

        try {
            Lead salvo = leadRepository.save(lead);
            registrarTramitacao(salvo, null, salvo.getStatus());
            // historico de responsavel
            try {
                crm_imobiliario.back.model.entity.LeadResponsavelHistorico h = crm_imobiliario.back.model.entity.LeadResponsavelHistorico.builder()
                        .lead(salvo)
                        .corretor(salvo.getCorretor())
                        .equipe(salvo.getEquipe())
                        .gestor(salvo.getCorretor() != null ? salvo.getCorretor().getGestor() : null)
                        .dataInicio(java.time.LocalDateTime.now())
                        .motivo("ATRIBUICAO_INICIAL")
                        .usuarioResponsavel(logado)
                        .build();
                historicoRepository.save(h);
            } catch (Exception ex) {
                log.warn("Falha ao criar histórico inicial do lead {}: {}", salvo.getId(), ex.getMessage());
            }
            return salvo;

        } catch (DataIntegrityViolationException e) {
            throw new RuntimeException("Lead já cadastrado");
        }
    }

    public Lead atualizarLeads(Long id, LeadAtualizacaoDTO dto) {
        Lead lead = leadRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Lead não encontrado"));
        verificarAcesso(lead);

        String statusAnterior = lead.getStatus();

            
        if (dto.getNome() != null) {
            lead.setNome(dto.getNome());
        }

        if (dto.getEmail() != null) {
            lead.setEmail(dto.getEmail());
        }

        if (dto.getTelefone() != null) {
            lead.setTelefone(dto.getTelefone());
        }

        if (dto.getOrigem() != null) {
            lead.setOrigem(dto.getOrigem());
        }

        if (dto.getHistorico() != null) {
            lead.setHistorico(dto.getHistorico());
        }

        if (dto.getStatus() != null) {
            lead.setStatus(dto.getStatus());
            if ("descarte".equals(dto.getStatus())) {
                if (dto.getMotivoDescarte() == null || dto.getMotivoDescarte().isBlank()) {
                    throw new RuntimeException("Motivo do descarte é obrigatório");
                }
                lead.setAtivo(false);
            } else {
                lead.setAtivo(true);
            }
        }

        if (dto.getValorInteresse() != null) {
            lead.setValorInteresse(dto.getValorInteresse());
        }

        if (dto.getObservacao() != null) {
            lead.setObservacao(dto.getObservacao());
        }

        if (dto.getMotivoDescarte() != null) {
            lead.setMotivoDescarte(dto.getMotivoDescarte());
        }

        if (dto.getImovelId() != null) {
            Imovel imovel = imovelRepository.findById(dto.getImovelId())
                .orElseThrow(() -> new RuntimeException("Imóvel não encontrado"));

            lead.setImovel(imovel);
        }  else {
            lead.setImovel(null); // <- remove o vínculo
        }

        Lead atualizado = leadRepository.save(lead);

        if (dto.getStatus() != null && !dto.getStatus().equals(statusAnterior)) {
            registrarTramitacao(atualizado, statusAnterior, atualizado.getStatus());
        }

        return atualizado;
    }

    public List<Tramitacao> listarTramitacoes(Long leadId) {
        Lead lead = leadRepository.findById(leadId)
                .orElseThrow(() -> new RuntimeException("Lead não encontrado"));
        verificarAcesso(lead);
        return tramitacaoRepository.findByLeadIdOrderByDataMovimentacaoAsc(leadId);
    }

    private void registrarTramitacao(Lead lead, String statusAnterior, String statusAtual) {
        Tramitacao tramitacao = new Tramitacao();
        tramitacao.setLead(lead);
        tramitacao.setStatus_anterior(statusAnterior);
        tramitacao.setStatus_atual(statusAtual);
        
        tramitacao.setUsuario(usuarioLogado());
        tramitacaoRepository.save(tramitacao);
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LeadsService.class);

    private Usuario usuarioLogado() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null || "anonymousUser".equals(auth.getName())) {
            log.debug("usuarioLogado: sem autenticação");
            return null;
        }
        try {
            return usuarioService.buscarPorEmail(auth.getName());
        } catch (RuntimeException e) {
            log.warn("usuarioLogado: usuário não encontrado para {}", auth.getName());
            return null;
        }
    }

    private Equipe resolverEquipeUsuario(Usuario u) {
        if (u == null) return null;
        if (u.getEquipe() != null) return u.getEquipe();
        if (u.getGestor() != null) {
            if (u.getGestor().getEquipe() != null) return u.getGestor().getEquipe();
            return equipeRepository.findByGestorId(u.getGestor().getId()).orElse(null);
        }
        return equipeRepository.findByGestorId(u.getId()).orElse(null);
    }

    private String papelDe(Usuario u) {
        return u != null && u.getPapel() != null ? u.getPapel().getPapel() : "";
    }

    private boolean podeVer(Lead lead, Usuario solicitante) {
        if (solicitante == null) return false;
        String papel = papelDe(solicitante);
        if ("admin".equals(papel)) return true;
        if ("gestor".equals(papel)) {
            Equipe eq = resolverEquipeUsuario(solicitante);
            if (eq == null) return false;
            if (lead.getEquipe() != null && eq.getId().equals(lead.getEquipe().getId())) return true;
            // fallback legado: lead sem equipe mas corretor pertence à equipe do gestor
            if (lead.getEquipe() == null && lead.getCorretor() != null) {
                Equipe eqCorretor = lead.getCorretor().getEquipe();
                if (eqCorretor != null && eq.getId().equals(eqCorretor.getId())) return true;
                // tenta via gestor do corretor
                if (lead.getCorretor().getGestor() != null && eq.getId().equals(resolverEquipeUsuario(lead.getCorretor()) != null ? resolverEquipeUsuario(lead.getCorretor()).getId() : null)) return true;
            }
            return false;
        }
        if ("corretor".equals(papel)) {
            return lead.getCorretor() != null && lead.getCorretor().getId().equals(solicitante.getId());
        }
        return false;
    }

    private void verificarAcesso(Lead lead) {
        Usuario solicitante = usuarioLogado();
        if (solicitante == null) throw new AccessDeniedException("Não autenticado");
        if (!podeVer(lead, solicitante)) throw new AccessDeniedException("Você não possui permissão para acessar este cliente");
    }

    private Specification<Lead> specEscopo(Usuario solicitante) {
        return (root, query, cb) -> {
            if (solicitante == null) return cb.disjunction();
            String papel = papelDe(solicitante);
            if ("admin".equals(papel)) return cb.conjunction();
            if ("gestor".equals(papel)) {
                Equipe eq = resolverEquipeUsuario(solicitante);
                if (eq == null) return cb.disjunction();
                query.distinct(true);
                Predicate equipeIgual = cb.equal(root.get("equipe").get("id"), eq.getId());
                var corretorJoin = root.join("corretor", JoinType.LEFT);
                var equipeCorretor = corretorJoin.join("equipe", JoinType.LEFT);
                Predicate corretorEquipeIgual = cb.and(cb.isNull(root.get("equipe")), cb.equal(equipeCorretor.get("id"), eq.getId()));
                return cb.or(equipeIgual, corretorEquipeIgual);
            }
            if ("corretor".equals(papel)) {
                return cb.equal(root.get("corretor").get("id"), solicitante.getId());
            }
            return cb.disjunction();
        };
    }

    private Specification<Lead> specSearch(String search) {
        return (root, query, cb) -> {
            if (search == null || search.isBlank()) return cb.conjunction();
            String q = "%" + search.toLowerCase() + "%";
            Predicate nome = cb.like(cb.lower(root.get("nome")), q);
            Predicate email = cb.like(cb.lower(root.get("email")), q);
            Predicate telefone = cb.like(root.get("telefone"), "%" + search + "%");
            return cb.or(nome, email, telefone);
        };
    }

    private Specification<Lead> specStatus(String status) {
        return (root, query, cb) -> {
            if (status == null || status.isBlank() || "all".equalsIgnoreCase(status)) return cb.conjunction();
            if ("active".equalsIgnoreCase(status)) {
                return cb.and(cb.notEqual(cb.lower(root.get("status")), "contrato"), cb.notEqual(cb.lower(root.get("status")), "descarte"));
            }
            if ("archived".equalsIgnoreCase(status)) {
                return cb.or(cb.equal(cb.lower(root.get("status")), "contrato"), cb.equal(cb.lower(root.get("status")), "descarte"));
            }
            return cb.equal(cb.lower(root.get("status")), status.toLowerCase());
        };
    }

    private Specification<Lead> specMonth(String month) {
        return (root, query, cb) -> {
            if (month == null || month.isBlank() || "all".equalsIgnoreCase(month)) return cb.conjunction();
            if ("current".equalsIgnoreCase(month)) {
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime start = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
                LocalDateTime end = start.plusMonths(1);
                return cb.and(cb.greaterThanOrEqualTo(root.get("dataCriacao"), start), cb.lessThan(root.get("dataCriacao"), end));
            }
            return cb.conjunction();
        };
    }

    public void inativarLead(Long id) {
        Lead lead = leadRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Lead não encontrado"));
        verificarAcesso(lead);
        lead.setAtivo(false);
        leadRepository.save(lead);
    }

    public void ativarLead(Long id) {
        Lead lead = leadRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Lead não encontrado"));
        verificarAcesso(lead);
        lead.setAtivo(true);
        leadRepository.save(lead);
    }

    public List<LeadListaDTO> ConsultarLeads() {
        Usuario solicitante = usuarioLogado();
        if (solicitante == null) throw new AccessDeniedException("Não autenticado");
        String papel = papelDe(solicitante);
        if ("admin".equals(papel)) {
            Specification<Lead> spec = specEscopo(solicitante);
            return leadRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "dataAtualizacao")).stream().map(LeadListaDTO::from).toList();
        }
        Specification<Lead> spec = specEscopo(solicitante);
        return leadRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "dataAtualizacao")).stream().map(LeadListaDTO::from).toList();
    }

    public LeadListaDTO cadastrarLeadsDTO(LeadsDTO dto) {
        Lead salvo = cadastrarLeads(dto);
        return LeadListaDTO.from(salvo);
    }

    public LeadListaDTO atualizarLeadsDTO(Long id, LeadAtualizacaoDTO dto) {
        Lead atualizado = atualizarLeads(id, dto);
        return LeadListaDTO.from(atualizado);
    }

    public Page<LeadListaDTO> findPaginated(int page, int size, Sort sort, String search, String status, String month) {
        int p = Math.max(0, page);
        int s = Math.min(Math.max(1, size), 100);
        Usuario solicitante = usuarioLogado();
        if (solicitante == null) throw new AccessDeniedException("Não autenticado");
        Specification<Lead> spec = Specification.where(specEscopo(solicitante))
                .and(specSearch(search))
                .and(specStatus(status))
                .and(specMonth(month));
        PageRequest pr = PageRequest.of(p, s, sort);
        Page<Lead> pg = leadRepository.findAll(spec, pr);
        List<LeadListaDTO> content = pg.getContent().stream().map(LeadListaDTO::from).toList();
        return new PageImpl<>(content, pr, pg.getTotalElements());
    }

    public void deletarLead(Long id){
        Lead lead = leadRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(
                        "Lead com id " + id + " não encontrado"
                ));
        verificarAcesso(lead);
        leadRepository.delete(lead);
    }

    public MetricsDTO getMetrics() {
        Usuario solicitante = usuarioLogado();
        if (solicitante == null) throw new AccessDeniedException("Não autenticado");
        Specification<Lead> spec = specEscopo(solicitante);
        List<Lead> leads = leadRepository.findAll(spec);

        MetricsDTO metrics = new MetricsDTO();

        long total = leads.size();

        long contratos = leads.stream()
                .filter(l -> "contrato".equalsIgnoreCase(l.getStatus()))
                .count();

        long descartes = leads.stream()
                .filter(l -> "descarte".equalsIgnoreCase(l.getStatus()))
                .count();

        metrics.setTotalLeads(leads.stream().filter(l -> "lead".equals(l.getStatus())).count());
        metrics.setTotalOportunidades(leads.stream().filter(l -> "oportunidade".equals(l.getStatus())).count());
        metrics.setTotalVisitasAgen(leads.stream().filter(l -> "visita-agendada".equals(l.getStatus())).count());
        metrics.setTotalVisitasReal(leads.stream().filter(l -> "visita-realizada".equals(l.getStatus())).count());
        metrics.setTotalPastas(leads.stream().filter(l -> "pasta".equals(l.getStatus())).count());
        metrics.setTotalAprovados(leads.stream().filter(l -> "aprovado".equals(l.getStatus())).count());
        metrics.setTotalContratos(contratos);
        metrics.setTotalDescartes(descartes);

        double taxa = total > 0 ? ((double) contratos / total) * 100 : 0;
        metrics.setTaxaConversaoGeral(taxa);

        double valorTotal = leads.stream()
                .filter(l -> "contrato".equals(l.getStatus()))
                .mapToDouble(l -> l.getValorInteresse() != null ? l.getValorInteresse() : 0)
                .sum();

        metrics.setValorTotalFechado(valorTotal);

        return metrics;
    }
}
