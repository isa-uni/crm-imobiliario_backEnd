package crm_imobiliario.back.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import crm_imobiliario.back.model.dto.LeadAtualizacaoDTO;
import crm_imobiliario.back.model.dto.LeadListaDTO;
import crm_imobiliario.back.model.dto.LeadsDTO;
import crm_imobiliario.back.model.dto.MetricsDTO;
import crm_imobiliario.back.model.dto.TramitacaoDTO;
import crm_imobiliario.back.model.entity.Tramitacao;
import crm_imobiliario.back.model.service.LeadsService;
import crm_imobiliario.back.util.DefaultResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/leads")
public class LeadController {

    @Autowired
    private LeadsService leadService;

    @GetMapping
    public ResponseEntity<?> getlead(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String month,
            org.springframework.security.core.Authentication authentication) {
        // sem paginação → compatível com callers legados (dashboard, etc.)
        if (page == null && size == null && search == null && status == null && month == null && sort == null) {
            List<LeadListaDTO> lead = leadService.ConsultarLeads();
            return ResponseEntity.ok(lead);
        }
        int p = Math.max(0, page != null ? page : 0);
        int s = size != null ? Math.min(Math.max(1, size), 100) : 20;
        Sort sd = parseSort(sort);
        Page<LeadListaDTO> pg = leadService.findPaginated(p, s, sd, search, status, month);
        return ResponseEntity.ok(pg);
    }

    private Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) return Sort.by(Sort.Direction.DESC, "dataAtualizacao");
        String[] parts = sort.split(",");
        String field = parts[0].trim();
        Sort.Direction dir = parts.length > 1 && parts[1].equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        // whitelist para evitar PropertyReferenceException
        if (!List.of("id", "nome", "email", "telefone", "origem", "status", "valorInteresse", "dataCriacao", "dataAtualizacao").contains(field)) {
            field = "dataAtualizacao";
            dir = Sort.Direction.DESC;
        }
        return Sort.by(dir, field);
    }

    @PostMapping("/cadastrar")
    public ResponseEntity<?> cadastrarCliente(@RequestBody @Valid LeadsDTO dto) {
        LeadListaDTO salvo = leadService.cadastrarLeadsDTO(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(salvo);
    }

    @PutMapping("/atualizar/{id}")
    public ResponseEntity<?> atualizarLeads(@PathVariable Long id, @RequestBody @Valid LeadAtualizacaoDTO dto, org.springframework.security.core.Authentication authentication) {
        LeadListaDTO atualizado = leadService.atualizarLeadsDTO(id, dto);
        return ResponseEntity.ok(atualizado);
    }

    @PutMapping("/inativar/{id}")
    public ResponseEntity<Void> inativar(@PathVariable Long id, org.springframework.security.core.Authentication authentication) {
        leadService.inativarLead(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/ativar/{id}")
    public ResponseEntity<Void> ativar(@PathVariable Long id, org.springframework.security.core.Authentication authentication) {
        leadService.ativarLead(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/deletar/{id}")
    public ResponseEntity<?> deletar(@PathVariable Long id, org.springframework.security.core.Authentication authentication) {
        leadService.deletarLead(id);
        return ResponseEntity.ok(
                DefaultResponse.construir(
                        HttpStatus.OK.value(),
                        "Lead deletado com sucesso",
                        null));
    }
    
    @GetMapping("/metrics")
    public ResponseEntity<MetricsDTO> getMetrics(org.springframework.security.core.Authentication authentication) {
        return ResponseEntity.ok(leadService.getMetrics());
    }

    @GetMapping("/{id}/tramitacoes")
    public ResponseEntity<List<TramitacaoDTO>> listarTramitacoes(@PathVariable Long id, org.springframework.security.core.Authentication authentication) {
        List<Tramitacao> tramitacoes = leadService.listarTramitacoes(id);
        List<TramitacaoDTO> dto = tramitacoes.stream()
                .map(TramitacaoDTO::from)
                .toList();
        return ResponseEntity.ok(dto);
    }
}
