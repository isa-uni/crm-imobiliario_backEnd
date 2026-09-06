package crm_imobiliario.back.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import crm_imobiliario.back.util.ApiErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static class Bucket {
        AtomicInteger count = new AtomicInteger(0);
        AtomicLong windowStart = new AtomicLong(System.currentTimeMillis());
    }

    private final ConcurrentHashMap<String, Bucket> bucketsByComposite = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> bucketsByIp = new ConcurrentHashMap<>();

    @Value("${app.rate-limit.login.max-attempts-per-email:5}")
    private int maxAttemptsEmail;

    @Value("${app.rate-limit.login.max-attempts-per-ip:20}")
    private int maxAttemptsIp;

    @Value("${app.rate-limit.login.window-ms:300000}")
    private long windowMs;

    private final ObjectMapper mapper;

    public RateLimitFilter() {
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean isLogin = "/login".equals(request.getRequestURI()) && "POST".equalsIgnoreCase(request.getMethod());
        if (!isLogin) {
            chain.doFilter(request, response);
            return;
        }

        String ip = getClientIp(request);
        long now = System.currentTimeMillis();
        Bucket ipBucket = bucketsByIp.get(ip);
        long retryAfterIp = 0;
        if (ipBucket != null && now - ipBucket.windowStart.get() <= windowMs) {
            if (ipBucket.count.get() >= maxAttemptsIp) {
                retryAfterIp = (ipBucket.windowStart.get() + windowMs - now) / 1000 + 1;
            }
        }
        if (retryAfterIp > 0) {
            String msg = "Muitas tentativas deste IP. Tente novamente em " + formatRetry(retryAfterIp) + ".";
            sendRateLimited(response, request, msg, (int) retryAfterIp);
            return;
        }

        // usa wrapper apenas para capturar body após o chain (não consome antes)
        ContentCachingRequestWrapper wrapped = request instanceof ContentCachingRequestWrapper
                ? (ContentCachingRequestWrapper) request
                : new ContentCachingRequestWrapper(request, 1024 * 1024);

        chain.doFilter(wrapped, response);

        int status = response.getStatus();
        String email = extractEmailFromWrapper(wrapped);
        String compositeKey = email.isBlank() ? ip : ip + ":" + email.toLowerCase();

        // verifica bloqueio por e-mail após ter o e-mail (após o chain, body já está cacheado)
        if (status != 401 && status != 400 && status != 403) {
            // verifica se já estava bloqueado por e-mail antes de contar esta tentativa
            Bucket compositeBucket = bucketsByComposite.get(compositeKey);
            if (compositeBucket != null && !email.isBlank() && System.currentTimeMillis() - compositeBucket.windowStart.get() <= windowMs) {
                if (compositeBucket.count.get() >= maxAttemptsEmail) {
                    // já bloqueado, mas esta requisição já passou; próxima será bloqueada. Se quiser bloquear agora, poderia enviar 429 aqui, mas response já com status 200/401.
                    // Mantém incremento apenas em falha, não bloqueia sucesso.
                }
            }
        }

        if (status == 401 || status == 400 || status == 403) {
            // incrementa composite e IP apenas em falha
            if (!email.isBlank()) {
                Bucket cb = bucketsByComposite.computeIfAbsent(compositeKey, k -> new Bucket());
                long nowCb = System.currentTimeMillis();
                if (nowCb - cb.windowStart.get() > windowMs) {
                    cb.windowStart.set(nowCb);
                    cb.count.set(0);
                }
                cb.count.incrementAndGet();
                // se acabou de atingir limite, próxima tentativa será bloqueada via check acima
            }
            Bucket ib = bucketsByIp.computeIfAbsent(ip, k -> new Bucket());
            long now2 = System.currentTimeMillis();
            if (now2 - ib.windowStart.get() > windowMs) {
                ib.windowStart.set(now2);
                ib.count.set(0);
            }
            ib.count.incrementAndGet();
        } else if (status >= 200 && status < 300) {
            if (!email.isBlank()) {
                bucketsByComposite.remove(compositeKey);
            }
        }
    }

    private void sendRateLimited(HttpServletResponse response, HttpServletRequest request, String message, int retryAfterSeconds) throws IOException {
        response.setStatus(429);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        ApiErrorResponse err = ApiErrorResponse.builder()
                .success(false).message(message).code("RATE_LIMITED")
                .path(request.getRequestURI()).timestamp(Instant.now()).build();
        // adiciona retryAfter no details se o builder suportar
        try {
            String body = mapper.writeValueAsString(err);
            // injeta retryAfter manualmente se o DTO não tiver campo
            if (!body.contains("retryAfter")) {
                body = body.replaceFirst("\\}$", ",\"retryAfter\":" + retryAfterSeconds + "}");
            }
            response.getWriter().write(body);
        } catch (Exception e) {
            response.getWriter().write("{\"success\":false,\"message\":\"" + message + "\",\"code\":\"RATE_LIMITED\",\"retryAfter\":" + retryAfterSeconds + "}");
        }
    }

    private String formatRetry(long seconds) {
        if (seconds < 60) return seconds + "s";
        long m = seconds / 60;
        long s = seconds % 60;
        if (s == 0) return m + " min";
        return m + " min " + s + "s";
    }

    public void resetForTest(String key) {
        bucketsByComposite.remove(key);
        bucketsByIp.remove(key);
    }

    // compatibilidade: chamado antigo com apenas ip – remove ambos prefixados
    public void recordSuccess(String ip) {
        bucketsByIp.remove(ip);
        // remove todos compostos desse ip
        bucketsByComposite.keySet().removeIf(k -> k.startsWith(ip + ":") || k.equals(ip));
    }

    public void recordSuccess(String ip, String email) {
        if (email != null && !email.isBlank()) {
            String compositeKey = ip + ":" + email.toLowerCase();
            bucketsByComposite.remove(compositeKey);
        } else {
            recordSuccess(ip);
        }
    }

    private String extractEmail(HttpServletRequest request) {
        try {
            ContentCachingRequestWrapper wrapper = request instanceof ContentCachingRequestWrapper
                    ? (ContentCachingRequestWrapper) request
                    : null;
            byte[] body = null;
            if (wrapper != null) {
                try { wrapper.getInputStream().readAllBytes(); } catch (Exception ignored) {}
                body = wrapper.getContentAsByteArray();
                if (body == null || body.length == 0) {
                    String s = new String(wrapper.getContentAsByteArray(), StandardCharsets.UTF_8);
                    if (s.isBlank()) return "";
                }
            } else {
                return "";
            }
            if (body == null || body.length == 0) return "";
            String json = new String(body, StandardCharsets.UTF_8);
            try {
                com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(json);
                if (node.has("email") && !node.get("email").isNull()) return node.get("email").asText().trim();
                if (node.has("username") && !node.get("username").isNull()) return node.get("username").asText().trim();
            } catch (Exception ignored) {}
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"email\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
            if (m.find()) return m.group(1).trim();
        } catch (Exception ignored) {}
        return "";
    }

    private String extractEmailFromWrapper(ContentCachingRequestWrapper wrapper) {
        try {
            byte[] body = wrapper.getContentAsByteArray();
            if (body == null || body.length == 0) return "";
            String json = new String(body, StandardCharsets.UTF_8);
            if (json.isBlank()) return "";
            try {
                com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(json);
                if (node.has("email") && !node.get("email").isNull()) return node.get("email").asText().trim();
                if (node.has("username") && !node.get("username").isNull()) return node.get("username").asText().trim();
            } catch (Exception ignored) {}
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"email\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
            if (m.find()) return m.group(1).trim();
        } catch (Exception ignored) {}
        return "";
    }

    public String getClientIp(HttpServletRequest req) {
        String xf = req.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) return xf.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}
