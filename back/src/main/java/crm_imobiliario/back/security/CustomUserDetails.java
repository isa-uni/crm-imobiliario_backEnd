package crm_imobiliario.back.security;

import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import crm_imobiliario.back.model.entity.Usuario;
import crm_imobiliario.back.model.service.UsuarioService;

public class CustomUserDetails implements UserDetailsService {

    private UsuarioService usuarioService;

    public CustomUserDetails(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Usuario usuario = usuarioService.buscarPorEmail(email);

        if (usuario == null) {
            throw new UsernameNotFoundException("Usuário não encontrado");
        }

        if (!usuario.isAtivo()) {
            throw new DisabledException("Usuário desativado");
        }

        return User.builder()
                .username(usuario.getEmail())
                .password(usuario.getSenha())
                .roles(usuario.getPapel().getPapel())
                .build();
    }
}
