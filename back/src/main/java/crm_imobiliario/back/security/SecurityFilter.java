package crm_imobiliario.back.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import crm_imobiliario.back.model.entity.Usuario;
import crm_imobiliario.back.model.repository.TokenBlacklistRepository;
import crm_imobiliario.back.model.service.TokenService;
import crm_imobiliario.back.model.service.UsuarioService;
import io.jsonwebtoken.Claims;

import java.io.IOException;

@Component
public class SecurityFilter extends OncePerRequestFilter {

    @Autowired
    private TokenService tokenService;
    @Autowired
    private UserDetailsService userDetailsService;
    @Autowired
    private TokenBlacklistRepository blacklistRepository;
    @Autowired
    private UsuarioService usuarioService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = recuperarToken(request);
        if (token != null) {
            Claims claims = tokenService.parseClaims(token);
            if (claims != null) {
                String login = claims.getSubject();
                String jti = claims.getId();
                String type = claims.get("type", String.class);
                // só aceita access
                if (type != null && !"access".equals(type)) {
                    filterChain.doFilter(request, response);
                    return;
                }
                if (jti != null && blacklistRepository.existsByJti(jti)) {
                    filterChain.doFilter(request, response);
                    return;
                }
                // tokenVersion check
                Integer tokenVer = claims.get("tokenVersion", Integer.class);
                try {
                    UserDetails userDetails = userDetailsService.loadUserByUsername(login);
                    Usuario usuario = usuarioService.buscarPorEmail(login);
                    int dbVer = usuario.getTokenVersion() != null ? usuario.getTokenVersion() : 0;
                    int tv = tokenVer != null ? tokenVer : 0;
                    if (tv != dbVer) {
                        filterChain.doFilter(request, response);
                        return;
                    }
                    var authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } catch (Exception e) {
                    org.slf4j.LoggerFactory.getLogger(SecurityFilter.class).debug("Falha ao autenticar usuário {}: {}", login, e.getMessage());
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    private String recuperarToken(HttpServletRequest request) {
        var header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        if (request.getCookies() != null) {
            for (Cookie c : request.getCookies()) {
                if ("accessToken".equals(c.getName())) {
                    return c.getValue();
                }
            }
        }
        return null;
    }
}
