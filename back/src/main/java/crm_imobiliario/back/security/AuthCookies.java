package crm_imobiliario.back.security;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import crm_imobiliario.back.model.service.TokenService;

/**
 * Fábrica única dos cookies httpOnly de autenticação.
 *
 * O refreshToken usa Path=/auth para ser enviado tanto em /auth/refresh quanto em /auth/logout —
 * com Path=/auth/refresh o navegador não o mandava no logout e o refresh nunca era revogado no
 * servidor. Ao limpar, também apaga o cookie legado com Path=/auth/refresh.
 */
@Component
public class AuthCookies {

    public static final String ACCESS = "accessToken";
    public static final String REFRESH = "refreshToken";
    private static final String REFRESH_PATH = "/auth";
    private static final String REFRESH_PATH_LEGADO = "/auth/refresh";

    private final TokenService tokenService;
    private final boolean secure;
    private final String sameSite;

    public AuthCookies(TokenService tokenService,
                       @Value("${api.security.cookie.secure:false}") boolean secure,
                       @Value("${api.security.cookie.same-site:Lax}") String sameSite) {
        this.tokenService = tokenService;
        this.secure = secure;
        this.sameSite = sameSite;
    }

    public ResponseCookie access(String token) {
        return cookie(ACCESS, token, "/", tokenService.getAccessExpiration() / 1000);
    }

    public ResponseCookie refresh(String token) {
        return cookie(REFRESH, token, REFRESH_PATH, tokenService.getRefreshExpiration() / 1000);
    }

    /** Cookies que apagam access e refresh (incluindo o caminho legado do refresh). */
    public List<ResponseCookie> limpar() {
        List<ResponseCookie> out = new ArrayList<>();
        out.add(cookie(ACCESS, "", "/", 0));
        out.add(cookie(REFRESH, "", REFRESH_PATH, 0));
        out.add(cookie(REFRESH, "", REFRESH_PATH_LEGADO, 0));
        return out;
    }

    /** Adiciona os cookies de limpeza à resposta. */
    public ResponseEntity.BodyBuilder limpar(ResponseEntity.BodyBuilder builder) {
        for (ResponseCookie c : limpar()) builder.header(HttpHeaders.SET_COOKIE, c.toString());
        return builder;
    }

    private ResponseCookie cookie(String nome, String valor, String path, long maxAgeSeg) {
        return ResponseCookie.from(nome, valor)
                .httpOnly(true).secure(secure).sameSite(sameSite)
                .path(path).maxAge(maxAgeSeg).build();
    }
}
