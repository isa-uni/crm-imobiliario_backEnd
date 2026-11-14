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
        //contagem dos leads com status de atribuido
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
        //Se o gestor for nulo ou inativo     ou se o corretor for o proprio gestor = retorna nulo
        if (g == null || !g.isAtivo() || (corretor.getId() != null && corretor.getId().equals(g.getId()))) return null;
        //se n retorna o gestor
        return g;
    }

    /**
     * Inativação de corretor: os leads atribuídos a ele passam a aguardar redistribuição e ganham um
     * responsável — o gestor do corretor ou, sem gestor, quem fez a inativação. O responsável é notificado
     * (plataforma). Retorna o responsável definido (null se não havia leads).
     */
    @Transactional
    public Usuario desligamentoCorretor(Long corretorId, Usuario solicitante) {
        Usuario corretor = usuarioRepository.findById(corretorId).orElseThrow(() -> new RecursoNaoEncontradoException("O corretor informado não foi encontrado."));
        List<Lead> leads = leadRepository.findByCorretorId(corretorId).stream()
                .filter(l -> "ATRIBUIDO".equals(l.getStatusAtribuicao()))
                .toList();

        //lista os leads com status atribuido de acordo com o id do corretor
        if (leads.isEmpty()) return null;

        Usuario gestor = gestorAtivo(corretor);

        //Se existir um gestor, ele será o responsável.
        //Caso contrário, verifica se existe um solicitante e se ele é diferente do corretor que está sendo desligado.
        Usuario responsavel = gestor != null ? gestor
                : (solicitante != null && !solicitante.getId().equals(corretor.getId()) ? solicitante : null);

        for (Lead lead : leads) {//para cada cliente
            //Precisa encerrar a responsabilidade anterior do lead antes de registrar a nova situação dele, 
            // que é aguardar uma redistribuição.
            encerrarHistoricoAberto(lead);
            lead.setCorretor(null);
            lead.setCorretor_responsavel(null);
            lead.setStatusAtribuicao("AGUARDANDO_REDISTRIBUICAO");
            lead.setResponsavelRedistribuicao(responsavel);
            // equipe permanece
            leadRepository.save(lead);

            //registro de historico de responsavel do lead
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
        Usuario novoCorretor = buscarCorretorDestino(novoCorretorId);//verifica se foi informado e se está no banco
        Usuario corretorAnterior = lead.getCorretor(); //corretor inativado
        Lead salvo = atribuir(lead, novoCorretor, solicitante); //atribuir o novo corretor
        notificacaoService.notificarTransferencia(salvo, corretorAnterior, novoCorretor); //notifica ao corretor que perdeu o lead numa troca manual
        notificacaoService.notificarLeadsRecebidos(novoCorretor, List.of(salvo), solicitante); //Corretor recebeu um ou mais leads
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
        //Remove IDs nulos e duplicados

        if (ids.size() > MAX_EM_MASSA) {//o maximo é 100
            throw new RegraNegocioException("Selecione no máximo " + MAX_EM_MASSA + " leads por vez.", "leadIds", "TOO_MANY_LEADS");
        }
        Usuario novoCorretor = buscarCorretorDestino(novoCorretorId);

        List<Lead> leads = leadRepository.findAllById(ids);//busca os leads no banco
        if (leads.size() != ids.size()) {
            int faltam = ids.size() - leads.size();
            throw new RecursoNaoEncontradoException((faltam == 1 ? "1 lead selecionado não foi encontrado" : faltam + " leads selecionados não foram encontrados")
                    + ". Atualize a lista e selecione novamente.");
        }
        //Verifica se todos os leads foram encontrados


        // mantém a ordem da seleção
        Map<Long, Lead> porId = leads.stream().collect(Collectors.toMap(Lead::getId, l -> l));
        List<Lead> ordenados = ids.stream().map(porId::get).toList();

        for (Lead lead : ordenados) { //Valida se todos ainda aguardam redistribuição (Casos em que outra pessoa lançou no meio do processamento)
            if (!"AGUARDANDO_REDISTRIBUICAO".equals(lead.getStatusAtribuicao())) {
                String atual = lead.getCorretor() != null ? " a " + lead.getCorretor().getNome() : "";
                throw new RegraNegocioException("O lead " + lead.getNome() + " já foi atribuído" + atual
                        + " e não está mais aguardando redistribuição. Atualize a lista e tente novamente. Nenhum lead foi atribuído.",
                        "leadIds", "LEAD_NOT_WAITING");
            }
        }


        //Executa a redistribuição de cada lead
        List<Lead> atribuidos = new ArrayList<>();
        for (Lead lead : ordenados) {
            atribuidos.add(atribuir(lead, novoCorretor, solicitante));
        }
        notificacaoService.notificarLeadsRecebidos(novoCorretor, atribuidos, solicitante); //notifica na plataforma
        return atribuidos;
    }

    private Usuario buscarCorretorDestino(Long novoCorretorId) {
        if (novoCorretorId == null) throw new RegraNegocioException("Selecione o novo corretor responsável.", "novoCorretorId", "MISSING_TARGET");
        return usuarioRepository.findById(novoCorretorId).orElseThrow(() -> new RecursoNaoEncontradoException("O corretor selecionado não foi encontrado. Atualize a página e escolha outro."));
    }

    /** Regras e registro de uma atribuição (usadas pela individual e pela em massa). Não notifica. */
    private Lead atribuir(Lead lead, Usuario novoCorretor, Usuario solicitante) {
        // se o corretor estiver inativo
        if (!novoCorretor.isAtivo()) throw new RegraNegocioException("Não é possível atribuir o lead a " + novoCorretor.getNome() + " porque o usuário está inativo. Escolha um corretor ativo.", "novoCorretorId", "TARGET_INACTIVE");
        String papel = novoCorretor.getPapel() != null ? novoCorretor.getPapel().getPapel() : "";
        //se o papel não estiver na lista
        if (!"corretor".equals(papel) && !"gestor".equals(papel) && !"admin".equals(papel)) throw new RegraNegocioException("Não é possível atribuir o lead a " + novoCorretor.getNome() + " porque o papel \"" + papel + "\" não pode receber leads.", "novoCorretorId", "TARGET_INVALID_ROLE");

        // permissão primeiro: quem não pode mexer no lead recebe esse motivo
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

        Equipe eqNovo = resolverEquipeUsuario(novoCorretor);//encontrar a equipe diretamente no cadastro do usuário. Se não encontrar, procura através do gestor associado a ele.
        Equipe equipeFinal = eqNovo != null ? eqNovo : lead.getEquipe();
        if (equipeFinal == null) {
            equipeFinal = equipeRepository.findByNome("Equipe Geral").orElse(null);
        }

        //atualiza os dados do lead
        lead.setCorretor(novoCorretor);
        lead.setCorretor_responsavel(novoCorretor.getNome());
        lead.setEquipe(equipeFinal);
        lead.setStatusAtribuicao("ATRIBUIDO");
        lead.setResponsavelRedistribuicao(null); // a pendência foi resolvida
        Lead salvo = leadRepository.save(lead);

        String motivo = corretorAnterior == null ? "REDISTRIBUICAO" : "ALTERACAO_MANUAL";
        if ("AGUARDANDO_REDISTRIBUICAO".equals(statusAnterior) || corretorAnterior == null) motivo = "REDISTRIBUICAO";

        //salva o historico de responsaveis do cliente
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
        //busca todos os históricos daquele lead.

        for (LeadResponsavelHistorico h : hist) {//percorre o historico
            if (h.getDataFim() == null) {//Verifica se o histórico ainda está aberto
                h.setDataFim(LocalDateTime.now());//Define a data de encerramento
                historicoRepository.save(h);//salva
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
            //O administrador pode visualizar todos os leads que estão aguardando redistribuição.
            //Porém se recebeu equipeId, filtra somente os leads que pertencem à equipe informada.
            return leadRepository.findByStatusAtribuicao("AGUARDANDO_REDISTRIBUICAO");
        }
        if ("gestor".equals(papel)) {
            Equipe equipe = resolverEquipeUsuario(solicitante);
            return leadRepository.findByStatusAtribuicao("AGUARDANDO_REDISTRIBUICAO").stream()
                    .filter(l -> (equipe != null && l.getEquipe() != null && l.getEquipe().getId().equals(equipe.getId()))
                            || (l.getResponsavelRedistribuicao() != null && solicitante.getId().equals(l.getResponsavelRedistribuicao().getId())))
                    .toList();
            //visualiza leads que pertence à equipe do gestor
            //OU o gestor é o responsável pela redistribuição
        }
        return List.of();
    }


    //listar os leads que estão aguardando redistribuição
    public Page<LeadAguardandoDTO> listarAguardandoComResumo(Long equipeId, Usuario solicitante, int page, int size) {
        String papel = solicitante.getPapel() != null ? solicitante.getPapel().getPapel() : "";
        int p = Math.max(0, page);//nunca negativo
        int s = Math.min(Math.max(1, size), 100);//minimo 1, maximo 100
        PageRequest pr = PageRequest.of(p, s, Sort.by(Sort.Direction.DESC, "dataAtualizacao"));
        //paginação: os leads com atualização mais recente aparecem primeiro.
        Page<Lead> leadsPage;
        if ("admin".equals(papel)) {
            if (equipeId != null) leadsPage = leadRepository.findByStatusAtribuicaoAndEquipeId("AGUARDANDO_REDISTRIBUICAO", equipeId, pr);
            else leadsPage = leadRepository.findByStatusAtribuicao("AGUARDANDO_REDISTRIBUICAO", pr);
            //visualiza a redistribuição de todo mundo/equipe
        } else if ("gestor".equals(papel)) {
            // leads da equipe do gestor + os que ficaram sob responsabilidade dele
            Equipe equipe = resolverEquipeUsuario(solicitante);
            leadsPage = leadRepository.findAguardandoDaEquipeOuDoResponsavel(equipe != null ? equipe.getId() : -1L, solicitante.getId(), pr);
            //O -1L é utilizado caso não encontre uma equipe, evitando passar null para a consulta.
        } else {
            return new PageImpl<>(List.of(), pr, 0);
            //Retorna uma página vazia.
        }
        if (leadsPage.isEmpty()) return new PageImpl<>(List.of(), pr, leadsPage.getTotalElements());
        //Se não encontrar leads, retorna uma página vazia.

        List<Long> ids = leadsPage.getContent().stream().map(Lead::getId).toList();
        //Pega os IDs de todos os leads encontrados na página atual.

        List<LeadResponsavelHistorico> hists = historicoRepository.findByLeadIdInOrderByDataInicioAsc(ids);
        //Busca todos os históricos desses leads

        Map<Long, List<LeadResponsavelHistorico>> byLead = hists.stream().collect(Collectors.groupingBy(h -> h.getLead().getId()));
        //os históricos são separados de acordo com o ID do lead.

        List<LeadAguardandoDTO> dtos = new ArrayList<>();
        for (Lead lead : leadsPage.getContent()) {
            //Percorre cada lead encontrado e recupera seu histórico.
            List<LeadResponsavelHistorico> hist = byLead.getOrDefault(lead.getId(), List.of());
            // hist já ordenado ASC, último é desligamento, penúltimo é anterior
            LeadResponsavelHistorico ultimo = hist.isEmpty() ? null : hist.get(hist.size() - 1);//último registro do histórico.
            LeadResponsavelHistorico anterior = hist.size() >= 2 ? hist.get(hist.size() - 2) : null;//penúltimo registro do histórico.
            // valida que último é realmente DESLIGAMENTO, senão busca último com corretor null
            if (ultimo != null && ultimo.getCorretor() != null && hist.size() >= 2) {
                //Se o último histórico possui um corretor associado, o código considera que talvez esse registro não seja o desligamento que está procurando.
                // procura último com corretor null
                for (int i = hist.size() - 1; i >= 0; i--) {//percorre o histórico de trás para frente
                //procura o registro mais recente em que corretor == null
                    if (hist.get(i).getCorretor() == null) { ultimo = hist.get(i); break; }
                }
            }
            dtos.add(LeadAguardandoDTO.from(lead, ultimo, anterior));
            //Para cada lead, cria um DTO contendo as informações necessárias para apresentar a listagem.
        }
        return new PageImpl<>(dtos, pr, leadsPage.getTotalElements());
    }

    private void validarPermissao(Usuario solicitante, Lead lead) {
        String papel = solicitante.getPapel() != null ? solicitante.getPapel().getPapel() : "";
        if ("admin".equals(papel)) return; //libera acesso
        if ("gestor".equals(papel)) {
            //o lead está sob responsabilidade dele.
            if (lead.getResponsavelRedistribuicao() != null && solicitante.getId().equals(lead.getResponsavelRedistribuicao().getId())) return;
            
            //o lead pertence à equipe do gestor.
            Equipe eqSolicitante = resolverEquipeUsuario(solicitante);

            //identifica a equipe à qual o gestor pertence.
            //O gestor não possui uma equipe. O lead não possui uma equipe. A equipe do lead é diferente da equipe do gestor.
            if (eqSolicitante == null || lead.getEquipe() == null || !eqSolicitante.getId().equals(lead.getEquipe().getId())) {
                throw new AccessDeniedException("Você só pode redistribuir leads da sua equipe.");
            }
            return;
        }
        throw new AccessDeniedException("Somente administradores e gestores podem redistribuir leads.");
    }
}
