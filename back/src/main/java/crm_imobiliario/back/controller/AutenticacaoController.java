package crm_imobiliario.back.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import crm_imobiliario.back.model.dto.DadosTokenJWT;
import crm_imobiliario.back.model.dto.LoginDTO;
import crm_imobiliario.back.model.dto.UsuarioRetorno;
import crm_imobiliario.back.model.entity.Usuario;
import crm_imobiliario.back.model.repository.UsuarioRepository;
import crm_imobiliario.back.model.service.TokenService;
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

    @PostMapping
    public ResponseEntity efetuarLogin(@RequestBody @Valid LoginDTO login) {
        var authenticationToken = new UsernamePasswordAuthenticationToken(login.email(), login.senha());

        var authentication = manager.authenticate(authenticationToken);

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();

        var tokenJWT = tokenService.gerarToken(userDetails);

        String email = userDetails.getUsername();

        Usuario usuarioLogado = usuarioRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

        // var tokenJWT = tokenService.gerarToken((UserDetails) authentication.getPrincipal());

        var usuarioDTO = new UsuarioRetorno(
            usuarioLogado.getId(),
            usuarioLogado.getNome(),
            usuarioLogado.getEmail(),
            usuarioLogado.getPapel().getPapel(),
            usuarioLogado.isTrocarSenha()
        );

        return ResponseEntity.ok(new DadosTokenJWT(tokenJWT, usuarioDTO));
    }
}
