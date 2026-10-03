package crm_imobiliario.back.security;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import crm_imobiliario.back.model.entity.Usuario;
import crm_imobiliario.back.model.repository.TokenBlacklistRepository;
import crm_imobiliario.back.model.repository.UsuarioRepository;
import crm_imobiliario.back.model.service.TokenService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class SecurityFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SecurityFilter.class);

    /** Únicos endpoints liberados enquanto o usuário ainda precisa trocar a senha inicial. */
    private static final Set<String> LIBERADOS_TROCA_SENHA = Set.of(
            "/usuarios/minha-senha", "/usuarios/me", "/auth/me", "/auth/logout", "/auth/refresh");

    @Autowired
    private TokenService tokenService;
    @Autowired
    private TokenBlacklistRepository blacklistRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Usuario usuario = autenticar(request);

        // troca de senha obrigatória imposta também no servidor (antes só o frontend redirecionava)
        if (usuario != null && usuario.isTrocarSenha() && !LIBERADOS_TROCA_SENHA.contains(request.getRequestURI())) {
            JsonErrorWriter.escrever(request, response, HttpServletResponse.SC_FORBIDDEN,
                    "Defina uma nova senha antes de continuar. A senha temporária só permite acessar a troca de senha.", "PASSWORD_CHANGE_REQUIRED");
            return;
        }
        filterChain.doFilter(request, response);
    }

    /**
     * Valida o token e, se tudo estiver certo, preenche o SecurityContext. Retorna o usuário
     * autenticado ou null (requisição segue anônima e a autorização decide: 401 se exigir login).
     */
    private Usuario autenticar(HttpServletRequest request) {
        String token = recuperarToken(request);
        if (token == null) return null;
        Claims claims = tokenService.parseClaims(token); // assinatura + expiração
        if (claims == null) return null;
        // só aceita access token (refresh token não serve para chamar a API)
        String type = claims.get("type", String.class);
        if (type != null && !"access".equals(type)) return null;
        // revogado no logout
        if (claims.getId() != null && blacklistRepository.existsByJti(claims.getId())) return null;

        String login = claims.getSubject();
        // uma única consulta ao usuário (antes: loadUserByUsername + buscarPorEmail)
        Usuario usuario = usuarioRepository.findByEmail(login).orElse(null);
        if (usuario == null || !usuario.isAtivo() || usuario.getPapel() == null) {
            log.debug("Token recusado: usuário {} inexistente, inativo ou sem papel", login);
            return null;
        }
        // tokenVersion: troca de senha/papel e inativação invalidam todos os tokens já emitidos
        Integer tokenVer = claims.get("tokenVersion", Integer.class);
        int dbVer = usuario.getTokenVersion() != null ? usuario.getTokenVersion() : 0;
        if ((tokenVer != null ? tokenVer : 0) != dbVer) return null;

        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getPapel().getPapel()));
        var authentication = new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        return usuario;
    }

    private String recuperarToken(HttpServletRequest request) {
        var header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        if (request.getCookies() != null) {
            for (Cookie c : request.getCookies()) {
                if (AuthCookies.ACCESS.equals(c.getName())) {
                    return c.getValue();
                }
            }
        }
        return null;
    }
}
