package crm_imobiliario.back.model.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** A senha inicial deixou de ser os 4 últimos dígitos do CPF: agora é aleatória e forte. */
class UsuarioServiceSenhaTemporariaTest {

    private final UsuarioService service = new UsuarioService();

    @Test
    void senhaTemporariaSempreAtendeAPoliticaDeForca() {
        for (int i = 0; i < 200; i++) {
            String senha = service.gerarSenhaTemporaria();
            assertEquals(12, senha.length());
            assertDoesNotThrow(() -> service.validarForcaSenha(senha), senha);
        }
    }

    @Test
    void senhasTemporariasNaoSeRepetem() {
        Set<String> geradas = new HashSet<>();
        for (int i = 0; i < 200; i++) geradas.add(service.gerarSenhaTemporaria());
        assertEquals(200, geradas.size());
    }
}
