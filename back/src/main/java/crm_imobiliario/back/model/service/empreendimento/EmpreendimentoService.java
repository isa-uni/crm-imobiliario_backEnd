package crm_imobiliario.back.model.service.empreendimento;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import crm_imobiliario.back.model.dto.empreendimento.EmpreendimentoDTO;
import crm_imobiliario.back.model.entity.Empreendimento;
import crm_imobiliario.back.model.entity.Unidade;
import crm_imobiliario.back.model.repository.EmpreendimentoRepository;
import crm_imobiliario.back.model.repository.UnidadeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmpreendimentoService {

    private final EmpreendimentoRepository empreendimentoRepository;
    private final UnidadeRepository unidadeRepository;
    private final ObjectMapper objectMapper;

    // ------------------------------------------------------------------ leitura
    @Transactional(readOnly = true)
    public List<EmpreendimentoDTO> listarDisponiveis(String cidade, String regiao) {
        List<Empreendimento> list = empreendimentoRepository.findByAtivoTrueAndDisponiveisGreaterThan(0);
        return filtrar(list, cidade, regiao).stream()
                .sorted(Comparator.comparing(Empreendimento::getNome))
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<EmpreendimentoDTO> listarPaginado(String cidade, String regiao, Boolean disponiveis,
                                                   Long precoMin, Long precoMax,
                                                   int page, int size, String sort) {
        Sort s = parseSort(sort);
        Page<Empreendimento> p = empreendimentoRepository.buscarComFiltros(
                cidade, regiao, disponiveis, precoMin, precoMax, PageRequest.of(page, size, s));
        return p.map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public Optional<EmpreendimentoDTO> buscarPorId(Long id) {
        return empreendimentoRepository.findById(id).map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public Optional<EmpreendimentoDTO> buscarPorSlug(String slug) {
        return empreendimentoRepository.findBySlug(slug).map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public Optional<EmpreendimentoDTO> buscarPorCodigoCrm(String codigo) {
        return empreendimentoRepository.findByCodigoCrm(codigo).map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public Optional<EmpreendimentoDTO> detalhe(Long id) {
        Optional<Empreendimento> emp = empreendimentoRepository.findById(id);
        if (emp.isEmpty()) return Optional.empty();
        Empreendimento e = emp.get();
        EmpreendimentoDTO dto = toDTO(e);
        List<Unidade> unidades = unidadeRepository.findByEmpreendimentoId(e.getId());
        dto.setUnidades(unidades.stream().map(this::toUnidadeDTO).collect(Collectors.toList()));
        return Optional.of(dto);
    }

    // ------------------------------------------------------------- escrita (sync)
    @Transactional
    public Empreendimento salvarOuAtualizar(Empreendimento emp) {
        Optional<Empreendimento> existente = Optional.empty();
        if (emp.getCodigoCrm() != null) {
            existente = empreendimentoRepository.findByCodigoCrm(emp.getCodigoCrm());
        }
        if (existente.isEmpty() && emp.getSlug() != null) {
            existente = empreendimentoRepository.findBySlug(emp.getSlug());
        }
        if (existente.isPresent()) {
            Empreendimento e = existente.get();
            // merge campos não-nulos
            merge(e, emp);
            e.setUltimaSincronizacao(Instant.now());
            return empreendimentoRepository.save(e);
        } else {
            emp.setUltimaSincronizacao(Instant.now());
            return empreendimentoRepository.save(emp);
        }
    }

    // ------------------------------------------------------------------ helpers
    private List<Empreendimento> filtrar(List<Empreendimento> list, String cidade, String regiao) {
        return list.stream()
                .filter(e -> cidade == null || cidade.isBlank() || cidade.equalsIgnoreCase(e.getCidade()))
                .filter(e -> regiao == null || regiao.isBlank() || regiao.equalsIgnoreCase(e.getRegiao()))
                .collect(Collectors.toList());
    }

    private Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) return Sort.by("nome").ascending();
        String[] parts = sort.split(",");
        String field = parts[0].trim();
        Sort.Direction dir = parts.length > 1 && parts[1].equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        // whitelist
        if (!List.of("nome", "precoMin", "precoMax", "metragemMin", "disponiveis", "andamento").contains(field)) {
            field = "nome";
        }
        return Sort.by(dir, field);
    }

    public EmpreendimentoDTO toDTO(Empreendimento e) {
        EmpreendimentoDTO dto = EmpreendimentoDTO.builder()
                .id(e.getId())
                .codigoCrm(e.getCodigoCrm())
                .nome(e.getNome())
                .slug(e.getSlug())
                .cidade(e.getCidade())
                .uf(e.getUf())
                .regiao(e.getRegiao())
                .bairro(e.getBairro())
                .endereco(e.getEndereco())
                .enriquecido(e.getEnriquecido())
                .status(e.getStatus())
                .atualizadoEm(e.getUltimaSincronizacao() != null ? e.getUltimaSincronizacao() : e.getDataAtualizacao())
                .capa(EmpreendimentoDTO.CapaDTO.builder()
                        .descricao(e.getDescricaoResumo())
                        .imagem(EmpreendimentoDTO.ImagemDTO.builder()
                                .url(e.getImagemUrl())
                                .legenda("Fachada")
                                .build())
                        .build())
                .disponibilidade(EmpreendimentoDTO.DisponibilidadeDTO.builder()
                        .possuiImoveisDisponiveis(e.getDisponiveis() != null && e.getDisponiveis() > 0)
                        .quantidade(e.getDisponiveis())
                        .total(e.getTotal())
                        .reservadas(e.getReservadas())
                        .vendidas(e.getVendidas())
                        .emProcesso(e.getEmProcesso())
                        .andamento(e.getAndamento())
                        .build())
                .informacoes(EmpreendimentoDTO.InformacoesDTO.builder()
                        .endereco(e.getEndereco())
                        .bairro(e.getBairro())
                        .cidade(e.getCidade())
                        .uf(e.getUf())
                        .regiao(e.getRegiao())
                        .geo(EmpreendimentoDTO.GeoDTO.builder()
                                .lat(e.getLat()).lng(e.getLng()).zoom(e.getZoom()).build())
                        .metragemMin(e.getMetragemMin())
                        .metragemMax(e.getMetragemMax())
                        .tipos(parseJsonList(e.getTiposJson()))
                        .quartos(parseJsonIntList(e.getQuartosJson()))
                        .plantas("")
                        .build())
                .precos(EmpreendimentoDTO.PrecosDTO.builder()
                        .min(e.getPrecoMin())
                        .max(e.getPrecoMax())
                        .parcelamentoMax(e.getParcelamentoMax())
                        .condicoes(e.getCondicoesComerciais())
                        .tabela(EmpreendimentoDTO.TabelaDTO.builder()
                                .referencia(e.getTabelaReferencia())
                                .validade(e.getTabelaValidade())
                                .hash(e.getTabelaHash())
                                .build())
                        .build())
                .build();
        return dto;
    }

    private EmpreendimentoDTO.UnidadeDTO toUnidadeDTO(Unidade u) {
        return EmpreendimentoDTO.UnidadeDTO.builder()
                .nomeUnidade(u.getNomeUnidade())
                .bloco(u.getBloco())
                .andar(u.getAndar())
                .coluna(u.getColuna())
                .areaPrivativa(u.getAreaPrivativa())
                .tipologia(u.getTipologia())
                .situacao(u.getSituacao())
                .preco(u.getPreco())
                .build();
    }

    @SuppressWarnings("unchecked")
    private List<String> parseJsonList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private List<Integer> parseJsonIntList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<Integer>>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private void merge(Empreendimento alvo, Empreendimento origem) {
        if (origem.getNome() != null) alvo.setNome(origem.getNome());
        if (origem.getSlug() != null) alvo.setSlug(origem.getSlug());
        if (origem.getCidade() != null) alvo.setCidade(origem.getCidade());
        if (origem.getUf() != null) alvo.setUf(origem.getUf());
        if (origem.getRegiao() != null) alvo.setRegiao(origem.getRegiao());
        if (origem.getBairro() != null) alvo.setBairro(origem.getBairro());
        if (origem.getEndereco() != null) alvo.setEndereco(origem.getEndereco());
        if (origem.getLat() != null) alvo.setLat(origem.getLat());
        if (origem.getLng() != null) alvo.setLng(origem.getLng());
        if (origem.getZoom() != null) alvo.setZoom(origem.getZoom());
        if (origem.getAndamento() != null) alvo.setAndamento(origem.getAndamento());
        if (origem.getTotal() != null) alvo.setTotal(origem.getTotal());
        if (origem.getDisponiveis() != null) alvo.setDisponiveis(origem.getDisponiveis());
        if (origem.getReservadas() != null) alvo.setReservadas(origem.getReservadas());
        if (origem.getVendidas() != null) alvo.setVendidas(origem.getVendidas());
        if (origem.getEmProcesso() != null) alvo.setEmProcesso(origem.getEmProcesso());
        if (origem.getMetragemMin() != null) alvo.setMetragemMin(origem.getMetragemMin());
        if (origem.getMetragemMax() != null) alvo.setMetragemMax(origem.getMetragemMax());
        if (origem.getPrecoMin() != null) alvo.setPrecoMin(origem.getPrecoMin());
        if (origem.getPrecoMax() != null) alvo.setPrecoMax(origem.getPrecoMax());
        if (origem.getTiposJson() != null) alvo.setTiposJson(origem.getTiposJson());
        if (origem.getQuartosJson() != null) alvo.setQuartosJson(origem.getQuartosJson());
        if (origem.getStatus() != null) alvo.setStatus(origem.getStatus());
        if (origem.getTabelaReferencia() != null) alvo.setTabelaReferencia(origem.getTabelaReferencia());
        if (origem.getTabelaValidade() != null) alvo.setTabelaValidade(origem.getTabelaValidade());
        if (origem.getTabelaHash() != null) alvo.setTabelaHash(origem.getTabelaHash());
        if (origem.getImagemUrl() != null) alvo.setImagemUrl(origem.getImagemUrl());
        if (origem.getDescricaoResumo() != null) alvo.setDescricaoResumo(origem.getDescricaoResumo());
        if (origem.getCondicoesComerciais() != null) alvo.setCondicoesComerciais(origem.getCondicoesComerciais());
        if (origem.getPrevisaoEntrega() != null) alvo.setPrevisaoEntrega(origem.getPrevisaoEntrega());
        if (origem.getEntregaContratual() != null) alvo.setEntregaContratual(origem.getEntregaContratual());
        if (origem.getParcelamentoMax() != null) alvo.setParcelamentoMax(origem.getParcelamentoMax());
        if (origem.getEnriquecido() != null) alvo.setEnriquecido(origem.getEnriquecido());
        if (origem.getUnmatchedReason() != null) alvo.setUnmatchedReason(origem.getUnmatchedReason());
    }
}
