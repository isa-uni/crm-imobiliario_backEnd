package crm_imobiliario.back.empreendimento;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import org.springframework.data.domain.PageRequest;

import crm_imobiliario.back.model.dto.empreendimento.EmpreendimentoConfirmacaoDTO;
import crm_imobiliario.back.model.dto.empreendimento.EmpreendimentoDetalheDTO;
import crm_imobiliario.back.model.dto.empreendimento.UnidadeDTO;
import crm_imobiliario.back.model.entity.Empreendimento;
import crm_imobiliario.back.model.entity.Unidade;
import crm_imobiliario.back.model.entity.UnidadeHistorico;
import crm_imobiliario.back.model.repository.EmpreendimentoRepository;
import crm_imobiliario.back.model.repository.UnidadeHistoricoRepository;
import crm_imobiliario.back.model.repository.UnidadeRepository;
import crm_imobiliario.back.model.service.empreendimento.EmpreendimentoIaService;

/**
 * Confirmação e persistência de unidades (§14/§15/§22 do spec de importação):
 * criação, atualização (upsert por nome), preservação de histórico de preço e
 * situação, e não-duplicação de unidades entre confirmações sucessivas.
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:unidadestestdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driverClassName=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration",
    "crm.sync-no-startup=false"
})
class EmpreendimentoIaServiceUnidadesTest {

    @Autowired
    private EmpreendimentoIaService service;
    @Autowired
    private UnidadeRepository unidadeRepository;
    @Autowired
    private UnidadeHistoricoRepository unidadeHistoricoRepository;
    @Autowired
    private EmpreendimentoRepository empreendimentoRepository;

    private EmpreendimentoConfirmacaoDTO.EmpreendimentoConfirmacaoDTOBuilder baseDto(String nome) {
        return EmpreendimentoConfirmacaoDTO.builder().nome(nome);
    }

    private UnidadeDTO unidade(String nomeUnidade, String situacao, Long preco) {
        return UnidadeDTO.builder().nomeUnidade(nomeUnidade).bloco("BLOCO 01").tipologia("2Q")
                .situacao(situacao).preco(preco).build();
    }

    private UnidadeDTO unidade(String nomeUnidade, String bloco, String tipologia, String situacao, Long preco, Double areaPrivativa) {
        return UnidadeDTO.builder().nomeUnidade(nomeUnidade).bloco(bloco).tipologia(tipologia)
                .situacao(situacao).preco(preco).areaPrivativa(areaPrivativa).build();
    }

    @Test
    void confirmarCriaUnidadesNovas() {
        var dto = baseDto("Residencial Teste Unidades A")
                .unidades(List.of(unidade("B01.101", "disponivel", 227200L), unidade("B01.102", "disponivel", 242500L)))
                .build();

        var detalhe = service.confirmar(dto, 1L);
        List<Unidade> unidades = unidadeRepository.findByEmpreendimentoId(detalhe.getId());

        assertEquals(2, unidades.size());
        assertTrue(unidades.stream().anyMatch(u -> u.getNomeUnidade().equals("B01.101") && u.getPreco().equals(227200L)));
    }

    @Test
    void confirmarNovamenteAtualizaUnidadeExistenteSemDuplicar() {
        var dtoInicial = baseDto("Residencial Teste Unidades B")
                .unidades(List.of(unidade("B02.201", "disponivel", 200000L)))
                .build();
        var detalhe = service.confirmar(dtoInicial, 1L);

        var dtoAtualizacao = baseDto(detalhe.getNome())
                .empreendimentoExistenteId(detalhe.getId())
                .unidades(List.of(unidade("B02.201", "reservada", 210000L)))
                .build();
        service.confirmar(dtoAtualizacao, 1L);

        List<Unidade> unidades = unidadeRepository.findByEmpreendimentoId(detalhe.getId());
        assertEquals(1, unidades.size(), "não deve duplicar a unidade — deve atualizar a existente");
        Unidade u = unidades.get(0);
        assertEquals("reservada", u.getSituacao());
        assertEquals(210000L, u.getPreco());
    }

    @Test
    void alteracaoDePrecoESituacaoGeraHistorico() {
        var dtoInicial = baseDto("Residencial Teste Unidades C")
                .unidades(List.of(unidade("B03.301", "disponivel", 300000L)))
                .build();
        var detalhe = service.confirmar(dtoInicial, 1L);
        Unidade unidadeCriada = unidadeRepository.findByEmpreendimentoId(detalhe.getId()).get(0);
        assertTrue(unidadeHistoricoRepository.findByUnidadeIdOrderByCriadoEmDesc(unidadeCriada.getId()).isEmpty(),
                "criação inicial não deve gerar histórico de alteração");

        var dtoAtualizacao = baseDto(detalhe.getNome())
                .empreendimentoExistenteId(detalhe.getId())
                .unidades(List.of(unidade("B03.301", "vendida", 310000L)))
                .build();
        service.confirmar(dtoAtualizacao, 2L);

        List<UnidadeHistorico> historico = unidadeHistoricoRepository.findByUnidadeIdOrderByCriadoEmDesc(unidadeCriada.getId());
        assertEquals(2, historico.size(), "deve registrar uma entrada para preco e outra para situacao");
        assertTrue(historico.stream().anyMatch(h -> h.getCampo().equals("preco") && h.getValorAnterior().equals("300000") && h.getValorNovo().equals("310000")));
        assertTrue(historico.stream().anyMatch(h -> h.getCampo().equals("situacao") && h.getValorAnterior().equals("disponivel") && h.getValorNovo().equals("vendida")));
    }

    @Test
    void confirmarSemMudancaNaoGeraHistoricoDuplicado() {
        var dtoInicial = baseDto("Residencial Teste Unidades D")
                .unidades(List.of(unidade("B04.401", "disponivel", 250000L)))
                .build();
        var detalhe = service.confirmar(dtoInicial, 1L);
        Unidade unidadeCriada = unidadeRepository.findByEmpreendimentoId(detalhe.getId()).get(0);

        var dtoRepeticao = baseDto(detalhe.getNome())
                .empreendimentoExistenteId(detalhe.getId())
                .unidades(List.of(unidade("B04.401", "disponivel", 250000L)))
                .build();
        service.confirmar(dtoRepeticao, 1L);

        assertTrue(unidadeHistoricoRepository.findByUnidadeIdOrderByCriadoEmDesc(unidadeCriada.getId()).isEmpty(),
                "confirmar com os mesmos valores não deve gerar histórico");
    }

    @Test
    void novaUnidadeAdicionadaEmUploadPosteriorNaoAfetaAsExistentes() {
        var dtoInicial = baseDto("Residencial Teste Unidades E")
                .unidades(List.of(unidade("B05.501", "disponivel", 260000L)))
                .build();
        var detalhe = service.confirmar(dtoInicial, 1L);

        var dtoComNovaUnidade = baseDto(detalhe.getNome())
                .empreendimentoExistenteId(detalhe.getId())
                .unidades(List.of(unidade("B05.502", "disponivel", 265000L)))
                .build();
        service.confirmar(dtoComNovaUnidade, 1L);

        List<Unidade> unidades = unidadeRepository.findByEmpreendimentoId(detalhe.getId());
        assertEquals(2, unidades.size());
        assertTrue(unidades.stream().anyMatch(u -> u.getNomeUnidade().equals("B05.501")));
        assertTrue(unidades.stream().anyMatch(u -> u.getNomeUnidade().equals("B05.502")));
    }

    @Test
    void confirmarRecalculaAgregadosDoEmpreendimentoAPartirDasUnidades() {
        var dto = baseDto("Residencial Teste Unidades F")
                .unidades(List.of(
                        unidade("B06.101", "BLOCO 01", "2Q", "disponivel", 220000L, 45.0),
                        unidade("B06.102", "BLOCO 01", "2Q - Garden", "vendida", 260000L, 55.0)))
                .build();

        var detalhe = service.confirmar(dto, 1L);

        assertNotNull(detalhe.getUnidadesResumo(), "detalhe deve expor um resumo calculado das unidades");
        assertEquals(2, detalhe.getUnidadesResumo().getTotal());
        assertEquals(1, detalhe.getUnidadesResumo().getDisponiveis());
        assertEquals(1, detalhe.getUnidadesResumo().getVendidas());
        assertEquals(220000L, detalhe.getUnidadesResumo().getPrecoMin());
        assertEquals(260000L, detalhe.getUnidadesResumo().getPrecoMax());
        assertEquals(45.0, detalhe.getUnidadesResumo().getMetragemMin());
        assertEquals(55.0, detalhe.getUnidadesResumo().getMetragemMax());
        assertTrue(detalhe.getUnidadesResumo().getTipologias().containsAll(List.of("2Q", "2Q - Garden")));

        // os mesmos agregados também ficam persistidos na entidade, para a listagem em cards
        Empreendimento persistido = empreendimentoRepository.findById(detalhe.getId()).orElseThrow();
        assertEquals(220000L, persistido.getPrecoMin());
        assertEquals(260000L, persistido.getPrecoMax());
        assertEquals(2, persistido.getTotal());
        assertEquals(1, persistido.getDisponiveis());
    }

    @Test
    void listarUnidadesFiltraPorBlocoTipologiaEBusca() {
        var dto = baseDto("Residencial Teste Unidades G")
                .unidades(List.of(
                        unidade("B07.101", "BLOCO 01", "2Q", "disponivel", 200000L, 40.0),
                        unidade("B07.201", "BLOCO 02", "3Q", "disponivel", 300000L, 60.0)))
                .build();
        var detalhe = service.confirmar(dto, 1L);

        var porBloco = service.listarUnidades(detalhe.getId(), null, "BLOCO 02", null, null, null, null, null, null, PageRequest.of(0, 10));
        assertEquals(1, porBloco.getTotalElements());
        assertEquals("B07.201", porBloco.getContent().get(0).getNomeUnidade());

        var porTipologia = service.listarUnidades(detalhe.getId(), null, null, "3Q", null, null, null, null, null, PageRequest.of(0, 10));
        assertEquals(1, porTipologia.getTotalElements());

        var porBusca = service.listarUnidades(detalhe.getId(), null, null, null, "b07.101", null, null, null, null, PageRequest.of(0, 10));
        assertEquals(1, porBusca.getTotalElements());
        assertEquals("B07.101", porBusca.getContent().get(0).getNomeUnidade());

        var porFaixaDeValor = service.listarUnidades(detalhe.getId(), null, null, null, null, 250000L, null, null, null, PageRequest.of(0, 10));
        assertEquals(1, porFaixaDeValor.getTotalElements());
        assertEquals("B07.201", porFaixaDeValor.getContent().get(0).getNomeUnidade());
    }
}
