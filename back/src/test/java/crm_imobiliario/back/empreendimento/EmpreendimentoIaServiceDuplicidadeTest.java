package crm_imobiliario.back.empreendimento;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import crm_imobiliario.back.model.dto.empreendimento.EmpreendimentoConfirmacaoDTO;
import crm_imobiliario.back.model.service.empreendimento.EmpreendimentoIaService;

/** Detecção de duplicidade determinística (§14 do spec de importação) — sem IA, por similaridade de texto. */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:duplicidadetestdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driverClassName=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration",
    "crm.sync-no-startup=false"
})
class EmpreendimentoIaServiceDuplicidadeTest {

    @Autowired
    private EmpreendimentoIaService service;

    @Test
    void encontraCandidatoPorNomeSemelhante() {
        service.confirmar(EmpreendimentoConfirmacaoDTO.builder().nome("Residencial London Plaza").cidade("Londrina").build(), 1L);

        var candidatos = service.buscarDuplicados("London Plaza", null, null);
        assertFalse(candidatos.isEmpty(), "nome muito semelhante deveria ser sinalizado como possível duplicata");
    }

    @Test
    void encontraCandidatoPorCodigoExternoIgual() {
        service.confirmar(EmpreendimentoConfirmacaoDTO.builder().nome("Solare Essenza").codigoExterno("SOLARE-2026").build(), 1L);

        var candidatos = service.buscarDuplicados("Nome Totalmente Diferente", "SOLARE-2026", null);
        assertFalse(candidatos.isEmpty(), "código externo idêntico deveria ser sinalizado independentemente do nome");
    }

    @Test
    void nomeCompletamenteDiferenteNaoEhSinalizado() {
        service.confirmar(EmpreendimentoConfirmacaoDTO.builder().nome("Marbella Residencial").build(), 1L);

        var candidatos = service.buscarDuplicados("Torre Norte Empreendimentos XYZ", null, null);
        assertTrue(candidatos.stream().noneMatch(c -> c.getNome().equals("Marbella Residencial")));
    }
}
