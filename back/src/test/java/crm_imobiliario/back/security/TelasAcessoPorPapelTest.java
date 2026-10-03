package crm_imobiliario.back.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

import crm_imobiliario.back.model.entity.Papel;
import crm_imobiliario.back.model.entity.Usuario;
import crm_imobiliario.back.model.repository.PapelRepository;
import crm_imobiliario.back.model.repository.UsuarioRepository;
import crm_imobiliario.back.model.service.TokenService;

/**
 * Cada tela do frontend depende de um conjunto de chamadas à API (ver crm-imobiliario_frontEnd/service).
 * Este teste garante, por papel, que essas chamadas respondem como o menu promete: se a tela aparece
 * para o papel, todas as chamadas dela devem funcionar (200); se não aparece, a API recusa (403).
 *
 * Telas cobertas: Perfil, Empreendimentos, Dashboard do Gestor, Equipes e Redistribuição.
 * Regras de menu equivalentes: crm-imobiliario_frontEnd/lib/acesso.ts.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:telastestdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driverClassName=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration",
    "crm.sync-no-startup=false"
})
class TelasAcessoPorPapelTest {

    private static final AtomicLong SEQ = new AtomicLong(7_000_000_000L + System.nanoTime() % 1_000_000_000L);

    // chamadas feitas por cada tela ao carregar (service/*.ts do frontend)
    private static final String[] PERFIL = { "/usuarios/me" };
    private static final String[] EMPREENDIMENTOS = { "/api/v1/empreendimentos/cards?page=0&size=12&sort=nome,asc" };
    private static final String[] DASHBOARD_GESTOR = { "/dashboard/gestor", "/dashboard/gestor/equipe" };
    private static final String[] EQUIPES = { "/equipes", "/usuarios" };
    private static final String[] REDISTRIBUICAO = {
        "/leads/aguardando-redistribuicao?includeResumo=true&page=0&size=50", "/equipes", "/usuarios" };

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PapelRepository papelRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TokenService tokenService;

    private Usuario admin;
    private Usuario gestor;
    private Usuario corretor;

    @BeforeEach
    void setUp() {
        admin = criarUsuario("admin", null);
        gestor = criarUsuario("gestor", null);
        corretor = criarUsuario("corretor", gestor);
    }

    private Usuario criarUsuario(String papel, Usuario chefe) {
        long n = SEQ.incrementAndGet();
        Papel p = papelRepository.findAll().stream().filter(x -> papel.equals(x.getPapel())).findFirst().orElseThrow();
        Usuario u = new Usuario();
        u.setNome(papel + " " + n);
        u.setEmail(papel + n + "@telas.com");
        u.setCpf(String.format("%011d", n % 100_000_000_000L));
        u.setMatricula("TL" + n);
        u.setGenero("F");
        u.setTelefone("(43) 99999-0000");
        u.setDataNascimento(LocalDate.of(1992, 3, 4));
        u.setSenha(passwordEncoder.encode("Senha@Teste1"));
        u.setAtivo(true);
        u.setTrocarSenha(false);
        u.setPapel(p);
        u.setGestor(chefe);
        u.setTokenVersion(0);
        return usuarioRepository.save(u);
    }

    private void verificar(Usuario usuario, String[] chamadas, ResultMatcher esperado) throws Exception {
        String bearer = "Bearer " + tokenService.gerarAccessToken(usuario);
        for (String url : chamadas) {
            mockMvc.perform(get(url).header("Authorization", bearer)).andExpect(esperado);
        }
    }

    // ------------------------------------------------------------------ telas para todos os papéis

    @Test
    void perfilAbreParaTodosOsPapeis() throws Exception {
        verificar(admin, PERFIL, status().isOk());
        verificar(gestor, PERFIL, status().isOk());
        verificar(corretor, PERFIL, status().isOk());
    }

    @Test
    void empreendimentosAbreParaTodosOsPapeis() throws Exception {
        verificar(admin, EMPREENDIMENTOS, status().isOk());
        verificar(gestor, EMPREENDIMENTOS, status().isOk());
        verificar(corretor, EMPREENDIMENTOS, status().isOk());
    }

    // ------------------------------------------------------------------ telas de admin/gestor

    @Test
    void dashboardGestorAbreParaAdminEGestor() throws Exception {
        verificar(admin, DASHBOARD_GESTOR, status().isOk());
        verificar(gestor, DASHBOARD_GESTOR, status().isOk());
    }

    @Test
    void dashboardGestorRecusadoParaCorretor() throws Exception {
        verificar(corretor, DASHBOARD_GESTOR, status().isForbidden());
    }

    @Test
    void redistribuicaoAbreParaAdminEGestor() throws Exception {
        verificar(admin, REDISTRIBUICAO, status().isOk());
        verificar(gestor, REDISTRIBUICAO, status().isOk());
    }

    @Test
    void redistribuicaoRecusadaParaCorretor() throws Exception {
        verificar(corretor, new String[] { REDISTRIBUICAO[0] }, status().isForbidden());
    }

    // ------------------------------------------------------------------ tela de admin

    @Test
    void equipesAbreParaAdmin() throws Exception {
        verificar(admin, EQUIPES, status().isOk());
    }

    @Test
    void equipesNaoExpoeListaDeUsuariosAoCorretor() throws Exception {
        verificar(corretor, new String[] { "/usuarios" }, status().isForbidden());
    }

    // ------------------------------------------------------------------ sessão

    @Test
    void semSessaoTodasAsTelasRespondem401() throws Exception {
        for (String[] tela : new String[][] { PERFIL, EMPREENDIMENTOS, DASHBOARD_GESTOR, EQUIPES, REDISTRIBUICAO }) {
            for (String url : tela) {
                mockMvc.perform(get(url)).andExpect(status().isUnauthorized());
            }
        }
    }

    @Test
    void tokenDeUsuarioInativadoNaoAbreNenhumaTela() throws Exception {
        String bearer = "Bearer " + tokenService.gerarAccessToken(gestor);
        gestor.setAtivo(false);
        usuarioRepository.save(gestor);
        mockMvc.perform(get(PERFIL[0]).header("Authorization", bearer)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(DASHBOARD_GESTOR[0]).header("Authorization", bearer)).andExpect(status().isUnauthorized());
    }
}
