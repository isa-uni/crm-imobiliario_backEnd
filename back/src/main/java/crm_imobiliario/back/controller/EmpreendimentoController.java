package crm_imobiliario.back.controller;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.core.task.TaskExecutor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import crm_imobiliario.back.model.dto.empreendimento.EmpreendimentoDTO;
import crm_imobiliario.back.model.dto.empreendimento.SyncStatusDTO;
import crm_imobiliario.back.model.entity.Sincronizacao;
import crm_imobiliario.back.model.repository.EmpreendimentoRepository;
import crm_imobiliario.back.model.repository.SincronizacaoRepository;
import crm_imobiliario.back.model.repository.UnidadeRepository;
import crm_imobiliario.back.model.service.empreendimento.CrmSyncService;
import crm_imobiliario.back.model.service.empreendimento.EmpreendimentoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/empreendimentos")
@RequiredArgsConstructor
public class EmpreendimentoController {

    private final EmpreendimentoService empreendimentoService;
    private final CrmSyncService crmSyncService;
    private final EmpreendimentoRepository empreendimentoRepository;
    private final UnidadeRepository unidadeRepository;
    private final SincronizacaoRepository sincronizacaoRepository;
    private final TaskExecutor taskExecutor;

    /**
     * GET /api/v1/empreendimentos/disponiveis
     * Lista paginada — apenas com disponiveis > 0 por padrão.
     * Filtros: cidade, regiao, precoMin, precoMax, quartos, page, size, sort
     */
    @GetMapping("/disponiveis")
    public ResponseEntity<Map<String, Object>> disponiveis(
            @RequestParam(required = false) String cidade,
            @RequestParam(required = false) String regiao,
            @RequestParam(required = false) Long precoMin,
            @RequestParam(required = false) Long precoMax,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "nome,asc") String sort) {

        // Se paginação simples sem filtros de preço, usa listagem direta (mais rápido)
        if (precoMin == null && precoMax == null) {
            List<EmpreendimentoDTO> todos = empreendimentoService.listarDisponiveis(cidade, regiao);
            // paginação manual quando sem filtro de preço
            int total = todos.size();
            int from = Math.min(page * size, total);
            int to = Math.min(from + size, total);
            List<EmpreendimentoDTO> pagina = todos.subList(from, to);

            Map<String, Object> resp = new HashMap<>();
            resp.put("data", pagina);
            Map<String, Object> meta = new HashMap<>();
            meta.put("total", total);
            meta.put("page", page);
            meta.put("size", size);
            meta.put("updatedAt", crmSyncService.getUltimoMontadoEm() != null ? crmSyncService.getUltimoMontadoEm().toString() : null);
            meta.put("unmatched", List.of());
            resp.put("meta", meta);
            return ResponseEntity.ok()
                    .header("X-Total-Count", String.valueOf(total))
                    .body(resp);
        }

        Page<EmpreendimentoDTO> p = empreendimentoService.listarPaginado(cidade, regiao, true, precoMin, precoMax, page, size, sort);
        // filtra disponiveis > 0 no caso do query genérico
        List<EmpreendimentoDTO> filtrados = p.getContent().stream()
                .filter(d -> d.getDisponibilidade() != null && Boolean.TRUE.equals(d.getDisponibilidade().getPossuiImoveisDisponiveis()))
                .toList();

        Map<String, Object> resp = new HashMap<>();
        resp.put("data", filtrados);
        Map<String, Object> meta = new HashMap<>();
        meta.put("total", filtrados.size());
        meta.put("page", page);
        meta.put("size", size);
        meta.put("updatedAt", crmSyncService.getUltimoMontadoEm() != null ? crmSyncService.getUltimoMontadoEm().toString() : null);
        resp.put("meta", meta);
        return ResponseEntity.ok(resp);
    }

    /** GET /api/v1/empreendimentos — lista completa (com flag disponiveis) */
    @GetMapping
    public ResponseEntity<Map<String, Object>> listar(
            @RequestParam(required = false) Boolean disponiveis,
            @RequestParam(required = false) String cidade,
            @RequestParam(required = false) String regiao) {
        List<EmpreendimentoDTO> lista;
        if (Boolean.TRUE.equals(disponiveis)) {
            lista = empreendimentoService.listarDisponiveis(cidade, regiao);
        } else {
            // todos ativos
            lista = empreendimentoRepository.findByAtivoTrue().stream()
                    .map(empreendimentoService::toDTO)
                    .filter(d -> cidade == null || cidade.equalsIgnoreCase(d.getCidade()))
                    .filter(d -> regiao == null || regiao.equalsIgnoreCase(d.getRegiao()))
                    .toList();
        }
        Map<String, Object> resp = new HashMap<>();
        resp.put("data", lista);
        Map<String, Object> meta = new HashMap<>();
        meta.put("total", lista.size());
        meta.put("updatedAt", crmSyncService.getUltimoMontadoEm() != null ? crmSyncService.getUltimoMontadoEm().toString() : Instant.now().toString());
        resp.put("meta", meta);
        return ResponseEntity.ok(resp);
    }

    /** GET /api/v1/empreendimentos/{id} — capa (por PK ou codigoCrm) */
    @GetMapping("/{id}")
    public ResponseEntity<?> buscar(@PathVariable String id,
                                    @RequestParam(required = false, defaultValue = "0") String detalhe) {
        boolean querDetalhe = "1".equals(detalhe) || "true".equalsIgnoreCase(detalhe);
        Optional<EmpreendimentoDTO> dto = Optional.empty();

        // tenta PK numérico
        try {
            Long pk = Long.parseLong(id);
            dto = querDetalhe ? empreendimentoService.detalhe(pk) : empreendimentoService.buscarPorId(pk);
        } catch (NumberFormatException ignored) {}

        if (dto.isEmpty()) {
            // tenta slug
            dto = empreendimentoService.buscarPorSlug(id);
        }
        if (dto.isEmpty()) {
            dto = empreendimentoService.buscarPorCodigoCrm(id);
        }
        // tenta codigoCrm como fallback mesmo se id era numérico mas não era PK
        if (dto.isEmpty()) {
            dto = empreendimentoService.buscarPorCodigoCrm(id);
        }

        if (dto.isEmpty()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(dto.get());
    }

    /** POST /api/v1/empreendimentos/sync — dispara sincronização assíncrona via TaskExecutor */
    @PostMapping("/sync")
    public ResponseEntity<Map<String, Object>> sync(
            @RequestParam(required = false) String id,
            @RequestParam(required = false) String codigo) {
        String alvo = id != null ? id : codigo;
        taskExecutor.execute(() -> {
            try {
                if (alvo != null && !alvo.isBlank()) {
                    crmSyncService.syncPorCodigo(alvo);
                } else {
                    crmSyncService.syncCompleta();
                }
            } catch (Exception e) {
                log.error("Sync manual falhou para alvo {}: {}", alvo, e.getMessage(), e);
            }
        });

        Map<String, Object> resp = new HashMap<>();
        resp.put("status", "em_processamento");
        resp.put("mensagem", alvo != null ? "Sync do empreendimento " + alvo + " enfileirado" : "Sync completa enfileirada");
        resp.put("emBuild", true);
        return ResponseEntity.accepted().body(resp);
    }

    /** GET /api/v1/empreendimentos/sync/status */
    @GetMapping("/sync/status")
    public ResponseEntity<SyncStatusDTO> syncStatus() {
        Optional<Sincronizacao> ultima = sincronizacaoRepository.findTopByOrderByInicioDesc();
        long capasProntas = empreendimentoRepository.count();
        long disponiveis = empreendimentoRepository.countByAtivoTrueAndDisponiveisGreaterThan(0);
        long totalUnidades = unidadeRepository.count();

        SyncStatusDTO dto = SyncStatusDTO.builder()
                .montadoEm(crmSyncService.getUltimoMontadoEm())
                .totalCatalogo((int) capasProntas)
                .totalDisponiveis(disponiveis)
                .capasProntas(capasProntas)
                .totalUnidades(totalUnidades)
                .emBuild(crmSyncService.isEmBuild())
                .ultimaSincronizacao(ultima.map(Sincronizacao::getInicio).orElse(null))
                .ultimoErro(ultima.map(Sincronizacao::getErro).orElse(null))
                .build();
        return ResponseEntity.ok(dto);
    }
}
