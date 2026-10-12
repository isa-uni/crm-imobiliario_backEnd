package crm_imobiliario.back.model.service;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import crm_imobiliario.back.model.dto.DashboardCorretorResumoDTO;
import crm_imobiliario.back.model.dto.DashboardCorretorTimelineDTO;
import crm_imobiliario.back.model.entity.Equipe;
import crm_imobiliario.back.model.entity.Lead;
import crm_imobiliario.back.model.entity.Tramitacao;
import crm_imobiliario.back.model.entity.Usuario;
import crm_imobiliario.back.model.repository.EquipeRepository;
import crm_imobiliario.back.model.repository.LeadRepository;
import crm_imobiliario.back.model.repository.TramitacaoRepository;
import crm_imobiliario.back.model.repository.UsuarioRepository;

/**
 * Gráfico "Cadastrados x Contratos fechados" do Dashboard, mês a mês.
 *
 * Escopo: corretor vê os próprios clientes; gestor escolhe entre os próprios (padrão) e os da equipe
 * (ele e os corretores dele); admin vê todos. Contratos seguem a regra única do {@link ContagemFunil}
 * (a mesma do Relógio de Vendas e do Dashboard do Gestor): contam no mês em que fecharam, se o cliente
 * ainda estava em contrato no fim daquele mês.
 */
@Service
public class DashboardCorretorService {

    public static final String ESCOPO_MEUS = "meus";
    public static final String ESCOPO_EQUIPE = "equipe";
    public static final String ESCOPO_TODOS = "todos";

    @Autowired private LeadRepository leadRepository;
    @Autowired private TramitacaoRepository tramitacaoRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private EquipeRepository equipeRepository;
    @Autowired private UsuarioService usuarioService;

    private static final DateTimeFormatter LABEL_FMT = DateTimeFormatter.ofPattern("MMM yyyy", new Locale("pt", "BR"));

    private String escopoDe(String papel, String escopoPedido) {
        if ("admin".equals(papel)) return ESCOPO_TODOS;
        return "gestor".equals(papel) && ESCOPO_EQUIPE.equals(escopoPedido) ? ESCOPO_EQUIPE : ESCOPO_MEUS;
    }

    private Map<Long, List<Tramitacao>> tramitacoes(List<Lead> base) {
        return base.isEmpty() ? Map.of()
                : tramitacaoRepository.findByLeadIdInOrderByDataMovimentacaoAsc(base.stream().map(Lead::getId).toList())
                        .stream().collect(Collectors.groupingBy(t -> t.getLead().getId()));
    }

    /** Cards do mês atual do Dashboard, com o mesmo escopo do gráfico e a regra única de contagem. */
    @Transactional(readOnly = true)
    public DashboardCorretorResumoDTO getResumo(String email, String escopoPedido, LocalDateTime agora) {
        Usuario solicitante = usuarioService.buscarPorEmail(email);
        String escopo = escopoDe(EquipeResolver.papel(solicitante), escopoPedido);
        List<Lead> base = carregarBase(solicitante, escopo);
        Map<Long, List<Tramitacao>> trams = tramitacoes(base);

        YearMonth mes = YearMonth.from(agora);
        long clientes = 0, contratos = 0, contratosAnterior = 0, andamento = 0, andamentoAnterior = 0;
        double valor = 0;
        YearMonth anterior = mes.minusMonths(1);
        LocalDateTime refAnterior = ContagemFunil.referencia(anterior, agora);
        for (Lead l : base) {
            List<Tramitacao> t = trams.getOrDefault(l.getId(), List.of());
            ContagemFunil.Situacao s = ContagemFunil.avaliar(l, t, mes, agora, true);
            if (s.entra()) {
                clientes++;
                if (s.tipo() == ContagemFunil.Tipo.ANDAMENTO) andamento++;
                if (s.tipo() == ContagemFunil.Tipo.CONTRATO) {
                    contratos++;
                    valor += l.getValorInteresse() != null ? l.getValorInteresse() : 0;
                }
            }
            ContagemFunil.Situacao sa = ContagemFunil.avaliar(l, t, anterior, refAnterior, false);
            if (sa.entra() && sa.tipo() == ContagemFunil.Tipo.CONTRATO) contratosAnterior++;
            if (sa.entra() && sa.tipo() == ContagemFunil.Tipo.ANDAMENTO) andamentoAnterior++;
        }
        double conversao = clientes > 0 ? (double) contratos / clientes * 100 : 0;
        return new DashboardCorretorResumoDTO(mes.toString(), clientes, andamento, andamentoAnterior, contratos, valor, conversao, contratosAnterior, escopo);
    }

    @Transactional(readOnly = true)
    public DashboardCorretorTimelineDTO getTimeline(String email, LocalDateTime inicio, LocalDateTime fim, String escopoPedido, LocalDateTime agora) {
        Usuario solicitante = usuarioService.buscarPorEmail(email);
        String papel = EquipeResolver.papel(solicitante);
        boolean gestor = "gestor".equals(papel);
        String escopo = escopoDe(papel, escopoPedido);

        List<Lead> base = carregarBase(solicitante, escopo);
        Map<Long, List<Tramitacao>> trams = tramitacoes(base);

        // série completa mês a mês, com 0 onde não há dados, até o mês atual
        YearMonth ymFim = YearMonth.from(fim);
        if (ymFim.isAfter(YearMonth.from(agora))) ymFim = YearMonth.from(agora);
        List<DashboardCorretorTimelineDTO.PontoMensalDTO> pontos = new ArrayList<>();
        for (YearMonth mes = YearMonth.from(inicio); !mes.isAfter(ymFim); mes = mes.plusMonths(1)) {
            LocalDateTime ref = ContagemFunil.referencia(mes, agora);
            boolean mesCorrente = mes.equals(YearMonth.from(agora));
            long cadastrados = 0, contratos = 0;
            for (Lead l : base) {
                if (l.getDataCriacao() != null && YearMonth.from(l.getDataCriacao()).equals(mes)) cadastrados++;
                ContagemFunil.Situacao s = ContagemFunil.avaliar(l, trams.getOrDefault(l.getId(), List.of()), mes, ref, mesCorrente);
                if (s.entra() && s.tipo() == ContagemFunil.Tipo.CONTRATO) contratos++;
            }
            pontos.add(DashboardCorretorTimelineDTO.PontoMensalDTO.builder()
                    .mes(mes.toString())
                    .label(rotulo(mes))
                    .leadsRecebidos(cadastrados)
                    .contratosFechados(contratos)
                    .build());
        }
        return DashboardCorretorTimelineDTO.builder().timeline(pontos).escopo(escopo).podeVerEquipe(gestor).build();
    }

    private List<Lead> carregarBase(Usuario solicitante, String escopo) {
        if (ESCOPO_TODOS.equals(escopo)) return leadRepository.findAll();
        if (ESCOPO_MEUS.equals(escopo)) return leadRepository.findByCorretorId(solicitante.getId());
        // equipe: o gestor, os corretores dele e os clientes sem corretor da equipe (aguardando redistribuição)
        List<Long> ids = new ArrayList<>(usuarioRepository.findByGestorId(solicitante.getId()).stream().map(Usuario::getId).toList());
        ids.add(solicitante.getId());
        Map<Long, Lead> porId = new LinkedHashMap<>();
        leadRepository.findByCorretorIdIn(ids).forEach(l -> porId.put(l.getId(), l));
        Equipe eq = EquipeResolver.resolver(solicitante, equipeRepository);
        if (eq != null) {
            leadRepository.findByEquipeId(eq.getId()).stream()
                    .filter(l -> l.getCorretor() == null)
                    .forEach(l -> porId.putIfAbsent(l.getId(), l));
        }
        return new ArrayList<>(porId.values());
    }

    /** "out 2026" → "Out 2026". */
    private static String rotulo(YearMonth mes) {
        String label = mes.format(LABEL_FMT).replace(".", "");
        return label.isEmpty() ? label : Character.toUpperCase(label.charAt(0)) + label.substring(1);
    }
}
