package crm_imobiliario.back.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import crm_imobiliario.back.model.dto.NovaSenhaDTO;
import crm_imobiliario.back.model.dto.TrocarSenhaDTO;
import crm_imobiliario.back.model.dto.UsuarioDTO;
import crm_imobiliario.back.model.dto.UsuarioResponse;
import crm_imobiliario.back.model.entity.Usuario;
import crm_imobiliario.back.model.service.UsuarioService;
import crm_imobiliario.back.util.DefaultResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/usuarios")
public class UsuarioController {
    @Autowired
    private UsuarioService usuarioService;

    @PostMapping("/cadastrar")
    public ResponseEntity<?> cadastrarCliente(@RequestBody @Valid UsuarioDTO dto) {
        try {
            Usuario salvo = usuarioService.cadastrarUsuario(dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(UsuarioResponse.from(salvo));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @DeleteMapping("/deletar/{id}")
    public ResponseEntity<?> deletarCliente(@PathVariable Long id){
        try{
            usuarioService.deletarUsuario(id);
            return ResponseEntity.ok(
                    DefaultResponse.construir(
                            HttpStatus.OK.value(),
                            "Usuário deletado com sucesso",
                            null));
        } catch (RuntimeException e){
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    DefaultResponse.construir(
                            HttpStatus.NOT_FOUND.value(),
                            e.getMessage(),
                            null));
        }
    }

    @GetMapping
    public ResponseEntity<List<UsuarioResponse>> getUsuarios() {
        List<UsuarioResponse> usuarios = usuarioService.ConsultarUsuarios();
        return ResponseEntity.ok(usuarios);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getUsuario(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(UsuarioResponse.from(usuarioService.buscarPorId(id)));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    DefaultResponse.construir(
                            HttpStatus.NOT_FOUND.value(),
                            e.getMessage(),
                            null));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> getMe(Authentication authentication) {
        try {
            Usuario usuario = usuarioService.buscarPorEmail(authentication.getName());
            return ResponseEntity.ok(UsuarioResponse.from(usuario));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    DefaultResponse.construir(
                            HttpStatus.NOT_FOUND.value(),
                            e.getMessage(),
                            null));
        }
    }

    @PutMapping("/atualizar/{id}")
    public ResponseEntity<?> atualizarUsuario(@PathVariable Long id, @RequestBody @Valid UsuarioDTO dto) {
        try {
            Usuario atualizado = usuarioService.atualizarUsuario(id, dto);
            return ResponseEntity.ok(UsuarioResponse.from(atualizado));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @PutMapping("/minha-senha")
    public ResponseEntity<?> trocarMinhaSenha(@RequestBody @Valid TrocarSenhaDTO dto, Authentication authentication) {
        try {
            usuarioService.trocarSenha(authentication.getName(), dto.senhaAtual(), dto.novaSenha());
            return ResponseEntity.ok(
                    DefaultResponse.construir(
                            HttpStatus.OK.value(),
                            "Senha alterada com sucesso",
                            null));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @PutMapping("/trocar-senha/{id}")
    public ResponseEntity<?> trocarSenhaUsuario(@PathVariable Long id, @RequestBody @Valid NovaSenhaDTO dto) {
        try {
            usuarioService.trocarSenhaAdmin(id, dto.novaSenha());
            return ResponseEntity.ok(
                    DefaultResponse.construir(
                            HttpStatus.OK.value(),
                            "Senha redefinida com sucesso",
                            null));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @PutMapping("/inativar/{id}")
    public ResponseEntity<?> inativarUsuario(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(UsuarioResponse.from(usuarioService.inativarUsuario(id)));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    DefaultResponse.construir(
                            HttpStatus.NOT_FOUND.value(),
                            e.getMessage(),
                            null));
        }
    }

    @PutMapping("/ativar/{id}")
    public ResponseEntity<?> ativarUsuario(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(UsuarioResponse.from(usuarioService.ativarUsuario(id)));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    DefaultResponse.construir(
                            HttpStatus.NOT_FOUND.value(),
                            e.getMessage(),
                            null));
        }
    }
}
