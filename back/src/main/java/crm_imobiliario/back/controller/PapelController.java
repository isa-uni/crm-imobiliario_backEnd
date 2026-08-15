package crm_imobiliario.back.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import crm_imobiliario.back.model.entity.Papel;
import crm_imobiliario.back.model.repository.PapelRepository;

@RestController
public class PapelController {
    @Autowired
    private PapelRepository papelRepository;

    @GetMapping("/papel")
    public ResponseEntity<List<Papel>> listar() {
        return ResponseEntity.ok(papelRepository.findByAtivoTrueOrAtivoIsNull());
    }

    @PostMapping("/papel/novo")
    public ResponseEntity<Boolean> save(@RequestBody Papel papel) {
        try {
            papel.setAtivo(true);
            papelRepository.save(papel);
            return ResponseEntity.status(201).build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/papel/{id}")
    public ResponseEntity<?> excluir(@PathVariable Long id) {
        Papel papel = papelRepository.findById(id).orElse(null);
        if (papel == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Papel não encontrado.");
        }

        if ("admin".equals(papel.getPapel()) || "corretor".equals(papel.getPapel())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("O papel de sistema \"" + papel.getPapel() + "\" não pode ser excluído.");
        }

        papel.setAtivo(false);
        papelRepository.save(papel);
        return ResponseEntity.noContent().build();
    }
}