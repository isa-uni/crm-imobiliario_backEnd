package crm_imobiliario.back.controller;

import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import crm_imobiliario.back.model.dto.MetaCreateDTO;
import crm_imobiliario.back.model.dto.MetaDTO;
import crm_imobiliario.back.model.entity.Usuario;
import crm_imobiliario.back.model.repository.UsuarioRepository;
import crm_imobiliario.back.model.service.MetaService;
import crm_imobiliario.back.model.service.UsuarioService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/metas")
public class MetaController {

    @Autowired
    private MetaService metaService;
    @Autowired
    private UsuarioService usuarioService;
    @Autowired
    private UsuarioRepository usuarioRepository;

    /**
     * Busca meta do usuário autenticado para um mês específico.
     * GET /metas/me?mesReferencia=2026-09-01 (primeiro dia do mês)
     */
    @GetMapping("/me")
    public ResponseEntity<?> getMinhaMeta(
            Authentication auth,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate mesReferencia) {
        Usuario solicitante = usuarioService.buscarPorEmail(auth.getName());
        MetaDTO dto = metaService.buscarPorUsuarioEMes(solicitante.getId(), mesReferencia);
        if (dto == null) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(dto);
    }

    /**
     * Cria ou atualiza meta.
     * - Corretor pode criar/atualizar apenas a própria meta
     * - Gestor/Admin pode criar para qualquer usuário (ideal para SalesClock pessoal + dashboard gestor)
     */
    @PostMapping
    public ResponseEntity<?> criarOuAtualizar(@RequestBody @Valid MetaCreateDTO dto, Authentication auth) {
        Usuario solicitante = usuarioService.buscarPorEmail(auth.getName());
        String papel = solicitante.getPapel() != null ? solicitante.getPapel().getPapel() : "";

        // corretor só pode mexer na própria meta
        if (!"gestor".equals(papel) && !"admin".equals(papel)) {
            if (!solicitante.getId().equals(dto.getUsuarioId())) {
                return ResponseEntity.status(403).body(java.util.Map.of("error", "Sem permissão para editar meta de outro usuário"));
            }
        } else {
            // gestor só pode editar metas da própria equipe (ou dele mesmo)
            if ("gestor".equals(papel) && !solicitante.getId().equals(dto.getUsuarioId())) {
                Usuario alvo = usuarioRepository.findById(dto.getUsuarioId()).orElse(null);
                if (alvo == null || alvo.getGestor() == null || !alvo.getGestor().getId().equals(solicitante.getId())) {
                    // permite se alvo não tem gestor mas é da equipe? bloqueia por segurança
                    // admin passa direto, gestor só equipe
                    return ResponseEntity.status(403).body(java.util.Map.of("error", "Gestor só pode editar metas da própria equipe"));
                }
            }
        }

        MetaDTO salvo = metaService.criarOuAtualizar(dto);
        return ResponseEntity.ok(salvo);
    }
}
