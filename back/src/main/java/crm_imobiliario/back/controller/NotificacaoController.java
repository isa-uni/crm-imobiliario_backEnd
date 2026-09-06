package crm_imobiliario.back.controller;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import crm_imobiliario.back.model.dto.NotificacaoDTO;
import crm_imobiliario.back.model.entity.Usuario;
import crm_imobiliario.back.model.service.NotificacaoService;
import crm_imobiliario.back.model.service.UsuarioService;

@RestController
@RequestMapping("/notificacoes")
public class NotificacaoController {

    @Autowired
    private NotificacaoService notificacaoService;
    @Autowired
    private UsuarioService usuarioService;

    @GetMapping
    public ResponseEntity<List<NotificacaoDTO>> listar(Authentication auth) {
        Usuario u = usuarioService.buscarPorEmail(auth.getName());
        return ResponseEntity.ok(notificacaoService.listarPorUsuario(u.getId()));
    }

    @GetMapping("/nao-lidas")
    public ResponseEntity<Map<String,Long>> contar(Authentication auth) {
        Usuario u = usuarioService.buscarPorEmail(auth.getName());
        long count = notificacaoService.contarNaoLidas(u.getId());
        return ResponseEntity.ok(Map.of("count", count));
    }

    @PostMapping("/{id}/ler")
    public ResponseEntity<?> marcarLida(@PathVariable Long id, Authentication auth) {
        Usuario u = usuarioService.buscarPorEmail(auth.getName());
        notificacaoService.marcarComoLida(id, u.getId());
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/ler-todas")
    public ResponseEntity<?> marcarTodas(Authentication auth) {
        Usuario u = usuarioService.buscarPorEmail(auth.getName());
        notificacaoService.marcarTodasComoLidas(u.getId());
        return ResponseEntity.ok(Map.of("ok", true));
    }
}
