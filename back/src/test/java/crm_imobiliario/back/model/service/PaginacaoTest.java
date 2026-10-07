package crm_imobiliario.back.model.service;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import crm_imobiliario.back.model.entity.Lead;
import crm_imobiliario.back.model.entity.Papel;
import crm_imobiliario.back.model.entity.Usuario;
import crm_imobiliario.back.model.repository.LeadRepository;
import crm_imobiliario.back.model.repository.PapelRepository;
import crm_imobiliario.back.model.repository.UsuarioRepository;

/**
 * Paginação no servidor das telas de Usuários e Leads: formato da resposta (content, totalElements,
 * totalPages, number, size), filtros aplicados antes de paginar e compatibilidade da lista sem page/size.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:paginacaotestdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driverClassName=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration",
    "crm.sync-no-startup=false"
})
class PaginacaoTest {

    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime() % 1_000_000_000L);

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PapelRepository papelRepository;
    @Autowired private LeadRepository leadRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TokenService tokenService;

    private Usuario usuario(String papel, String nome, boolean ativo) {
        long n = SEQ.incrementAndGet();
        Papel p = papelRepository.findAll().stream().filter(x -> papel.equals(x.getPapel())).findFirst().orElseThrow();
        Usuario u = new Usuario();
        u.setNome(nome);
        u.setEmail(papel + n + "@teste.com");
        u.setCpf(String.format("%011d", n));
        u.setMatricula("T" + n);
        u.setGenero("M");
        u.setTelefone("(43) 99999-0000");
        u.setDataNascimento(LocalDate.of(1990, 1, 1));
        u.setSenha(passwordEncoder.encode("Senha@Teste1"));
        u.setAtivo(ativo);
        u.setTrocarSenha(false);
        u.setPapel(p);
        u.setTokenVersion(0);
        return usuarioRepository.save(u);
    }

    private Lead lead(Usuario corretor, String nome, String status) {
        Lead l = new Lead();
        l.setNome(nome);
        l.setTelefone("43999990000");
        l.setOrigem("espontaneo");
        l.setHistorico("primeiro_contato");
        l.setStatus(status);
        l.setValorInteresse(100000L);
        l.setAtivo(true);
        l.setCorretor(corretor);
        l.setStatusAtribuicao("ATRIBUIDO");
        return leadRepository.save(l);
    }

    private String bearer(Usuario u) {
        return "Bearer " + tokenService.gerarAccessToken(u);
    }

    // ------------------------------------------------------------------ usuários

    @Test
    void usuarios_paginaComTotaisEFiltrosAplicadosNoServidor() throws Exception {
        Usuario admin = usuario("admin", "Admin Paginacao", true);
        String prefixo = "Pag" + SEQ.incrementAndGet();
        for (int i = 1; i <= 5; i++) usuario("corretor", prefixo + " Corretor " + i, true);
        usuario("corretor", prefixo + " Corretor Inativo", false);
        usuario("gestor", prefixo + " Gestor", true);

        // 7 usuários com o prefixo, 3 por página → 3 páginas; a última tem 1 registro
        mockMvc.perform(get("/usuarios").param("page", "2").param("size", "3").param("search", prefixo)
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements").value(7))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.number").value(2))
                .andExpect(jsonPath("$.size").value(3));

        // filtros (papel + status) entram antes da paginação, não só na página carregada
        mockMvc.perform(get("/usuarios").param("page", "0").param("size", "10").param("search", prefixo)
                        .param("papel", "corretor").param("status", "ativos")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.content[*].papel", everyItem(is("corretor"))))
                .andExpect(jsonPath("$.content[*].ativo", everyItem(is(true))));

        mockMvc.perform(get("/usuarios").param("page", "0").param("size", "10").param("search", prefixo)
                        .param("status", "inativos").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].nome").value(prefixo + " Corretor Inativo"));

        // ordenação padrão por nome: páginas consecutivas não repetem usuários
        mockMvc.perform(get("/usuarios").param("page", "0").param("size", "3").param("search", prefixo)
                        .header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.content[0].nome").value(prefixo + " Corretor 1"))
                .andExpect(jsonPath("$.content[2].nome").value(prefixo + " Corretor 3"));
    }

    @Test
    void usuarios_tamanhoDePaginaLimitadoA100() throws Exception {
        Usuario admin = usuario("admin", "Admin Limite", true);
        mockMvc.perform(get("/usuarios").param("page", "0").param("size", "5000").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void usuarios_semPageSize_continuaDevolvendoListaEFiltraPorPapel() throws Exception {
        Usuario admin = usuario("admin", "Admin Lista", true);
        usuario("gestor", "Gestor Lista", true);

        mockMvc.perform(get("/usuarios").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        mockMvc.perform(get("/usuarios").param("papel", "gestor,admin").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[*].papel", everyItem(oneOf("gestor", "admin"))));
    }

    @Test
    void usuarios_resumoContaTodosOsUsuarios() throws Exception {
        Usuario admin = usuario("admin", "Admin Resumo", true);
        long total = usuarioRepository.count();
        long inativos = usuarioRepository.countByAtivo(false);

        mockMvc.perform(get("/usuarios/resumo").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(total))
                .andExpect(jsonPath("$.inativos").value(inativos))
                .andExpect(jsonPath("$.ativos").value(total - inativos))
                .andExpect(jsonPath("$.porPapel.admin").isNumber());
    }

    @Test
    void usuarios_resumoNaoLiberadoParaCorretor() throws Exception {
        Usuario corretor = usuario("corretor", "Corretor Sem Acesso", true);
        mockMvc.perform(get("/usuarios/resumo").header("Authorization", bearer(corretor)))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ leads

    @Test
    void leads_paginaNoServidorEResumoContaArquivados() throws Exception {
        Usuario corretor = usuario("corretor", "Corretor Leads Paginados", true);
        for (int i = 1; i <= 4; i++) lead(corretor, "Lead Ativo " + i, "lead");
        lead(corretor, "Lead Contrato", "contrato");
        lead(corretor, "Lead Descarte", "descarte");

        mockMvc.perform(get("/leads").param("page", "1").param("size", "4").header("Authorization", bearer(corretor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.number").value(1));

        mockMvc.perform(get("/leads").param("page", "0").param("size", "10").param("status", "archived")
                        .header("Authorization", bearer(corretor)))
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get("/leads/resumo").header("Authorization", bearer(corretor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(6))
                .andExpect(jsonPath("$.ativos").value(4))
                .andExpect(jsonPath("$.arquivados").value(2));
    }
}
