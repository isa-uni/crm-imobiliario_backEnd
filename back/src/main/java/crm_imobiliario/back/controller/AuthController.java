package crm_imobiliario.back.controller;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import crm_imobiliario.back.model.dto.DadosTokenJWT;
import crm_imobiliario.back.model.dto.UsuarioRetorno;
import crm_imobiliario.back.model.entity.RefreshToken;
import crm_imobiliario.back.model.entity.TokenBlacklist;
import crm_imobiliario.back.model.entity.Usuario;
import crm_imobiliario.back.model.repository.RefreshTokenRepository;
import crm_imobiliario.back.model.repository.TokenBlacklistRepository;
import crm_imobiliario.back.model.repository.UsuarioRepository;
import crm_imobiliario.back.model.service.TokenService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired private TokenService tokenService;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private TokenBlacklistRepository blacklistRepository;

    @Value("${api.security.cookie.secure:false}") private boolean cookieSecure;
    @Value("${api.security.cookie.same-site:Lax}") private String cookieSameSite;

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@CookieValue(value = "refreshToken", required = false) String refreshToken,
                                     HttpServletRequest request) {
        if (refreshToken == null) {
            // tenta Authorization header fallback
            String h = request.getHeader("Authorization");
            if (h != null && h.startsWith("Bearer ")) refreshToken = h.substring(7);
        }
        if (refreshToken == null) return ResponseEntity.status(401).body("{\"error\":\"Refresh token ausente\"}");
        String email = tokenService.validarRefreshToken(refreshToken);
        if (email == null) return ResponseEntity.status(401).body("{\"error\":\"Refresh token inválido ou expirado\"}");
        String jti = tokenService.getJti(refreshToken);
        var stored = refreshTokenRepository.findByJti(jti).orElse(null);
        if (stored == null || stored.isRevoked() || stored.getExpiresAt().isBefore(Instant.now())) {
            return ResponseEntity.status(401).body("{\"error\":\"Refresh token revogado\"}");
        }
        Usuario usuario = usuarioRepository.findByEmail(email).orElse(null);
        if (usuario == null || !usuario.isAtivo()) return ResponseEntity.status(401).body("{\"error\":\"Usuário inválido\"}");

        // rotaciona: revoga antigo
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        var pair = tokenService.gerarTokens(usuario);
        RefreshToken rt = new RefreshToken();
        rt.setUsuario(usuario);
        rt.setJti(pair.refreshJti());
        rt.setExpiresAt(Instant.now().plusMillis(tokenService.getRefreshExpiration()));
        refreshTokenRepository.save(rt);

        ResponseCookie accessCookie = ResponseCookie.from("accessToken", pair.accessToken())
                .httpOnly(true).secure(cookieSecure).sameSite(cookieSameSite).path("/").maxAge(tokenService.getAccessExpiration()/1000).build();
        // Unificado: refreshToken sempre com Path=/auth/refresh
        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", pair.refreshToken())
                .httpOnly(true).secure(cookieSecure).sameSite(cookieSameSite).path("/auth/refresh").maxAge(tokenService.getRefreshExpiration()/1000).build();

        var dto = new UsuarioRetorno(usuario.getId(), usuario.getNome(), usuario.getEmail(), usuario.getPapel().getPapel(), usuario.isTrocarSenha());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(new DadosTokenJWT(pair.accessToken(), dto));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(Authentication auth, @CookieValue(value="accessToken", required=false) String access,
                                    @CookieValue(value="refreshToken", required=false) String refresh) {
        if (access != null) {
            String jti = tokenService.getJti(access);
            Claims c = tokenService.parseClaims(access);
            if (jti != null && c != null) {
                TokenBlacklist bl = new TokenBlacklist();
                bl.setJti(jti);
                bl.setExpiresAt(c.getExpiration().toInstant());
                try { blacklistRepository.save(bl); } catch (Exception ignored){}
            }
        }
        if (refresh != null) {
            String jti = tokenService.getJti(refresh);
            refreshTokenRepository.findByJti(jti).ifPresent(rt -> { rt.setRevoked(true); refreshTokenRepository.save(rt); });
        }
        ResponseCookie clearAccess = ResponseCookie.from("accessToken","").httpOnly(true).secure(cookieSecure).sameSite(cookieSameSite).path("/").maxAge(0).build();
        ResponseCookie clearRefresh = ResponseCookie.from("refreshToken","").httpOnly(true).secure(cookieSecure).sameSite(cookieSameSite).path("/").maxAge(0).build();
        ResponseCookie clearRefresh2 = ResponseCookie.from("refreshToken","").httpOnly(true).secure(cookieSecure).sameSite(cookieSameSite).path("/auth/refresh").maxAge(0).build();
        ResponseCookie clearRefresh3 = ResponseCookie.from("refreshToken","").httpOnly(true).secure(cookieSecure).sameSite(cookieSameSite).path("/auth/logout").maxAge(0).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearAccess.toString())
                .header(HttpHeaders.SET_COOKIE, clearRefresh.toString())
                .header(HttpHeaders.SET_COOKIE, clearRefresh2.toString())
                .header(HttpHeaders.SET_COOKIE, clearRefresh3.toString())
                .body("{\"message\":\"Logout realizado\"}");
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication auth) {
        if (auth == null) return ResponseEntity.status(401).body("{\"error\":\"Não autenticado\"}");
        Usuario u = usuarioRepository.findByEmail(auth.getName()).orElse(null);
        if (u == null) return ResponseEntity.status(401).body("{\"error\":\"Usuário não encontrado\"}");
        return ResponseEntity.ok(new UsuarioRetorno(u.getId(), u.getNome(), u.getEmail(), u.getPapel().getPapel(), u.isTrocarSenha()));
    }
}
