package crm_imobiliario.back.model.service;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import crm_imobiliario.back.model.entity.Lead;
import crm_imobiliario.back.model.entity.Papel;
import crm_imobiliario.back.model.entity.Tramitacao;
import crm_imobiliario.back.model.entity.Usuario;
import crm_imobiliario.back.model.repository.LeadRepository;
import crm_imobiliario.back.model.repository.PapelRepository;
import crm_imobiliario.back.model.repository.TramitacaoRepository;
import crm_imobiliario.back.model.repository.UsuarioRepository;

/**
 * Gráfico "Cadastrados x Contratos fechados" do Dashboard: escopo por papel (gestor escolhe entre os
 * próprios clientes e a equipe) e contratos pela regra única do ContagemFunil.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:timelinetestdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driverClassName=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration",
    "crm.sync-no-startup=false"
})
class DashboardTimelineTest {

    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime() % 1_000_000_000L);

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PapelRepository papelRepository;
    @Autowired private LeadRepository leadRepository;
    @Autowired private TramitacaoRepository tramitacaoRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TokenService tokenService;
    @Autowired private JdbcTemplate jdbc;

    private final YearMonth mesAnterior = YearMonth.now().minusMonths(1);

    private LocalDateTime dia(int d) {
        return mesAnterior.atDay(d).atTime(10, 0);
    }

    private Usuario usuario(String papel, Usuario gestor) {
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
        u.setGestor(gestor);
        return usuarioRepository.save(u);
    }

    /** passos = {status, data (null = agora)}... */
    private Lead lead(Usuario corretor, long valor, LocalDateTime cadastro, Object... passos) {
        Lead l = new Lead();
        l.setNome("Lead " + SEQ.incrementAndGet());
        l.setTelefone("43999990000");
        l.setOrigem("espontaneo");
        l.setHistorico("novo");
        l.setValorInteresse(valor);
        l.setCorretor(corretor);
        l.setStatusAtribuicao("ATRIBUIDO");
        String atual = (String) passos[passos.length - 2];
        l.setStatus(atual);
        l.setAtivo(!"descarte".equals(atual));
        l = leadRepository.save(l);
        if (cadastro != null) jdbc.update("UPDATE lead SET data_criacao = ? WHERE id = ?", Timestamp.valueOf(cadastro), l.getId());
        String anterior = null;
        for (int i = 0; i < passos.length; i += 2) {
            Tramitacao t = new Tramitacao();
            t.setLead(l);
            t.setUsuario(corretor);
            t.setStatus_anterior(anterior);
            t.setStatus_atual((String) passos[i]);
            t = tramitacaoRepository.save(t);
            if (passos[i + 1] != null) jdbc.update("UPDATE tramitacao_status SET data_movimentacao = ? WHERE id = ?", Timestamp.valueOf((LocalDateTime) passos[i + 1]), t.getId());
            anterior = (String) passos[i];
        }
        return l;
    }

    private String bearer(Usuario u) {
        return "Bearer " + tokenService.gerarAccessToken(u);
    }

    private org.springframework.test.web.servlet.ResultActions timeline(Usuario u, String escopo) throws Exception {
        var req = get("/dashboard/corretor/timeline")
                .param("inicio", mesAnterior.atDay(1).toString())
                .param("fim", YearMonth.now().atEndOfMonth().toString())
                .header("Authorization", bearer(u));
        if (escopo != null) req = req.param("escopo", escopo);
        return mockMvc.perform(req).andExpect(status().isOk());
    }

    @Test
    void escopoPorPapelEContratosPelaRegraUnica() throws Exception {
        Usuario gestor = usuario("gestor", null);
        Usuario c1 = usuario("corretor", gestor);
        Usuario fora = usuario("corretor", null);
        Usuario admin = usuario("admin", null);

        // do próprio gestor: cadastrado e fechado no mês passado
        lead(gestor, 100_000, dia(3), "lead", dia(3), "contrato", dia(10));
        // do corretor: fechado no mês passado e reaberto (Pasta) neste mês -> conta no mês passado
        lead(c1, 100_000, dia(5), "lead", dia(5), "contrato", dia(20), "pasta", null);
        // do corretor: cadastrado e fechado neste mês
        lead(c1, 100_000, null, "lead", null, "contrato", null);
        // do corretor: cadastrado neste mês e descartado (não é contrato)
        lead(c1, 100_000, null, "lead", null, "descarte", null);
        // fora da equipe
        lead(fora, 100_000, null, "lead", null, "contrato", null);

        // corretor: só os dele; pedir "equipe" não muda nada
        for (String escopo : new String[] { null, "equipe" }) {
            timeline(c1, escopo)
                    .andExpect(jsonPath("$.escopo").value("meus"))
                    .andExpect(jsonPath("$.podeVerEquipe").value(false))
                    .andExpect(jsonPath("$.timeline", hasSize(2)))
                    .andExpect(jsonPath("$.timeline[0].leadsRecebidos").value(1))
                    .andExpect(jsonPath("$.timeline[0].contratosFechados").value(1))
                    .andExpect(jsonPath("$.timeline[1].leadsRecebidos").value(2))
                    .andExpect(jsonPath("$.timeline[1].contratosFechados").value(1));
        }

        // gestor: padrão = só os clientes dele
        timeline(gestor, null)
                .andExpect(jsonPath("$.escopo").value("meus"))
                .andExpect(jsonPath("$.podeVerEquipe").value(true))
                .andExpect(jsonPath("$.timeline[*].leadsRecebidos", contains(1, 0)))
                .andExpect(jsonPath("$.timeline[*].contratosFechados", contains(1, 0)));

        // gestor: equipe = ele + os corretores dele (sem o de fora)
        timeline(gestor, "equipe")
                .andExpect(jsonPath("$.escopo").value("equipe"))
                .andExpect(jsonPath("$.timeline[*].leadsRecebidos", contains(2, 2)))
                .andExpect(jsonPath("$.timeline[*].contratosFechados", contains(2, 1)));

        // cards do Dashboard (mês atual) com a mesma regra e o mesmo escopo
        mockMvc.perform(get("/dashboard/corretor/resumo").param("escopo", "equipe").header("Authorization", bearer(gestor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.escopo").value("equipe"))
                .andExpect(jsonPath("$.clientesNoMes").value(3))          // reaberto (andamento), contrato e descarte do mês
                .andExpect(jsonPath("$.emAndamento").value(1))            // o reaberto
                .andExpect(jsonPath("$.emAndamentoMesAnterior").value(0)) // no fim do mês passado os dois estavam em contrato
                .andExpect(jsonPath("$.contratos").value(1))
                .andExpect(jsonPath("$.descartes").value(1))
                // funil: o reaberto (até Aprovado, mesmo tendo tido contrato) e o contrato do mês; sem o
                // descarte e sem o contrato do mês passado
                .andExpect(jsonPath("$.funil[*].etapa", contains("lead", "oportunidade", "visita-agendada", "visita-realizada", "pasta", "aprovado", "contrato")))
                .andExpect(jsonPath("$.funil[*].quantidade", contains(2, 2, 2, 2, 2, 2, 1)))
                .andExpect(jsonPath("$.valorContratos").value(100_000.0))
                .andExpect(jsonPath("$.conversao").value(closeTo(33.33, 0.01)))
                .andExpect(jsonPath("$.contratosMesAnterior").value(2));
        mockMvc.perform(get("/dashboard/corretor/resumo").header("Authorization", bearer(gestor)))
                .andExpect(jsonPath("$.escopo").value("meus"))
                .andExpect(jsonPath("$.clientesNoMes").value(0))
                .andExpect(jsonPath("$.contratosMesAnterior").value(1));
        mockMvc.perform(get("/dashboard/corretor/resumo").param("escopo", "equipe").header("Authorization", bearer(c1)))
                .andExpect(jsonPath("$.escopo").value("meus"))
                .andExpect(jsonPath("$.clientesNoMes").value(3))
                .andExpect(jsonPath("$.contratos").value(1));

        // admin: todos
        timeline(admin, null)
                .andExpect(jsonPath("$.escopo").value("todos"))
                .andExpect(jsonPath("$.podeVerEquipe").value(false))
                .andExpect(jsonPath("$.timeline[*].leadsRecebidos", contains(2, 3)))
                .andExpect(jsonPath("$.timeline[*].contratosFechados", contains(2, 2)));
    }
}
