package crm_imobiliario.back.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import crm_imobiliario.back.model.entity.Papel;
import crm_imobiliario.back.model.repository.PapelRepository;

/**
 * Papéis só para leitura (cadastro de usuário, filtros). Os papéis do sistema vêm da migration e
 * as regras de negócio dependem deles; não há criação nem exclusão pela API.
 */
@RestController
public class PapelController {

    @Autowired
    private PapelRepository papelRepository;

    @GetMapping("/papel")
    public ResponseEntity<List<Papel>> listar() {
        return ResponseEntity.ok(papelRepository.findByAtivoTrueOrAtivoIsNull());
    }
}
