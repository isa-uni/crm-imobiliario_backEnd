package crm_imobiliario.back.empreendimento;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import crm_imobiliario.back.controller.EmpreendimentoController;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
    "spring.datasource.driverClassName=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
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
