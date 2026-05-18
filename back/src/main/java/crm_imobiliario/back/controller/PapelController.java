package crm_imobiliario.back.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import crm_imobiliario.back.model.entity.Papel;
import crm_imobiliario.back.model.repository.PapelRepository;

@RestController
public class PapelController {
    @Autowired
    private PapelRepository papelRepository;

    @PostMapping("/papel/novo")
    public ResponseEntity<Boolean> save(@RequestBody Papel papel) {
        try {
            papelRepository.save(papel);
            return ResponseEntity.status(201).build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
