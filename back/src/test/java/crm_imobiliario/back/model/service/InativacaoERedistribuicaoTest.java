package crm_imobiliario.back.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;

import crm_imobiliario.back.model.entity.Equipe;
import crm_imobiliario.back.model.entity.Lead;
import crm_imobiliario.back.model.entity.Notificacao;
import crm_imobiliario.back.model.entity.Papel;
import crm_imobiliario.back.model.entity.Usuario;
import crm_imobiliario.back.model.repository.EquipeRepository;
import crm_imobiliario.back.model.repository.LeadRepository;
import crm_imobiliario.back.model.repository.NotificacaoRepository;
import crm_imobiliario.back.model.repository.PapelRepository;
import crm_imobiliario.back.model.repository.UsuarioRepository;
import crm_imobiliario.back.model.service.notificacao.EmailNotificacaoEvent;

/**
 * Inativação de corretor (com e sem gestor), responsável pela redistribuição, notificações
 * (plataforma + e-mail) e atribuição em massa — pela API, como a tela usa.
 */
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:inativacaotestdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driverClassName=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration",
    "crm.sync-no-startup=false"
})
class InativacaoERedistribuicaoTest {

    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime() % 1_000_000_000L);

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PapelRepository papelRepository;
    @Autowired private LeadRepository leadRepository;
    @Autowired private EquipeRepository equipeRepository;
    @Autowired private NotificacaoRepository notificacaoRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TokenService tokenService;
    @Autowired private ApplicationEvents eventos;

    // ------------------------------------------------------------------ cenário

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
        return equipeRepository.save(e);
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

    private String bearer(Usuario u) {
        return "Bearer " + tokenService.gerarAccessToken(u);
    }

    private List<Notificacao> notificacoes(Usuario u) {
        return notificacaoRepository.findByUsuarioIdOrderByDataCriacaoDesc(u.getId());
    }

    private List<EmailNotificacaoEvent> emailsPara(Usuario u) {
        return eventos.stream(EmailNotificacaoEvent.class).filter(e -> u.getEmail().equals(e.destinatarioEmail())).toList();
    }

    private String massa(List<Lead> leads, Usuario destino) {
        String ids = String.join(",", leads.stream().map(l -> String.valueOf(l.getId())).toList());
        return "{\"leadIds\":[" + ids + "],\"novoCorretorId\":" + destino.getId() + "}";
    }

    // ------------------------------------------------------------------ inativação com gestor

    @Test
    void inativarCorretorComGestor_gestorFicaResponsavelERecebeNotificacaoEEmail() throws Exception {
        Usuario admin = usuario("admin", null, null);
        Usuario gestor = usuario("gestor", null, null);
        Equipe eq = equipe(gestor);
        Usuario corretor = usuario("corretor", gestor, eq);
        Lead l1 = lead(corretor, eq, "ATRIBUIDO");
        Lead l2 = lead(corretor, eq, "ATRIBUIDO");

        mockMvc.perform(get("/usuarios/" + corretor.getId() + "/previa-inativacao").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gestorId").value(gestor.getId()))
                .andExpect(jsonPath("$.leadsAtribuidos").value(2))
                .andExpect(jsonPath("$.exigeDecisaoSobreGestor").value(false));

        mockMvc.perform(put("/usuarios/inativar/" + corretor.getId()).header("Authorization", bearer(admin)))
                .andExpect(status().isOk());

        for (Lead l : List.of(l1, l2)) {
            Lead atual = leadRepository.findById(l.getId()).orElseThrow();
            assertThat(atual.getStatusAtribuicao()).isEqualTo("AGUARDANDO_REDISTRIBUICAO");
            assertThat(atual.getResponsavelRedistribuicao().getId()).isEqualTo(gestor.getId());
        }
        List<Notificacao> doGestor = notificacoes(gestor);
        assertThat(doGestor).hasSize(1);
        assertThat(doGestor.get(0).getTipo()).isEqualTo(NotificacaoService.TIPO_REDISTRIBUICAO_PENDENTE);
        assertThat(doGestor.get(0).getTitulo()).isEqualTo("2 leads aguardando redistribuição");
        assertThat(doGestor.get(0).getMensagem()).contains(corretor.getNome()).contains("porque é o gestor").contains("Redistribuição");
        assertThat(doGestor.get(0).getLink()).isEqualTo("/redistribuicao");
        assertThat(emailsPara(gestor)).hasSize(1);
        // somente os envolvidos: o admin que inativou não recebe nada quando há gestor
        assertThat(notificacoes(admin)).isEmpty();
    }

    // ------------------------------------------------------------------ corretor sem gestor

    @Test
    void corretorSemGestorEComLeads_previaIdentificaEInativacaoExigeDecisao() throws Exception {
        Usuario admin = usuario("admin", null, null);
        Usuario corretor = usuario("corretor", null, null);
        Lead l = lead(corretor, null, "ATRIBUIDO");

        mockMvc.perform(get("/usuarios/" + corretor.getId() + "/previa-inativacao").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.gestorId").doesNotExist())
                .andExpect(jsonPath("$.leadsAtribuidos").value(1))
                .andExpect(jsonPath("$.exigeDecisaoSobreGestor").value(true));

        mockMvc.perform(put("/usuarios/inativar/" + corretor.getId()).header("Authorization", bearer(admin)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CORRETOR_SEM_GESTOR"));

        // nada mudou: o corretor continua ativo e com o lead
        assertThat(usuarioRepository.findById(corretor.getId()).orElseThrow().isAtivo()).isTrue();
        assertThat(leadRepository.findById(l.getId()).orElseThrow().getStatusAtribuicao()).isEqualTo("ATRIBUIDO");
    }

    @Test
    void corretorSemGestorESemLeads_naoPrecisaDeDecisao() throws Exception {
        Usuario admin = usuario("admin", null, null);
        Usuario corretor = usuario("corretor", null, null);
        mockMvc.perform(put("/usuarios/inativar/" + corretor.getId()).header("Authorization", bearer(admin)))
                .andExpect(status().isOk());
        assertThat(usuarioRepository.findById(corretor.getId()).orElseThrow().isAtivo()).isFalse();
    }

    @Test
    void vincularGestorAntesDeInativar_gestorPassaASerOResponsavel() throws Exception {
        Usuario admin = usuario("admin", null, null);
        Usuario gestor = usuario("gestor", null, null);
        Equipe eq = equipe(gestor);
        Usuario corretor = usuario("corretor", null, null);
        Lead l = lead(corretor, null, "ATRIBUIDO");

        mockMvc.perform(put("/usuarios/inativar/" + corretor.getId()).param("gestorId", String.valueOf(gestor.getId()))
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk());

        Usuario atualizado = usuarioRepository.findById(corretor.getId()).orElseThrow();
        assertThat(atualizado.isAtivo()).isFalse();
        assertThat(atualizado.getGestor().getId()).isEqualTo(gestor.getId());
        assertThat(atualizado.getEquipe().getId()).isEqualTo(eq.getId());
        assertThat(leadRepository.findById(l.getId()).orElseThrow().getResponsavelRedistribuicao().getId()).isEqualTo(gestor.getId());
        assertThat(notificacoes(gestor)).hasSize(1);
        assertThat(emailsPara(gestor)).hasSize(1);
    }

    @Test
    void vincularComoGestorUmUsuarioQueNaoEGestor_eRecusadoSemInativar() throws Exception {
        Usuario admin = usuario("admin", null, null);
        Usuario outroCorretor = usuario("corretor", null, null);
        Usuario corretor = usuario("corretor", null, null);
        lead(corretor, null, "ATRIBUIDO");

        mockMvc.perform(put("/usuarios/inativar/" + corretor.getId()).param("gestorId", String.valueOf(outroCorretor.getId()))
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_GESTOR"))
                .andExpect(jsonPath("$.fields.gestorId").exists());
        assertThat(usuarioRepository.findById(corretor.getId()).orElseThrow().isAtivo()).isTrue();
    }

    @Test
    void inativarSemGestor_quemInativouFicaResponsavelERecebeNotificacaoEEmail() throws Exception {
        Usuario admin = usuario("admin", null, null);
        Usuario corretor = usuario("corretor", null, null);
        Lead l = lead(corretor, null, "ATRIBUIDO");

        mockMvc.perform(put("/usuarios/inativar/" + corretor.getId()).param("semGestor", "true")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk());

        assertThat(usuarioRepository.findById(corretor.getId()).orElseThrow().isAtivo()).isFalse();
        Lead atual = leadRepository.findById(l.getId()).orElseThrow();
        assertThat(atual.getStatusAtribuicao()).isEqualTo("AGUARDANDO_REDISTRIBUICAO");
        assertThat(atual.getResponsavelRedistribuicao().getId()).isEqualTo(admin.getId());
        List<Notificacao> doAdmin = notificacoes(admin);
        assertThat(doAdmin).hasSize(1);
        assertThat(doAdmin.get(0).getMensagem()).contains("não tinha gestor vinculado");
        assertThat(emailsPara(admin)).hasSize(1);
        assertThat(emailsPara(admin).get(0).acao()).contains("Redistribuição");
    }

    // ------------------------------------------------------------------ atribuição em massa

    @Test
    void atribuicaoEmMassa_todosOsSelecionadosVaoParaOCorretorComUmaNotificacaoEUmEmail() throws Exception {
        Usuario admin = usuario("admin", null, null);
        Usuario gestor = usuario("gestor", null, null);
        Equipe eq = equipe(gestor);
        Usuario destino = usuario("corretor", gestor, eq);
        List<Lead> selecionados = List.of(lead(null, eq, "AGUARDANDO_REDISTRIBUICAO"),
                lead(null, eq, "AGUARDANDO_REDISTRIBUICAO"), lead(null, eq, "AGUARDANDO_REDISTRIBUICAO"));
        Lead naoSelecionado = lead(null, eq, "AGUARDANDO_REDISTRIBUICAO");

        mockMvc.perform(post("/leads/redistribuir-em-massa").header("Authorization", bearer(gestor))
                        .contentType(MediaType.APPLICATION_JSON).content(massa(selecionados, destino)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.atribuidos").value(3));

        for (Lead l : selecionados) {
            Lead atual = leadRepository.findById(l.getId()).orElseThrow();
            assertThat(atual.getCorretor().getId()).isEqualTo(destino.getId());
            assertThat(atual.getStatusAtribuicao()).isEqualTo("ATRIBUIDO");
            assertThat(atual.getResponsavelRedistribuicao()).isNull();
        }
        assertThat(leadRepository.findById(naoSelecionado.getId()).orElseThrow().getStatusAtribuicao()).isEqualTo("AGUARDANDO_REDISTRIBUICAO");

        List<Notificacao> doCorretor = notificacoes(destino);
        assertThat(doCorretor).hasSize(1);
        assertThat(doCorretor.get(0).getTipo()).isEqualTo(NotificacaoService.TIPO_LEADS_RECEBIDOS);
        assertThat(doCorretor.get(0).getTitulo()).isEqualTo("3 novos leads atribuídos a você");
        selecionados.forEach(l -> assertThat(doCorretor.get(0).getMensagem()).contains(l.getNome()));
        assertThat(emailsPara(destino)).hasSize(1);
    }

    @Test
    void atribuicaoEmMassaComLeadQueJaNaoAguarda_naoAtribuiNenhum() throws Exception {
        Usuario admin = usuario("admin", null, null);
        Usuario destino = usuario("corretor", null, null);
        Usuario outro = usuario("corretor", null, null);
        Lead ok = lead(null, null, "AGUARDANDO_REDISTRIBUICAO");
        Lead jaAtribuido = lead(outro, null, "ATRIBUIDO");

        mockMvc.perform(post("/leads/redistribuir-em-massa").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(massa(List.of(ok, jaAtribuido), destino)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LEAD_NOT_WAITING"));

        // tudo ou nada: o lead válido também não foi atribuído
        assertThat(leadRepository.findById(ok.getId()).orElseThrow().getStatusAtribuicao()).isEqualTo("AGUARDANDO_REDISTRIBUICAO");
        assertThat(leadRepository.findById(jaAtribuido.getId()).orElseThrow().getCorretor().getId()).isEqualTo(outro.getId());
        assertThat(notificacoes(destino)).isEmpty();
    }

    @Test
    void atribuicaoEmMassaParaCorretorInativo_eRecusada() throws Exception {
        Usuario admin = usuario("admin", null, null);
        Usuario inativo = usuario("corretor", null, null);
        inativo.setAtivo(false);
        usuarioRepository.save(inativo);
        Lead l = lead(null, null, "AGUARDANDO_REDISTRIBUICAO");

        mockMvc.perform(post("/leads/redistribuir-em-massa").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(massa(List.of(l), inativo)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TARGET_INACTIVE"));
        assertThat(leadRepository.findById(l.getId()).orElseThrow().getCorretor()).isNull();
    }

    @Test
    void atribuicaoEmMassaSemLeadsSelecionados_eRecusada() throws Exception {
        Usuario admin = usuario("admin", null, null);
        Usuario destino = usuario("corretor", null, null);
        mockMvc.perform(post("/leads/redistribuir-em-massa").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"leadIds\":[],\"novoCorretorId\":" + destino.getId() + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.leadIds").value("Selecione pelo menos um lead para atribuir."));
    }

    @Test
    void gestorNaoAtribuiEmMassaLeadsDeOutraEquipe() throws Exception {
        Usuario gestorA = usuario("gestor", null, null);
        Equipe eqA = equipe(gestorA);
        Usuario gestorB = usuario("gestor", null, null);
        Equipe eqB = equipe(gestorB);
        Usuario destino = usuario("corretor", gestorA, eqA);
        Lead deB = lead(null, eqB, "AGUARDANDO_REDISTRIBUICAO");

        mockMvc.perform(post("/leads/redistribuir-em-massa").header("Authorization", bearer(gestorA))
                        .contentType(MediaType.APPLICATION_JSON).content(massa(List.of(deB), destino)))
                .andExpect(status().isForbidden());
        assertThat(leadRepository.findById(deB.getId()).orElseThrow().getCorretor()).isNull();
    }

    @Test
    void corretorNaoPodeUsarAtribuicaoEmMassa() throws Exception {
        Usuario corretor = usuario("corretor", null, null);
        Lead l = lead(null, null, "AGUARDANDO_REDISTRIBUICAO");
        mockMvc.perform(post("/leads/redistribuir-em-massa").header("Authorization", bearer(corretor))
                        .contentType(MediaType.APPLICATION_JSON).content(massa(List.of(l), corretor)))
                .andExpect(status().isForbidden());
    }

    @Test
    void atribuicaoIndividualTambemNotificaCorretorPorEmail() throws Exception {
        Usuario admin = usuario("admin", null, null);
        Usuario destino = usuario("corretor", null, null);
        Lead l = lead(null, null, "AGUARDANDO_REDISTRIBUICAO");
        mockMvc.perform(post("/leads/redistribuir").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"leadId\":" + l.getId() + ",\"novoCorretorId\":" + destino.getId() + "}"))
                .andExpect(status().isOk());
        assertThat(notificacoes(destino)).hasSize(1);
        assertThat(notificacoes(destino).get(0).getTitulo()).isEqualTo("Novo lead atribuído a você");
        assertThat(emailsPara(destino)).hasSize(1);
    }
}
