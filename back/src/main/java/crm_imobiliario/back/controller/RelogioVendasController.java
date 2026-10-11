package crm_imobiliario.back.controller;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import crm_imobiliario.back.model.dto.ProporcaoFunilDTO;
import crm_imobiliario.back.model.dto.RelogioVendasDTO;
import crm_imobiliario.back.model.service.RelogioVendasService;
import crm_imobiliario.back.model.service.UsuarioService;
import jakarta.validation.Valid;

/**
 * Relógio de Vendas: contagem do mês atual sobre os clientes do próprio usuário logado (gestor e
 * admin também veem só os próprios). Ajuste das proporções do funil: somente admin (FilterChain).
 */
@RestController
@RequestMapping("/relogio-vendas")
public class RelogioVendasController {

    @Autowired private RelogioVendasService relogioVendasService;
    @Autowired private UsuarioService usuarioService;

    @GetMapping
    public ResponseEntity<RelogioVendasDTO> resumo(Authentication auth) {
        return ResponseEntity.ok(relogioVendasService.resumo(usuarioService.buscarPorEmail(auth.getName()), LocalDateTime.now()));
    }

    @PutMapping("/proporcoes")
    public ResponseEntity<ProporcaoFunilDTO> atualizarProporcoes(@RequestBody @Valid ProporcaoFunilDTO dto, Authentication auth) {
        return ResponseEntity.ok(ProporcaoFunilDTO.from(
                relogioVendasService.atualizarProporcoes(dto, usuarioService.buscarPorEmail(auth.getName()))));
    }
}
