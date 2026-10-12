package crm_imobiliario.back.model.service;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
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
    @Autowired private EquipeRepository equipeRepository;
    @Autowired private JdbcTemplate jdbc;

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

    @Test
    void leads_mesesComCadastroEFiltroPorMesEspecifico() throws Exception {
        Usuario corretor = usuario("corretor", "Corretor Meses", true);
        Usuario outro = usuario("corretor", "Corretor Outro Mes", true);
        java.time.YearMonth atual = java.time.YearMonth.now();
        java.time.YearMonth anterior = atual.minusMonths(1);
        lead(corretor, "Cliente Mes Atual", "lead");
        Lead doMesAnterior = lead(corretor, "Cliente Mes Anterior", "oportunidade");
        jdbc.update("UPDATE lead SET data_criacao = ? WHERE id = ?", Timestamp.valueOf(anterior.atDay(15).atTime(10, 0)), doMesAnterior.getId());
        // de outro corretor, três meses atrás: não aparece para o primeiro
        Lead deOutro = lead(outro, "Cliente Outro", "lead");
        jdbc.update("UPDATE lead SET data_criacao = ? WHERE id = ?", Timestamp.valueOf(atual.minusMonths(3).atDay(5).atTime(10, 0)), deOutro.getId());

        mockMvc.perform(get("/leads/meses").header("Authorization", bearer(corretor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", contains(atual.toString(), anterior.toString())));

        mockMvc.perform(get("/leads").param("page", "0").param("size", "10").param("month", anterior.toString())
                        .header("Authorization", bearer(corretor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].nome").value("Cliente Mes Anterior"));

        mockMvc.perform(get("/leads").param("page", "0").param("size", "10").param("month", "setembro")
                        .header("Authorization", bearer(corretor)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void leads_buscaPorNomeTelefoneEEmail() throws Exception {
        Usuario corretor = usuario("corretor", "Corretor Busca Leads", true);
        Lead maria = lead(corretor, "Maria Buscável", "lead");
        maria.setTelefone("43984011094");
        maria.setEmail("maria.busca@exemplo.com");
        leadRepository.save(maria);
        lead(corretor, "Outro Cliente", "lead");

        for (String termo : new String[] { "buscÁvel", "(43) 98401-1094", "4011094", "MARIA.BUSCA@" }) {
            mockMvc.perform(get("/leads").param("page", "0").param("size", "10").param("search", termo)
                            .header("Authorization", bearer(corretor)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].nome").value("Maria Buscável"));
        }
        // texto sem dígitos não casa com qualquer telefone
        mockMvc.perform(get("/leads").param("page", "0").param("size", "10").param("search", "zzz")
                        .header("Authorization", bearer(corretor)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    /** Lead com status, valor, origem e data de atualização definidos (dataAtualizacao é @UpdateTimestamp). */
    private Lead leadCom(Usuario corretor, String nome, String status, long valor, String origem, String telefone, String email, LocalDate atualizado) {
        Lead l = lead(corretor, nome, status);
        l.setValorInteresse(valor);
        l.setOrigem(origem);
        l.setTelefone(telefone);
        l.setEmail(email);
        l = leadRepository.save(l);
        jdbc.update("UPDATE lead SET data_atualizacao = ? WHERE id = ?", Timestamp.valueOf(atualizado.atTime(12, 0)), l.getId());
        return l;
    }

    @Test
    void leads_filtrosEOrdenacaoDasColunas() throws Exception {
        Usuario gestor = usuario("gestor", "Gestor Colunas", true);
        Equipe eq = new Equipe();
        eq.setNome("Equipe Colunas " + SEQ.incrementAndGet());
        eq.setGestor(gestor);
        eq = equipeRepository.save(eq);
        Usuario ana = usuario("corretor", "Ana Colunas", true);
        Usuario bia = usuario("corretor", "Bia Colunas", true);
        for (Usuario c : new Usuario[] { ana, bia }) { c.setGestor(gestor); c.setEquipe(eq); usuarioRepository.save(c); }

        leadCom(ana, "Carlos", "pasta", 300_000, "espontaneo", "43911112222", "carlos@x.com", LocalDate.of(2026, 9, 10));
        leadCom(ana, "Daniel", "lead", 100_000, "digital_lead", "43933334444", null, LocalDate.of(2026, 10, 1));
        leadCom(bia, "Eva", "contrato", 500_000, "espontaneo", "43955556666", "eva@y.com", LocalDate.of(2026, 10, 5));
        leadCom(bia, "Fabio", "descarte", 200_000, "ganhe_mais", "43977778888", null, LocalDate.of(2026, 10, 9));
        String auth = bearer(gestor);

        // ordenações especiais (etapa do funil e responsável) no escopo do gestor
        mockMvc.perform(get("/leads").param("page", "0").param("size", "10").param("sort", "status,asc").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.content[*].nome", contains("Daniel", "Carlos", "Eva", "Fabio")));
        mockMvc.perform(get("/leads").param("page", "0").param("size", "10").param("sort", "responsavel,desc").header("Authorization", auth))
                .andExpect(jsonPath("$.content[0].corretorNome").value("Bia Colunas"))
                .andExpect(jsonPath("$.content[3].corretorNome").value("Ana Colunas"));
        mockMvc.perform(get("/leads").param("page", "0").param("size", "10").param("sort", "valorInteresse,desc").header("Authorization", auth))
                .andExpect(jsonPath("$.content[*].nome", contains("Eva", "Carlos", "Fabio", "Daniel")));
        // página 2 de 2 com ordenação especial: total certo (a contagem não leva o ORDER BY)
        mockMvc.perform(get("/leads").param("page", "1").param("size", "3").param("sort", "status,desc").header("Authorization", auth))
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.content[*].nome", contains("Daniel")));

        // filtros
        mockMvc.perform(get("/leads").param("page", "0").param("size", "10").param("nome", "car").header("Authorization", auth))
                .andExpect(jsonPath("$.content[*].nome", contains("Carlos")));
        mockMvc.perform(get("/leads").param("page", "0").param("size", "10").param("contato", "(43) 95555").header("Authorization", auth))
                .andExpect(jsonPath("$.content[*].nome", contains("Eva")));
        mockMvc.perform(get("/leads").param("page", "0").param("size", "10").param("contato", "@X.COM").header("Authorization", auth))
                .andExpect(jsonPath("$.content[*].nome", contains("Carlos")));
        mockMvc.perform(get("/leads").param("page", "0").param("size", "10").param("etapas", "lead", "pasta").param("sort", "nome,asc").header("Authorization", auth))
                .andExpect(jsonPath("$.content[*].nome", contains("Carlos", "Daniel")));
        mockMvc.perform(get("/leads").param("page", "0").param("size", "10").param("origens", "espontaneo").param("sort", "nome,asc").header("Authorization", auth))
                .andExpect(jsonPath("$.content[*].nome", contains("Carlos", "Eva")));
        mockMvc.perform(get("/leads").param("page", "0").param("size", "10").param("valorMin", "200000").param("valorMax", "300000").param("sort", "nome,asc").header("Authorization", auth))
                .andExpect(jsonPath("$.content[*].nome", contains("Carlos", "Fabio")));
        mockMvc.perform(get("/leads").param("page", "0").param("size", "10").param("responsavel", "bia").param("sort", "nome,asc").header("Authorization", auth))
                .andExpect(jsonPath("$.content[*].nome", contains("Eva", "Fabio")));
        mockMvc.perform(get("/leads").param("page", "0").param("size", "10").param("dataDe", "2026-10-01").param("dataAte", "2026-10-05").param("sort", "nome,asc").header("Authorization", auth))
                .andExpect(jsonPath("$.content[*].nome", contains("Daniel", "Eva")));
        // filtros somam entre si e com as abas Ativos/Arquivados
        mockMvc.perform(get("/leads").param("page", "0").param("size", "10").param("status", "archived").param("origens", "espontaneo", "ganhe_mais").param("sort", "nome,asc").header("Authorization", auth))
                .andExpect(jsonPath("$.content[*].nome", contains("Eva", "Fabio")));

        // faixas invertidas são recusadas com mensagem
        mockMvc.perform(get("/leads").param("page", "0").param("size", "10").param("valorMin", "5").param("valorMax", "1").header("Authorization", auth))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.valorMin").exists());
        mockMvc.perform(get("/leads").param("page", "0").param("size", "10").param("dataDe", "2026-10-05").param("dataAte", "2026-10-01").header("Authorization", auth))
                .andExpect(status().isBadRequest());
    }

    @Test
    void leads_gestorEscolheEntreOsDeleEOsDaEquipe() throws Exception {
        Usuario gestor = usuario("gestor", "Gestor Escopo", true);
        Equipe eq = new Equipe();
        eq.setNome("Equipe Escopo " + SEQ.incrementAndGet());
        eq.setGestor(gestor);
        eq = equipeRepository.save(eq);
        gestor.setEquipe(eq);
        usuarioRepository.save(gestor);
        Usuario corretor = usuario("corretor", "Corretor Escopo", true);
        corretor.setGestor(gestor);
        corretor.setEquipe(eq);
        usuarioRepository.save(corretor);
        lead(gestor, "Cliente do Gestor", "lead");
        Lead doCorretor = lead(corretor, "Cliente do Corretor", "contrato");
        java.time.YearMonth atual = java.time.YearMonth.now();
        java.time.YearMonth anterior = atual.minusMonths(1);
        jdbc.update("UPDATE lead SET data_criacao = ? WHERE id = ?", Timestamp.valueOf(anterior.atDay(10).atTime(9, 0)), doCorretor.getId());
        String auth = bearer(gestor);

        // padrão da API (e "equipe"): a equipe inteira, inclusive os clientes do próprio gestor
        for (String escopo : new String[] { null, "equipe" }) {
            var req = get("/leads").param("page", "0").param("size", "10").param("sort", "nome,asc").header("Authorization", auth);
            if (escopo != null) req = req.param("escopo", escopo);
            mockMvc.perform(req).andExpect(jsonPath("$.content[*].nome", contains("Cliente do Corretor", "Cliente do Gestor")));
        }
        mockMvc.perform(get("/leads").param("page", "0").param("size", "10").param("escopo", "meus").header("Authorization", auth))
                .andExpect(jsonPath("$.content[*].nome", contains("Cliente do Gestor")));

        // lista completa, sem paginação (Dashboard): mesmo escopo
        mockMvc.perform(get("/leads").param("escopo", "meus").header("Authorization", auth))
                .andExpect(jsonPath("$[*].nome", contains("Cliente do Gestor")));
        mockMvc.perform(get("/leads").param("escopo", "equipe").header("Authorization", auth))
                .andExpect(jsonPath("$", hasSize(2)));
        mockMvc.perform(get("/leads").header("Authorization", auth))
                .andExpect(jsonPath("$", hasSize(2)));

        // cards do resumo seguem o mesmo escopo
        mockMvc.perform(get("/leads/resumo").param("escopo", "meus").header("Authorization", auth))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.contratos").value(0));
        mockMvc.perform(get("/leads/resumo").param("escopo", "equipe").header("Authorization", auth))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.contratos").value(1));

        // Exportar Clientes: os meses do filtro de período e a planilha seguem o mesmo escopo
        mockMvc.perform(get("/leads/meses").param("escopo", "meus").header("Authorization", auth))
                .andExpect(jsonPath("$", contains(atual.toString())));
        mockMvc.perform(get("/leads/meses").param("escopo", "equipe").header("Authorization", auth))
                .andExpect(jsonPath("$", contains(atual.toString(), anterior.toString())));
        org.junit.jupiter.api.Assertions.assertEquals(1, linhasExportadas(auth, "meus", null));
        org.junit.jupiter.api.Assertions.assertEquals(2, linhasExportadas(auth, "equipe", null));
        // "meus" + mês anterior: o cliente daquele mês é do corretor, então não sobra nenhum
        org.junit.jupiter.api.Assertions.assertEquals(0, linhasExportadas(auth, "meus", anterior.toString()));
        org.junit.jupiter.api.Assertions.assertEquals(1, linhasExportadas(auth, "equipe", anterior.toString()));

        // para o corretor o parâmetro não muda nada
        mockMvc.perform(get("/leads").param("page", "0").param("size", "10").param("escopo", "equipe").header("Authorization", bearer(corretor)))
                .andExpect(jsonPath("$.content[*].nome", contains("Cliente do Corretor")));
    }

    /** Quantidade de clientes na aba de dados da planilha exportada (sem o cabeçalho). */
    private int linhasExportadas(String auth, String escopo, String month) throws Exception {
        var req = get("/leads/exportar").param("escopo", escopo).header("Authorization", auth);
        if (month != null) req = req.param("month", month);
        byte[] bytes = mockMvc.perform(req).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(bytes))) {
            return wb.getSheetAt(0).getLastRowNum();
        }
    }
}
