package crm_imobiliario.back.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import crm_imobiliario.back.model.entity.Lead;
import crm_imobiliario.back.model.entity.Papel;
import crm_imobiliario.back.model.entity.Usuario;
import crm_imobiliario.back.model.repository.LeadRepository;
import crm_imobiliario.back.model.repository.PapelRepository;
import crm_imobiliario.back.model.repository.UsuarioRepository;
import crm_imobiliario.back.model.service.TokenService;
import jakarta.servlet.http.Cookie;

/**
 * Regressão das correções de segurança: autorização por papel no backend (antes só o front
 * escondia as telas), troca de senha obrigatória imposta pela API, limite de tentativas por e-mail,
 * tokens fora do corpo da resposta e logout revogando o refresh token.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:segurancatestdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driverClassName=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration",
    "crm.sync-no-startup=false"
})
class SegurancaEndpointsTest {

    private static final String SENHA = "Senha@Teste1";
    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime() % 1_000_000_000L);

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PapelRepository papelRepository;
    @Autowired private LeadRepository leadRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TokenService tokenService;

    private Usuario criarUsuario(String papel, boolean trocarSenha) {
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
        u.setSenha(passwordEncoder.encode(SENHA));
        u.setAtivo(true);
        u.setTrocarSenha(trocarSenha);
        u.setPapel(p);
        u.setTokenVersion(0);
        return usuarioRepository.save(u);
    }

    private String bearer(Usuario u) {
        return "Bearer " + tokenService.gerarAccessToken(u);
    }

    private MvcResult login(String email, String senha) throws Exception {
        return mockMvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"senha\":\"" + senha + "\"}")).andReturn();
    }

    private String valorCookie(MvcResult r, String nome) {
        List<String> headers = r.getResponse().getHeaders("Set-Cookie");
        return headers.stream().filter(h -> h.startsWith(nome + "=") && !h.startsWith(nome + "=;"))
                .map(h -> h.substring(nome.length() + 1, h.indexOf(';')))
                .findFirst().orElse(null);
    }

    // ---------------------------------------------------------------- autorização por papel

    @Test
    void corretorNaoPodeCadastrarUsuario() throws Exception {
        Usuario corretor = criarUsuario("corretor", false);
        mockMvc.perform(post("/usuarios/cadastrar").header("Authorization", bearer(corretor))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void corretorNaoPodePromoverASiMesmo() throws Exception {
        Usuario corretor = criarUsuario("corretor", false);
        mockMvc.perform(put("/usuarios/atualizar/" + corretor.getId()).header("Authorization", bearer(corretor))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/usuarios/inativar/" + corretor.getId()).header("Authorization", bearer(corretor)))
                .andExpect(status().isForbidden());
    }

    @Test
    void corretorNaoPodeCriarPapelNemListarUsuarios() throws Exception {
        Usuario corretor = criarUsuario("corretor", false);
        mockMvc.perform(post("/papel/novo").header("Authorization", bearer(corretor))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"papel\":\"superuser\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/usuarios").header("Authorization", bearer(corretor)))
                .andExpect(status().isForbidden());
    }

    @Test
    void corretorAcessaOProprioPerfil() throws Exception {
        Usuario corretor = criarUsuario("corretor", false);
        mockMvc.perform(get("/usuarios/me").header("Authorization", bearer(corretor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(corretor.getEmail()));
    }

    @Test
    void adminEGestorListamUsuarios() throws Exception {
        mockMvc.perform(get("/usuarios").header("Authorization", bearer(criarUsuario("admin", false))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/usuarios").header("Authorization", bearer(criarUsuario("gestor", false))))
                .andExpect(status().isOk());
    }

    @Test
    void corretorNaoDefineMetaDeGestorParaOutroUsuario() throws Exception {
        Usuario corretor = criarUsuario("corretor", false);
        Usuario outro = criarUsuario("corretor", false);
        String body = "{\"usuarioId\":" + outro.getId() + ",\"mesReferencia\":\"2026-10-01\",\"metaContratos\":3}";
        mockMvc.perform(post("/dashboard/gestor/metas").header("Authorization", bearer(corretor))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/metas").header("Authorization", bearer(corretor))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void gestorSoDefineMetaDosProprioSubordinados() throws Exception {
        Usuario gestor = criarUsuario("gestor", false);
        Usuario subordinado = criarUsuario("corretor", false);
        subordinado.setGestor(gestor);
        usuarioRepository.save(subordinado);
        Usuario deOutraEquipe = criarUsuario("corretor", false);

        mockMvc.perform(post("/dashboard/gestor/metas").header("Authorization", bearer(gestor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioId\":" + subordinado.getId() + ",\"mesReferencia\":\"2026-10-01\",\"metaContratos\":3}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/dashboard/gestor/metas").header("Authorization", bearer(gestor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioId\":" + deOutraEquipe.getId() + ",\"mesReferencia\":\"2026-10-01\",\"metaContratos\":3}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void corretorNaoVeHistoricoDeResponsaveisDeLeadDeOutro() throws Exception {
        Usuario dono = criarUsuario("corretor", false);
        Usuario intruso = criarUsuario("corretor", false);
        Lead lead = Lead.builder().nome("Cliente").telefone("43999990000").status("lead")
                .ativo(true).corretor(dono).statusAtribuicao("ATRIBUIDO").build();
        lead = leadRepository.save(lead);
        mockMvc.perform(get("/leads/" + lead.getId() + "/historico-responsaveis").header("Authorization", bearer(intruso)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/leads/" + lead.getId() + "/historico-responsaveis").header("Authorization", bearer(dono)))
                .andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- troca de senha obrigatória

    @Test
    void usuarioComTrocaDeSenhaPendenteSoAcessaATrocaDeSenha() throws Exception {
        Usuario novo = criarUsuario("corretor", true);
        mockMvc.perform(get("/leads").header("Authorization", bearer(novo)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        mockMvc.perform(get("/usuarios/me").header("Authorization", bearer(novo)))
                .andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- login, rate limit, cookies

    @Test
    void loginNaoDevolveTokenNoCorpoEUsaCookiesHttpOnly() throws Exception {
        Usuario u = criarUsuario("corretor", false);
        MvcResult r = login(u.getEmail(), SENHA);
        assertEquals(200, r.getResponse().getStatus());
        String body = r.getResponse().getContentAsString();
        assertFalse(body.contains("\"token\""), "o token não pode ir no corpo (ficaria acessível ao JavaScript)");
        List<String> cookies = r.getResponse().getHeaders("Set-Cookie");
        assertTrue(cookies.stream().anyMatch(c -> c.startsWith("accessToken=") && c.contains("HttpOnly")));
        assertTrue(cookies.stream().anyMatch(c -> c.startsWith("refreshToken=") && c.contains("Path=/auth;") && c.contains("HttpOnly")));
    }

    @Test
    void limitePorEmailBloqueiaAntesDeAutenticarMesmoComSenhaCorreta() throws Exception {
        Usuario u = criarUsuario("corretor", false);
        for (int i = 0; i < 5; i++) {
            assertEquals(401, login(u.getEmail(), "errada").getResponse().getStatus());
        }
        MvcResult bloqueado = login(u.getEmail(), SENHA);
        assertEquals(429, bloqueado.getResponse().getStatus());
        assertNotNull(bloqueado.getResponse().getHeader("Retry-After"));
    }

    @Test
    void logoutRevogaORefreshTokenNoServidor() throws Exception {
        Usuario u = criarUsuario("corretor", false);
        MvcResult r = login(u.getEmail(), SENHA);
        String refresh = valorCookie(r, "refreshToken");
        String access = valorCookie(r, "accessToken");
        assertNotNull(refresh);

        mockMvc.perform(post("/auth/logout").cookie(new Cookie("accessToken", access), new Cookie("refreshToken", refresh)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/auth/refresh").cookie(new Cookie("refreshToken", refresh)))
                .andExpect(status().isUnauthorized());
        // access token do logout também vai para a blacklist
        mockMvc.perform(get("/usuarios/me").cookie(new Cookie("accessToken", access)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshRotacionaOToken() throws Exception {
        Usuario u = criarUsuario("corretor", false);
        String refresh = valorCookie(login(u.getEmail(), SENHA), "refreshToken");

        MvcResult r = mockMvc.perform(post("/auth/refresh").cookie(new Cookie("refreshToken", refresh)))
                .andExpect(status().isOk()).andReturn();
        assertNotNull(valorCookie(r, "refreshToken"));
        // o refresh usado foi revogado: reutilizá-lo falha
        mockMvc.perform(post("/auth/refresh").cookie(new Cookie("refreshToken", refresh)))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- validação de campos (erro estruturado por campo)

    @Test
    void cadastroDeUsuarioDevolveTodosOsCamposInvalidosDeUmaVez() throws Exception {
        Usuario admin = criarUsuario("admin", false);
        long papelId = papelRepository.findAll().stream().filter(x -> "corretor".equals(x.getPapel())).findFirst().orElseThrow().getId();
        // antes o CPF só era verificado depois que os demais campos passavam: o usuário corrigia o nome
        // e só então descobria que o CPF também estava errado
        String corpo = "{\"nome\":\"\",\"email\":\"ana@empresa\",\"cpf\":\"111.111.111-11\",\"genero\":\"F\","
                + "\"telefone\":\"(20) 99999-9999\",\"dataNascimento\":\"2999-01-01\",\"papelId\":" + papelId + "}";
        mockMvc.perform(post("/usuarios/cadastrar").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields.nome").value("Informe o nome do usuário."))
                .andExpect(jsonPath("$.fields.email").value("O e-mail informado não é válido. Use o formato nome@dominio.com."))
                .andExpect(jsonPath("$.fields.cpf").value("O CPF informado não é válido. Confira os 11 dígitos."))
                .andExpect(jsonPath("$.fields.telefone").value("O DDD 20 não existe. Confira o código de área."))
                .andExpect(jsonPath("$.fields.dataNascimento").value("A data de nascimento deve ser anterior a hoje."));
    }

    @Test
    void cadastroDeLeadComTelefoneEValorInvalidosApontaOsCampos() throws Exception {
        Usuario admin = criarUsuario("admin", false);
        String corpo = "{\"nome\":\"Carlos\",\"telefone\":\"4312\",\"origem\":\"espontaneo\",\"historico\":\"primeiro_contato\","
                + "\"status\":\"lead\",\"valorInteresse\":-10}";
        mockMvc.perform(post("/leads/cadastrar").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.telefone").value("Informe o telefone com DDD (10 ou 11 dígitos)."))
                .andExpect(jsonPath("$.fields.valorInteresse").value("O valor de interesse não pode ser negativo."));
    }

    @Test
    void periodoComDataInicialDepoisDaFinalERecusado() throws Exception {
        Usuario gestor = criarUsuario("gestor", false);
        mockMvc.perform(get("/dashboard/gestor").param("inicio", "2026-10-31").param("fim", "2026-10-01")
                        .header("Authorization", bearer(gestor)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PERIOD"))
                .andExpect(jsonPath("$.fields.inicio").value("A data inicial deve ser igual ou anterior à data final."));
    }

    // ---------------------------------------------------------------- login de usuário inativado

    @Test
    void usuarioInativadoComSenhaCorretaRecebeAvisoDeInativacaoESemSessao() throws Exception {
        Usuario u = criarUsuario("corretor", false);
        u.setAtivo(false);
        usuarioRepository.save(u);

        MvcResult r = login(u.getEmail(), SENHA);
        // antes: a recusa virava InternalAuthenticationServiceException e a tela mostrava "Sua sessão expirou"
        assertEquals(401, r.getResponse().getStatus());
        String corpo = r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(corpo.contains("\"code\":\"AUTH_USER_DISABLED\""), corpo);
        assertTrue(corpo.contains("inativado"), corpo);
        assertNull(valorCookie(r, "accessToken"), "usuário inativo não pode receber sessão");
        assertNull(valorCookie(r, "refreshToken"));
    }

    @Test
    void usuarioInativadoComSenhaErradaNaoRevelaAInativacao() throws Exception {
        Usuario u = criarUsuario("corretor", false);
        u.setAtivo(false);
        usuarioRepository.save(u);

        MvcResult r = login(u.getEmail(), "SenhaErrada@1");
        assertEquals(401, r.getResponse().getStatus());
        assertTrue(r.getResponse().getContentAsString().contains("AUTH_INVALID_CREDENTIALS"));
    }

    @Test
    void emailInexistenteDizEmailOuSenhaIncorretos() throws Exception {
        MvcResult r = login("ninguem" + SEQ.incrementAndGet() + "@teste.com", SENHA);
        assertEquals(401, r.getResponse().getStatus());
        // antes: "Sua sessão expirou. Faça login novamente."
        assertTrue(r.getResponse().getContentAsString().contains("AUTH_INVALID_CREDENTIALS"));
    }
}
