package crm_imobiliario.back.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import crm_imobiliario.back.model.dto.EquipeCreateDTO;
import crm_imobiliario.back.model.dto.EquipeDTO;
import crm_imobiliario.back.model.entity.Equipe;
import crm_imobiliario.back.model.service.EquipeService;
import crm_imobiliario.back.model.service.UsuarioService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/equipes")
public class EquipeController {

    @Autowired
    private EquipeService equipeService;
    @Autowired
    private UsuarioService usuarioService;

    @GetMapping
    public ResponseEntity<List<EquipeDTO>> listar() {
        return ResponseEntity.ok(equipeService.listar().stream().map(EquipeDTO::from).toList());
    }

    @PostMapping
    public ResponseEntity<EquipeDTO> criar(@RequestBody @Valid EquipeCreateDTO dto, Authentication auth) {
        // apenas admin
        var solicitante = usuarioService.buscarPorEmail(auth.getName());
        String papel = solicitante.getPapel() != null ? solicitante.getPapel().getPapel() : "";
        if (!"admin".equals(papel)) return ResponseEntity.status(403).build();
        Equipe e = equipeService.criar(dto.getNome(), dto.getDescricao(), dto.getGestorId());
        return ResponseEntity.ok(EquipeDTO.from(e));
    }

    @PutMapping("/{id}/gestor/{gestorId}")
    public ResponseEntity<EquipeDTO> atribuirGestor(@PathVariable Long id, @PathVariable Long gestorId, Authentication auth) {
        var solicitante = usuarioService.buscarPorEmail(auth.getName());
        Equipe e = equipeService.atribuirGestor(id, gestorId, solicitante);
        return ResponseEntity.ok(EquipeDTO.from(e));
    }

    @PutMapping("/{id}/gestor/remover")
    public ResponseEntity<EquipeDTO> removerGestor(@PathVariable Long id, Authentication auth) {
        var solicitante = usuarioService.buscarPorEmail(auth.getName());
        Equipe e = equipeService.atribuirGestor(id, null, solicitante);
        return ResponseEntity.ok(EquipeDTO.from(e));
    }

    @PutMapping("/{id}/sincronizar")
    public ResponseEntity<java.util.Map<String,Object>> sincronizar(@PathVariable Long id, Authentication auth) {
        var solicitante = usuarioService.buscarPorEmail(auth.getName());
        String papel = solicitante.getPapel() != null ? solicitante.getPapel().getPapel() : "";
        if (!"admin".equals(papel)) return ResponseEntity.status(403).build();
        int migrados = equipeService.sincronizarEquipe(id);
        return ResponseEntity.ok(java.util.Map.of("migrados", migrados, "equipeId", id));
    }
}
