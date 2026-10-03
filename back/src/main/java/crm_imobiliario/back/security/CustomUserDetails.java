package crm_imobiliario.back.security;

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
        Usuario usuario;
        try {
            usuario = usuarioService.buscarPorEmail(email);
        } catch (RuntimeException e) {
            usuario = null;
        }
        // e-mail inexistente → UsernameNotFoundException, que o Spring converte em "E-mail ou senha incorretos"
        // (antes a exceção de "não encontrado" escapava e a tela de login dizia "Sua sessão expirou")
        if (usuario == null) {
            throw new UsernameNotFoundException("Usuário não encontrado");
        }

        // Usuário inativo NÃO é recusado aqui: exceções lançadas neste método são embrulhadas pelo Spring
        // em InternalAuthenticationServiceException, e o motivo se perdia ("Sua sessão expirou").
        // A recusa acontece no AutenticacaoController, depois de a senha ser conferida — assim o aviso de
        // inativação só aparece para quem sabe a senha e não revela quais e-mails existem.

        return User.builder()
                .username(usuario.getEmail())
                .password(usuario.getSenha())
                .roles(usuario.getPapel().getPapel())
                .build();
    }
}
