package crm_imobiliario.back.util;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

/**
 * Converte qualquer exceção em um {@link ApiErrorResponse} com:
 * <ul>
 *   <li>{@code message}: texto para o usuário — diz o que aconteceu e, quando se sabe, por quê e o que fazer;
 *       nunca contém SQL, nomes de tabela/classe ou stack trace;</li>
 *   <li>{@code code}: motivo estável para o frontend decidir a apresentação (ex.: DUPLICATE_EMAIL);</li>
 *   <li>{@code fields}: erros por campo do formulário (o frontend mostra ao lado do campo);</li>
 *   <li>{@code requestId}: referência para localizar o erro no log (também mostrada ao usuário nos erros inesperados).</li>
 * </ul>
 * O detalhe técnico vai sempre para o log, junto com o requestId.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ------------------------------------------------------------------ validação

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        List<Map<String, String>> errors = new ArrayList<>();
        // um campo pode violar várias regras ao mesmo tempo (ex.: CPF vazio viola @NotBlank e @Cpf);
        // mostra primeiro a de presença ("Informe o CPF"), depois as de formato/conteúdo
        ex.getBindingResult().getFieldErrors().stream()
                .sorted(java.util.Comparator.comparingInt(GlobalExceptionHandler::prioridadeDaRegra))
                .forEach(error -> {
            fields.putIfAbsent(error.getField(), error.getDefaultMessage());
            errors.add(Map.of("field", error.getField(), "message", String.valueOf(error.getDefaultMessage())));
        });
        String requestId = novoRequestId();
        log.warn("[{}] Validação falhou em {}: {}", requestId, request.getRequestURI(), fields);
        ApiErrorResponse body = ApiErrorResponse.builder()
                .success(false)
                .message(mensagemDeValidacao(fields))
                .code("VALIDATION_ERROR")
                .fields(fields)
                .details(errors)
                .path(request.getRequestURI())
                .timestamp(Instant.now())
                .requestId(requestId)
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(v -> {
            String path = v.getPropertyPath().toString();
            String field = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
            fields.putIfAbsent(field, v.getMessage());
        });
        return buildError(HttpStatus.BAD_REQUEST, mensagemDeValidacao(fields), "VALIDATION_ERROR", fields, request);
    }

    /** Um erro: a própria mensagem do campo (mais útil que um texto genérico). Vários: pede revisão. */
    static int prioridadeDaRegra(org.springframework.validation.FieldError error) {
        String regra = error.getCode() == null ? "" : error.getCode();
        // nos DTOs de atualização parcial a presença é um @Pattern "tem conteúdo" com mensagem "Informe…/Selecione…"
        String msg = String.valueOf(error.getDefaultMessage());
        if ("Pattern".equals(regra) && (msg.startsWith("Informe o") || msg.startsWith("Informe a") || msg.startsWith("Selecione"))) return 0;
        return switch (regra) {
            case "NotBlank", "NotNull", "NotEmpty" -> 0;
            case "Size" -> 1;
            default -> 2;
        };
    }

    static String mensagemDeValidacao(Map<String, String> fields) {
        if (fields.size() == 1) return fields.values().iterator().next();
        return "Revise os " + fields.size() + " campos destacados no formulário.";
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        String campo = campoComValorInvalido(ex);
        log.warn("Corpo da requisição ilegível em {} (campo={}): {}", request.getRequestURI(), campo, ex.getMostSpecificCause().getMessage());
        if (campo != null) {
            return buildError(HttpStatus.BAD_REQUEST, "O valor informado em \"" + campo + "\" não está no formato esperado.",
                    "INVALID_FORMAT", Map.of(campo, "Valor em formato inválido."), request);
        }
        return buildError(HttpStatus.BAD_REQUEST,
                "Os dados enviados não puderam ser lidos. Verifique os campos e tente novamente.", "BAD_REQUEST", null, request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParam(MissingServletRequestParameterException ex, HttpServletRequest request) {
        Map<String, String> fields = Map.of(ex.getParameterName(), "Informe este valor.");
        return buildError(HttpStatus.BAD_REQUEST, "Informe o parâmetro obrigatório \"" + ex.getParameterName() + "\".",
                "MISSING_PARAMETER", fields, request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return buildError(HttpStatus.BAD_REQUEST,
                "O valor \"" + ex.getValue() + "\" não é válido para \"" + ex.getName() + "\".",
                "INVALID_PARAMETER", Map.of(ex.getName(), "Valor inválido."), request);
    }

    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ApiErrorResponse> handlePropertyReference(PropertyReferenceException ex, HttpServletRequest request) {
        return buildError(HttpStatus.BAD_REQUEST,
                "Não é possível ordenar ou filtrar por \"" + ex.getPropertyName() + "\". Escolha outra coluna.",
                "INVALID_SORT", null, request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleUploadTooLarge(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return buildError(HttpStatus.PAYLOAD_TOO_LARGE,
                "Os arquivos enviados excedem o tamanho permitido: até 20 MB por arquivo e 100 MB por envio.",
                "PAYLOAD_TOO_LARGE", null, request);
    }

    // ------------------------------------------------------------------ regras de negócio e recursos

    @ExceptionHandler(RegraNegocioException.class)
    public ResponseEntity<ApiErrorResponse> handleRegraNegocio(RegraNegocioException ex, HttpServletRequest request) {
        Map<String, String> fields = ex.getCampo() != null ? Map.of(ex.getCampo(), ex.getMessage()) : null;
        String code = ex.getCodigo() != null ? ex.getCodigo() : "BUSINESS_RULE";
        return buildError(HttpStatus.BAD_REQUEST, ex.getMessage(), code, fields, request);
    }

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<ApiErrorResponse> handleRecursoNaoEncontrado(RecursoNaoEncontradoException ex, HttpServletRequest request) {
        return buildError(HttpStatus.NOT_FOUND, ex.getMessage(), "NOT_FOUND", null, request);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(EntityNotFoundException ex, HttpServletRequest request) {
        log.warn("EntityNotFound em {}: {}", request.getRequestURI(), ex.getMessage());
        return buildError(HttpStatus.NOT_FOUND, "O registro solicitado não foi encontrado. Ele pode ter sido removido.", "NOT_FOUND", null, request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return buildError(HttpStatus.NOT_FOUND, "O endereço solicitado não existe nesta API.", "ENDPOINT_NOT_FOUND", null, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return buildError(HttpStatus.METHOD_NOT_ALLOWED,
                "Esta operação não é permitida neste endereço (" + ex.getMethod() + ").", "METHOD_NOT_ALLOWED", null, request);
    }

    // ------------------------------------------------------------------ autenticação e permissão

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        return buildError(HttpStatus.UNAUTHORIZED, "E-mail ou senha incorretos. Confira os dados e tente novamente.", "AUTH_INVALID_CREDENTIALS", null, request);
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ApiErrorResponse> handleDisabledUser(DisabledException ex, HttpServletRequest request) {
        return buildError(HttpStatus.UNAUTHORIZED, "Seu usuário foi inativado por um administrador e, por isso, não é possível entrar no sistema. Procure o administrador da sua equipe para reativar o acesso.", "AUTH_USER_DISABLED", null, request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuth(AuthenticationException ex, HttpServletRequest request) {
        return buildError(HttpStatus.UNAUTHORIZED, "Sua sessão expirou. Faça login novamente.", "AUTH_REQUIRED", null, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        // as mensagens de AccessDeniedException do projeto explicam o motivo (ex.: "Você só pode
        // redistribuir leads da sua equipe."); as do próprio Spring são genéricas e em inglês
        String msg = ex.getMessage();
        boolean mensagemDoProjeto = msg != null && !msg.isBlank() && !msg.startsWith("Access") && !msg.equals("Acesso negado");
        return buildError(HttpStatus.FORBIDDEN,
                mensagemDoProjeto ? msg : "Você não tem permissão para realizar esta ação.", "FORBIDDEN", null, request);
    }

    // ------------------------------------------------------------------ integridade do banco

    /** Constraint do banco → motivo para o usuário. Casa pelo nome (PostgreSQL) ou por tabela+coluna (H2). */
    private record RegraIntegridade(Predicate<String> casa, String code, String campo, String mensagem) {}

    private static Predicate<String> constraint(String nomePg, String tabela, String coluna) {
        return c -> c.contains(nomePg) || (c.contains(tabela + "(" + coluna) || c.contains("public." + tabela + "(" + coluna));
    }

    private static final List<RegraIntegridade> REGRAS_INTEGRIDADE = List.of(
        new RegraIntegridade(constraint("usuario_email_key", "usuario", "email"), "DUPLICATE_EMAIL", "email",
                "Já existe um usuário cadastrado com este e-mail."),
        new RegraIntegridade(constraint("usuario_cpf_key", "usuario", "cpf"), "DUPLICATE_CPF", "cpf",
                "Já existe um usuário cadastrado com este CPF."),
        new RegraIntegridade(constraint("usuario_matricula_key", "usuario", "matricula"), "DUPLICATE_MATRICULA", null,
                "Não foi possível gerar uma matrícula única para o usuário. Tente cadastrar novamente."),
        new RegraIntegridade(constraint("equipe_nome_key", "equipe", "nome"), "DUPLICATE_EQUIPE", "nome",
                "Já existe uma equipe com este nome. Escolha outro nome."),
        new RegraIntegridade(constraint("papel_papel_key", "papel", "papel"), "DUPLICATE_PAPEL", "papel",
                "Já existe um papel com este nome."),
        new RegraIntegridade(c -> c.contains("idx_emp_codigo_ext") || c.contains("codigo_externo"), "CONFLICT_CODIGO_EXTERNO", "codigoExterno",
                "Já existe um empreendimento com este código externo. Use outro código ou deixe o campo em branco."),
        new RegraIntegridade(c -> c.contains("idx_emp_slug") || c.contains("empreendimento_slug_key") || c.contains("empreendimento(slug"), "CONFLICT_SLUG", "nome",
                "Já existe um empreendimento com nome igual ou muito parecido. Altere o nome."),
        new RegraIntegridade(c -> c.contains("meta_usuario_mes_origem_key") || c.contains("meta(usuario_id"), "DUPLICATE_META", null,
                "Já existe uma meta deste tipo para este usuário neste mês. Atualize a meta existente.")
    );

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        String cause = ex.getMostSpecificCause() != null && ex.getMostSpecificCause().getMessage() != null
                ? ex.getMostSpecificCause().getMessage() : String.valueOf(ex.getMessage());
        String c = cause.toLowerCase(Locale.ROOT);
        String requestId = novoRequestId();
        log.warn("[{}] Violação de integridade em {}: {}", requestId, request.getRequestURI(), cause);

        for (RegraIntegridade r : REGRAS_INTEGRIDADE) {
            if (r.casa().test(c)) {
                Map<String, String> fields = r.campo() != null ? Map.of(r.campo(), r.mensagem()) : null;
                return buildError(HttpStatus.CONFLICT, r.mensagem(), r.code(), fields, request, requestId);
            }
        }
        if (c.contains("foreign key") || c.contains("referential integrity") || c.contains("violates foreign")) {
            return buildError(HttpStatus.CONFLICT,
                    "Não foi possível concluir a operação porque este registro está vinculado a outros dados do sistema (por exemplo, leads ou históricos). Inative o registro em vez de excluí-lo.",
                    "CONFLICT_IN_USE", null, request, requestId);
        }
        if (c.contains("not-null") || c.contains("null value") || c.contains("not null")) {
            return buildError(HttpStatus.BAD_REQUEST,
                    "Não foi possível salvar porque um campo obrigatório não foi preenchido. Revise o formulário.",
                    "MISSING_REQUIRED_DATA", null, request, requestId);
        }
        return buildError(HttpStatus.CONFLICT,
                "Não foi possível salvar porque os dados entram em conflito com um registro já existente.",
                "CONFLICT", null, request, requestId);
    }

    // ------------------------------------------------------------------ inesperados

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArg(IllegalArgumentException ex, HttpServletRequest request) {
        // usado pelo código do projeto com mensagens para o usuário (ex.: validação de upload)
        return buildError(HttpStatus.BAD_REQUEST, ex.getMessage(), "BAD_REQUEST", null, request);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiErrorResponse> handleRuntime(RuntimeException ex, HttpServletRequest request) {
        // As regras de negócio usam RegraNegocioException/RecursoNaoEncontradoException/AccessDeniedException.
        // Esta heurística só cobre RuntimeException genéricas remanescentes; qualquer outra é tratada como
        // inesperada para não expor mensagens técnicas de bibliotecas ao usuário.
        String msg = ex.getMessage();
        String lower = msg != null ? msg.toLowerCase(Locale.ROOT) : "";
        if (lower.contains("não encontrad") || lower.contains("not found")) {
            return buildError(HttpStatus.NOT_FOUND, msg, "NOT_FOUND", null, request);
        }
        if (lower.contains("permissão")) {
            return buildError(HttpStatus.FORBIDDEN, msg, "FORBIDDEN", null, request);
        }
        return erroInesperado(ex, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
        return erroInesperado(ex, request);
    }

    private ResponseEntity<ApiErrorResponse> erroInesperado(Exception ex, HttpServletRequest request) {
        String requestId = novoRequestId();
        String referencia = referencia(requestId);
        log.error("[{}] (ref {}) Erro inesperado em {} {}: {}", requestId, referencia, request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ApiErrorResponse.builder()
                        .success(false)
                        .message("Não foi possível concluir a operação por um erro inesperado no servidor. Tente novamente. "
                                + "Se o problema persistir, informe ao suporte o código de referência " + referencia + ".")
                        .code("INTERNAL_ERROR")
                        .path(request.getRequestURI())
                        .timestamp(Instant.now())
                        .requestId(requestId)
                        .build());
    }

    // ------------------------------------------------------------------ utilitários

    static String novoRequestId() {
        return UUID.randomUUID().toString();
    }

    /** Código curto e legível para o usuário informar ao suporte (os 8 primeiros caracteres do requestId). */
    static String referencia(String requestId) {
        return requestId.substring(0, 8).toUpperCase(Locale.ROOT);
    }

    /** Nome do campo com valor em formato inválido (Jackson 2 ou 3), sem depender da versão da biblioteca. */
    private static String campoComValorInvalido(HttpMessageNotReadableException ex) {
        Throwable t = ex.getCause();
        while (t != null) {
            try {
                Method getPath = t.getClass().getMethod("getPath");
                Object path = getPath.invoke(t);
                if (path instanceof List<?> refs && !refs.isEmpty()) {
                    Object ultimo = refs.get(refs.size() - 1);
                    Object nome = ultimo.getClass().getMethod("getPropertyName").invoke(ultimo);
                    if (nome != null) return nome.toString();
                }
            } catch (ReflectiveOperationException ignored) {
                // não é uma exceção do Jackson com caminho do campo
            }
            t = t.getCause();
        }
        return null;
    }

    private ResponseEntity<ApiErrorResponse> buildError(HttpStatus status, String message, String code, Map<String, String> fields, HttpServletRequest request) {
        return buildError(status, message, code, fields, request, novoRequestId());
    }

    private ResponseEntity<ApiErrorResponse> buildError(HttpStatus status, String message, String code, Map<String, String> fields,
                                                        HttpServletRequest request, String requestId) {
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
