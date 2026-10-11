package crm_imobiliario.back.model.service;

import crm_imobiliario.back.util.validacao.Documentos;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

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
import crm_imobiliario.back.model.dto.LeadFiltroColunas;
import crm_imobiliario.back.model.dto.LeadListaDTO;
import crm_imobiliario.back.model.dto.LeadsDTO;
import crm_imobiliario.back.model.dto.MetricsDTO;
import crm_imobiliario.back.model.entity.Empreendimento;
import crm_imobiliario.back.model.entity.Equipe;
import crm_imobiliario.back.model.entity.Lead;
import crm_imobiliario.back.model.entity.Tramitacao;
import crm_imobiliario.back.model.entity.Usuario;
import crm_imobiliario.back.model.repository.EmpreendimentoRepository;
import crm_imobiliario.back.model.repository.LeadRepository;
import crm_imobiliario.back.model.repository.TramitacaoRepository;
import crm_imobiliario.back.util.RecursoNaoEncontradoException;
import crm_imobiliario.back.util.RegraNegocioException;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;

@Service
public class LeadsService {
    
    @Autowired
    private LeadRepository leadRepository;

    @Autowired
    private EmpreendimentoRepository empreendimentoRepository;

    @Autowired
    private TramitacaoRepository tramitacaoRepository;

    @Autowired
    private UsuarioService usuarioService;
    @Autowired
    private crm_imobiliario.back.model.repository.LeadResponsavelHistoricoRepository historicoRepository;
    @Autowired
    private crm_imobiliario.back.model.repository.EquipeRepository equipeRepository;

    /** Status aceitos pelo funil (não há CHECK no banco; a validação fica aqui). */
    static final java.util.Set<String> STATUS_VALIDOS = java.util.Set.of(
            "lead", "oportunidade", "visita-agendada", "visita-realizada", "pasta", "aprovado", "contrato", "descarte");

    private void validarStatus(String status) {
        if (status != null && !STATUS_VALIDOS.contains(status)) {
            throw new RegraNegocioException("O status \"" + status + "\" não é válido para um lead.", "status", "INVALID_STATUS");
        }
    }

    /** E-mail é opcional (muitos leads chegam só com telefone); vazio vira null. */
    private static String normalizarEmail(String email) {
        return email == null || email.isBlank() ? null : email.trim();
    }

    public Lead cadastrarLeads(LeadsDTO dto) {
        validarStatus(dto.getStatus());

        Lead lead = new Lead();

        lead.setNome(dto.getNome());
        lead.setEmail(normalizarEmail(dto.getEmail()));
        lead.setTelefone(Documentos.somenteDigitos(dto.getTelefone()));
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
        
        if (dto.getEmpreendimentoId() != null) {
            Empreendimento empreendimento = empreendimentoRepository.findById(dto.getEmpreendimentoId())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("O empreendimento selecionado não foi encontrado. Atualize a página e escolha outro."));

            lead.setEmpreendimento(empreendimento);
        } else {
            lead.setEmpreendimento(null);
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
            // o e-mail do lead deixou de ser único (V5); a violação é traduzida pelo GlobalExceptionHandler
            // com o motivo real (ex.: dado obrigatório ausente), em vez de um genérico "Lead já cadastrado"
            throw e;
        }
    }

    public Lead atualizarLeads(Long id, LeadAtualizacaoDTO dto) {
        Lead lead = leadRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("O lead solicitado não foi encontrado. Ele pode ter sido removido."));
        verificarAcesso(lead);

        String statusAnterior = lead.getStatus();

            
        if (dto.getNome() != null) {
            lead.setNome(dto.getNome());
        }

        if (dto.getEmail() != null) {
            lead.setEmail(normalizarEmail(dto.getEmail()));
        }

        if (dto.getTelefone() != null) {
            lead.setTelefone(Documentos.somenteDigitos(dto.getTelefone()));
        }

        if (dto.getOrigem() != null) {
            lead.setOrigem(dto.getOrigem());
        }

        if (dto.getHistorico() != null) {
            lead.setHistorico(dto.getHistorico());
        }

        if (dto.getStatus() != null) {
            validarStatus(dto.getStatus());
            lead.setStatus(dto.getStatus());
            if ("descarte".equals(dto.getStatus())) {
                if (dto.getMotivoDescarte() == null || dto.getMotivoDescarte().isBlank()) {
                    throw new RegraNegocioException("Informe o motivo do descarte para descartar o lead.", "motivoDescarte", "MISSING_DISCARD_REASON");
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

        if (dto.getEmpreendimentoId() != null) {
            Empreendimento empreendimento = empreendimentoRepository.findById(dto.getEmpreendimentoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("O empreendimento selecionado não foi encontrado. Atualize a página e escolha outro."));

            lead.setEmpreendimento(empreendimento);
        } else if (Boolean.TRUE.equals(dto.getLimparEmpreendimento())) {
            lead.setEmpreendimento(null); // <- remove o vínculo, só quando pedido explicitamente
        }
        // nenhum dos dois: atualização parcial (ex.: só mudança de status) não deve mexer no vínculo atual

        Lead atualizado = leadRepository.save(lead);

        if (dto.getStatus() != null && !dto.getStatus().equals(statusAnterior)) {
            registrarTramitacao(atualizado, statusAnterior, atualizado.getStatus());
        }

        return atualizado;
    }

    public List<Tramitacao> listarTramitacoes(Long leadId) {
        Lead lead = leadRepository.findById(leadId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("O lead solicitado não foi encontrado. Ele pode ter sido removido."));
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
        return EquipeResolver.resolver(u, equipeRepository);
    }

    private String papelDe(Usuario u) {
        return EquipeResolver.papel(u);
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
        if (solicitante == null) throw new AccessDeniedException("Sua sessão expirou. Faça login novamente.");
        if (!podeVer(lead, solicitante)) throw new AccessDeniedException("Você não tem permissão para acessar este lead. Ele pertence a outro corretor ou a outra equipe.");
    }

    private Specification<Lead> specEscopo(Usuario solicitante) {
        return (root, query, cb) -> {
            if (solicitante == null) return cb.disjunction();
            String papel = papelDe(solicitante);
            if ("admin".equals(papel)) return cb.conjunction();
            if ("gestor".equals(papel)) {
                Equipe eq = resolverEquipeUsuario(solicitante);
                if (eq == null) return cb.disjunction();
                Predicate equipeIgual = cb.equal(root.get("equipe").get("id"), eq.getId());
                // sem join (e sem DISTINCT): o Postgres não aceita DISTINCT com ORDER BY por expressão,
                // usado na ordenação por etapa do funil e por responsável
                var daEquipe = query.subquery(Long.class);
                var u = daEquipe.from(Usuario.class);
                daEquipe.select(u.get("id")).where(cb.equal(u.get("equipe").get("id"), eq.getId()));
                Predicate corretorEquipeIgual = cb.and(cb.isNull(root.get("equipe")), root.get("corretor").get("id").in(daEquipe));
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
            String q = "%" + search.trim().toLowerCase() + "%";
            Predicate nome = cb.like(cb.lower(root.get("nome")), q);
            Predicate email = cb.like(cb.lower(root.get("email")), q);
            // telefone é gravado só com dígitos: "(43) 9840-1" procura por "4398401"
            String digitos = search.replaceAll("\\D", "");
            if (digitos.isEmpty()) return cb.or(nome, email);
            Predicate telefone = cb.like(root.get("telefone"), "%" + digitos + "%");
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

    private Specification<Lead> specOrigem(String origem) {
        return (root, query, cb) -> {
            if (origem == null || origem.isBlank() || "all".equalsIgnoreCase(origem)) return cb.conjunction();
            return cb.equal(root.get("origem"), origem);
        };
    }

    private Specification<Lead> specHistorico(String historico) {
        return (root, query, cb) -> {
            if (historico == null || historico.isBlank() || "all".equalsIgnoreCase(historico)) return cb.conjunction();
            return cb.equal(root.get("historico"), historico);
        };
    }

    /**
     * Gestor escolhe entre os próprios clientes ("meus") e os da equipe ("equipe" ou nulo, o padrão
     * da API). Para os demais papéis não muda nada: o escopo já é definido por {@link #specEscopo}.
     */
    private Specification<Lead> specSomenteMeus(Usuario solicitante, String escopo) {
        return (root, query, cb) -> {
            if (!"meus".equalsIgnoreCase(escopo) || !"gestor".equals(papelDe(solicitante))) return cb.conjunction();
            return cb.equal(root.get("corretor").get("id"), solicitante.getId());
        };
    }

    /** Filtros dos cabeçalhos da tabela de Leads. */
    private Specification<Lead> specColunas(LeadFiltroColunas f) {
        return (root, query, cb) -> {
            List<Predicate> ps = new java.util.ArrayList<>();
            if (temTexto(f.nome())) ps.add(cb.like(cb.lower(root.get("nome")), contem(f.nome())));
            if (temTexto(f.contato())) {
                Predicate email = cb.like(cb.lower(root.get("email")), contem(f.contato()));
                String digitos = f.contato().replaceAll("\\D", "");
                ps.add(digitos.isEmpty() ? email : cb.or(email, cb.like(root.get("telefone"), "%" + digitos + "%")));
            }
            if (f.etapas() != null && !f.etapas().isEmpty()) {
                ps.add(cb.lower(root.get("status")).in(f.etapas().stream().map(String::toLowerCase).toList()));
            }
            if (f.origens() != null && !f.origens().isEmpty()) ps.add(root.get("origem").in(f.origens()));
            if (f.valorMin() != null) ps.add(cb.greaterThanOrEqualTo(root.get("valorInteresse"), f.valorMin()));
            if (f.valorMax() != null) ps.add(cb.lessThanOrEqualTo(root.get("valorInteresse"), f.valorMax()));
            if (temTexto(f.responsavel())) {
                var comNome = query.subquery(Long.class);
                var u = comNome.from(Usuario.class);
                comNome.select(u.get("id")).where(cb.like(cb.lower(u.get("nome")), contem(f.responsavel())));
                ps.add(cb.or(root.get("corretor").get("id").in(comNome),
                        cb.like(cb.lower(root.get("corretor_responsavel")), contem(f.responsavel()))));
            }
            if (f.dataDe() != null) ps.add(cb.greaterThanOrEqualTo(root.get("dataAtualizacao"), f.dataDe().atStartOfDay()));
            if (f.dataAte() != null) ps.add(cb.lessThan(root.get("dataAtualizacao"), f.dataAte().plusDays(1).atStartOfDay()));
            return cb.and(ps.toArray(Predicate[]::new));
        };
    }

    private static boolean temTexto(String s) {
        return s != null && !s.isBlank();
    }

    private static String contem(String s) {
        return "%" + s.trim().toLowerCase() + "%";
    }

    /** Colunas ordenadas por expressão (não por um campo do lead): etapa na ordem do funil e nome do responsável. */
    public static final Set<String> ORDENACAO_ESPECIAL = Set.of("status", "responsavel");

    private Specification<Lead> specOrdenacao(Sort.Order ordem) {
        return (root, query, cb) -> {
            // a consulta de contagem da página não tem ORDER BY
            if (Long.class.equals(query.getResultType()) || long.class.equals(query.getResultType())) return cb.conjunction();
            Expression<?> expr;
            if ("status".equals(ordem.getProperty())) {
                var etapa = cb.<String, Integer>selectCase(cb.lower(root.get("status")));
                List<String> funil = LeadExportService.FUNIL_ORDEM;
                for (int i = 0; i < funil.size(); i++) etapa = etapa.when(funil.get(i), i);
                expr = etapa.when("descarte", funil.size()).otherwise(funil.size() + 1);
            } else {
                var corretor = root.join("corretor", JoinType.LEFT);
                expr = cb.lower(cb.coalesce(corretor.<String>get("nome"), root.<String>get("corretor_responsavel")));
            }
            query.orderBy(ordem.isAscending() ? cb.asc(expr) : cb.desc(expr), cb.desc(root.get("id")));
            return cb.conjunction();
        };
    }

    /** Todos os leads que batem com o filtro (sem paginação) — usado pela exportação, que precisa do conjunto completo, não só de uma página. */
    public List<Lead> findAllForExport(String search, String status, String month, String origem, String historico) {
        Usuario solicitante = usuarioLogado();
        if (solicitante == null) throw new AccessDeniedException("Sua sessão expirou. Faça login novamente.");
        Specification<Lead> spec = Specification.where(specEscopo(solicitante))
                .and(specSearch(search))
                .and(specStatus(status))
                .and(specMonth(month))
                .and(specOrigem(origem))
                .and(specHistorico(historico));
        return leadRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "dataAtualizacao"));
    }

    public void inativarLead(Long id) {
        Lead lead = leadRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("O lead solicitado não foi encontrado. Ele pode ter sido removido."));
        verificarAcesso(lead);
        lead.setAtivo(false);
        leadRepository.save(lead);
    }

    public void ativarLead(Long id) {
        Lead lead = leadRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("O lead solicitado não foi encontrado. Ele pode ter sido removido."));
        verificarAcesso(lead);
        lead.setAtivo(true);
        leadRepository.save(lead);
    }

    public List<LeadListaDTO> ConsultarLeads() {
        Usuario solicitante = usuarioLogado();
        if (solicitante == null) throw new AccessDeniedException("Sua sessão expirou. Faça login novamente.");
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
        return findPaginated(page, size, sort, search, status, month, LeadFiltroColunas.VAZIO, null);
    }

    public Page<LeadListaDTO> findPaginated(int page, int size, Sort sort, String search, String status, String month,
                                            LeadFiltroColunas colunas, String escopo) {
        int p = Math.max(0, page);
        int s = Math.min(Math.max(1, size), 100);
        Usuario solicitante = usuarioLogado();
        if (solicitante == null) throw new AccessDeniedException("Sua sessão expirou. Faça login novamente.");
        Specification<Lead> spec = Specification.where(specEscopo(solicitante))
                .and(specSomenteMeus(solicitante, escopo))
                .and(specSearch(search))
                .and(specStatus(status))
                .and(specMonth(month))
                .and(specColunas(colunas != null ? colunas : LeadFiltroColunas.VAZIO));
        Sort.Order ordem = sort.stream().findFirst().orElse(null);
        PageRequest pr;
        if (ordem != null && ORDENACAO_ESPECIAL.contains(ordem.getProperty())) {
            spec = spec.and(specOrdenacao(ordem));
            pr = PageRequest.of(p, s);
        } else {
            // id desempata valores iguais: sem isso um lead pode aparecer em duas páginas
            pr = PageRequest.of(p, s, sort.and(Sort.by(Sort.Direction.DESC, "id")));
        }
        Page<Lead> pg = leadRepository.findAll(spec, pr);
        List<LeadListaDTO> content = pg.getContent().stream().map(LeadListaDTO::from).toList();
        return new PageImpl<>(content, pr, pg.getTotalElements());
    }

    /**
     * Contadores dos cards da tela de Leads, calculados no banco (COUNT) sobre todos os leads do
     * escopo do usuário — antes o front contava só os 20 leads da página visível.
     */
    public java.util.Map<String, Long> resumo() {
        return resumo(null);
    }

    /** @param escopoGestor "meus" ou "equipe" (só faz diferença para gestor; ver specSomenteMeus) */
    public java.util.Map<String, Long> resumo(String escopoGestor) {
        Usuario solicitante = usuarioLogado();
        if (solicitante == null) throw new AccessDeniedException("Sua sessão expirou. Faça login novamente.");
        Specification<Lead> escopo = Specification.where(specEscopo(solicitante)).and(specSomenteMeus(solicitante, escopoGestor));
        return java.util.Map.of(
                "total", leadRepository.count(escopo),
                "ativos", leadRepository.count(escopo.and(specStatus("active"))),
                "arquivados", leadRepository.count(escopo.and(specStatus("archived"))),
                "contratos", leadRepository.count(escopo.and(specStatus("contrato"))),
                "esteMes", leadRepository.count(escopo.and(specMonth("current"))));
    }

    /** Histórico de responsáveis do lead, respeitando o mesmo escopo de visibilidade do lead. */
    public List<crm_imobiliario.back.model.entity.LeadResponsavelHistorico> listarHistoricoResponsaveis(Long leadId) {
        Lead lead = leadRepository.findById(leadId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("O lead solicitado não foi encontrado. Ele pode ter sido removido."));
        verificarAcesso(lead);
        return historicoRepository.findByLeadIdOrderByDataInicioAsc(leadId);
    }

    public MetricsDTO getMetrics() {
        Usuario solicitante = usuarioLogado();
        if (solicitante == null) throw new AccessDeniedException("Sua sessão expirou. Faça login novamente.");
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
