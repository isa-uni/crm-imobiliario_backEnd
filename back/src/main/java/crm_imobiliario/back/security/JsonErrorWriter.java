package crm_imobiliario.back.security;

import java.io.IOException;
import java.time.Instant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import crm_imobiliario.back.util.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Escreve um {@link ApiErrorResponse} direto na resposta — usado pelos filtros de segurança, que
 * rodam antes do DispatcherServlet e por isso não passam pelo GlobalExceptionHandler.
 */
final class JsonErrorWriter {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private JsonErrorWriter() {}

    static void escrever(HttpServletRequest request, HttpServletResponse response,
                         int status, String message, String code) throws IOException {
        escrever(request, response, status, ApiErrorResponse.builder()
                .success(false).message(message).code(code)
                .path(request.getRequestURI()).timestamp(Instant.now()).build());
    }

    static void escrever(HttpServletRequest request, HttpServletResponse response,
                         int status, ApiErrorResponse body) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(MAPPER.writeValueAsString(body));
    }
}
