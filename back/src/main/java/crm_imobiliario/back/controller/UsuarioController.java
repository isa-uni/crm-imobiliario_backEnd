package crm_imobiliario.back.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

import crm_imobiliario.back.model.dto.DadosTokenJWT;
import crm_imobiliario.back.model.dto.NovaSenhaDTO;
import crm_imobiliario.back.model.dto.PerfilDTO;
import crm_imobiliario.back.model.dto.TrocarSenhaDTO;
import crm_imobiliario.back.model.dto.UsuarioDTO;
import crm_imobiliario.back.model.dto.UsuarioResponse;
import crm_imobiliario.back.model.dto.UsuarioRetorno;
import crm_imobiliario.back.model.entity.Usuario;
import crm_imobiliario.back.model.service.TokenService;
import crm_imobiliario.back.model.service.UsuarioService;
import crm_imobiliario.back.util.DefaultResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/usuarios")
public class UsuarioController {
    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private crm_imobiliario.back.model.repository.RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private crm_imobiliario.back.model.repository.TokenBlacklistRepository blacklistRepository;

    @org.springframework.beans.factory.annotation.Value("${api.security.cookie.secure:false}")
    private boolean cookieSecure;

    @org.springframework.beans.factory.annotation.Value("${api.security.cookie.same-site:Lax}")
    private String cookieSameSite;

    @PostMapping("/cadastrar")
    public ResponseEntity<?> cadastrarCliente(@RequestBody @Valid UsuarioDTO dto) {
        Usuario salvo = usuarioService.cadastrarUsuario(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(UsuarioResponse.from(salvo));
    }

    @DeleteMapping("/deletar/{id}")
    public ResponseEntity<?> deletarCliente(@PathVariable Long id){
        usuarioService.deletarUsuario(id);
        return ResponseEntity.ok(
                DefaultResponse.construir(
                        HttpStatus.OK.value(),
                        "Usuário deletado com sucesso",
                        null));
    }

    @GetMapping
    public ResponseEntity<List<UsuarioResponse>> getUsuarios() {
        List<UsuarioResponse> usuarios = usuarioService.ConsultarUsuarios();
        return ResponseEntity.ok(usuarios);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getUsuario(@PathVariable Long id) {
        return ResponseEntity.ok(UsuarioResponse.from(usuarioService.buscarPorId(id)));
    }

    @GetMapping("/me")
    public ResponseEntity<?> getMe(Authentication authentication) {
        Usuario usuario = usuarioService.buscarPorEmail(authentication.getName());
        return ResponseEntity.ok(UsuarioResponse.from(usuario));
    }

    @PutMapping("/me")
    public ResponseEntity<?> atualizarMe(@RequestBody @Valid PerfilDTO dto, Authentication authentication) {
        Usuario atualizado = usuarioService.atualizarPerfil(authentication.getName(), dto);
        // Alteração normal de perfil: papel/permissões permanecem iguais -> sem version++
        // Gera novo accessToken com claims atualizadas (nome/email) sem incrementar version
        String newAccessToken = tokenService.gerarAccessToken(atualizado);
        var usuarioResponse = UsuarioResponse.from(atualizado);
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("token", newAccessToken);
        body.put("usuario", usuarioResponse);
        org.springframework.http.ResponseCookie accessCookie = org.springframework.http.ResponseCookie.from("accessToken", newAccessToken)
                .httpOnly(true).secure(cookieSecure).sameSite(cookieSameSite)
                .path("/").maxAge(tokenService.getAccessExpiration() / 1000).build();
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.SET_COOKIE, accessCookie.toString())
                .body(body);
    }

    @PutMapping("/atualizar/{id}")
    public ResponseEntity<?> atualizarUsuario(@PathVariable Long id, @RequestBody @Valid UsuarioDTO dto,
                                              Authentication authentication,
                                              HttpServletRequest request,
                                              @CookieValue(value = "accessToken", required = false) String accessToken) {
        // Detecta mudança sensível de papel/permissões antes de atualizar
        Usuario antes = usuarioService.buscarPorId(id);
        String papelAntes = antes.getPapel() != null ? antes.getPapel().getPapel() : null;

        Usuario atualizado = usuarioService.atualizarUsuario(id, dto);

        String papelDepois = atualizado.getPapel() != null ? atualizado.getPapel().getPapel() : null;
        boolean papelMudou = (papelAntes == null && papelDepois != null) ||
                (papelAntes != null && !papelAntes.equals(papelDepois));

        if (papelMudou) {
            // Sensível: incrementa version e revoga refresh do alvo; invalida sessão apenas se o alvo for o próprio usuário autenticado
            usuarioService.forcarIncrementoVersion(atualizado.getId());
            try { refreshTokenRepository.deleteByUsuarioId(atualizado.getId()); } catch (Exception ignored) {}
            boolean isSelf = authentication != null && atualizado.getEmail() != null && atualizado.getEmail().equals(authentication.getName());
            if (isSelf) {
                invalidarAccessToken(request, accessToken);
                ResponseCookie clearAccess = ResponseCookie.from("accessToken", "").httpOnly(true).secure(cookieSecure).sameSite(cookieSameSite).path("/").maxAge(0).build();
                ResponseCookie clearRefresh = ResponseCookie.from("refreshToken", "").httpOnly(true).secure(cookieSecure).sameSite(cookieSameSite).path("/auth/refresh").maxAge(0).build();
                return ResponseEntity.status(HttpStatus.OK)
                        .header(HttpHeaders.SET_COOKIE, clearAccess.toString())
                        .header(HttpHeaders.SET_COOKIE, clearRefresh.toString())
                        .body(DefaultResponse.construir(200, "Usuário atualizado. Papel alterado exige nova autenticação.", null));
            }
            return ResponseEntity.ok()
                    .body(DefaultResponse.construir(200, "Usuário atualizado. Papel alterado exige nova autenticação do usuário.", null));
        }

        return ResponseEntity.ok(UsuarioResponse.from(atualizado));
    }

    @PutMapping("/minha-senha")
    public ResponseEntity<?> trocarMinhaSenha(@RequestBody @Valid TrocarSenhaDTO dto, Authentication authentication,
                                              HttpServletRequest request,
                                              @CookieValue(value = "accessToken", required = false) String accessToken) {
        usuarioService.trocarSenha(authentication.getName(), dto.senhaAtual(), dto.novaSenha());
        // Sensível: troca de senha já incrementou tokenVersion no service; revoga refresh e limpa cookies exigindo nova autenticação
        Usuario usuario = usuarioService.buscarPorEmail(authentication.getName());
        try { refreshTokenRepository.deleteByUsuarioId(usuario.getId()); } catch (Exception ignored) {}
        invalidarAccessToken(request, accessToken);
        ResponseCookie clearAccess = ResponseCookie.from("accessToken", "").httpOnly(true).secure(cookieSecure).sameSite(cookieSameSite).path("/").maxAge(0).build();
        ResponseCookie clearRefresh = ResponseCookie.from("refreshToken", "").httpOnly(true).secure(cookieSecure).sameSite(cookieSameSite).path("/auth/refresh").maxAge(0).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearAccess.toString())
                .header(HttpHeaders.SET_COOKIE, clearRefresh.toString())
                .body(DefaultResponse.construir(HttpStatus.OK.value(), "Senha alterada com sucesso. Faça login novamente.", null));
    }

    @PutMapping("/trocar-senha/{id}")
    public ResponseEntity<?> trocarSenhaUsuario(@PathVariable Long id, @RequestBody @Valid NovaSenhaDTO dto,
                                                Authentication authentication,
                                                HttpServletRequest request,
                                                @CookieValue(value = "accessToken", required = false) String accessToken) {
        usuarioService.trocarSenhaAdmin(id, dto.novaSenha());
        try { refreshTokenRepository.deleteByUsuarioId(id); } catch (Exception ignored) {}
        // Invalida sessão apenas se admin trocou a própria senha; para outros, apenas revoga refresh do alvo
        boolean isSelf = authentication != null && id != null && usuarioService.buscarPorId(id).getEmail().equals(authentication.getName());
        if (isSelf) {
            invalidarAccessToken(request, accessToken);
            ResponseCookie clearAccess = ResponseCookie.from("accessToken", "").httpOnly(true).secure(cookieSecure).sameSite(cookieSameSite).path("/").maxAge(0).build();
            ResponseCookie clearRefresh = ResponseCookie.from("refreshToken", "").httpOnly(true).secure(cookieSecure).sameSite(cookieSameSite).path("/auth/refresh").maxAge(0).build();
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, clearAccess.toString())
                    .header(HttpHeaders.SET_COOKIE, clearRefresh.toString())
                    .body(DefaultResponse.construir(HttpStatus.OK.value(), "Senha redefinida com sucesso. Faça login novamente.", null));
        }
        return ResponseEntity.ok()
                .body(DefaultResponse.construir(HttpStatus.OK.value(), "Senha redefinida com sucesso. O usuário precisará fazer login novamente.", null));
    }

    @PutMapping("/inativar/{id}")
    public ResponseEntity<?> inativarUsuario(@PathVariable Long id, Authentication auth,
                                             HttpServletRequest request,
                                             @CookieValue(value = "accessToken", required = false) String accessToken) {
        String email = auth != null ? auth.getName() : null;
        Usuario salvo = usuarioService.inativarUsuario(id, email);
        try { refreshTokenRepository.deleteByUsuarioId(id); } catch (Exception ignored) {}
        // Se inativou a si mesmo, limpar sessão
        if (auth != null && salvo.getEmail() != null && salvo.getEmail().equals(auth.getName())) {
            invalidarAccessToken(request, accessToken);
            ResponseCookie clearAccess = ResponseCookie.from("accessToken", "").httpOnly(true).secure(cookieSecure).sameSite(cookieSameSite).path("/").maxAge(0).build();
            ResponseCookie clearRefresh = ResponseCookie.from("refreshToken", "").httpOnly(true).secure(cookieSecure).sameSite(cookieSameSite).path("/auth/refresh").maxAge(0).build();
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, clearAccess.toString())
                    .header(HttpHeaders.SET_COOKIE, clearRefresh.toString())
                    .body(UsuarioResponse.from(salvo));
        }
        return ResponseEntity.ok(UsuarioResponse.from(salvo));
    }

    @PutMapping("/ativar/{id}")
    public ResponseEntity<?> ativarUsuario(@PathVariable Long id,
                                           HttpServletRequest request,
                                           @CookieValue(value = "accessToken", required = false) String accessToken) {
        Usuario salvo = usuarioService.ativarUsuario(id);
        try { refreshTokenRepository.deleteByUsuarioId(id); } catch (Exception ignored) {}
        return ResponseEntity.ok(UsuarioResponse.from(salvo));
    }

    private void invalidarAccessToken(HttpServletRequest request, String accessTokenCookie) {
        String token = accessTokenCookie;
        if (token == null) {
            String h = request.getHeader("Authorization");
            if (h != null && h.startsWith("Bearer ")) token = h.substring(7);
        }
        if (token != null) {
            try {
                String jti = tokenService.getJti(token);
                io.jsonwebtoken.Claims claims = tokenService.parseClaims(token);
                if (jti != null && claims != null) {
                    crm_imobiliario.back.model.entity.TokenBlacklist bl = new crm_imobiliario.back.model.entity.TokenBlacklist();
                    bl.setJti(jti);
                    bl.setExpiresAt(claims.getExpiration().toInstant());
                    try { blacklistRepository.save(bl); } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}
        }
    }
}
