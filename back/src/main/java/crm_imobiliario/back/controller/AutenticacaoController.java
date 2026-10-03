package crm_imobiliario.back.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import crm_imobiliario.back.model.dto.LoginDTO;
import crm_imobiliario.back.model.dto.SessaoResponse;
import crm_imobiliario.back.model.dto.UsuarioRetorno;
import crm_imobiliario.back.model.entity.Usuario;
import crm_imobiliario.back.model.service.SessaoService;
import crm_imobiliario.back.model.service.UsuarioService;
import crm_imobiliario.back.security.AuthCookies;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/login")
public class AutenticacaoController {
    @Autowired
    private AuthenticationManager manager;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private SessaoService sessaoService;

    @Autowired
    private AuthCookies authCookies;

    @PostMapping
    public ResponseEntity<SessaoResponse> efetuarLogin(@RequestBody @Valid LoginDTO login) {
        // lança BadCredentialsException/DisabledException → 401 via GlobalExceptionHandler;
        // o RateLimitFilter contabiliza a falha e zera os contadores no sucesso
        manager.authenticate(new UsernamePasswordAuthenticationToken(login.email(), login.senha()));

        Usuario usuarioLogado = usuarioService.buscarPorEmail(login.email());
        // senha correta, mas o usuário foi inativado: nenhuma sessão é emitida e a tela explica o motivo
        if (!usuarioLogado.isAtivo()) {
            throw new org.springframework.security.authentication.DisabledException("Usuário inativo");
        }
        var pair = sessaoService.emitir(usuarioLogado);

        var usuarioDTO = new UsuarioRetorno(
            usuarioLogado.getId(),
            usuarioLogado.getNome(),
            usuarioLogado.getEmail(),
            usuarioLogado.getPapel().getPapel(),
            usuarioLogado.isTrocarSenha()
        );

        // tokens só em cookies httpOnly — nunca no corpo
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, authCookies.access(pair.accessToken()).toString())
                .header(HttpHeaders.SET_COOKIE, authCookies.refresh(pair.refreshToken()).toString())
                .body(new SessaoResponse(usuarioDTO));
    }
}
