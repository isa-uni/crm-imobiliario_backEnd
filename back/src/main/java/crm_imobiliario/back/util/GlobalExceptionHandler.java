package crm_imobiliario.back.util;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fields = new HashMap<>();
        List<Map<String, String>> errors = new ArrayList<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> {
            fields.put(error.getField(), error.getDefaultMessage());
            Map<String, String> err = new HashMap<>();
            err.put("field", error.getField());
            err.put("message", error.getDefaultMessage());
            errors.add(err);
        });
        String requestId = UUID.randomUUID().toString();
        log.warn("[{}] Validation error on {}: {}", requestId, request.getRequestURI(), fields);
        ApiErrorResponse body = ApiErrorResponse.builder()
                .success(false)
                .message("Existem campos inválidos.")
                .code("VALIDATION_ERROR")
                .fields(fields)
                .details(errors)
                .path(request.getRequestURI())
                .timestamp(Instant.now())
                .requestId(requestId)
                .build();
        // mantém compat: também envia 'errors' no topo para frontend legado
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        Map<String, String> fields = new HashMap<>();
        ex.getConstraintViolations().forEach(v -> {
            String path = v.getPropertyPath().toString();
            String field = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
            fields.put(field, v.getMessage());
        });
        return buildError(HttpStatus.BAD_REQUEST, "Erro de validação.", "VALIDATION_ERROR", fields, request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        return buildError(HttpStatus.UNAUTHORIZED, "E-mail ou senha incorretos.", "AUTH_INVALID_CREDENTIALS", null, request);
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ApiErrorResponse> handleDisabledUser(DisabledException ex, HttpServletRequest request) {
        return buildError(HttpStatus.UNAUTHORIZED, "Usuário desativado. Fale com o administrador.", "AUTH_USER_DISABLED", null, request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuth(AuthenticationException ex, HttpServletRequest request) {
        return buildError(HttpStatus.UNAUTHORIZED, "Não autenticado. Faça login novamente.", "AUTH_REQUIRED", null, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return buildError(HttpStatus.FORBIDDEN, "Você não possui permissão para realizar esta ação.", "FORBIDDEN", null, request);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(EntityNotFoundException ex, HttpServletRequest request) {
        return buildError(HttpStatus.NOT_FOUND, ex.getMessage() != null ? ex.getMessage() : "Recurso não encontrado.", "NOT_FOUND", null, request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("DataIntegrityViolation on {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return buildError(HttpStatus.CONFLICT, "Conflito de dados. Registro já existe ou viola regra de negócio.", "CONFLICT", null, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return buildError(HttpStatus.BAD_REQUEST, "Dados da requisição inválidos. Verifique o formato enviado.", "BAD_REQUEST", null, request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParam(MissingServletRequestParameterException ex, HttpServletRequest request) {
        Map<String, String> fields = Map.of(ex.getParameterName(), "Parâmetro obrigatório ausente.");
        return buildError(HttpStatus.BAD_REQUEST, "Parâmetro obrigatório ausente: " + ex.getParameterName(), "MISSING_PARAMETER", fields, request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArg(IllegalArgumentException ex, HttpServletRequest request) {
        return buildError(HttpStatus.BAD_REQUEST, ex.getMessage(), "BAD_REQUEST", null, request);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiErrorResponse> handleRuntime(RuntimeException ex, HttpServletRequest request) {
        String msg = ex.getMessage();
        // mapeia mensagens conhecidas para códigos apropriados
        if (msg != null && (msg.toLowerCase().contains("não encontrado") || msg.toLowerCase().contains("not found"))) {
            return buildError(HttpStatus.NOT_FOUND, msg, "NOT_FOUND", null, request);
        }
        if (msg != null && msg.toLowerCase().contains("permissão")) {
            return buildError(HttpStatus.FORBIDDEN, msg, "FORBIDDEN", null, request);
        }
        log.warn("RuntimeException on {}: {}", request.getRequestURI(), msg);
        return buildError(HttpStatus.BAD_REQUEST, msg != null ? msg : "Erro ao processar a requisição.", "BAD_REQUEST", null, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
        String requestId = UUID.randomUUID().toString();
        log.error("[{}] Unhandled error on {}: {}", requestId, request.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ApiErrorResponse.builder()
                        .success(false)
                        .message("Erro interno no servidor. Tente novamente mais tarde.")
                        .code("INTERNAL_ERROR")
                        .path(request.getRequestURI())
                        .timestamp(Instant.now())
                        .requestId(requestId)
                        .build()
        );
    }

    private ResponseEntity<ApiErrorResponse> buildError(HttpStatus status, String message, String code, Map<String, String> fields, HttpServletRequest request) {
        String requestId = UUID.randomUUID().toString();
        ApiErrorResponse body = ApiErrorResponse.builder()
                .success(false)
                .message(message)
                .code(code)
                .fields(fields)
                .path(request.getRequestURI())
                .timestamp(Instant.now())
                .requestId(requestId)
                .build();
        return ResponseEntity.status(status).body(body);
    }
}
