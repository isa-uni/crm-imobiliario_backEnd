package crm_imobiliario.back.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import crm_imobiliario.back.model.dto.LeadAtualizacaoDTO;
import crm_imobiliario.back.model.dto.LeadsDTO;
import crm_imobiliario.back.model.dto.MetricsDTO;
import crm_imobiliario.back.model.entity.Lead;
import crm_imobiliario.back.model.service.LeadsService;
import crm_imobiliario.back.util.DefaultResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/leads")
public class LeadController {

    @Autowired
    private LeadsService leadService;

    @GetMapping
    public ResponseEntity<List<Lead>> getlead() {
        List<Lead> lead = leadService.ConsultarLeads();
        return ResponseEntity.ok(lead);
    }

    @PostMapping("/cadastrar")
    public ResponseEntity<?> cadastrarCliente(@RequestBody @Valid LeadsDTO dto) {
        try {
            Lead salvo = leadService.cadastrarLeads(dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(salvo);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @PutMapping("/atualizar/{id}")
    public ResponseEntity<?> atualizarLeads(@PathVariable Long id, @RequestBody @Valid LeadAtualizacaoDTO dto) {
        try {
            Lead atualizado = leadService.atualizarLeads(id, dto);
            return ResponseEntity.ok(atualizado);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @PutMapping("/inativar/{id}")
    public ResponseEntity<Void> inativar(@PathVariable Long id) {
        leadService.inativarLead(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/ativar/{id}")
    public ResponseEntity<Void> ativar(@PathVariable Long id) {
        leadService.ativarLead(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/deletar/{id}")
    public ResponseEntity<?> deletar(@PathVariable Long id) {
        try{
            leadService.deletarLead(id);
            return ResponseEntity.ok(
                    DefaultResponse.construir(
                            HttpStatus.OK.value(),
                            "Lead deletado com sucesso",
                            null));
        } catch (RuntimeException e){
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    DefaultResponse.construir(
                            HttpStatus.NOT_FOUND.value(),
                            e.getMessage(),
                            null));
        }
    }
    
    @GetMapping("/metrics")
    public ResponseEntity<MetricsDTO> getMetrics() {
        return ResponseEntity.ok(leadService.getMetrics());
    }
}
