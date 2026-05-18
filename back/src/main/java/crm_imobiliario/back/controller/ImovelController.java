package crm_imobiliario.back.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import crm_imobiliario.back.model.dto.ImovelAtualizacaoDTO;
import crm_imobiliario.back.model.dto.ImovelDTO;
import crm_imobiliario.back.model.entity.Imovel;
import crm_imobiliario.back.model.service.ImovelService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/imovel")
public class ImovelController {
    
    @Autowired
    private ImovelService imovelService;

    @GetMapping
    public ResponseEntity<List<Imovel>> getimovel() {
        List<Imovel> imovel = imovelService.ConsultarImoveis();
        return ResponseEntity.ok(imovel);
    }

    @PostMapping("/cadastrar")
    public ResponseEntity<?> cadastrarImovel(@RequestBody @Valid ImovelDTO dto) {
        try {
            Imovel salvo = imovelService.cadastrarImovel(dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(salvo);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @PutMapping("/atualizar/{id}")
    public ResponseEntity<?> atualizarImovel(@PathVariable Long id, @RequestBody @Valid ImovelAtualizacaoDTO dto) {
        try {
            Imovel atualizado = imovelService.atualizarimovel(id, dto);
            return ResponseEntity.ok(atualizado);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @PutMapping("/inativar/{id}")
    public ResponseEntity<Void> inativarImovel(@PathVariable Long id) {
        imovelService.inativarImovel(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/ativar/{id}")
    public ResponseEntity<Void> ativarImovel(@PathVariable Long id) {
        imovelService.ativarImovel(id);
        return ResponseEntity.noContent().build();
    }
    
}
