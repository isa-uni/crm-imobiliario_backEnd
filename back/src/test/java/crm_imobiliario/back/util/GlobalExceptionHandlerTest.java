package crm_imobiliario.back.util;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import crm_imobiliario.back.model.dto.UsuarioDTO;

public class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest();

    @Test
    void handleValidation_shouldReturnFieldsMap() throws Exception {
        request.setRequestURI("/usuarios/cadastrar");
        UsuarioDTO dto = new UsuarioDTO(null, null, null, null, null, null, null, null);
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(dto, "usuarioDTO");
        bindingResult.rejectValue("email", "NotBlank", "O email é obrigatória");
        bindingResult.rejectValue("nome", "NotBlank", "O nome é obrigatória");
        MethodParameter param = new MethodParameter(
                crm_imobiliario.back.controller.UsuarioController.class.getDeclaredMethod("cadastrarCliente", UsuarioDTO.class), 0);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(param, bindingResult);

        ResponseEntity<ApiErrorResponse> resp = handler.handleValidationExceptions(ex, request);
        assertEquals(400, resp.getStatusCode().value());
        assertNotNull(resp.getBody());
        assertFalse(resp.getBody().isSuccess());
        assertEquals("VALIDATION_ERROR", resp.getBody().getCode());
        assertNotNull(resp.getBody().getFields());
        assertNotNull(resp.getBody().getRequestId());
    }

    @Test
    void handleRuntime_notFoundShouldMapTo404() {
        request.setRequestURI("/leads/999");
        RuntimeException ex = new RuntimeException("Lead não encontrado");
        ResponseEntity<ApiErrorResponse> resp = handler.handleRuntime(ex, request);
        assertEquals(404, resp.getStatusCode().value());
        assertEquals("NOT_FOUND", resp.getBody().getCode());
    }

    @Test
    void handleRuntime_genericaEhTratadaComoInesperadaSemExporTextoTecnico() {
        request.setRequestURI("/usuarios/cadastrar");
        RuntimeException ex = new RuntimeException("could not execute statement; SQL [n/a]");
        ResponseEntity<ApiErrorResponse> resp = handler.handleRuntime(ex, request);
        assertEquals(500, resp.getStatusCode().value());
        assertEquals("INTERNAL_ERROR", resp.getBody().getCode());
        assertFalse(resp.getBody().getMessage().contains("SQL"));
        // o usuário recebe um código de referência que também está no log
        String ref = GlobalExceptionHandler.referencia(resp.getBody().getRequestId());
        assertTrue(resp.getBody().getMessage().contains(ref), resp.getBody().getMessage());
    }

    @Test
    void handleRegraNegocio_informaCampoECodigo() {
        request.setRequestURI("/usuarios/minha-senha");
        RegraNegocioException ex = new RegraNegocioException("A senha atual informada está incorreta.", "senhaAtual", "WRONG_CURRENT_PASSWORD");
        ResponseEntity<ApiErrorResponse> resp = handler.handleRegraNegocio(ex, request);
        assertEquals(400, resp.getStatusCode().value());
        assertEquals("WRONG_CURRENT_PASSWORD", resp.getBody().getCode());
        assertEquals("A senha atual informada está incorreta.", resp.getBody().getMessage());
        assertEquals("A senha atual informada está incorreta.", resp.getBody().getFields().get("senhaAtual"));
    }

    @Test
    void handleValidation_umUnicoCampoUsaAMensagemDoCampo() throws Exception {
        request.setRequestURI("/leads/cadastrar");
        UsuarioDTO dto = new UsuarioDTO(null, null, null, null, null, null, null, null);
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(dto, "usuarioDTO");
        bindingResult.rejectValue("nome", "NotBlank", "Informe o nome do usuário.");
        MethodParameter param = new MethodParameter(
                crm_imobiliario.back.controller.UsuarioController.class.getDeclaredMethod("cadastrarCliente", UsuarioDTO.class), 0);
        ResponseEntity<ApiErrorResponse> resp = handler.handleValidationExceptions(new MethodArgumentNotValidException(param, bindingResult), request);
        assertEquals("Informe o nome do usuário.", resp.getBody().getMessage());
    }

    @Test
    void handleDataIntegrity_emailDuplicadoNoPostgresApontaOCampo() {
        request.setRequestURI("/usuarios/cadastrar");
        DataIntegrityViolationException ex = new DataIntegrityViolationException("x",
                new RuntimeException("ERROR: duplicate key value violates unique constraint \"usuario_email_key\"\n  Detail: Key (email)=(a@b.com) already exists."));
        ResponseEntity<ApiErrorResponse> resp = handler.handleDataIntegrity(ex, request);
        assertEquals(409, resp.getStatusCode().value());
        assertEquals("DUPLICATE_EMAIL", resp.getBody().getCode());
        assertEquals("Já existe um usuário cadastrado com este e-mail.", resp.getBody().getMessage());
        assertNotNull(resp.getBody().getFields().get("email"));
        assertFalse(resp.getBody().getMessage().contains("constraint"));
    }

    @Test
    void handleDataIntegrity_cpfDuplicadoNoH2ApontaOCampo() {
        request.setRequestURI("/usuarios/cadastrar");
        DataIntegrityViolationException ex = new DataIntegrityViolationException("x",
                new RuntimeException("Unique index or primary key violation: \"PUBLIC.CONSTRAINT_INDEX_8 ON PUBLIC.USUARIO(CPF NULLS FIRST) VALUES ( /* 2 */ '52998224725' )\""));
        ResponseEntity<ApiErrorResponse> resp = handler.handleDataIntegrity(ex, request);
        assertEquals("DUPLICATE_CPF", resp.getBody().getCode());
        assertNotNull(resp.getBody().getFields().get("cpf"));
    }

    @Test
    void handleDataIntegrity_chaveEstrangeiraExplicaQueORegistroEstaEmUso() {
        request.setRequestURI("/papel/3");
        DataIntegrityViolationException ex = new DataIntegrityViolationException("x",
                new RuntimeException("ERROR: update or delete on table \"papel\" violates foreign key constraint \"fk_usuario_papel\""));
        ResponseEntity<ApiErrorResponse> resp = handler.handleDataIntegrity(ex, request);
        assertEquals("CONFLICT_IN_USE", resp.getBody().getCode());
        assertTrue(resp.getBody().getMessage().contains("vinculado"));
    }

    @Test
    void handleDataIntegrity_shouldReturn409() {
        request.setRequestURI("/usuarios/cadastrar");
        DataIntegrityViolationException ex = new DataIntegrityViolationException("duplicate key");
        ResponseEntity<ApiErrorResponse> resp = handler.handleDataIntegrity(ex, request);
        assertEquals(409, resp.getStatusCode().value());
        assertEquals("CONFLICT", resp.getBody().getCode());
        assertFalse(resp.getBody().getMessage().contains("duplicate"));
    }

    @Test
    void handleAccessDenied_shouldReturn403() {
        request.setRequestURI("/usuarios");
        AccessDeniedException ex = new AccessDeniedException("Acesso negado");
        ResponseEntity<ApiErrorResponse> resp = handler.handleAccessDenied(ex, request);
        assertEquals(403, resp.getStatusCode().value());
        assertEquals("FORBIDDEN", resp.getBody().getCode());
        assertTrue(resp.getBody().getMessage().contains("permissão"));
    }

    @Test
    void handleGeneric_shouldReturn500WithRequestId() {
        request.setRequestURI("/leads");
        Exception ex = new Exception("NPE inesperado");
        ResponseEntity<ApiErrorResponse> resp = handler.handleGeneric(ex, request);
        assertEquals(500, resp.getStatusCode().value());
        assertEquals("INTERNAL_ERROR", resp.getBody().getCode());
        assertNotNull(resp.getBody().getRequestId());
        assertFalse(resp.getBody().getMessage().contains("NPE"));
    }
}
