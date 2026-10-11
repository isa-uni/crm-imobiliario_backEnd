package crm_imobiliario.back.model.service;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import crm_imobiliario.back.model.entity.Lead;
import crm_imobiliario.back.model.entity.Papel;
import crm_imobiliario.back.model.entity.ProporcaoFunil;
import crm_imobiliario.back.model.entity.Tramitacao;
import crm_imobiliario.back.model.entity.Usuario;
import crm_imobiliario.back.model.repository.LeadRepository;
import crm_imobiliario.back.model.repository.PapelRepository;
import crm_imobiliario.back.model.repository.ProporcaoFunilRepository;
import crm_imobiliario.back.model.repository.TramitacaoRepository;
import crm_imobiliario.back.model.repository.UsuarioRepository;

/**
 * Regra de contagem do Relógio de Vendas: clientes em andamento de qualquer mês + contratos e
 * descartes do mês atual; somente os clientes do próprio usuário; proporções ajustáveis pelo admin.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:relogiotestdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driverClassName=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration",
    "crm.sync-no-startup=false"
})
class RelogioVendasTest {

    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime() % 1_000_000_000L);

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PapelRepository papelRepository;
    @Autowired private LeadRepository leadRepository;
    @Autowired private TramitacaoRepository tramitacaoRepository;
    @Autowired private ProporcaoFunilRepository proporcaoRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TokenService tokenService;
    @Autowired private JdbcTemplate jdbc;

    /** Um dia no meio do mês anterior: "cadastrado / movimentado no mês passado". */
    private final LocalDateTime mesPassado = YearMonth.now().minusMonths(1).atDay(10).atTime(10, 0);

    @AfterEach
    void restaurarProporcoes() {
        proporcaoRepository.save(ProporcaoFunil.padrao());
    }

    private Usuario usuario(String papel) {
        long n = SEQ.incrementAndGet();
        Papel p = papelRepository.findAll().stream().filter(x -> papel.equals(x.getPapel())).findFirst().orElseThrow();
        Usuario u = new Usuario();
        u.setNome(papel + " " + n);
        u.setEmail(papel + n + "@teste.com");
        u.setCpf(String.format("%011d", n));
        u.setMatricula("T" + n);
        u.setGenero("M");
        u.setTelefone("(43) 99999-0000");
        u.setDataNascimento(LocalDate.of(1990, 1, 1));
        u.setSenha(passwordEncoder.encode("Senha@Teste1"));
        u.setAtivo(true);
        u.setTrocarSenha(false);
        u.setPapel(p);
        u.setTokenVersion(0);
        return usuarioRepository.save(u);
    }

    /** Lead do corretor com o histórico de status informado; cada passo é {status, data} (data null = agora). */
    private Lead lead(Usuario corretor, LocalDateTime cadastro, Object... passos) {
        Lead l = new Lead();
        l.setNome("Lead " + SEQ.incrementAndGet());
        l.setTelefone("43999990000");
        l.setOrigem("espontaneo");
        l.setHistorico("novo");
        l.setValorInteresse(100000L);
        l.setCorretor(corretor);
        l.setStatusAtribuicao("ATRIBUIDO");
        String anterior = null;
        String atual = (String) passos[passos.length - 2];
        l.setStatus(atual);
        l.setAtivo(!"descarte".equals(atual));
        l = leadRepository.save(l);
        if (cadastro != null) jdbc.update("UPDATE lead SET data_criacao = ? WHERE id = ?", Timestamp.valueOf(cadastro), l.getId());
        for (int i = 0; i < passos.length; i += 2) {
            Tramitacao t = new Tramitacao();
            t.setLead(l);
            t.setStatus_anterior(anterior);
            t.setStatus_atual((String) passos[i]);
            t = tramitacaoRepository.save(t);
            LocalDateTime quando = (LocalDateTime) passos[i + 1];
            if (quando != null) jdbc.update("UPDATE tramitacao_status SET data_movimentacao = ? WHERE id = ?", Timestamp.valueOf(quando), t.getId());
            anterior = (String) passos[i];
        }
        return l;
    }

    private String bearer(Usuario u) {
        return "Bearer " + tokenService.gerarAccessToken(u);
    }

    @Test
    void contaEmAndamentoDeQualquerMesEContratosEDescartesSoDoMes() throws Exception {
        Usuario corretor = usuario("corretor");
        Usuario outro = usuario("corretor");
        String mesAnt = YearMonth.from(mesPassado).toString();
        String mesAtual = YearMonth.now().toString();

        // parado em Pasta desde o mês passado: entra
        lead(corretor, mesPassado, "lead", mesPassado, "oportunidade", mesPassado, "visita-agendada", mesPassado,
                "visita-realizada", mesPassado, "pasta", mesPassado);
        // contrato fechado no mês passado: não entra
        lead(corretor, mesPassado, "lead", mesPassado, "contrato", mesPassado);
        // descartado no mês passado: não entra
        lead(corretor, mesPassado, "lead", mesPassado, "oportunidade", mesPassado, "descarte", mesPassado);
        // cadastrado este mês e pulou de Lead para Aprovado: entra em todas as etapas até Aprovado
        lead(corretor, null, "lead", null, "aprovado", null);
        // cadastrado este mês e descartado este mês em Visita agendada: entra até Visita agendada + 1 descarte
        lead(corretor, null, "lead", null, "oportunidade", null, "visita-agendada", null, "descarte", null);
        // cadastrado no mês passado, contrato fechado este mês: entra em todas
        lead(corretor, mesPassado, "lead", mesPassado, "pasta", mesPassado, "contrato", null);
        // de outro corretor: nunca entra
        lead(outro, null, "lead", null, "aprovado", null);

        mockMvc.perform(get("/relogio-vendas").header("Authorization", bearer(corretor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mesReferencia").value(mesAtual))
                .andExpect(jsonPath("$.clientesNaContagem").value(4))
                .andExpect(jsonPath("$.proporcoes.leads").value(33))
                // Leads: os 4 que entram, 2 cadastrados no mês passado e 2 neste
                .andExpect(jsonPath("$.etapas[0].etapa").value("lead"))
                .andExpect(jsonPath("$.etapas[0].realizado").value(4))
                .andExpect(jsonPath("$.etapas[0].emAndamento").value(2))
                .andExpect(jsonPath("$.etapas[0].contratosNoMes").value(1))
                .andExpect(jsonPath("$.etapas[0].descartadosNoMes").value(1))
                .andExpect(jsonPath("$.etapas[0].porMesCadastro[?(@.mes == '" + mesAnt + "')].quantidade").value(contains(2)))
                .andExpect(jsonPath("$.etapas[0].porMesCadastro[?(@.mes == '" + mesAtual + "')].quantidade").value(contains(2)))
                // Visita agendada: pasta parada, aprovado (salto), descartado ali, contrato
                .andExpect(jsonPath("$.etapas[2].realizado").value(4))
                .andExpect(jsonPath("$.etapas[2].descartesNestaEtapa").value(1))
                // Visita realizada: o descartado em Visita agendada não chegou aqui
                .andExpect(jsonPath("$.etapas[3].realizado").value(3))
                .andExpect(jsonPath("$.etapas[4].realizado").value(3))
                .andExpect(jsonPath("$.etapas[5].realizado").value(2))
                .andExpect(jsonPath("$.etapas[6].etapa").value("contrato"))
                .andExpect(jsonPath("$.etapas[6].realizado").value(1))
                .andExpect(jsonPath("$.etapas[6].porMesCadastro[0].mes").value(mesAnt));
    }

    @Test
    void descarteDesfeitoNoMesmoMesNaoContaComoDescarte() throws Exception {
        Usuario corretor = usuario("corretor");
        lead(corretor, null, "lead", null, "oportunidade", null, "descarte", null, "oportunidade", null);

        mockMvc.perform(get("/relogio-vendas").header("Authorization", bearer(corretor)))
                .andExpect(jsonPath("$.etapas[1].realizado").value(1))
                .andExpect(jsonPath("$.etapas[1].emAndamento").value(1))
                .andExpect(jsonPath("$.etapas[*].descartesNestaEtapa", everyItem(is(0))));
    }

    @Test
    void gestorVeSoOsProprioClientes() throws Exception {
        Usuario gestor = usuario("gestor");
        Usuario corretorDaEquipe = usuario("corretor");
        corretorDaEquipe.setGestor(gestor);
        usuarioRepository.save(corretorDaEquipe);
        lead(corretorDaEquipe, null, "lead", null);
        lead(gestor, null, "lead", null, "oportunidade", null);

        mockMvc.perform(get("/relogio-vendas").header("Authorization", bearer(gestor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientesNaContagem").value(1))
                .andExpect(jsonPath("$.etapas[1].realizado").value(1));
    }

    @Test
    void somenteAdminAjustaProporcoesEElasSeguemAOrdemDoFunil() throws Exception {
        Usuario admin = usuario("admin");
        Usuario corretor = usuario("corretor");
        String valido = "{\"leads\":40,\"oportunidades\":25,\"visitasAgendadas\":12,\"visitasRealizadas\":9,\"pastas\":5,\"aprovados\":2}";

        mockMvc.perform(put("/relogio-vendas/proporcoes").header("Authorization", bearer(corretor))
                        .contentType(MediaType.APPLICATION_JSON).content(valido))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/relogio-vendas/proporcoes").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"leads\":40,\"oportunidades\":25,\"visitasAgendadas\":30,\"visitasRealizadas\":9,\"pastas\":5,\"aprovados\":2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.visitasAgendadas").exists());

        mockMvc.perform(put("/relogio-vendas/proporcoes").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(valido))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leads").value(40));

        mockMvc.perform(get("/relogio-vendas").header("Authorization", bearer(corretor)))
                .andExpect(jsonPath("$.proporcoes.leads").value(40))
                .andExpect(jsonPath("$.proporcoes.aprovados").value(2));
    }
}
