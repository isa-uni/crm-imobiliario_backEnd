package crm_imobiliario.back.model.service;

import java.time.Instant;
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

    public String gerarTokenLegacy(org.springframework.security.core.userdetails.UserDetails usuario) {
        // mantido para compatibilidade se necessário
        return Jwts.builder()
                .setSubject(usuario.getUsername())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + accessExpiration))
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

    public String validarToken(String token) {
        Claims c = parseClaims(token);
        if (c == null) return null;
        if (!"access".equals(c.get("type", String.class))) {
            // aceita tokens antigos sem type para retrocompat
            if (c.get("type") != null) return null;
        }
        return c.getSubject();
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
