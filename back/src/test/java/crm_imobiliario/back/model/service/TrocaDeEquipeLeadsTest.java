package crm_imobiliario.back.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import crm_imobiliario.back.model.entity.Equipe;
import crm_imobiliario.back.model.entity.Lead;
import crm_imobiliario.back.model.entity.Papel;
import crm_imobiliario.back.model.entity.Usuario;
import crm_imobiliario.back.model.repository.EquipeRepository;
import crm_imobiliario.back.model.repository.LeadRepository;
import crm_imobiliario.back.model.repository.PapelRepository;
import crm_imobiliario.back.model.repository.UsuarioRepository;

/** Ao trocar o gestor do corretor (e, com ele, a equipe), os leads ATRIBUIDO dele acompanham a nova equipe. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:trocaequipetestdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driverClassName=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration",
    "crm.sync-no-startup=false"
})
class TrocaDeEquipeLeadsTest {

    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime() % 1_000_000_000L);

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PapelRepository papelRepository;
    @Autowired private LeadRepository leadRepository;
    @Autowired private EquipeRepository equipeRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TokenService tokenService;

    private Usuario usuario(String papel, Usuario gestor, Equipe equipe) {
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
        u.setEquipe(equipe);
        return usuarioRepository.save(u);
    }

    private Equipe equipe(Usuario gestor) {
        Equipe e = Equipe.builder().nome("Equipe " + SEQ.incrementAndGet()).gestor(gestor).ativo(true)
                .dataCriacao(LocalDateTime.now()).dataAtualizacao(LocalDateTime.now()).build();
        Equipe salva = equipeRepository.save(e);
        gestor.setEquipe(salva);
        usuarioRepository.save(gestor);
        return salva;
    }

    private Lead lead(Usuario corretor, Equipe equipe, String statusAtribuicao) {
        Lead l = new Lead();
        l.setNome("Lead " + SEQ.incrementAndGet());
        l.setTelefone("43999990000");
        l.setOrigem("espontaneo");
        l.setHistorico("primeiro_contato");
        l.setStatus("lead");
        l.setValorInteresse(100000L);
        l.setAtivo(true);
        l.setCorretor(corretor);
        l.setEquipe(equipe);
        l.setStatusAtribuicao(statusAtribuicao);
        return leadRepository.save(l);
    }

    @Test
    void inativarCorretorSemGestorVinculandoGestor_leadsFicamNaEquipeDoGestor() throws Exception {
        Usuario admin = usuario("admin", null, null);
        Usuario gestor = usuario("gestor", null, null);
        Equipe eq = equipe(gestor);
        Usuario corretor = usuario("corretor", null, null);
        Lead l1 = lead(corretor, null, "ATRIBUIDO");
        Lead l2 = lead(corretor, null, "ATRIBUIDO");

        mockMvc.perform(put("/usuarios/inativar/" + corretor.getId()).param("gestorId", String.valueOf(gestor.getId()))
                        .header("Authorization", "Bearer " + tokenService.gerarAccessToken(admin)))
                .andExpect(status().isOk());

        Usuario atual = usuarioRepository.findById(corretor.getId()).orElseThrow();
        assertThat(atual.getEquipe().getId()).isEqualTo(eq.getId());
        for (Lead l : new Lead[] { l1, l2 }) {
            Lead salvo = leadRepository.findById(l.getId()).orElseThrow();
            assertThat(salvo.getEquipe().getId()).isEqualTo(eq.getId());
            assertThat(salvo.getStatusAtribuicao()).isEqualTo("AGUARDANDO_REDISTRIBUICAO");
        }
    }

    @Test
    void trocarGestorDoCorretor_movePraNovaEquipeSoOsLeadsAtribuidos() throws Exception {
        Usuario admin = usuario("admin", null, null);
        Usuario gestorA = usuario("gestor", null, null);
        Equipe eqA = equipe(gestorA);
        Usuario gestorB = usuario("gestor", null, null);
        Equipe eqB = equipe(gestorB);
        Usuario corretor = usuario("corretor", gestorA, eqA);
        Usuario outro = usuario("corretor", gestorA, eqA);

        Lead atribuido1 = lead(corretor, eqA, "ATRIBUIDO");
        Lead atribuido2 = lead(corretor, eqA, "ATRIBUIDO");
        Lead semEquipe = lead(corretor, null, "ATRIBUIDO");
        Lead aguardando = lead(corretor, eqA, "AGUARDANDO_REDISTRIBUICAO");
        Lead doOutro = lead(outro, eqA, "ATRIBUIDO");

        String body = "{\"nome\":\"" + corretor.getNome() + "\",\"email\":\"" + corretor.getEmail()
                + "\",\"cpf\":\"" + corretor.getCpf() + "\",\"genero\":\"M\",\"telefone\":\"(43) 99999-0000\","
                + "\"dataNascimento\":\"1990-01-01\",\"papelId\":" + corretor.getPapel().getId()
                + ",\"gestorId\":" + gestorB.getId() + "}";

        mockMvc.perform(put("/usuarios/atualizar/" + corretor.getId())
                        .header("Authorization", "Bearer " + tokenService.gerarAccessToken(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        assertThat(usuarioRepository.findById(corretor.getId()).orElseThrow().getEquipe().getId()).isEqualTo(eqB.getId());
        for (Lead l : new Lead[] { atribuido1, atribuido2, semEquipe }) {
            assertThat(leadRepository.findById(l.getId()).orElseThrow().getEquipe().getId()).isEqualTo(eqB.getId());
        }
        // pendente de redistribuição e leads de outro corretor não são tocados
        assertThat(leadRepository.findById(aguardando.getId()).orElseThrow().getEquipe().getId()).isEqualTo(eqA.getId());
        assertThat(leadRepository.findById(doOutro.getId()).orElseThrow().getEquipe().getId()).isEqualTo(eqA.getId());
    }
}
