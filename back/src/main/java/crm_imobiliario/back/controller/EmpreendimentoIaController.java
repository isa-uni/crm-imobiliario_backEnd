package crm_imobiliario.back.controller;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import crm_imobiliario.back.model.dto.empreendimento.*;
import crm_imobiliario.back.model.entity.EmpreendimentoDocumento;
import crm_imobiliario.back.model.entity.EmpreendimentoExtracao;
import crm_imobiliario.back.model.service.UsuarioService;
import crm_imobiliario.back.model.service.empreendimento.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/empreendimentos")
@RequiredArgsConstructor
public class EmpreendimentoIaController {

    private final EmpreendimentoStorageService storageService;
    private final EmpreendimentoExtracaoService extracaoService;
    private final EmpreendimentoIaService empreendimentoIaService;
    private final UsuarioService usuarioService;

    private Long usuarioId(Authentication auth) {
        if (auth == null) throw new RuntimeException("Não autenticado");
        return usuarioService.buscarPorEmail(auth.getName()).getId();
    }

    private void exigirAdminGestor(Authentication auth) {
        var user = usuarioService.buscarPorEmail(auth.getName());
        String papel = user.getPapel()!=null?user.getPapel().getPapel():"";
        if (!"admin".equals(papel) && !"gestor".equals(papel)) {
            throw new org.springframework.security.access.AccessDeniedException("Apenas admin/gestor pode executar esta ação");
        }
    }

    @PostMapping("/upload")
    public ResponseEntity<?> upload(@RequestParam("files") List<MultipartFile> files,
                                    @RequestParam(value = "empreendimentoId", required = false) Long empreendimentoId,
                                    Authentication auth) throws Exception {
        exigirAdminGestor(auth);
        if (files == null || files.isEmpty()) throw new IllegalArgumentException("Nenhum arquivo enviado");
        if (files.size() > 5) throw new IllegalArgumentException("Máximo 5 arquivos");
        Long uid = usuarioId(auth);
        List<EmpreendimentoDocumento> docs = new java.util.ArrayList<>();
        for (MultipartFile f : files) {
            docs.add(storageService.armazenar(f, empreendimentoId, uid));
        }
        List<Long> ids = docs.stream().map(EmpreendimentoDocumento::getId).toList();
        // cria extracao pendente
        EmpreendimentoExtracao extracao = extracaoService.criarExtracao(ids, uid, empreendimentoId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("extracaoId", extracao.getId(), "documentoIds", ids, "status", extracao.getStatus()));
    }

    @PostMapping("/extrair")
    public ResponseEntity<?> extrair(@RequestBody Map<String, List<Long>> body, Authentication auth) {
        exigirAdminGestor(auth);
        List<Long> ids = body.get("documentoIds");
        if (ids == null || ids.isEmpty()) throw new IllegalArgumentException("documentoIds obrigatório");
        Long uid = usuarioId(auth);
        EmpreendimentoExtracao extracao = extracaoService.criarExtracao(ids, uid, null);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("extracaoId", extracao.getId(), "status", extracao.getStatus()));
    }

    @GetMapping("/extracoes/{id}")
    public ResponseEntity<EmpreendimentoExtracaoDTO> getExtracao(@PathVariable Long id, Authentication auth) {
        // qualquer autenticado pode ver (revisão)
        return ResponseEntity.ok(extracaoService.obter(id));
    }

    @PostMapping("/extracoes/{id}/reprocessar")
    public ResponseEntity<?> reprocessar(@PathVariable Long id, Authentication auth) {
        exigirAdminGestor(auth);
        EmpreendimentoExtracao e = extracaoService.reprocessar(id);
        return ResponseEntity.ok(Map.of("extracaoId", e.getId(), "status", e.getStatus()));
    }

    @PostMapping("/confirmar")
    public ResponseEntity<EmpreendimentoDetalheDTO> confirmar(@RequestBody @Valid EmpreendimentoConfirmacaoDTO dto, Authentication auth) {
        exigirAdminGestor(auth);
        Long uid = usuarioId(auth);
        EmpreendimentoDetalheDTO detalhe = empreendimentoIaService.confirmar(dto, uid);
        // vincula os documentos da extração ao empreendimento recém-confirmado e recarrega
        // a resposta para refletir o vínculo imediatamente (não só em uma consulta futura)
        if (dto.getExtracaoId() != null) {
            try {
                extracaoService.vincularEmpreendimento(dto.getExtracaoId(), detalhe.getId());
                detalhe = empreendimentoIaService.detalhar(detalhe.getId());
            } catch (Exception e) {
                log.warn("Falha ao vincular documentos da extração {} ao empreendimento {}: {}", dto.getExtracaoId(), detalhe.getId(), e.getMessage());
            }
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(detalhe);
    }

    @GetMapping("/cards")
    public ResponseEntity<Page<EmpreendimentoCardDTO>> listarCards(
            @RequestParam(required = false) String cidade,
            @RequestParam(required = false) String bairro,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long precoMin,
            @RequestParam(required = false) Long precoMax,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(defaultValue = "nome,asc") String sort,
            Authentication auth) {
        Sort s = parseSort(sort);
        PageRequest pr = PageRequest.of(Math.max(0,page), Math.min(Math.max(1,size),50), s);
        Page<EmpreendimentoCardDTO> pg = empreendimentoIaService.listar(cidade, bairro, status, precoMin, precoMax, search, pr);
        return ResponseEntity.ok(pg);
    }

    @GetMapping("/{id}/detalhe")
    public ResponseEntity<EmpreendimentoDetalheDTO> detalhe(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(empreendimentoIaService.detalhar(id));
    }

    @GetMapping("/{id}/unidades")
    public ResponseEntity<Page<UnidadeDTO>> listarUnidades(
            @PathVariable Long id,
            @RequestParam(required = false) String situacao,
            @RequestParam(required = false) String bloco,
            @RequestParam(required = false) String tipologia,
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) Long precoMin,
            @RequestParam(required = false) Long precoMax,
            @RequestParam(required = false) Double areaMin,
            @RequestParam(required = false) Double areaMax,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String sort,
            Authentication auth) {
        PageRequest pr = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 500), parseSortUnidade(sort));
        return ResponseEntity.ok(empreendimentoIaService.listarUnidades(id, situacao, bloco, tipologia, busca, precoMin, precoMax, areaMin, areaMax, pr));
    }

    @GetMapping("/{id}/fontes")
    public ResponseEntity<List<EmpreendimentoExtracaoDTO.FonteDTO>> fontes(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(empreendimentoIaService.listarFontes(id));
    }

    @GetMapping("/slug/{slug}")
    public ResponseEntity<EmpreendimentoDetalheDTO> detalhePorSlug(@PathVariable String slug, Authentication auth) {
        return ResponseEntity.ok(empreendimentoIaService.detalharPorSlug(slug));
    }

    @GetMapping("/duplicados")
    public ResponseEntity<List<EmpreendimentoCardDTO>> duplicados(
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) String codigo,
            @RequestParam(required = false) String endereco,
            Authentication auth) {
        return ResponseEntity.ok(empreendimentoIaService.buscarDuplicados(nome, codigo, endereco));
    }

    @PostMapping("/{id}/documentos")
    public ResponseEntity<?> uploadParaExistente(@PathVariable Long id, @RequestParam("files") List<MultipartFile> files, Authentication auth) throws Exception {
        exigirAdminGestor(auth);
        Long uid = usuarioId(auth);
        List<Long> ids = new java.util.ArrayList<>();
        for (MultipartFile f : files) {
            var doc = storageService.armazenar(f, id, uid);
            ids.add(doc.getId());
        }
        EmpreendimentoExtracao extracao = extracaoService.criarExtracao(ids, uid, id);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("extracaoId", extracao.getId(), "documentoIds", ids));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EmpreendimentoDetalheDTO> atualizar(@PathVariable Long id, @RequestBody EmpreendimentoConfirmacaoDTO dto, Authentication auth) {
        exigirAdminGestor(auth);
        dto.setEmpreendimentoExistenteId(id);
        Long uid = usuarioId(auth);
        return ResponseEntity.ok(empreendimentoIaService.confirmar(dto, uid));
    }

    private Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) return Sort.by(Sort.Direction.ASC, "nome");
        String[] parts = sort.split(",");
        String field = parts[0].trim();
        Sort.Direction dir = parts.length>1 && parts[1].equalsIgnoreCase("desc")? Sort.Direction.DESC: Sort.Direction.ASC;
        if (!List.of("nome","cidade","precoMin","precoMax","metragemMin","disponiveis").contains(field)) field="nome";
        return Sort.by(dir, field);
    }

    private Sort parseSortUnidade(String sort) {
        if (sort == null || sort.isBlank()) return Sort.unsorted();
        String[] parts = sort.split(",");
        String field = parts[0].trim();
        Sort.Direction dir = parts.length>1 && parts[1].trim().equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(dir, field);
    }
}
