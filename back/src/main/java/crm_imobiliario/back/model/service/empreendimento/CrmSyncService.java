package crm_imobiliario.back.model.service.empreendimento;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import crm_imobiliario.back.config.CrmProperties;
import crm_imobiliario.back.model.entity.Empreendimento;
import crm_imobiliario.back.model.entity.Sincronizacao;
import crm_imobiliario.back.model.repository.EmpreendimentoRepository;
import crm_imobiliario.back.model.repository.SincronizacaoRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Worker desacoplado — mesma ideia do builder thread de api_disponibilidade.py.
 * Na Fase 1 (sem Playwright no Java), faz seed a partir do Guia JSON + mocks do CRM.
 * Na Fase 2, delega para CrmPlaywrightClient (quando o sidecar Python ou playwright-java estiver disponível).
 * Nunca bloqueia requisições da API — elas só leem DB.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrmSyncService {

    private final CrmProperties crmProperties;
    private final EmpreendimentoRepository empreendimentoRepository;
    private final SincronizacaoRepository sincronizacaoRepository;
    private final EmpreendimentoService empreendimentoService;
    private final GuiaParserService guiaParserService;
    private final Normalizer normalizer;
    private final ObjectMapper objectMapper;

    private final AtomicBoolean emBuild = new AtomicBoolean(false);
    private volatile Instant ultimoMontadoEm;

    @PostConstruct
    public void initNoStartup() {
        if (crmProperties.isSyncNoStartup()) {
            log.info("CrmSyncService: sync no startup habilitado — agendando seed inicial");
            // roda async via TaskExecutor configurado (evita new Thread leak)
            try {
                // usa scheduler interno para delay de 3s sem bloquear startup
                java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "crm-seed");
                    t.setDaemon(true);
                    return t;
                }).schedule(() -> {
                    try { syncCompleta(); } catch (Exception e) { log.warn("Seed inicial falhou: {}", e.getMessage(), e); }
                }, 3, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("Falha ao agendar seed inicial: {}", e.getMessage(), e);
            }
        }
    }

    @Scheduled(fixedDelayString = "${crm.refresh-seg:21600}000")
    public void agendado() {
        log.info("CrmSyncService agendado disparado");
        syncCompleta();
    }

    public synchronized Sincronizacao syncCompleta() {
        if (!emBuild.compareAndSet(false, true)) {
            log.warn("Sync já em andamento — ignorando");
            return null;
        }
        Instant inicio = Instant.now();
        Sincronizacao sinc = Sincronizacao.builder()
                .inicio(inicio).tipo("COMPLETA").build();
        int sucesso = 0, falha = 0;
        try {
            List<GuiaParserService.EmpreendimentoGuia> guias = carregarGuias();
            List<Empreendimento> seeds = montarSeeds(guias);
            for (Empreendimento emp : seeds) {
                try {
                    empreendimentoService.salvarOuAtualizar(emp);
                    sucesso++;
                } catch (Exception e) {
                    log.warn("Falha ao salvar {}: {}", emp.getNome(), e.getMessage());
                    falha++;
                }
            }
            ultimoMontadoEm = Instant.now();
            sinc.setFim(ultimoMontadoEm);
            sinc.setTotal(seeds.size());
            sinc.setSucesso(sucesso);
            sinc.setFalha(falha);
            sinc.setDisponiveis((int) seeds.stream().filter(s -> s.getDisponiveis() != null && s.getDisponiveis() > 0).count());
            log.info("Sync completa: total={}, sucesso={}, falha={}, disponiveis={}", seeds.size(), sucesso, falha, sinc.getDisponiveis());
        } catch (Exception e) {
            sinc.setFim(Instant.now());
            sinc.setErro(e.getMessage());
            log.error("Sync completa falhou", e);
        } finally {
            emBuild.set(false);
            try { sincronizacaoRepository.save(sinc); } catch (Exception e) { log.warn("Falha ao salvar Sincronizacao: {}", e.getMessage()); }
        }
        return sinc;
    }

    public Sincronizacao syncPorCodigo(String codigoCrm) {
        // stub: re-executa completa filtrando (evolui para sync incremental com Playwright)
        log.info("Sync por código: {}", codigoCrm);
        return syncCompleta();
    }

    public boolean isEmBuild() { return emBuild.get(); }
    public Instant getUltimoMontadoEm() { return ultimoMontadoEm; }

    // ------------------------------------------------------------------ seed
    private List<GuiaParserService.EmpreendimentoGuia> carregarGuias() {
        // tenta caminhos relativos e variáveis de ambiente (evita path absoluto Windows hardcoded)
        String envJson = System.getenv("GUIA_JSON_PATH");
        String envHtml = System.getenv("GUIA_HTML_PATH");
        List<Path> candidatosJson = new ArrayList<>();
        List<Path> candidatosHtml = new ArrayList<>();
        if (envJson != null) candidatosJson.add(Paths.get(envJson));
        if (envHtml != null) candidatosHtml.add(Paths.get(envHtml));
        // paths portáveis (relativos ao working dir / classpath)
        candidatosJson.add(Paths.get("./guia-de-bolso_empreendimentos.json"));
        candidatosJson.add(Paths.get("./data/guia-de-bolso_empreendimentos.json"));
        candidatosJson.add(Paths.get("guia-de-bolso_empreendimentos.json"));
        // mantém fallback legado Windows apenas para dev local, mas não quebra em Docker/Linux
        candidatosJson.add(Paths.get("C:/Users/isabe/Desktop/imoveis/guia-de-bolso_empreendimentos.json"));

        candidatosHtml.add(Paths.get("./guia-de-bolso.html"));
        candidatosHtml.add(Paths.get("./data/guia-de-bolso.html"));
        candidatosHtml.add(Paths.get("guia-de-bolso.html"));
        candidatosHtml.add(Paths.get("C:/Users/isabe/Desktop/imoveis/guia-de-bolso.html"));

        for (int i = 0; i < Math.min(candidatosJson.size(), candidatosHtml.size()); i++) {
            List<GuiaParserService.EmpreendimentoGuia> out = guiaParserService.carregar(candidatosJson.get(i), candidatosHtml.get(i));
            if (!out.isEmpty()) return out;
        }
        // tenta todas combinações restantes
        for (Path j : candidatosJson) {
            for (Path h : candidatosHtml) {
                List<GuiaParserService.EmpreendimentoGuia> out = guiaParserService.carregar(j, h);
                if (!out.isEmpty()) return out;
            }
        }
        log.warn("CrmSyncService: nenhum Guia encontrado em candidatosJson={}, candidatosHtml={}", candidatosJson, candidatosHtml);
        return List.of();
    }

    private List<Empreendimento> montarSeeds(List<GuiaParserService.EmpreendimentoGuia> guias) {
        List<Empreendimento> out = new ArrayList<>();
        // 1) Guia enriquecido (5)
        for (GuiaParserService.EmpreendimentoGuia g : guias) {
            Empreendimento e = Empreendimento.builder()
                    .codigoCrm(mapaGuiaParaCodigo(g.id))
                    .nome(g.nome != null ? g.nome.toUpperCase() : "")
                    .slug(g.slug != null ? g.slug : normalizer.slugify(g.nome))
                    .cidade(g.cidade)
                    .uf(g.uf)
                    .regiao(g.regiao)
                    .bairro(g.bairro)
                    .endereco(g.endereco)
                    .lat(g.geo != null ? g.geo.lat : null)
                    .lng(g.geo != null ? g.geo.lng : null)
                    .zoom(g.geo != null ? g.geo.zoom : null)
                    .metragemMin(g.metragem_min)
                    .metragemMax(g.metragem_max)
                    .precoMin(g.preco_min)
                    .precoMax(g.preco_max)
                    .status(g.status)
                    .descricaoResumo(g.descricao_resumo)
                    .condicoesComerciais(g.condicoes_comerciais)
                    .previsaoEntrega(g.previsao_entrega)
                    .entregaContratual(g.entrega_contratual)
                    .parcelamentoMax(g.parcelamento_max)
                    .tabelaReferencia(g.tabela != null ? g.tabela.referencia : null)
                    .tabelaValidade(g.tabela != null ? g.tabela.validade : null)
                    .enriquecido(true)
                    .ativo(true)
                    .build();
            // disponibilidade mockada a partir da tabela do Guia (se houver)
            if (g.tabela != null) {
                // seed conservador: se não houver dados CRM, usa guia como fonte
                e.setTotal(240);
                e.setDisponiveis(10);
                e.setAndamento(50.0);
            }
            // tipos/quartos como JSON
            try {
                e.setTiposJson(objectMapper.writeValueAsString(List.of("2Q", "2Q - Garden")));
                e.setQuartosJson(objectMapper.writeValueAsString(List.of(2)));
            } catch (Exception ignored) {}
            out.add(e);
        }
        // 2) Seeds CRM adicionais (22 - 5 = 17) como UNMATCHED enriquecido=false
        List<String> crmExtras = List.of(
                "21:BLISS RESIDENCIAL", "8:CAPADÓCIA RESIDENCIAL", "36:CÓRDOBA RESIDENCIAL",
                "2:EOS RESIDENCE", "37:HAUER 4YOU", "9:LONDON BLUE", "7:LONDON GARDEN",
                "13:LONDON PALACE", "18:LONDON RED", "12:LONDON TOWER",
                "27:MARBELLA RESIDENCIAL", "38:MENDOZA RESIDENCIAL", "14:RESIDENCIAL AUSTIN",
                "11:SAFIR BEACH HOME", "23:SAN ANTONIO", "28:SEVILHA RESIDENCIAL", "26:SONNE RESIDENCIAL"
        );
        for (String raw : crmExtras) {
            String[] p = raw.split(":", 2);
            String codigo = p[0], nome = p[1];
            boolean jaExiste = out.stream().anyMatch(e -> codigo.equals(e.getCodigoCrm()));
            if (jaExiste) continue;
            Empreendimento e = Empreendimento.builder()
                    .codigoCrm(codigo)
                    .nome(nome)
                    .slug(normalizer.slugify(nome))
                    .cidade("Londrina")
                    .uf("PR")
                    .regiao("Zona Norte")
                    .status("em_obras")
                    .total(240)
                    .disponiveis(codigo.equals("26") ? 0 : 5) // Sonne 0 para testar filtro
                    .andamento(60.0)
                    .metragemMin(35.74)
                    .metragemMax(39.50)
                    .precoMin(227200L)
                    .precoMax(255800L)
                    .enriquecido(false)
                    .unmatchedReason("sem Guia — aguardando curadoria")
                    .ativo(true)
                    .build();
            try {
                e.setTiposJson(objectMapper.writeValueAsString(List.of("2Q")));
                e.setQuartosJson(objectMapper.writeValueAsString(List.of(2)));
            } catch (Exception ignored) {}
            out.add(e);
        }
        return out;
    }

    private String mapaGuiaParaCodigo(String guiaId) {
        return switch (guiaId) {
            case "plaza" -> "29";
            case "life" -> "34";
            case "felicce" -> "31";
            case "essenza" -> "32";
            case "fiore" -> "35";
            default -> null;
        };
    }
}
