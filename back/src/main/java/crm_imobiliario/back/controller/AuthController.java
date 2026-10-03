package crm_imobiliario.back.controller;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import crm_imobiliario.back.model.dto.SessaoResponse;
import crm_imobiliario.back.model.dto.UsuarioRetorno;
import crm_imobiliario.back.model.entity.Usuario;
import crm_imobiliario.back.model.repository.RefreshTokenRepository;
import crm_imobiliario.back.model.repository.UsuarioRepository;
import crm_imobiliario.back.model.service.SessaoService;
import crm_imobiliario.back.model.service.TokenService;
import crm_imobiliario.back.security.AuthCookies;
import crm_imobiliario.back.util.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired private TokenService tokenService;
    @Autowired private SessaoService sessaoService;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private AuthCookies authCookies;

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@CookieValue(value = AuthCookies.REFRESH, required = false) String refreshToken,
                                     HttpServletRequest request) {
        if (refreshToken == null) {
            // fallback para clientes não-navegador (ex.: Postman) que enviam o refresh no header
            String h = request.getHeader("Authorization");
            if (h != null && h.startsWith("Bearer ")) refreshToken = h.substring(7);
        }
        if (refreshToken == null) return naoAutorizado("Sua sessão expirou. Faça login novamente.", request);
        String email = tokenService.validarRefreshToken(refreshToken);
        if (email == null) return naoAutorizado("Sua sessão expirou. Faça login novamente.", request);
        var stored = refreshTokenRepository.findByJti(tokenService.getJti(refreshToken)).orElse(null);
        if (stored == null || stored.isRevoked() || stored.getExpiresAt().isBefore(Instant.now())) {
            return naoAutorizado("Sua sessão foi encerrada. Faça login novamente.", request);
        }
        Usuario usuario = usuarioRepository.findByEmail(email).orElse(null);
        if (usuario == null || !usuario.isAtivo()) return naoAutorizado("Seu usuário não está mais ativo. Procure um administrador.", request);

        // rotação: o refresh usado é revogado e um par novo é emitido
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);
        var pair = sessaoService.emitir(usuario);

        var dto = new UsuarioRetorno(usuario.getId(), usuario.getNome(), usuario.getEmail(), usuario.getPapel().getPapel(), usuario.isTrocarSenha());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, authCookies.access(pair.accessToken()).toString())
                .header(HttpHeaders.SET_COOKIE, authCookies.refresh(pair.refreshToken()).toString())
                .body(new SessaoResponse(dto));
    }

    /**
     * Público: revoga apenas os tokens apresentados pelo próprio cliente. Precisa funcionar mesmo
     * com o access token já expirado — senão o refresh token continuaria válido no servidor.
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@CookieValue(value = AuthCookies.ACCESS, required = false) String access,
                                    @CookieValue(value = AuthCookies.REFRESH, required = false) String refresh,
                                    HttpServletRequest request) {
        if (access == null) {
            String h = request.getHeader("Authorization");
            if (h != null && h.startsWith("Bearer ")) access = h.substring(7);
        }
        sessaoService.revogarAccessToken(access);
        sessaoService.revogarRefreshToken(refresh);
        return authCookies.limpar(ResponseEntity.ok())
                .body(java.util.Map.of("message", "Você saiu do sistema."));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication auth, HttpServletRequest request) {
        if (auth == null) return naoAutorizado("Sua sessão expirou. Faça login novamente.", request);
        Usuario u = usuarioRepository.findByEmail(auth.getName()).orElse(null);
        if (u == null) return naoAutorizado("Sua sessão não corresponde a um usuário válido. Faça login novamente.", request);
        return ResponseEntity.ok(new UsuarioRetorno(u.getId(), u.getNome(), u.getEmail(), u.getPapel().getPapel(), u.isTrocarSenha()));
    }

    private ResponseEntity<ApiErrorResponse> naoAutorizado(String mensagem, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiErrorResponse.of(mensagem, "AUTH_REQUIRED", request.getRequestURI()));
    }
}
