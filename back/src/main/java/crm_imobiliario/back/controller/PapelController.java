package crm_imobiliario.back.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import crm_imobiliario.back.util.RecursoNaoEncontradoException;
import crm_imobiliario.back.util.RegraNegocioException;

import crm_imobiliario.back.model.entity.Papel;
import crm_imobiliario.back.model.repository.PapelRepository;

@RestController
public class PapelController {

    private static final java.util.Set<String> PAPEIS_DE_SISTEMA = java.util.Set.of("admin", "gestor", "corretor");
    @Autowired
    private PapelRepository papelRepository;

    @GetMapping("/papel")
    public ResponseEntity<List<Papel>> listar() {
        return ResponseEntity.ok(papelRepository.findByAtivoTrueOrAtivoIsNull());
    }

    @PostMapping("/papel/novo")
    public ResponseEntity<?> save(@RequestBody @jakarta.validation.Valid Papel papel) {
        if (papel.getPapel() == null || papel.getPapel().isBlank()) {
            throw new RegraNegocioException("Informe o nome do papel.", "papel", "MISSING_NAME");
        }
        String nome = papel.getPapel().trim().toLowerCase();
        if (nome.length() < 2 || nome.length() > 50) {
            throw new RegraNegocioException("O nome do papel deve ter entre 2 e 50 caracteres.", "papel", "INVALID_LENGTH");
        }
        if (!nome.matches("[a-zà-ú0-9 _-]+")) {
            throw new RegraNegocioException("O nome do papel pode conter apenas letras, números, espaço, hífen e sublinhado.", "papel", "INVALID_FORMAT");
        }
        // verifica antes de gravar para dizer exatamente por que o nome não pode ser usado
        papelRepository.findAll().stream().filter(p -> nome.equalsIgnoreCase(p.getPapel())).findFirst().ifPresent(existente -> {
            boolean inativo = Boolean.FALSE.equals(existente.getAtivo());
            throw new RegraNegocioException("Já existe um papel chamado \"" + nome + "\"" + (inativo ? ", mas ele está excluído. Escolha outro nome." : ". Escolha outro nome."),
                    "papel", "DUPLICATE_PAPEL");
        });
        papel.setPapel(nome);
        papel.setAtivo(true);
        papelRepository.save(papel);
        return ResponseEntity.status(201).body(java.util.Map.of("message", "Papel \"" + nome + "\" criado com sucesso."));
    }

    @DeleteMapping("/papel/{id}")
    public ResponseEntity<?> excluir(@PathVariable Long id) {
        Papel papel = papelRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Papel não encontrado."));

        // papéis usados pelas regras de negócio (escopo de leads, redistribuição, metas, dashboards)
        if (PAPEIS_DE_SISTEMA.contains(papel.getPapel())) {
            throw new RegraNegocioException("O papel de sistema \"" + papel.getPapel() + "\" não pode ser excluído.");
        }

        papel.setAtivo(false);
        papelRepository.save(papel);
        return ResponseEntity.noContent().build();
    }
}