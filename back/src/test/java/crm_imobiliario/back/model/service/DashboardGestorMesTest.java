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
 * Dashboard do Gestor por mês: eventos pela data do evento, carteira pela regra do Relógio de Vendas
 * e, em mês passado, a carteira como estava no fim daquele mês.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:dashmestestdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driverClassName=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration",
    "crm.sync-no-startup=false"
})
class DashboardGestorMesTest {

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

    @Test
    void mesAtualEMesAnteriorReconstruidoNoFimDoMes() throws Exception {
        Usuario gestor = usuario("gestor", null);
        Usuario c1 = usuario("corretor", gestor);
        Usuario c2 = usuario("corretor", gestor);
        Usuario foraDaEquipe = usuario("corretor", null);

        // A: cadastrado mês passado, parado em Pasta
        lead(c1, 100_000, dia(5), "lead", dia(5), "oportunidade", dia(6), "pasta", dia(20));
        // B: cadastrado e fechado no mês passado
        lead(c1, 200_000, dia(5), "lead", dia(5), "contrato", dia(25));
        // C: cadastrado mês passado, Aprovado no mês passado, contrato fechado neste mês
        lead(c1, 300_000, dia(8), "lead", dia(8), "aprovado", dia(10), "contrato", null);
        // D: cadastrado neste mês e descartado neste mês, quando estava em Oportunidade
        lead(c1, 50_000, null, "lead", null, "oportunidade", null, "descarte", null);
        // E: cadastrado e descartado no mês passado, ainda como Lead
        lead(c1, 50_000, dia(12), "lead", dia(12), "descarte", dia(15));
        // F: de outro corretor da equipe, cadastrado neste mês
        lead(c2, 80_000, null, "lead", null);
        // fora da equipe do gestor
        lead(foraDaEquipe, 999_000, null, "lead", null, "aprovado", null);

        // ---- mês atual
        mockMvc.perform(get("/dashboard/gestor").header("Authorization", bearer(gestor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mesReferencia").value(YearMonth.now().toString()))
                .andExpect(jsonPath("$.kpis.leadsRecebidos").value(2))              // D, F
                .andExpect(jsonPath("$.kpis.negociosFechados").value(1))            // C, fechado neste mês
                .andExpect(jsonPath("$.kpis.valorVendido").value(300_000.0))
                .andExpect(jsonPath("$.kpis.leadsRecebidosAnterior").value(4))      // A, B, C, E
                .andExpect(jsonPath("$.kpis.negociosAnterior").value(1))            // B
                .andExpect(jsonPath("$.kpis.taxaConversao").value(25.0))            // 1 contrato ÷ 4 na contagem
                .andExpect(jsonPath("$.pipeline", hasSize(7)))
                .andExpect(jsonPath("$.pipeline[0].quantidade").value(4))           // A, C, D, F
                .andExpect(jsonPath("$.pipeline[1].quantidade").value(3))           // A, C, D
                .andExpect(jsonPath("$.pipeline[1].descartesNestaEtapa").value(1))  // D
                .andExpect(jsonPath("$.pipeline[4].quantidade").value(2))           // Pasta: A, C (pulou)
                .andExpect(jsonPath("$.pipeline[4].valorPotencial").value(100_000.0)) // A parado em Pasta
                .andExpect(jsonPath("$.pipeline[6].quantidade").value(1))           // Fechado: C
                .andExpect(jsonPath("$.pipeline[0].porMesCadastro[?(@.mes == '" + mesAnterior + "')].quantidade").value(contains(2)))
                .andExpect(jsonPath("$.metas.realizadoContratos").value(1))
                .andExpect(jsonPath("$.rankingCorretores[?(@.corretorId == " + c1.getId() + ")].leads").value(contains(3)))
                .andExpect(jsonPath("$.rankingCorretores[?(@.corretorId == " + c2.getId() + ")].leads").value(contains(1)))
                .andExpect(jsonPath("$.tempoMedio.mediaGeralDias").value(greaterThan(0.0)));

        // ---- mês anterior, como estava no último dia dele
        mockMvc.perform(get("/dashboard/gestor").param("mes", mesAnterior.toString()).header("Authorization", bearer(gestor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mesReferencia").value(mesAnterior.toString()))
                .andExpect(jsonPath("$.kpis.leadsRecebidos").value(4))              // A, B, C, E
                .andExpect(jsonPath("$.kpis.negociosFechados").value(1))            // B (C ainda não tinha fechado)
                .andExpect(jsonPath("$.kpis.valorVendido").value(200_000.0))
                .andExpect(jsonPath("$.pipeline[0].quantidade").value(4))           // A, B, C, E (D e F não existiam)
                .andExpect(jsonPath("$.pipeline[0].descartesNestaEtapa").value(1))  // E, descartado como Lead
                .andExpect(jsonPath("$.pipeline[5].quantidade").value(2))           // Aprovado: B, C
                .andExpect(jsonPath("$.pipeline[6].quantidade").value(1))           // Fechado: B
                .andExpect(jsonPath("$.historico", hasSize(mesAnterior.lengthOfMonth())))
                .andExpect(jsonPath("$.historico[24].contratos").value(1))          // dia 25: B
                .andExpect(jsonPath("$.historico[14].descartes").value(1));         // dia 15: E
    }

    @Test
    void comFiltroDoCorretorOPipelineBateComORelogioDeVendasDele() throws Exception {
        Usuario gestor = usuario("gestor", null);
        Usuario c1 = usuario("corretor", gestor);
        lead(c1, 100_000, dia(5), "lead", dia(5), "oportunidade", dia(6), "pasta", dia(20));
        lead(c1, 300_000, dia(8), "lead", dia(8), "aprovado", dia(10), "contrato", null);
        lead(c1, 50_000, null, "lead", null, "oportunidade", null, "descarte", null);
        lead(c1, 50_000, dia(12), "lead", dia(12), "descarte", dia(15));

        mockMvc.perform(get("/relogio-vendas").header("Authorization", bearer(c1)))
                .andExpect(jsonPath("$.etapas[*].realizado", contains(3, 3, 2, 2, 2, 1, 1)));
        mockMvc.perform(get("/dashboard/gestor").param("corretorId", String.valueOf(c1.getId())).header("Authorization", bearer(gestor)))
                .andExpect(jsonPath("$.pipeline[*].quantidade", contains(3, 3, 2, 2, 2, 1, 1)));
    }

    @Test
    void administradorNaoEntraNosCalculos() throws Exception {
        Usuario admin = usuario("admin", null);
        Usuario corretor = usuario("corretor", null);
        lead(admin, 900_000, null, "lead", null, "contrato", null);      // cliente com o admin
        lead(corretor, 100_000, null, "lead", null, "oportunidade", null);

        // ranking e metas: só quem não é admin
        mockMvc.perform(get("/dashboard/gestor").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rankingCorretores[?(@.corretorId == " + admin.getId() + ")]", hasSize(0)))
                .andExpect(jsonPath("$.rankingCorretores[?(@.corretorId == " + corretor.getId() + ")]", hasSize(1)))
                .andExpect(jsonPath("$.metas.porCorretor[?(@.corretorId == " + admin.getId() + ")]", hasSize(0)));
        // os clientes do admin não contam em nenhum número do painel
        mockMvc.perform(get("/dashboard/gestor").param("corretorId", String.valueOf(admin.getId())).header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.pipeline[0].quantidade").value(0))
                .andExpect(jsonPath("$.kpis.valorVendido").value(0.0));
        mockMvc.perform(get("/dashboard/gestor").param("corretorId", String.valueOf(corretor.getId())).header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.pipeline[*].quantidade", contains(1, 1, 0, 0, 0, 0, 0)));
        // lista de corretores do filtro também sem o admin
        mockMvc.perform(get("/dashboard/gestor/equipe").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$[?(@.id == " + admin.getId() + ")]", hasSize(0)))
                .andExpect(jsonPath("$[?(@.id == " + corretor.getId() + ")]", hasSize(1)));
    }
}
