package crm_imobiliario.back.model.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import crm_imobiliario.back.model.dto.DashboardGestorDTO;
import crm_imobiliario.back.model.entity.Lead;
import crm_imobiliario.back.model.entity.Meta;
import crm_imobiliario.back.model.entity.Tramitacao;
import crm_imobiliario.back.model.entity.Usuario;
import crm_imobiliario.back.model.repository.LeadRepository;
import crm_imobiliario.back.model.repository.MetaRepository;
import crm_imobiliario.back.model.repository.TramitacaoRepository;
import crm_imobiliario.back.model.repository.UsuarioRepository;

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

    private static final List<String> PIPELINE_ORDER = List.of(
            "lead", "oportunidade", "visita-agendada", "visita-realizada", "pasta", "aprovado", "contrato"
    );

    private static final Map<String,String> PIPELINE_LABELS = Map.of(
            "lead","Novos leads",
            "oportunidade","Em atendimento",
            "visita-agendada","Visita agendada",
            "visita-realizada","Visita realizada",
            "pasta","Pasta",
            "aprovado","Aprovado",
            "contrato","Fechado"
    );

    public DashboardGestorDTO getDashboard(String email, LocalDateTime inicio, LocalDateTime fim,
                                           Long corretorId, String origem, String status, Long imovelId) {
        Usuario solicitante = usuarioService.buscarPorEmail(email);
        String papel = solicitante.getPapel() != null ? solicitante.getPapel().getPapel() : "";

        // equipes: se gestor, só subordinados + ele mesmo se tiver leads; se admin, todos
        List<Usuario> equipe = getEquipe(solicitante, papel);
        List<Long> equipeIds = equipe.stream().map(Usuario::getId).toList();

        List<Lead> allLeads = leadRepository.findAll();

        // Filtro principal por período e equipe
        List<Lead> filtrados = allLeads.stream()
                .filter(l -> l.getDataCriacao() != null)
                .filter(l -> !l.getDataCriacao().isBefore(inicio) && !l.getDataCriacao().isAfter(fim))
                .filter(l -> filtroEquipe(l, equipeIds, papel, solicitante))
                .filter(l -> corretorId == null || isLeadDoCorretor(l, corretorId))
                .filter(l -> origem == null || origem.isBlank() || origem.equals(l.getOrigem()))
                .filter(l -> status == null || status.isBlank() || status.equals(l.getStatus()))
                .filter(l -> imovelId == null || (l.getImovel() != null && imovelId.equals(l.getImovel().getId())))
                .collect(Collectors.toList());

        // período anterior para comparação (mesmo tamanho de janela)
        long dias = ChronoUnit.DAYS.between(inicio, fim) + 1;
        LocalDateTime inicioAnt = inicio.minusDays(dias);
        LocalDateTime fimAnt = fim.minusDays(dias);
        List<Lead> periodoAnterior = allLeads.stream()
                .filter(l -> l.getDataCriacao() != null)
                .filter(l -> !l.getDataCriacao().isBefore(inicioAnt) && !l.getDataCriacao().isAfter(fimAnt))
                .filter(l -> filtroEquipe(l, equipeIds, papel, solicitante))
                .collect(Collectors.toList());

        DashboardGestorDTO dto = new DashboardGestorDTO();
        dto.setKpis(calcularKpis(filtrados, periodoAnterior));
        dto.setPipeline(calcularPipeline(filtrados));
        dto.setRankingCorretores(calcularRanking(filtrados, equipe));
        dto.setMetas(calcularMetas(equipe, filtrados, inicio));
        dto.setTempoMedio(calcularTempoMedio(filtrados));
        dto.setOrigens(calcularOrigens(filtrados));
        dto.setHistorico(calcularHistorico(filtrados, inicio, fim));
        dto.setImoveisMaisProcurados(calcularImoveis(filtrados));
        dto.setAlertas(calcularAlertas(dto.getRankingCorretores(), dto.getMetas()));

        return dto;
    }

    public List<Usuario> getEquipeCorretores(String email) {
        Usuario sol = usuarioService.buscarPorEmail(email);
        String papel = sol.getPapel() != null ? sol.getPapel().getPapel() : "";
        return getEquipe(sol, papel);
    }

    private List<Usuario> getEquipe(Usuario solicitante, String papel) {
        if ("admin".equals(papel)) {
            return usuarioRepository.findAll().stream()
                    .filter(Usuario::isAtivo)
                    .collect(Collectors.toList());
        }
        if ("gestor".equals(papel)) {
            List<Usuario> subs = usuarioRepository.findAll().stream()
                    .filter(u -> u.getGestor() != null && u.getGestor().getId().equals(solicitante.getId()))
                    .filter(Usuario::isAtivo)
                    .collect(Collectors.toList());
            // gestor vê subordinados; se não tem subordinado, vê ao menos a si (para não ficar vazio)
            if (subs.isEmpty()) {
                // tenta buscar por equipe vazia: retorna lista com o próprio gestor para permitir gestão
                List<Usuario> self = new ArrayList<>();
                self.add(solicitante);
                return self;
            }
            return subs;
        }
        // corretor não deveria chamar, mas retorna vazio
        return List.of();
    }

    private boolean filtroEquipe(Lead lead, List<Long> equipeIds, String papel, Usuario solicitante) {
        if ("admin".equals(papel)) return true;
        // para gestor, verifica se lead pertence a alguém da equipe via corretor ou corretor_responsavel ou tramitacao
        if (lead.getCorretor() != null) {
            return equipeIds.contains(lead.getCorretor().getId());
        }
        // fallback: tenta match por nome do corretor_responsavel
        if (lead.getCorretor_responsavel() != null) {
            // busca usuario da equipe com nome igual
            return equipeIds.stream().anyMatch(id -> {
                Usuario u = usuarioRepository.findById(id).orElse(null);
                return u != null && lead.getCorretor_responsavel().equalsIgnoreCase(u.getNome());
            });
        }
        // se não tem vínculo, inclui se equipe é vazia e lead é órfão? Para gestor com equipe, órfãos não aparecem
        // Para admin já retornou true
        // Para gestor, órfãos contam como não atribuídos, mas mostramos apenas se lead foi criado por equipe (via tramitação)
        // Simplificação: se não há corretor, não filtra (aparece para admin, para gestor aparece)
        // Vamos incluir órfãos para gestor apenas se equipeIds contém solicitante e lead sem dono = considera órfão da equipe
        return true; // temporário: sem vínculo, mostra para todos (será corrigido quando corretor_id for preenchido)
    }

    private boolean isLeadDoCorretor(Lead l, Long corretorId) {
        if (l.getCorretor() != null) return corretorId.equals(l.getCorretor().getId());
        if (l.getCorretor_responsavel() != null) {
            Usuario u = usuarioRepository.findById(corretorId).orElse(null);
            return u != null && l.getCorretor_responsavel().equalsIgnoreCase(u.getNome());
        }
        return false;
    }

    private DashboardGestorDTO.KpiDTO calcularKpis(List<Lead> atuais, List<Lead> anteriores) {
        long leads = atuais.size();
        long negocios = atuais.stream().filter(l -> "contrato".equals(l.getStatus())).count();
        double valor = atuais.stream().filter(l -> "contrato".equals(l.getStatus()))
                .mapToDouble(l -> l.getValorInteresse() != null ? l.getValorInteresse() : 0).sum();
        double taxa = leads > 0 ? (double) negocios / leads * 100 : 0;
        double ticket = negocios > 0 ? valor / negocios : 0;

        // tempo médio até fechamento
        double tempoMedio = calcularTempoMedioGeral(atuais);

        long leadsAnt = anteriores.size();
        long negAnt = anteriores.stream().filter(l -> "contrato".equals(l.getStatus())).count();
        double valorAnt = anteriores.stream().filter(l -> "contrato".equals(l.getStatus()))
                .mapToDouble(l -> l.getValorInteresse() != null ? l.getValorInteresse() : 0).sum();

        double varLeads = leadsAnt > 0 ? (double)(leads - leadsAnt)/leadsAnt*100 : (leads>0?100:0);
        double varNeg = negAnt > 0 ? (double)(negocios - negAnt)/negAnt*100 : (negocios>0?100:0);
        double varValor = valorAnt > 0 ? (valor - valorAnt)/valorAnt*100 : (valor>0?100:0);

        return DashboardGestorDTO.KpiDTO.builder()
                .leadsRecebidos(leads).negociosFechados(negocios).valorVendido(valor)
                .taxaConversao(taxa).ticketMedio(ticket).tempoMedioDias(tempoMedio)
                .leadsRecebidosAnterior(leadsAnt).variacaoLeads(varLeads)
                .negociosAnterior(negAnt).variacaoNegocios(varNeg)
                .valorAnterior(valorAnt).variacaoValor(varValor)
                .build();
    }

    private double calcularTempoMedioGeral(List<Lead> leads) {
        List<Lead> contratos = leads.stream().filter(l -> "contrato".equals(l.getStatus())).toList();
        if (contratos.isEmpty()) return 0;
        double sum = 0;
        int count = 0;
        for (Lead l : contratos) {
            List<Tramitacao> trams = tramitacaoRepository.findByLeadIdOrderByDataMovimentacaoAsc(l.getId());
            if (trams.size() >= 1) {
                LocalDateTime inicio = l.getDataCriacao();
                // última tramitação com status contrato
                Tramitacao fech = trams.stream().filter(t -> "contrato".equals(t.getStatus_atual())).reduce((a,b)->b).orElse(null);
                LocalDateTime fim = fech != null ? fech.getDataMovimentacao() : l.getDataAtualizacao();
                if (inicio != null && fim != null) {
                    long dias = ChronoUnit.DAYS.between(inicio, fim);
                    if (dias < 0) dias = 0;
                    sum += dias;
                    count++;
                }
            }
        }
        return count > 0 ? sum / count : 0;
    }

    private List<DashboardGestorDTO.PipelineEtapaDTO> calcularPipeline(List<Lead> leads) {
        long total = leads.size();
        Map<String, Long> cont = leads.stream().collect(Collectors.groupingBy(l -> l.getStatus() != null ? l.getStatus() : "lead", Collectors.counting()));
        Map<String, Double> valorPorStatus = leads.stream().collect(Collectors.groupingBy(
                l -> l.getStatus() != null ? l.getStatus() : "lead",
                Collectors.summingDouble(l -> l.getValorInteresse() != null ? l.getValorInteresse() : 0)
        ));
        List<DashboardGestorDTO.PipelineEtapaDTO> res = new ArrayList<>();
        for (String s : PIPELINE_ORDER) {
            long q = cont.getOrDefault(s, 0L);
            double perc = total > 0 ? (double) q / total * 100 : 0;
            res.add(DashboardGestorDTO.PipelineEtapaDTO.builder()
                    .status(s).label(PIPELINE_LABELS.getOrDefault(s,s))
                    .quantidade(q).percentual(perc).valorPotencial(valorPorStatus.getOrDefault(s,0.0))
                    .build());
        }
        // incluir descarte separado
        long desc = cont.getOrDefault("descarte", 0L);
        if (desc > 0) {
            res.add(DashboardGestorDTO.PipelineEtapaDTO.builder()
                    .status("descarte").label("Descarte").quantidade(desc)
                    .percentual(total>0?(double)desc/total*100:0).valorPotencial(valorPorStatus.getOrDefault("descarte",0.0)).build());
        }
        return res;
    }

    private List<DashboardGestorDTO.RankingCorretorDTO> calcularRanking(List<Lead> leads, List<Usuario> equipe) {
        // agrupa por corretor (via corretor_id ou corretor_responsavel)
        Map<Long, List<Lead>> porCorretor = new HashMap<>();
        Map<String, List<Lead>> porNomeFallback = new HashMap<>();

        for (Lead l : leads) {
            if (l.getCorretor() != null) {
                porCorretor.computeIfAbsent(l.getCorretor().getId(), k->new ArrayList<>()).add(l);
            } else if (l.getCorretor_responsavel() != null) {
                porNomeFallback.computeIfAbsent(l.getCorretor_responsavel(), k->new ArrayList<>()).add(l);
            }
        }

        // também conta tramitações como contatos
        Map<Long, Long> contatosPorCorretor = new HashMap<>();
        for (Lead l : leads) {
            List<Tramitacao> trams = tramitacaoRepository.findByLeadIdOrderByDataMovimentacaoAsc(l.getId());
            for (Tramitacao t : trams) {
                if (t.getUsuario() != null) {
                    contatosPorCorretor.merge(t.getUsuario().getId(), 1L, Long::sum);
                }
            }
        }

        List<DashboardGestorDTO.RankingCorretorDTO> lista = new ArrayList<>();
        for (Usuario u : equipe) {
            List<Lead> meus = porCorretor.getOrDefault(u.getId(), new ArrayList<>());
            // merge fallback por nome
            if (porNomeFallback.containsKey(u.getNome())) {
                meus.addAll(porNomeFallback.get(u.getNome()));
            }
            long total = meus.size();
            long negocios = meus.stream().filter(l -> "contrato".equals(l.getStatus())).count();
            long propostas = meus.stream().filter(l -> List.of("visita-realizada","pasta","aprovado","contrato").contains(l.getStatus())).count();
            long contatos = contatosPorCorretor.getOrDefault(u.getId(), 0L);
            double conv = total > 0 ? (double) negocios/total*100 : 0;
            String atencao = "ok";
            if (total == 0 || conv < 2) atencao = "critico";
            else if (conv < 5 || total < 3) atencao = "atencao";

            lista.add(DashboardGestorDTO.RankingCorretorDTO.builder()
                    .corretorId(u.getId()).nome(u.getNome())
                    .leads(total).contatos(contatos).propostas(propostas).negocios(negocios)
                    .conversao(conv).statusAtencao(atencao).build());
        }
        lista.sort(Comparator.comparingLong(DashboardGestorDTO.RankingCorretorDTO::getNegocios).reversed()
                .thenComparingLong(DashboardGestorDTO.RankingCorretorDTO::getLeads).reversed());
        // corrige ordem: maior negócios primeiro
        lista.sort((a,b) -> Long.compare(b.getNegocios(), a.getNegocios()));
        return lista;
    }

    private DashboardGestorDTO.MetaEquipeDTO calcularMetas(List<Usuario> equipe, List<Lead> leadsPeriodo, LocalDateTime inicio) {
        YearMonth ym = YearMonth.from(inicio);
        LocalDate mesRef = ym.atDay(1);

        List<Meta> metas = new ArrayList<>();
        for (Usuario u : equipe) {
            metaRepository.findByUsuarioIdAndMesReferencia(u.getId(), mesRef).ifPresent(metas::add);
        }

        int metaContratosTotal = metas.stream().mapToInt(m -> m.getMetaContratos() != null ? m.getMetaContratos() : 0).sum();

        long realizadoContratos = leadsPeriodo.stream().filter(l -> "contrato".equals(l.getStatus())).count();

        double percContr = metaContratosTotal > 0 ? (double) realizadoContratos / metaContratosTotal * 100 : 0;

        List<DashboardGestorDTO.MetaCorretorDTO> porCorretor = new ArrayList<>();
        for (Usuario u : equipe) {
            Meta m = metaRepository.findByUsuarioIdAndMesReferencia(u.getId(), mesRef).orElse(null);
            // filtra leads do corretor
            List<Lead> meus = leadsPeriodo.stream().filter(l -> {
                if (l.getCorretor()!=null) return u.getId().equals(l.getCorretor().getId());
                if (l.getCorretor_responsavel()!=null) return u.getNome().equalsIgnoreCase(l.getCorretor_responsavel());
                return false;
            }).toList();
            long rc = meus.stream().filter(l->"contrato".equals(l.getStatus())).count();
            Integer mc = m != null ? m.getMetaContratos() : null;
            double pc = (mc != null && mc > 0) ? (double) rc / mc * 100 : 0;
            String st = "sem_meta";
            if (m == null) st = "sem_meta";
            else if (pc >= 100) st = "atingida";
            else if (pc >= 70) st = "proxima";
            else if (pc > 0) st = "abaixo";
            else st = "sem_movimentacao";

            porCorretor.add(DashboardGestorDTO.MetaCorretorDTO.builder()
                    .corretorId(u.getId()).nome(u.getNome())
                    .metaContratos(mc)
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

    private DashboardGestorDTO.TempoMedioDTO calcularTempoMedio(List<Lead> leads) {
        // contratos no período
        List<Lead> contratos = leads.stream().filter(l->"contrato".equals(l.getStatus())).toList();
        double mediaGeral = calcularTempoMedioGeral(leads);

        // por corretor
        Map<Long, List<Long>> diasPorCorretor = new HashMap<>();
        for (Lead l : contratos) {
            List<Tramitacao> trams = tramitacaoRepository.findByLeadIdOrderByDataMovimentacaoAsc(l.getId());
            LocalDateTime ini = l.getDataCriacao();
            Tramitacao fech = trams.stream().filter(t->"contrato".equals(t.getStatus_atual())).reduce((a,b)->b).orElse(null);
            LocalDateTime fim = fech != null ? fech.getDataMovimentacao() : l.getDataAtualizacao();
            if (ini==null || fim==null) continue;
            long dias = ChronoUnit.DAYS.between(ini, fim);
            if (dias<0) dias=0;
            Long corrId = l.getCorretor()!=null ? l.getCorretor().getId() : null;
            if (corrId==null && l.getCorretor_responsavel()!=null) {
                // tenta achar id por nome
                Usuario u = usuarioRepository.findAll().stream().filter(x->l.getCorretor_responsavel().equalsIgnoreCase(x.getNome())).findFirst().orElse(null);
                if (u!=null) corrId = u.getId();
            }
            if (corrId!=null) diasPorCorretor.computeIfAbsent(corrId,k->new ArrayList<>()).add(dias);
        }
        List<DashboardGestorDTO.TempoCorretorDTO> porCorr = new ArrayList<>();
        for (Map.Entry<Long, List<Long>> e: diasPorCorretor.entrySet()) {
            double avg = e.getValue().stream().mapToLong(Long::longValue).average().orElse(0);
            Usuario u = usuarioRepository.findById(e.getKey()).orElse(null);
            porCorr.add(DashboardGestorDTO.TempoCorretorDTO.builder()
                    .corretorId(e.getKey()).nome(u!=null?u.getNome():"-")
                    .mediaDias(avg).totalNegocios(e.getValue().size()).build());
        }

        // evolução por mês (últimos 6 meses dos contratos filtrados, mas usa dataCriacao)
        Map<String, List<Long>> porMes = new HashMap<>();
        for (Lead l : contratos) {
            String mes = YearMonth.from(l.getDataCriacao()).toString(); // yyyy-MM
            List<Tramitacao> trams = tramitacaoRepository.findByLeadIdOrderByDataMovimentacaoAsc(l.getId());
            Tramitacao fech = trams.stream().filter(t->"contrato".equals(t.getStatus_atual())).reduce((a,b)->b).orElse(null);
            LocalDateTime fim = fech != null ? fech.getDataMovimentacao() : l.getDataAtualizacao();
            if (l.getDataCriacao()==null || fim==null) continue;
            long dias = ChronoUnit.DAYS.between(l.getDataCriacao(), fim);
            porMes.computeIfAbsent(mes,k->new ArrayList<>()).add(dias);
        }
        List<DashboardGestorDTO.TempoEvolucaoDTO> evol = porMes.entrySet().stream()
                .map(e -> new DashboardGestorDTO.TempoEvolucaoDTO(e.getKey(), e.getValue().stream().mapToLong(Long::longValue).average().orElse(0)))
                .sorted(Comparator.comparing(DashboardGestorDTO.TempoEvolucaoDTO::getMes))
                .toList();

        return DashboardGestorDTO.TempoMedioDTO.builder()
                .mediaGeralDias(mediaGeral).porCorretor(porCorr).evolucao(evol).build();
    }

    private List<DashboardGestorDTO.OrigemDTO> calcularOrigens(List<Lead> leads) {
        long total = leads.size();
        Map<String, List<Lead>> porOrigem = leads.stream()
                .collect(Collectors.groupingBy(l -> l.getOrigem()!=null?l.getOrigem():"sem_origem"));
        List<DashboardGestorDTO.OrigemDTO> res = new ArrayList<>();
        for (Map.Entry<String, List<Lead>> e: porOrigem.entrySet()) {
            long q = e.getValue().size();
            long neg = e.getValue().stream().filter(l->"contrato".equals(l.getStatus())).count();
            long conv = e.getValue().stream().filter(l-> List.of("oportunidade","visita-agendada","visita-realizada","pasta","aprovado","contrato").contains(l.getStatus())).count();
            double taxa = q>0 ? (double)neg/q*100:0;
            res.add(DashboardGestorDTO.OrigemDTO.builder()
                    .origem(e.getKey()).label(e.getKey()).quantidade(q).percentual(total>0?(double)q/total*100:0)
                    .conversoes(conv).negocios(neg).taxaConversao(taxa).build());
        }
        res.sort(Comparator.comparingLong(DashboardGestorDTO.OrigemDTO::getQuantidade).reversed());
        return res;
    }

    private List<DashboardGestorDTO.HistoricoDTO> calcularHistorico(List<Lead> leads, LocalDateTime inicio, LocalDateTime fim) {
        // agrupa por dia se período <= 31 dias, senão por mês
        long dias = ChronoUnit.DAYS.between(inicio, fim);
        boolean porDia = dias <= 31;
        Map<String, List<Lead>> grouped = leads.stream().collect(Collectors.groupingBy(l -> {
            if (porDia) return l.getDataCriacao().toLocalDate().toString();
            return YearMonth.from(l.getDataCriacao()).toString();
        }));
        List<DashboardGestorDTO.HistoricoDTO> res = new ArrayList<>();
        // gerar série completa (inclui zero)
        LocalDate cursor = inicio.toLocalDate();
        LocalDate end = fim.toLocalDate();
        while (!cursor.isAfter(end)) {
            String key = porDia ? cursor.toString() : YearMonth.from(cursor).toString();
            // evita duplicar meses
            if (res.stream().noneMatch(r->r.getPeriodo().equals(key))) {
                List<Lead> lista = grouped.getOrDefault(key, List.of());
                // para agrupamento mensal, precisa somar todos os dias do mês
                if (!porDia) {
                    // já agrupado por mês, ok
                }
                long rec = lista.size();
                long cont = lista.stream().filter(l->"contrato".equals(l.getStatus())).count();
                long desc = lista.stream().filter(l->"descarte".equals(l.getStatus())).count();
                res.add(DashboardGestorDTO.HistoricoDTO.builder().periodo(key).recebidos(rec).contratos(cont).descartes(desc).build());
            }
            cursor = porDia ? cursor.plusDays(1) : cursor.plusMonths(1).withDayOfMonth(1);
            if (!porDia && cursor.isAfter(end)) break;
            if (porDia && cursor.isAfter(end)) break;
        }
        // filtra apenas chaves que existem no grouped para não poluir com zeros excessivos quando porDia=false mas já adicionou
        // Simplifica: retorna ordenado por periodo
        res.sort(Comparator.comparing(DashboardGestorDTO.HistoricoDTO::getPeriodo));
        return res;
    }

    private List<DashboardGestorDTO.ImovelInteresseDTO> calcularImoveis(List<Lead> leads) {
        Map<Long, List<Lead>> porImovel = leads.stream()
                .filter(l -> l.getImovel()!=null)
                .collect(Collectors.groupingBy(l -> l.getImovel().getId()));
        List<DashboardGestorDTO.ImovelInteresseDTO> res = new ArrayList<>();
        for (Map.Entry<Long, List<Lead>> e: porImovel.entrySet()) {
            List<Lead> lista = e.getValue();
            long interessados = lista.size();
            long propostas = lista.stream().filter(l-> List.of("pasta","aprovado","contrato").contains(l.getStatus())).count();
            long negocios = lista.stream().filter(l->"contrato".equals(l.getStatus())).count();
            double conv = interessados>0 ? (double)negocios/interessados*100:0;
            String titulo = lista.get(0).getImovel().getTitulo();
            res.add(DashboardGestorDTO.ImovelInteresseDTO.builder()
                    .imovelId(e.getKey()).titulo(titulo).interessados(interessados).propostas(propostas).negocios(negocios).conversao(conv).build());
        }
        res.sort(Comparator.comparingLong(DashboardGestorDTO.ImovelInteresseDTO::getInteressados).reversed());
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
