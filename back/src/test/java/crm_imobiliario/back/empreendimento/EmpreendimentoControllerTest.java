package crm_imobiliario.back.empreendimento;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import crm_imobiliario.back.controller.EmpreendimentoController;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driverClassName=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration",
    "crm.sync-no-startup=false"
})
public class EmpreendimentoControllerTest {

    @Autowired
    private EmpreendimentoController controller;

    @Test
    void contextLoads() {
        assertNotNull(controller);
    }

    @Test
    void disponiveisNaoNulo() {
        var resp = controller.disponiveis(null, null, null, null, 0, 20, "nome,asc");
        assertNotNull(resp.getBody());
        assertTrue(resp.getBody().containsKey("data"));
        assertTrue(resp.getBody().containsKey("meta"));
    }

    @Test
    void syncStatusNaoNulo() {
        var resp = controller.syncStatus();
        assertNotNull(resp.getBody());
        assertNotNull(resp.getBody().getEmBuild());
    }
}
