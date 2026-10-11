package crm_imobiliario.back.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
import crm_imobiliario.back.model.entity.OrigemMeta;
import crm_imobiliario.back.model.entity.Usuario;
import crm_imobiliario.back.model.repository.MetaRepository;
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
    private MetaService metaService;

    @Autowired
    private crm_imobiliario.back.model.service.UsuarioService usuarioService;

    /**
     * Indicadores de um mês (parâmetro mes = AAAA-MM; padrão: mês atual). Mês passado mostra a carteira
     * como estava no fim daquele mês.
     */
    @GetMapping
    public ResponseEntity<DashboardGestorDTO> getDashboard(
            Authentication auth,
            @RequestParam(required = false) String mes,
            @RequestParam(required = false) Long corretorId,
            @RequestParam(required = false) String origem,
            @RequestParam(required = false) Long empreendimentoId
    ) {
        LocalDateTime agora = LocalDateTime.now();
        YearMonth mesAtual = YearMonth.from(agora);
        YearMonth ym;
        try {
            ym = mes == null || mes.isBlank() ? mesAtual : YearMonth.parse(mes.trim());
        } catch (java.time.format.DateTimeParseException e) {
            throw new crm_imobiliario.back.util.RegraNegocioException("Informe o mês no formato AAAA-MM, por exemplo 2026-09.", "mes", "INVALID_MONTH");
        }
        if (ym.isAfter(mesAtual)) {
            throw new crm_imobiliario.back.util.RegraNegocioException("Escolha um mês até o mês atual.", "mes", "INVALID_MONTH");
        }
        return ResponseEntity.ok(dashboardService.getDashboard(auth.getName(), ym, agora, corretorId, origem, empreendimentoId));
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
        Set<Long> ids = equipe.stream().map(Usuario::getId).collect(Collectors.toSet());
        List<Meta> metas = metaRepository.findByMesReferencia(ref).stream()
                .filter(m -> ids.contains(m.getUsuario().getId()))
                .toList();
        return ResponseEntity.ok(metas.stream().map(MetaDTO::from).toList());
    }

    @PostMapping("/metas")
    public ResponseEntity<?> criarOuAtualizarMeta(@RequestBody @Valid MetaCreateDTO dto, Authentication auth) {
        // mesma regra do POST /metas: gestor só define metas dos próprios subordinados (antes não
        // havia checagem e qualquer autenticado definia a meta de "gestor" de qualquer usuário)
        metaService.validarPermissao(usuarioService.buscarPorEmail(auth.getName()), dto.getUsuarioId());
        // meta cadastrada pelo gestor no dashboard da equipe = sempre origem GESTOR
        MetaDTO salvo = metaService.criarOuAtualizar(dto, OrigemMeta.GESTOR);
        return ResponseEntity.ok(salvo);
    }
}
