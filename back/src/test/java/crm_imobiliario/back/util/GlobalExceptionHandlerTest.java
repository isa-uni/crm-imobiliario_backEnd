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
import jakarta.validation.Validation;
import jakarta.validation.Validator;

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
    void handleRuntime_genericShouldMapTo400() {
        request.setRequestURI("/usuarios/cadastrar");
        RuntimeException ex = new RuntimeException("Já existe um usuário com este e-mail");
        ResponseEntity<ApiErrorResponse> resp = handler.handleRuntime(ex, request);
        assertEquals(400, resp.getStatusCode().value());
        assertEquals("BAD_REQUEST", resp.getBody().getCode());
    }

    @Test
    void handleDataIntegrity_shouldReturn409() {
        request.setRequestURI("/usuarios/cadastrar");
        DataIntegrityViolationException ex = new DataIntegrityViolationException("duplicate key");
        ResponseEntity<ApiErrorResponse> resp = handler.handleDataIntegrity(ex, request);
        assertEquals(409, resp.getStatusCode().value());
        assertEquals("CONFLICT", resp.getBody().getCode());
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
        request.setRequestURI("/imovel");
        Exception ex = new Exception("NPE inesperado");
        ResponseEntity<ApiErrorResponse> resp = handler.handleGeneric(ex, request);
        assertEquals(500, resp.getStatusCode().value());
        assertEquals("INTERNAL_ERROR", resp.getBody().getCode());
        assertNotNull(resp.getBody().getRequestId());
        assertFalse(resp.getBody().getMessage().contains("NPE"));
    }
}
