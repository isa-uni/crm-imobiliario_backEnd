package crm_imobiliario.back.model.service;

import java.util.Date;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import crm_imobiliario.back.model.entity.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

@Service
public class TokenService {

    @Value("${api.security.token.secret}")
    private String secret;

    @Value("${api.security.token.access-expiration:900000}")
    private long accessExpiration;

    @Value("${api.security.token.refresh-expiration:604800000}")
    private long refreshExpiration;

    private static final String SEGREDO_DEV = "minha-chave-dev-apenas-para-desenvolvimento";

    /** Avisa no startup quando o segredo de desenvolvimento (público no repositório) está em uso. */
    @jakarta.annotation.PostConstruct
    void verificarSegredo() {
        if (secret == null || secret.getBytes().length < 32) {
            throw new IllegalStateException("api.security.token.secret (JWT_SECRET) precisa ter ao menos 32 bytes para HS256");
        }
        if (secret.startsWith(SEGREDO_DEV)) {
            org.slf4j.LoggerFactory.getLogger(TokenService.class).warn(
                    "JWT_SECRET não definido: usando o segredo de desenvolvimento. Defina JWT_SECRET em produção — "
                    + "com o segredo padrão qualquer pessoa consegue forjar tokens.");
        }
    }

    public record TokenPair(String accessToken, String refreshToken, String accessJti, String refreshJti) {}

    public TokenPair gerarTokens(Usuario usuario) {
        String accessJti = UUID.randomUUID().toString();
        String refreshJti = UUID.randomUUID().toString();
        String access = Jwts.builder()
                .setSubject(usuario.getEmail())
                .setId(accessJti)
                .claim("roles", usuario.getPapel() != null ? usuario.getPapel().getPapel() : "")
                .claim("tokenVersion", usuario.getTokenVersion() != null ? usuario.getTokenVersion() : 0)
                .claim("type", "access")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + accessExpiration))
                .setIssuer("crm-imobiliario")
                .signWith(Keys.hmacShaKeyFor(secret.getBytes()), SignatureAlgorithm.HS256)
                .compact();
        String refresh = Jwts.builder()
                .setSubject(usuario.getEmail())
                .setId(refreshJti)
                .claim("type", "refresh")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + refreshExpiration))
                .setIssuer("crm-imobiliario")
                .signWith(Keys.hmacShaKeyFor(secret.getBytes()), SignatureAlgorithm.HS256)
                .compact();
        return new TokenPair(access, refresh, accessJti, refreshJti);
    }

    public String gerarAccessToken(Usuario usuario) {
        String accessJti = UUID.randomUUID().toString();
        return Jwts.builder()
                .setSubject(usuario.getEmail())
                .setId(accessJti)
                .claim("roles", usuario.getPapel() != null ? usuario.getPapel().getPapel() : "")
                .claim("tokenVersion", usuario.getTokenVersion() != null ? usuario.getTokenVersion() : 0)
                .claim("type", "access")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + accessExpiration))
                .setIssuer("crm-imobiliario")
                .signWith(Keys.hmacShaKeyFor(secret.getBytes()), SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims parseClaims(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(Keys.hmacShaKeyFor(secret.getBytes()))
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (Exception e) {
            return null;
        }
    }

    public String validarRefreshToken(String token) {
        Claims c = parseClaims(token);
        if (c == null) return null;
        String type = c.get("type", String.class);
        if (!"refresh".equals(type)) return null;
        return c.getSubject();
    }

    public String getJti(String token) {
        Claims c = parseClaims(token);
        return c != null ? c.getId() : null;
    }

    public Integer getTokenVersion(String token) {
        Claims c = parseClaims(token);
        return c != null ? c.get("tokenVersion", Integer.class) : null;
    }

    public long getAccessExpiration() { return accessExpiration; }
    public long getRefreshExpiration() { return refreshExpiration; }
}
