package crm_imobiliario.back.controller;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import crm_imobiliario.back.model.dto.DadosTokenJWT;
import crm_imobiliario.back.model.dto.LoginDTO;
import crm_imobiliario.back.model.dto.UsuarioRetorno;
import crm_imobiliario.back.model.entity.RefreshToken;
import crm_imobiliario.back.model.entity.Usuario;
import crm_imobiliario.back.model.repository.RefreshTokenRepository;
import crm_imobiliario.back.model.repository.UsuarioRepository;
import crm_imobiliario.back.model.service.TokenService;
import crm_imobiliario.back.security.RateLimitFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/login")
public class AutenticacaoController {
    @Autowired
    private AuthenticationManager manager;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private RateLimitFilter rateLimitFilter;

    @Value("${api.security.cookie.secure:false}")
    private boolean cookieSecure;

    @Value("${api.security.cookie.same-site:Lax}")
    private String cookieSameSite;

    @PostMapping
    public ResponseEntity efetuarLogin(@RequestBody @Valid LoginDTO login, HttpServletRequest request) {
        var authenticationToken = new UsernamePasswordAuthenticationToken(login.email(), login.senha());

        var authentication = manager.authenticate(authenticationToken);

        String email = login.email();

        Usuario usuarioLogado = usuarioRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

        var pair = tokenService.gerarTokens(usuarioLogado);

        RefreshToken rt = new RefreshToken();
        rt.setUsuario(usuarioLogado);
        rt.setJti(pair.refreshJti());
        rt.setExpiresAt(Instant.now().plusMillis(tokenService.getRefreshExpiration()));
        refreshTokenRepository.save(rt);

        // limpa rate limit após sucesso (mesma chave ip:email usada no filtro)
        String clientIp = rateLimitFilter.getClientIp(request);
        rateLimitFilter.recordSuccess(clientIp, email);

        var usuarioDTO = new UsuarioRetorno(
            usuarioLogado.getId(),
            usuarioLogado.getNome(),
            usuarioLogado.getEmail(),
            usuarioLogado.getPapel().getPapel(),
            usuarioLogado.isTrocarSenha()
        );

        ResponseCookie accessCookie = ResponseCookie.from("accessToken", pair.accessToken())
                .httpOnly(true).secure(cookieSecure).sameSite(cookieSameSite)
                .path("/").maxAge(tokenService.getAccessExpiration() / 1000).build();
        // Unificado: refreshToken sempre com Path=/auth/refresh (evita duplicidade Path=/)
        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", pair.refreshToken())
                .httpOnly(true).secure(cookieSecure).sameSite(cookieSameSite)
                .path("/auth/refresh").maxAge(tokenService.getRefreshExpiration() / 1000).build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(new DadosTokenJWT(pair.accessToken(), usuarioDTO));
    }
}
