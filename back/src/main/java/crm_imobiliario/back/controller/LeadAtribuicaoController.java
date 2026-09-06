package crm_imobiliario.back.controller;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import crm_imobiliario.back.model.dto.LeadListaDTO;
import crm_imobiliario.back.model.dto.LeadRedistribuicaoDTO;
import crm_imobiliario.back.model.entity.LeadResponsavelHistorico;
import crm_imobiliario.back.model.repository.LeadResponsavelHistoricoRepository;
import crm_imobiliario.back.model.service.LeadAtribuicaoService;
import crm_imobiliario.back.model.service.UsuarioService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/leads")
public class LeadAtribuicaoController {

    @Autowired
    private LeadAtribuicaoService atribuicaoService;
    @Autowired
    private UsuarioService usuarioService;
    @Autowired
    private LeadResponsavelHistoricoRepository historicoRepository;

    @GetMapping("/aguardando-redistribuicao")
    public ResponseEntity<?> aguardando(
            @RequestParam(required = false) Long equipeId,
            @RequestParam(required = false, defaultValue = "false") boolean includeResumo,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "50") int size,
            Authentication auth) {
        var solicitante = usuarioService.buscarPorEmail(auth.getName());
        if (includeResumo) {
            return ResponseEntity.ok(atribuicaoService.listarAguardandoComResumo(equipeId, solicitante, page, size));
        }
        var leads = atribuicaoService.listarAguardando(equipeId, solicitante);
        return ResponseEntity.ok(leads.stream().map(LeadListaDTO::from).toList());
    }

    @PostMapping("/redistribuir")
    public ResponseEntity<?> redistribuir(@RequestBody @Valid LeadRedistribuicaoDTO dto, Authentication auth) {
        var solicitante = usuarioService.buscarPorEmail(auth.getName());
        var lead = atribuicaoService.redistribuir(dto.getLeadId(), dto.getNovoCorretorId(), solicitante);
        return ResponseEntity.ok(LeadListaDTO.from(lead));
    }

    @PostMapping("/{id}/redistribuir/{novoCorretorId}")
    public ResponseEntity<?> redistribuirPath(@PathVariable Long id, @PathVariable Long novoCorretorId, Authentication auth) {
        var solicitante = usuarioService.buscarPorEmail(auth.getName());
        var lead = atribuicaoService.redistribuir(id, novoCorretorId, solicitante);
        return ResponseEntity.ok(LeadListaDTO.from(lead));
    }

    @GetMapping("/{id}/historico-responsaveis")
    public ResponseEntity<List<Map<String,Object>>> historico(@PathVariable Long id, Authentication auth) {
        // qualquer autenticado da equipe pode ver
        List<LeadResponsavelHistorico> hist = historicoRepository.findByLeadIdOrderByDataInicioAsc(id);
        List<Map<String,Object>> resp = hist.stream().map(h -> {
            Map<String,Object> m = new java.util.HashMap<>();
            m.put("id", h.getId());
            m.put("corretorId", h.getCorretor() != null ? h.getCorretor().getId() : null);
            m.put("corretorNome", h.getCorretor() != null ? h.getCorretor().getNome() : null);
            m.put("equipeId", h.getEquipe() != null ? h.getEquipe().getId() : null);
            m.put("equipeNome", h.getEquipe() != null ? h.getEquipe().getNome() : null);
            m.put("gestorId", h.getGestor() != null ? h.getGestor().getId() : null);
            m.put("dataInicio", h.getDataInicio());
            m.put("dataFim", h.getDataFim());
            m.put("motivo", h.getMotivo());
            m.put("usuarioResponsavel", h.getUsuarioResponsavel() != null ? h.getUsuarioResponsavel().getNome() : null);
            return m;
        }).toList();
        return ResponseEntity.ok(resp);
    }
}
