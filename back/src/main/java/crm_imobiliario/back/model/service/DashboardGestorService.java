package crm_imobiliario.back.model.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import crm_imobiliario.back.model.dto.DashboardGestorDTO;
import crm_imobiliario.back.model.dto.RelogioVendasDTO;
import crm_imobiliario.back.model.entity.Lead;
import crm_imobiliario.back.model.entity.Meta;
import crm_imobiliario.back.model.entity.OrigemMeta;
import crm_imobiliario.back.model.entity.Tramitacao;
import crm_imobiliario.back.model.entity.Usuario;
import crm_imobiliario.back.model.repository.LeadRepository;
import crm_imobiliario.back.model.repository.MetaRepository;
import crm_imobiliario.back.model.repository.TramitacaoRepository;
import crm_imobiliario.back.model.repository.UsuarioRepository;
import crm_imobiliario.back.model.service.ContagemFunil.Avaliado;
import crm_imobiliario.back.model.service.ContagemFunil.Tipo;

/**
 * Dashboard do Gestor, sempre de um mês. Dois tipos de número:
 * - eventos do mês, pela data do próprio evento: leads recebidos (cadastro), contratos (fechamento),
 *   descartes (descarte);
 * - carteira, pela mesma regra do Relógio de Vendas ({@link ContagemFunil}): em andamento de qualquer
 *   mês + contratos e descartes do mês. Em mês passado, reconstruída como estava no fim do mês.
 * Com o filtro de um corretor no mês atual, os números do pipeline batem com o Relógio dele.
 */
@Service
public class DashboardGestorService {

    @Autowired
    private LeadRepository leadRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private TramitacaoRepository tramitacaoRepository;
    @Autowired
    private MetaRepository metaRepository;
    @Autowired
    private UsuarioService usuarioService;

    private static final List<String> ETAPAS = ContagemFunil.ETAPAS;

    private static final Map<String,String> PIPELINE_LABELS = Map.of(
            "lead","Clientes no mês",
            "oportunidade","Oportunidade",
            "visita-agendada","Visita agendada",
            "visita-realizada","Visita realizada",
            "pasta","Pasta",
            "aprovado","Aprovado",
            "contrato","Fechado"
    );

    /** Clientes da base avaliados para um mês. */
    private record Mes(YearMonth mes, LocalDateTime ref, List<Avaliado> todos) {
        List<Avaliado> naContagem() { return todos.stream().filter(a -> a.situacao().entra()).toList(); }
        List<Avaliado> recebidos() {
            return todos.stream().filter(a -> a.situacao().existe() && a.lead().getDataCriacao() != null
                    && YearMonth.from(a.lead().getDataCriacao()).equals(mes)).toList();
        }
        List<Avaliado> contratos() { return naContagem().stream().filter(a -> a.situacao().tipo() == Tipo.CONTRATO).toList(); }
        List<Avaliado> descartes() { return naContagem().stream().filter(a -> a.situacao().tipo() == Tipo.DESCARTE).toList(); }
    }

    @Transactional(readOnly = true)
    public DashboardGestorDTO getDashboard(String email, YearMonth mes, LocalDateTime agora,
                                           Long corretorId, String origem, Long empreendimentoId) {
        Usuario solicitante = usuarioService.buscarPorEmail(email);
        String papel = solicitante.getPapel() != null ? solicitante.getPapel().getPapel() : "";

        // equipes: se gestor, só subordinados (ou ele mesmo, se não tiver); se admin, todos
        List<Usuario> equipe = getEquipe(solicitante, papel);
        List<Long> equipeIds = equipe.stream().map(Usuario::getId).toList();

        // nomes da equipe (fallback legado por corretor_responsavel) resolvidos em memória
        Map<String, Long> idPorNome = indexarPorNome(equipe);

        // todos os clientes da equipe, de qualquer mês: a carteira inclui quem está em andamento
        // desde meses anteriores
        List<Lead> base = carregarBase(papel, equipeIds, solicitante).stream()
                .filter(l -> filtroEquipe(l, equipeIds, idPorNome, papel, solicitante))
                .filter(l -> corretorId == null || isLeadDoCorretor(l, corretorId, idPorNome))
                .filter(l -> origem == null || origem.isBlank() || origem.equals(l.getOrigem()))
                .filter(l -> empreendimentoId == null || (l.getEmpreendimento() != null && empreendimentoId.equals(l.getEmpreendimento().getId())))
                .toList();
        Map<Long, List<Tramitacao>> tramitacoes = carregarTramitacoes(base);

        Mes atual = avaliarMes(base, tramitacoes, mes, agora);
        Mes anterior = avaliarMes(base, tramitacoes, mes.minusMonths(1), agora);

        DashboardGestorDTO dto = new DashboardGestorDTO();
        dto.setMesReferencia(mes.toString());
        dto.setKpis(calcularKpis(atual, anterior));
        dto.setPipeline(calcularPipeline(atual));
        dto.setRankingCorretores(calcularRanking(atual, equipe, tramitacoes, idPorNome));
        dto.setMetas(calcularMetas(equipe, atual.contratos().stream().map(Avaliado::lead).toList(), mes.atDay(1).atStartOfDay()));
        dto.setTempoMedio(calcularTempoMedio(atual, idPorNome));
        dto.setOrigens(calcularOrigens(atual));
        dto.setHistorico(calcularHistorico(atual));
        dto.setEmpreendimentosMaisProcurados(calcularEmpreendimentos(atual));
        dto.setAlertas(calcularAlertas(dto.getRankingCorretores(), dto.getMetas()));
        return dto;
    }

    private Mes avaliarMes(List<Lead> base, Map<Long, List<Tramitacao>> tramitacoes, YearMonth mes, LocalDateTime agora) {
        LocalDateTime ref = ContagemFunil.referencia(mes, agora);
        boolean mesCorrente = mes.equals(YearMonth.from(agora));
        List<Avaliado> todos = base.stream()
                .map(l -> new Avaliado(l, ContagemFunil.avaliar(l, tramitacoes.getOrDefault(l.getId(), List.of()), mes, ref, mesCorrente)))
                .toList();
        return new Mes(mes, ref, todos);
    }

    private List<Lead> carregarBase(String papel, List<Long> equipeIds, Usuario solicitante) {
        if ("admin".equals(papel)) return leadRepository.findAll();
        Map<Long, Lead> porId = new LinkedHashMap<>();
        if (!equipeIds.isEmpty()) leadRepository.findByCorretorIdIn(equipeIds).forEach(l -> porId.put(l.getId(), l));
        // leads sem corretor (ex.: aguardando redistribuição) da equipe do gestor
        if (solicitante.getEquipe() != null) leadRepository.findByEquipeId(solicitante.getEquipe().getId()).forEach(l -> porId.putIfAbsent(l.getId(), l));
        return new ArrayList<>(porId.values());
    }

    public List<Usuario> getEquipeCorretores(String email) {
        Usuario sol = usuarioService.buscarPorEmail(email);
        String papel = sol.getPapel() != null ? sol.getPapel().getPapel() : "";
        return getEquipe(sol, papel);
    }

    /** Administrador não entra nos cálculos do painel (ranking, metas, filtro de corretor, clientes). */
    private static boolean ehAdmin(Usuario u) {
        return u != null && "admin".equals(EquipeResolver.papel(u));
    }

    private List<Usuario> getEquipe(Usuario solicitante, String papel) {
        if ("admin".equals(papel)) {
            return usuarioRepository.findAll().stream()
                    .filter(Usuario::isAtivo)
                    .filter(u -> !ehAdmin(u))
                    .collect(Collectors.toList());
        }
        if ("gestor".equals(papel)) {
            List<Usuario> subs = usuarioRepository.findByGestorId(solicitante.getId()).stream()
                    .filter(Usuario::isAtivo)
                    .filter(u -> !ehAdmin(u))
                    .collect(Collectors.toList());
            // gestor vê subordinados; se não tem subordinado, vê ao menos a si (para não ficar vazio)
            if (subs.isEmpty()) {
                List<Usuario> self = new ArrayList<>();
                self.add(solicitante);
                return self;
            }
            return subs;
        }
        // corretor não deveria chamar, mas retorna vazio
        return List.of();
    }

    /** nome do usuário (minúsculo) → id, para o fallback legado de leads sem corretor_id. */
    private Map<String, Long> indexarPorNome(List<Usuario> usuarios) {
        Map<String, Long> m = new HashMap<>();
        for (Usuario u : usuarios) if (u.getNome() != null) m.putIfAbsent(u.getNome().toLowerCase(), u.getId());
        return m;
    }

    private Map<Long, List<Tramitacao>> carregarTramitacoes(List<Lead> leads) {
        if (leads.isEmpty()) return Map.of();
        List<Long> ids = leads.stream().map(Lead::getId).toList();
        return tramitacaoRepository.findByLeadIdInOrderByDataMovimentacaoAsc(ids).stream()
                .collect(Collectors.groupingBy(t -> t.getLead().getId()));
    }

    private boolean filtroEquipe(Lead lead, List<Long> equipeIds, Map<String, Long> idPorNome, String papel, Usuario solicitante) {
        // clientes que estão com um administrador ficam fora do painel
        if (ehAdmin(lead.getCorretor())) return false;
        if ("admin".equals(papel)) return true;
        // gestor: lead pertence a alguém da equipe via corretor_id
        if (lead.getCorretor() != null) {
            return equipeIds.contains(lead.getCorretor().getId());
        }
        // fallback legado: corretor_responsavel (nome) de alguém da equipe
        if (lead.getCorretor_responsavel() != null) {
            Long id = idPorNome.get(lead.getCorretor_responsavel().toLowerCase());
            return id != null && equipeIds.contains(id);
        }
        // lead sem responsável (ex.: aguardando redistribuição): só entra se for da equipe do gestor.
        return lead.getEquipe() != null && solicitante.getEquipe() != null
                && lead.getEquipe().getId().equals(solicitante.getEquipe().getId());
    }

    private boolean isLeadDoCorretor(Lead l, Long corretorId, Map<String, Long> idPorNome) {
        if (l.getCorretor() != null) return corretorId.equals(l.getCorretor().getId());
        if (l.getCorretor_responsavel() != null) {
            return corretorId.equals(idPorNome.get(l.getCorretor_responsavel().toLowerCase()));
        }
        return false;
    }

    private static double valor(Lead l) {
        return l.getValorInteresse() != null ? l.getValorInteresse() : 0;
    }

    private static double variacao(double atual, double anterior) {
        return anterior > 0 ? (atual - anterior) / anterior * 100 : (atual > 0 ? 100 : 0);
    }

    private DashboardGestorDTO.KpiDTO calcularKpis(Mes atual, Mes anterior) {
        long recebidos = atual.recebidos().size();
        List<Avaliado> contratos = atual.contratos();
        long negocios = contratos.size();
        double valor = contratos.stream().mapToDouble(a -> valor(a.lead())).sum();
        long naContagem = atual.naContagem().size();

        long recebidosAnt = anterior.recebidos().size();
        List<Avaliado> contratosAnt = anterior.contratos();
        long negociosAnt = contratosAnt.size();
        double valorAnt = contratosAnt.stream().mapToDouble(a -> valor(a.lead())).sum();

        return DashboardGestorDTO.KpiDTO.builder()
                .leadsRecebidos(recebidos).negociosFechados(negocios).valorVendido(valor)
                // contratos do mês ÷ clientes trabalhados no mês (mesma população do Relógio de Vendas)
                .taxaConversao(naContagem > 0 ? (double) negocios / naContagem * 100 : 0)
                .ticketMedio(negocios > 0 ? valor / negocios : 0)
                .tempoMedioDias(mediaDias(contratos))
                .leadsRecebidosAnterior(recebidosAnt).variacaoLeads(variacao(recebidos, recebidosAnt))
                .negociosAnterior(negociosAnt).variacaoNegocios(variacao(negocios, negociosAnt))
                .valorAnterior(valorAnt).variacaoValor(variacao(valor, valorAnt))
                .build();
    }

    /** Dias entre o cadastro e o fechamento do contrato. */
    private static long diasAteFechar(Avaliado a) {
        LocalDateTime ini = a.lead().getDataCriacao();
        LocalDateTime fim = a.situacao().quando();
        if (ini == null || fim == null) return 0;
        return Math.max(0, ChronoUnit.DAYS.between(ini, fim));
    }

    private static double mediaDias(List<Avaliado> contratos) {
        return contratos.stream().mapToLong(DashboardGestorService::diasAteFechar).average().orElse(0);
    }

    private List<DashboardGestorDTO.PipelineEtapaDTO> calcularPipeline(Mes atual) {
        List<Avaliado> naContagem = atual.naContagem();
        List<RelogioVendasDTO.Etapa> etapas = ContagemFunil.etapas(naContagem, atual.ref());
        long base = etapas.get(0).realizado();
        List<DashboardGestorDTO.PipelineEtapaDTO> res = new ArrayList<>();
        for (RelogioVendasDTO.Etapa e : etapas) {
            // valor parado na etapa: clientes em andamento cuja etapa atual é esta; em Fechado, o vendido no mês
            double valorEtapa = "contrato".equals(e.etapa())
                    ? atual.contratos().stream().mapToDouble(a -> valor(a.lead())).sum()
                    : naContagem.stream().filter(a -> a.situacao().tipo() == Tipo.ANDAMENTO && e.etapa().equals(a.situacao().etapaAtual()))
                            .mapToDouble(a -> valor(a.lead())).sum();
            res.add(DashboardGestorDTO.PipelineEtapaDTO.builder()
                    .status(e.etapa()).label(PIPELINE_LABELS.getOrDefault(e.etapa(), e.etapa()))
                    .quantidade(e.realizado())
                    .percentual(base > 0 ? (double) e.realizado() / base * 100 : 0)
                    .valorPotencial(valorEtapa)
                    .emAndamento(e.emAndamento()).contratosNoMes(e.contratosNoMes()).descartadosNoMes(e.descartadosNoMes())
                    .descartesNestaEtapa(e.descartesNestaEtapa()).porMesCadastro(e.porMesCadastro())
                    .build());
        }
        return res;
    }

    private List<DashboardGestorDTO.RankingCorretorDTO> calcularRanking(Mes atual, List<Usuario> equipe,
                                                                         Map<Long, List<Tramitacao>> tramitacoes, Map<String, Long> idPorNome) {
        LocalDateTime inicioMes = atual.mes().atDay(1).atStartOfDay();
        // contatos = movimentações de status feitas pelo corretor no mês
        Map<Long, Long> contatosPorCorretor = new HashMap<>();
        tramitacoes.values().forEach(lista -> lista.forEach(t -> {
            if (t.getUsuario() != null && t.getDataMovimentacao() != null
                    && !t.getDataMovimentacao().isBefore(inicioMes) && !t.getDataMovimentacao().isAfter(atual.ref())) {
                contatosPorCorretor.merge(t.getUsuario().getId(), 1L, Long::sum);
            }
        }));

        List<Avaliado> naContagem = atual.naContagem();
        List<DashboardGestorDTO.RankingCorretorDTO> lista = new ArrayList<>();
        for (Usuario u : equipe) {
            List<Avaliado> meus = naContagem.stream().filter(a -> isLeadDoCorretor(a.lead(), u.getId(), idPorNome)).toList();
            long total = meus.size();
            long negocios = meus.stream().filter(a -> a.situacao().tipo() == Tipo.CONTRATO).count();
            long oportunidades = meus.stream().filter(a -> a.situacao().maxIdx() >= 1).count();
            double conv = total > 0 ? (double) negocios / total * 100 : 0;
            String atencao = "ok";
            if (total == 0 || conv < 2) atencao = "critico";
            else if (conv < 5 || total < 3) atencao = "atencao";

            lista.add(DashboardGestorDTO.RankingCorretorDTO.builder()
                    .corretorId(u.getId()).nome(u.getNome())
                    .leads(total).contatos(contatosPorCorretor.getOrDefault(u.getId(), 0L)).propostas(oportunidades).negocios(negocios)
                    .conversao(conv).statusAtencao(atencao).build());
        }
        lista.sort((a, b) -> Long.compare(b.getNegocios(), a.getNegocios()));
        return lista;
    }

    /** @param contratosDoMes clientes que fecharam contrato no mês da meta */
    private DashboardGestorDTO.MetaEquipeDTO calcularMetas(List<Usuario> equipe, List<Lead> contratosDoMes, LocalDateTime inicio) {
        YearMonth ym = YearMonth.from(inicio);
        LocalDate mesRef = ym.atDay(1);

        Map<Long, List<Meta>> metasPorUsuario = new HashMap<>();
        Map<Long, Meta> metaEfetivaPorUsuario = new HashMap<>();
        for (Usuario u : equipe) {
            List<Meta> ms = metaRepository.findByUsuarioIdAndMesReferencia(u.getId(), mesRef);
            metasPorUsuario.put(u.getId(), ms);
            Meta efetiva = metaEfetiva(ms);
            if (efetiva != null) metaEfetivaPorUsuario.put(u.getId(), efetiva);
        }

        int metaContratosTotal = metaEfetivaPorUsuario.values().stream().mapToInt(m -> m.getMetaContratos() != null ? m.getMetaContratos() : 0).sum();

        long realizadoContratos = contratosDoMes.size();

        double percContr = metaContratosTotal > 0 ? (double) realizadoContratos / metaContratosTotal * 100 : 0;

        List<DashboardGestorDTO.MetaCorretorDTO> porCorretor = new ArrayList<>();
        for (Usuario u : equipe) {
            Meta m = metaEfetivaPorUsuario.get(u.getId());
            long rc = contratosDoMes.stream().filter(l -> {
                if (l.getCorretor()!=null) return u.getId().equals(l.getCorretor().getId());
                if (l.getCorretor_responsavel()!=null) return u.getNome().equalsIgnoreCase(l.getCorretor_responsavel());
                return false;
            }).count();
            Integer mc = m != null ? m.getMetaContratos() : null;
            List<Meta> ms = metasPorUsuario.get(u.getId());
            Integer metaGestor = ms.stream().filter(x -> x.getOrigem() == OrigemMeta.GESTOR)
                    .map(Meta::getMetaContratos).findFirst().orElse(null);
            Integer metaPropria = ms.stream().filter(x -> x.getOrigem() == OrigemMeta.CORRETOR)
                    .map(Meta::getMetaContratos).findFirst().orElse(null);
            double pc = (mc != null && mc > 0) ? (double) rc / mc * 100 : 0;
            String st;
            if (m == null) st = "sem_meta";
            else if (pc >= 100) st = "atingida";
            else if (pc >= 70) st = "proxima";
            else if (pc > 0) st = "abaixo";
            else st = "sem_movimentacao";

            porCorretor.add(DashboardGestorDTO.MetaCorretorDTO.builder()
                    .corretorId(u.getId()).nome(u.getNome())
                    .metaContratos(mc)
                    .metaGestor(metaGestor)
                    .metaPropria(metaPropria)
                    .realizadoContratos(rc)
                    .percentualContratos(pc).status(st).build());
        }

        return DashboardGestorDTO.MetaEquipeDTO.builder()
                .metaContratosTotal(metaContratosTotal)
                .realizadoContratos(realizadoContratos)
                .percentualContratos(percContr)
                .faltanteContratos(Math.max(0, metaContratosTotal - realizadoContratos))
                .porCorretor(porCorretor)
                .build();
    }

    /** Meta própria do corretor tem prioridade sobre a meta atribuída pelo gestor. */
    private Meta metaEfetiva(List<Meta> metas) {
        return metas.stream().filter(m -> m.getOrigem() == OrigemMeta.CORRETOR).findFirst()
                .orElseGet(() -> metas.stream().filter(m -> m.getOrigem() == OrigemMeta.GESTOR).findFirst().orElse(null));
    }

    private DashboardGestorDTO.TempoMedioDTO calcularTempoMedio(Mes atual, Map<String, Long> idPorNome) {
        List<Avaliado> contratos = atual.contratos();

        // por corretor, sobre os contratos fechados no mês
        Map<Long, List<Long>> diasPorCorretor = new HashMap<>();
        Map<Long, String> nomePorCorretor = new HashMap<>();
        for (Avaliado a : contratos) {
            Lead l = a.lead();
            Long corrId = l.getCorretor() != null ? l.getCorretor().getId()
                    : l.getCorretor_responsavel() != null ? idPorNome.get(l.getCorretor_responsavel().toLowerCase()) : null;
            if (corrId == null) continue;
            diasPorCorretor.computeIfAbsent(corrId, k -> new ArrayList<>()).add(diasAteFechar(a));
            nomePorCorretor.putIfAbsent(corrId, l.getCorretor() != null ? l.getCorretor().getNome() : l.getCorretor_responsavel());
        }
        List<DashboardGestorDTO.TempoCorretorDTO> porCorr = new ArrayList<>();
        for (Map.Entry<Long, List<Long>> e : diasPorCorretor.entrySet()) {
            String nome = nomePorCorretor.get(e.getKey());
            porCorr.add(DashboardGestorDTO.TempoCorretorDTO.builder()
                    .corretorId(e.getKey()).nome(nome != null ? nome : "-")
                    .mediaDias(e.getValue().stream().mapToLong(Long::longValue).average().orElse(0))
                    .totalNegocios(e.getValue().size()).build());
        }

        return DashboardGestorDTO.TempoMedioDTO.builder()
                .mediaGeralDias(mediaDias(contratos)).porCorretor(porCorr).build();
    }

    private List<DashboardGestorDTO.OrigemDTO> calcularOrigens(Mes atual) {
        List<Avaliado> recebidos = atual.recebidos();
        long total = recebidos.size();
        String semOrigem = "sem_origem";
        Map<String, List<Avaliado>> recebidosPorOrigem = recebidos.stream()
                .collect(Collectors.groupingBy(a -> Objects.requireNonNullElse(a.lead().getOrigem(), semOrigem)));
        Map<String, Long> negociosPorOrigem = atual.contratos().stream()
                .collect(Collectors.groupingBy(a -> Objects.requireNonNullElse(a.lead().getOrigem(), semOrigem), Collectors.counting()));

        List<String> origens = new ArrayList<>(recebidosPorOrigem.keySet());
        negociosPorOrigem.keySet().stream().filter(o -> !origens.contains(o)).forEach(origens::add);

        List<DashboardGestorDTO.OrigemDTO> res = new ArrayList<>();
        for (String o : origens) {
            List<Avaliado> lista = recebidosPorOrigem.getOrDefault(o, List.of());
            long q = lista.size();
            long neg = negociosPorOrigem.getOrDefault(o, 0L);
            long conv = lista.stream().filter(a -> a.situacao().maxIdx() >= 1).count();
            res.add(DashboardGestorDTO.OrigemDTO.builder()
                    .origem(o).label(o).quantidade(q).percentual(total > 0 ? (double) q / total * 100 : 0)
                    .conversoes(conv).negocios(neg).taxaConversao(q > 0 ? (double) neg / q * 100 : 0).build());
        }
        res.sort(Comparator.comparingLong(DashboardGestorDTO.OrigemDTO::getQuantidade).reversed());
        return res;
    }

    /** Dia a dia do mês: recebidos pelo cadastro, contratos pelo fechamento, descartes pelo descarte. */
    private List<DashboardGestorDTO.HistoricoDTO> calcularHistorico(Mes atual) {
        Map<LocalDate, Long> recebidos = atual.recebidos().stream()
                .collect(Collectors.groupingBy(a -> a.lead().getDataCriacao().toLocalDate(), Collectors.counting()));
        Map<LocalDate, Long> contratos = atual.contratos().stream()
                .collect(Collectors.groupingBy(a -> a.situacao().quando().toLocalDate(), Collectors.counting()));
        Map<LocalDate, Long> descartes = atual.descartes().stream()
                .collect(Collectors.groupingBy(a -> a.situacao().quando().toLocalDate(), Collectors.counting()));

        List<DashboardGestorDTO.HistoricoDTO> res = new ArrayList<>();
        LocalDate fim = atual.ref().toLocalDate();
        for (LocalDate d = atual.mes().atDay(1); !d.isAfter(fim); d = d.plusDays(1)) {
            res.add(DashboardGestorDTO.HistoricoDTO.builder().periodo(d.toString())
                    .recebidos(recebidos.getOrDefault(d, 0L))
                    .contratos(contratos.getOrDefault(d, 0L))
                    .descartes(descartes.getOrDefault(d, 0L)).build());
        }
        return res;
    }

    private List<DashboardGestorDTO.EmpreendimentoInteresseDTO> calcularEmpreendimentos(Mes atual) {
        Map<Long, String> nomes = new HashMap<>();
        Map<Long, List<Avaliado>> interessados = new HashMap<>();
        for (Avaliado a : atual.recebidos()) {
            if (a.lead().getEmpreendimento() == null) continue;
            interessados.computeIfAbsent(a.lead().getEmpreendimento().getId(), k -> new ArrayList<>()).add(a);
            nomes.putIfAbsent(a.lead().getEmpreendimento().getId(), a.lead().getEmpreendimento().getNome());
        }
        Map<Long, Long> negocios = new HashMap<>();
        for (Avaliado a : atual.contratos()) {
            if (a.lead().getEmpreendimento() == null) continue;
            negocios.merge(a.lead().getEmpreendimento().getId(), 1L, Long::sum);
            nomes.putIfAbsent(a.lead().getEmpreendimento().getId(), a.lead().getEmpreendimento().getNome());
        }
        int idxPasta = ETAPAS.indexOf("pasta");
        List<DashboardGestorDTO.EmpreendimentoInteresseDTO> res = new ArrayList<>();
        for (Map.Entry<Long, String> e : nomes.entrySet()) {
            List<Avaliado> lista = interessados.getOrDefault(e.getKey(), List.of());
            long q = lista.size();
            long propostas = lista.stream().filter(a -> a.situacao().maxIdx() >= idxPasta).count();
            long neg = negocios.getOrDefault(e.getKey(), 0L);
            res.add(DashboardGestorDTO.EmpreendimentoInteresseDTO.builder()
                    .empreendimentoId(e.getKey()).nome(e.getValue()).interessados(q).propostas(propostas).negocios(neg)
                    .conversao(q > 0 ? (double) neg / q * 100 : 0).build());
        }
        res.sort(Comparator.comparingLong(DashboardGestorDTO.EmpreendimentoInteresseDTO::getInteressados).reversed());
        return res.stream().limit(10).toList();
    }

    private List<DashboardGestorDTO.AlertaDTO> calcularAlertas(List<DashboardGestorDTO.RankingCorretorDTO> ranking, DashboardGestorDTO.MetaEquipeDTO metas) {
        List<DashboardGestorDTO.AlertaDTO> alertas = new ArrayList<>();
        for (DashboardGestorDTO.RankingCorretorDTO r: ranking) {
            if ("critico".equals(r.getStatusAtencao())) {
                alertas.add(DashboardGestorDTO.AlertaDTO.builder()
                        .tipo("baixa_atividade").corretorId(r.getCorretorId()).corretorNome(r.getNome())
                        .mensagem(r.getNome()+" com baixa atividade/conversão ("+String.format("%.1f",r.getConversao())+"%)")
                        .severidade("critical").build());
            } else if ("atencao".equals(r.getStatusAtencao())) {
                alertas.add(DashboardGestorDTO.AlertaDTO.builder()
                        .tipo("atencao").corretorId(r.getCorretorId()).corretorNome(r.getNome())
                        .mensagem(r.getNome()+" precisa de atenção — conversão "+String.format("%.1f",r.getConversao())+"%")
                        .severidade("warning").build());
            }
            if (r.getLeads() > 5 && r.getNegocios()==0) {
                alertas.add(DashboardGestorDTO.AlertaDTO.builder()
                        .tipo("sem_conversao").corretorId(r.getCorretorId()).corretorNome(r.getNome())
                        .mensagem(r.getNome()+" com "+r.getLeads()+" leads e nenhum negócio")
                        .severidade("warning").build());
            }
        }
        if (metas != null) {
            for (DashboardGestorDTO.MetaCorretorDTO m: metas.getPorCorretor()) {
                if ("abaixo".equals(m.getStatus()) && m.getMetaContratos()!=null) {
                    alertas.add(DashboardGestorDTO.AlertaDTO.builder()
                            .tipo("meta_distante").corretorId(m.getCorretorId()).corretorNome(m.getNome())
                            .mensagem(m.getNome()+" distante da meta ("+String.format("%.0f",m.getPercentualContratos())+"% dos contratos)")
                            .severidade("warning").build());
                }
                if ("sem_movimentacao".equals(m.getStatus())) {
                    alertas.add(DashboardGestorDTO.AlertaDTO.builder()
                            .tipo("sem_movimentacao").corretorId(m.getCorretorId()).corretorNome(m.getNome())
                            .mensagem(m.getNome()+" sem negócios no mês")
                            .severidade("info").build());
                }
            }
        }
        return alertas;
    }
}
