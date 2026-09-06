package crm_imobiliario.back.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

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

import crm_imobiliario.back.model.dto.DashboardGestorDTO;
import crm_imobiliario.back.model.dto.MetaCreateDTO;
import crm_imobiliario.back.model.dto.MetaDTO;
import crm_imobiliario.back.model.dto.UsuarioResponse;
import crm_imobiliario.back.model.entity.Meta;
import crm_imobiliario.back.model.entity.Usuario;
import crm_imobiliario.back.model.repository.MetaRepository;
import crm_imobiliario.back.model.repository.UsuarioRepository;
import crm_imobiliario.back.model.service.DashboardGestorService;
import crm_imobiliario.back.model.service.MetaService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/dashboard/gestor")
public class DashboardGestorController {

    @Autowired
    private DashboardGestorService dashboardService;
    @Autowired
    private MetaRepository metaRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private MetaService metaService;

    @GetMapping
    public ResponseEntity<DashboardGestorDTO> getDashboard(
            Authentication auth,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) Long corretorId,
            @RequestParam(required = false) String origem,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long imovelId
    ) {
        String email = auth.getName();
        LocalDateTime ini;
        LocalDateTime fi;
        if (inicio != null && fim != null) {
            ini = inicio.atStartOfDay();
            fi = fim.atTime(LocalTime.MAX);
        } else {
            // padrão mês atual
            LocalDate now = LocalDate.now();
            ini = now.withDayOfMonth(1).atStartOfDay();
            fi = now.withDayOfMonth(now.lengthOfMonth()).atTime(LocalTime.MAX);
        }
        DashboardGestorDTO dto = dashboardService.getDashboard(email, ini, fi, corretorId, origem, status, imovelId);
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/equipe")
    public ResponseEntity<List<UsuarioResponse>> getEquipe(Authentication auth) {
        List<Usuario> equipe = dashboardService.getEquipeCorretores(auth.getName());
        return ResponseEntity.ok(equipe.stream().map(UsuarioResponse::from).toList());
    }

    @GetMapping("/metas")
    public ResponseEntity<List<MetaDTO>> listarMetas(
            Authentication auth,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate mesReferencia) {
        // mesReferencia esperado como yyyy-MM-01
        LocalDate ref = mesReferencia.withDayOfMonth(1);
        List<Usuario> equipe = dashboardService.getEquipeCorretores(auth.getName());
        List<Long> ids = equipe.stream().map(Usuario::getId).toList();
        List<Meta> metas = metaRepository.findAll().stream()
                .filter(m -> ids.contains(m.getUsuario().getId()) && m.getMesReferencia().equals(ref))
                .toList();
        return ResponseEntity.ok(metas.stream().map(MetaDTO::from).toList());
    }

    @PostMapping("/metas")
    public ResponseEntity<?> criarOuAtualizarMeta(@RequestBody @Valid MetaCreateDTO dto) {
        MetaDTO salvo = metaService.criarOuAtualizar(dto);
        return ResponseEntity.ok(salvo);
    }
}
