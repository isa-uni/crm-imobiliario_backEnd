package crm_imobiliario.back.security;

import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import crm_imobiliario.back.util.ApiErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Limite de tentativas de login (janela fixa): por IP+e-mail (protege uma conta específica) e por IP
 * (protege contra varredura de várias contas). Ambos são verificados ANTES de autenticar.
 *
 * O estado fica em memória: é perdido ao reiniciar e não é compartilhado entre instâncias — para
 * rodar mais de uma instância seria preciso um armazenamento comum (ex.: Redis/Bucket4j).
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_BODY_BYTES = 16 * 1024;

    private static class Bucket {
        final AtomicInteger count = new AtomicInteger(0);
        final AtomicLong windowStart = new AtomicLong(System.currentTimeMillis());
    }

    private final ConcurrentHashMap<String, Bucket> bucketsByComposite = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> bucketsByIp = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${app.rate-limit.login.max-attempts-per-email:5}")
    private int maxAttemptsEmail;

    @Value("${app.rate-limit.login.max-attempts-per-ip:20}")
    private int maxAttemptsIp;

    @Value("${app.rate-limit.login.window-ms:300000}")
    private long windowMs;

    /**
     * Só confia em X-Forwarded-For quando a API está atrás de um proxy reverso que sobrescreve o
     * header. Sem proxy, o cliente pode forjá-lo a cada tentativa e escapar do limite por IP.
     */
    @Value("${app.rate-limit.trust-forwarded-for:false}")
    private boolean trustForwardedFor;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean isLogin = "/login".equals(request.getRequestURI()) && "POST".equalsIgnoreCase(request.getMethod());
        if (!isLogin) {
            chain.doFilter(request, response);
            return;
        }

        // lê o corpo uma vez (para saber o e-mail antes de autenticar) e o reapresenta ao controller
        CachedBodyRequest cached = new CachedBodyRequest(request, request.getInputStream().readNBytes(MAX_BODY_BYTES));
        String ip = getClientIp(request);
        String email = extrairEmail(cached.body);
        String compositeKey = email.isBlank() ? null : ip + ":" + email.toLowerCase();

        long retryEmail = compositeKey != null ? segundosBloqueado(bucketsByComposite.get(compositeKey), maxAttemptsEmail) : 0;
        if (retryEmail > 0) {
            sendRateLimited(response, request, "Muitas tentativas para este e-mail. Tente novamente em " + formatRetry(retryEmail) + ".", retryEmail);
            return;
        }
        long retryIp = segundosBloqueado(bucketsByIp.get(ip), maxAttemptsIp);
        if (retryIp > 0) {
            sendRateLimited(response, request, "Muitas tentativas deste IP. Tente novamente em " + formatRetry(retryIp) + ".", retryIp);
            return;
        }

        chain.doFilter(cached, response);

        int status = response.getStatus();
        if (status == 401 || status == 400 || status == 403) {
            if (compositeKey != null) incrementar(bucketsByComposite, compositeKey);
            incrementar(bucketsByIp, ip);
        } else if (status >= 200 && status < 300 && compositeKey != null) {
            bucketsByComposite.remove(compositeKey);
        }
    }

    /** Segundos restantes de bloqueio, ou 0 se o bucket está abaixo do limite ou com a janela vencida. */
    private long segundosBloqueado(Bucket bucket, int limite) {
        if (bucket == null) return 0;
        long now = System.currentTimeMillis();
        if (now - bucket.windowStart.get() > windowMs) return 0;
        if (bucket.count.get() < limite) return 0;
        return (bucket.windowStart.get() + windowMs - now) / 1000 + 1;
    }

    private void incrementar(ConcurrentHashMap<String, Bucket> buckets, String chave) {
        Bucket b = buckets.computeIfAbsent(chave, k -> new Bucket());
        long now = System.currentTimeMillis();
        if (now - b.windowStart.get() > windowMs) {
            b.windowStart.set(now);
            b.count.set(0);
        }
        b.count.incrementAndGet();
    }

    private void sendRateLimited(HttpServletResponse response, HttpServletRequest request, String message, long retryAfterSeconds) throws IOException {
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        ApiErrorResponse err = ApiErrorResponse.builder()
                .success(false).message(message).code("RATE_LIMITED")
                .details(java.util.Map.of("retryAfter", retryAfterSeconds))
                .path(request.getRequestURI()).timestamp(Instant.now()).build();
        JsonErrorWriter.escrever(request, response, 429, err);
    }

    private String formatRetry(long seconds) {
        if (seconds < 60) return seconds + "s";
        long m = seconds / 60;
        long s = seconds % 60;
        if (s == 0) return m + " min";
        return m + " min " + s + "s";
    }

    private String extrairEmail(byte[] body) {
        if (body == null || body.length == 0) return "";
        try {
            JsonNode node = mapper.readTree(body);
            if (node != null && node.hasNonNull("email")) return node.get("email").asText().trim();
        } catch (Exception ignored) {
            // corpo inválido: o controller devolverá 400; conta só no limite por IP
        }
        return "";
    }

    public String getClientIp(HttpServletRequest req) {
        if (trustForwardedFor) {
            String xf = req.getHeader("X-Forwarded-For");
            if (xf != null && !xf.isBlank()) return xf.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }

    /** Request cujo corpo já foi lido e pode ser relido pelo controller. */
    private static class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream in = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public int read() { return in.read(); }
                @Override public int read(byte[] b, int off, int len) { return in.read(b, off, len); }
                @Override public boolean isFinished() { return in.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException(); }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }

        @Override
        public int getContentLength() { return body.length; }

        @Override
        public long getContentLengthLong() { return body.length; }
    }
}
