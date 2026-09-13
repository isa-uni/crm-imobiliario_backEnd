package crm_imobiliario.back.model.service.empreendimento;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import crm_imobiliario.back.model.dto.empreendimento.*;
import crm_imobiliario.back.model.entity.*;
import crm_imobiliario.back.model.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmpreendimentoIaService {

    private final EmpreendimentoRepository empreendimentoRepository;
    private final EmpreendimentoCaracteristicaRepository caracteristicaRepository;
    private final EmpreendimentoPrecoRepository precoRepository;
    private final EmpreendimentoCondicaoRepository condicaoRepository;
    private final EmpreendimentoPlantaRepository plantaRepository;
    private final EmpreendimentoAreaComumRepository areaRepository;
    private final EmpreendimentoDiferencialRepository diferencialRepository;
    private final EmpreendimentoPontoReferenciaRepository pontoRepository;
    private final EmpreendimentoImagemRepository imagemRepository;
    private final EmpreendimentoDocumentoRepository documentoRepository;
    private final EmpreendimentoHistoricoRepository historicoRepository;
    private final EmpreendimentoFonteRepository fonteRepository;
    private final UnidadeRepository unidadeRepository;
    private final UnidadeHistoricoRepository unidadeHistoricoRepository;
    private final ObjectMapper objectMapper;

    private void registrarMudanca(Long empreendimentoId, String campo, String valorAnterior, String valorNovo, Long usuarioId) {
        boolean mudou = valorAnterior == null ? valorNovo != null : !valorAnterior.equals(valorNovo);
        if (!mudou) return;
        historicoRepository.save(EmpreendimentoHistorico.builder()
            .empreendimentoId(empreendimentoId).campo(campo)
            .valorAnterior(valorAnterior).valorNovo(valorNovo)
            .usuario(usuarioId).origem("usuario").build());
    }

    private String slugify(String nome) {
        if (nome == null) return UUID.randomUUID().toString();
        String s = nome.toLowerCase().trim().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        return s.isEmpty() ? UUID.randomUUID().toString() : s;
    }

    @Transactional(readOnly = true)
    public Page<EmpreendimentoCardDTO> listar(String cidade, String bairro, String status, Long precoMin, Long precoMax, String search, Pageable pageable) {
        if (search != null && !search.isBlank()) {
            List<Empreendimento> all = empreendimentoRepository.findByAtivoTrue();
            List<Empreendimento> filtered = all.stream()
                .filter(e -> e.getNome().toLowerCase().contains(search.toLowerCase()) || (e.getBairro()!=null && e.getBairro().toLowerCase().contains(search.toLowerCase())))
                .filter(e -> cidade==null || cidade.isBlank() || cidade.equalsIgnoreCase(e.getCidade()))
                .filter(e -> bairro==null || bairro.isBlank() || bairro.equalsIgnoreCase(e.getBairro()))
                .filter(e -> status==null || status.isBlank() || status.equalsIgnoreCase(e.getStatus()))
                .collect(Collectors.toList());
            int start = (int) pageable.getOffset();
            int end = Math.min(start + pageable.getPageSize(), filtered.size());
            List<EmpreendimentoCardDTO> content = filtered.subList(Math.min(start, filtered.size()), end).stream().map(this::toCard).toList();
            return new PageImpl<>(content, pageable, filtered.size());
        }
        Page<Empreendimento> page = empreendimentoRepository.buscarComFiltros(cidade, null, null, precoMin, precoMax, pageable);
        List<EmpreendimentoCardDTO> cards = page.getContent().stream().filter(e -> {
            if (bairro!=null && !bairro.isBlank() && !bairro.equalsIgnoreCase(e.getBairro())) return false;
            if (status!=null && !status.isBlank() && !status.equalsIgnoreCase(e.getStatus())) return false;
            return true;
        }).map(this::toCard).toList();
        return new PageImpl<>(cards, pageable, page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public EmpreendimentoDetalheDTO detalhar(Long id) {
        Empreendimento emp = empreendimentoRepository.findById(id).orElseThrow(() -> new RuntimeException("Empreendimento não encontrado"));
        return toDetalhe(emp);
    }

    @Transactional(readOnly = true)
    public EmpreendimentoDetalheDTO detalharPorSlug(String slug) {
        Empreendimento emp = empreendimentoRepository.findBySlug(slug).orElseThrow(() -> new RuntimeException("Empreendimento não encontrado"));
        return toDetalhe(emp);
    }

    @Transactional
    public EmpreendimentoDetalheDTO confirmar(EmpreendimentoConfirmacaoDTO dto, Long usuarioId) {
        if (dto.getNome() == null || dto.getNome().isBlank()) throw new IllegalArgumentException("Nome é obrigatório");
        Empreendimento emp;
        boolean isUpdate = dto.getEmpreendimentoExistenteId() != null;
        if (isUpdate) {
            emp = empreendimentoRepository.findById(dto.getEmpreendimentoExistenteId()).orElseThrow(() -> new RuntimeException("Empreendimento existente não encontrado"));
        } else {
            emp = new Empreendimento();
            emp.setSlug(slugify(dto.getNome()));
            int c=1; String base=emp.getSlug();
            while (empreendimentoRepository.findBySlug(emp.getSlug()).isPresent()) emp.setSlug(base+"-"+c++);
        }
        Map<String,String> antes = new HashMap<>();
        antes.put("nome", emp.getNome());
        antes.put("precoMin", emp.getPrecoMin() != null ? String.valueOf(emp.getPrecoMin()) : null);
        antes.put("status", emp.getStatus());
        antes.put("endereco", emp.getEndereco());
        antes.put("cidade", emp.getCidade());
        antes.put("bairro", emp.getBairro());
        antes.put("cep", emp.getCep());
        emp.setNome(dto.getNome());
        emp.setCodigoExterno(dto.getCodigoExterno());
        emp.setStatus(dto.getStatus());
        emp.setDescricaoCurta(dto.getDescricaoCurta());
        emp.setDescricaoCompleta(dto.getDescricaoCompleta());
        emp.setIncorporadora(dto.getIncorporadora());
        emp.setConstrutora(dto.getConstrutora());
        emp.setEndereco(dto.getEndereco());
        emp.setNumero(dto.getNumero());
        emp.setComplemento(dto.getComplemento());
        emp.setBairro(dto.getBairro());
        emp.setCidade(dto.getCidade());
        emp.setUf(dto.getUf());
        emp.setCep(dto.getCep());
        emp.setLat(dto.getLat());
        emp.setLng(dto.getLng());
        emp.setImagemUrl(dto.getImagemUrl());
        emp.setAtivo(true);
        if (emp.getId()==null) emp.setCriadoPor(usuarioId);
        emp.setAtualizadoPor(usuarioId);
        if (dto.getCaracteristica()!=null) {
            if (dto.getCaracteristica().getMetragemMin()!=null) emp.setMetragemMin(dto.getCaracteristica().getMetragemMin());
            if (dto.getCaracteristica().getMetragemMax()!=null) emp.setMetragemMax(dto.getCaracteristica().getMetragemMax());
        }
        if (dto.getPrecos()!=null && !dto.getPrecos().isEmpty()) {
            var p = dto.getPrecos().get(0);
            if (p.getValorMin()!=null) emp.setPrecoMin(p.getValorMin());
            if (p.getValorMax()!=null) emp.setPrecoMax(p.getValorMax());
        }
        emp = empreendimentoRepository.save(emp);
        if (isUpdate) {
            registrarMudanca(emp.getId(), "nome", antes.get("nome"), emp.getNome(), usuarioId);
            registrarMudanca(emp.getId(), "status", antes.get("status"), emp.getStatus(), usuarioId);
            registrarMudanca(emp.getId(), "endereco", antes.get("endereco"), emp.getEndereco(), usuarioId);
            registrarMudanca(emp.getId(), "cidade", antes.get("cidade"), emp.getCidade(), usuarioId);
            registrarMudanca(emp.getId(), "bairro", antes.get("bairro"), emp.getBairro(), usuarioId);
            registrarMudanca(emp.getId(), "cep", antes.get("cep"), emp.getCep(), usuarioId);
        }
        if (dto.getCaracteristica()!=null) {
            var c = dto.getCaracteristica();
            EmpreendimentoCaracteristica car = caracteristicaRepository.findByEmpreendimentoId(emp.getId()).orElse(new EmpreendimentoCaracteristica());
            car.setEmpreendimentoId(emp.getId());
            car.setMetragemMin(c.getMetragemMin()); car.setMetragemMax(c.getMetragemMax());
            car.setQuartosMin(c.getQuartosMin()); car.setQuartosMax(c.getQuartosMax());
            car.setSuitesMin(c.getSuitesMin()); car.setSuitesMax(c.getSuitesMax());
            car.setBanheirosMin(c.getBanheirosMin()); car.setBanheirosMax(c.getBanheirosMax());
            car.setVagasMin(c.getVagasMin()); car.setVagasMax(c.getVagasMax());
            car.setPavimentos(c.getPavimentos()); car.setUnidadesPorAndar(c.getUnidadesPorAndar());
            car.setQtdTorres(c.getQtdTorres()); car.setPossuiElevador(c.getPossuiElevador());
            caracteristicaRepository.save(car);
        }
        if (dto.getPrecos()!=null) {
            for (var pDto : dto.getPrecos()) {
                if (pDto.getValorMin()==null && pDto.getValorMax()==null) continue;
                EmpreendimentoPreco preco = EmpreendimentoPreco.builder()
                    .empreendimentoId(emp.getId())
                    .tipo(pDto.getTipo()!=null?pDto.getTipo():"venda")
                    .valorMin(pDto.getValorMin())
                    .valorMax(pDto.getValorMax())
                    .moeda(pDto.getMoeda()!=null?pDto.getMoeda():"BRL")
                    .dataReferencia(pDto.getDataReferencia()!=null? LocalDate.parse(pDto.getDataReferencia()): LocalDate.now())
                    .observacao(pDto.getObservacao())
                    .criadoPor(usuarioId)
                    .build();
                precoRepository.save(preco);
                historicoRepository.save(EmpreendimentoHistorico.builder().empreendimentoId(emp.getId()).campo("preco").valorAnterior(antes.get("precoMin")).valorNovo(String.valueOf(pDto.getValorMin())).usuario(usuarioId).origem("usuario").build());
            }
        }
        if (dto.getCondicao()!=null) {
            var cd = dto.getCondicao();
            EmpreendimentoCondicaoComercial cond = condicaoRepository.findByEmpreendimentoId(emp.getId()).orElse(new EmpreendimentoCondicaoComercial());
            cond.setEmpreendimentoId(emp.getId());
            cond.setEntrada(cd.getEntrada()); cond.setAto(cd.getAto()); cond.setParcelas(cd.getParcelas()); cond.setValorParcela(cd.getValorParcela());
            cond.setBaloes(cd.getBaloes()); cond.setFinanciamento(cd.getFinanciamento()); cond.setSubsidio(cd.getSubsidio()); cond.setFgts(cd.getFgts());
            cond.setCorrecao(cd.getCorrecao()); cond.setCondicoesEspeciais(cd.getCondicoesEspeciais()); cond.setObservacoes(cd.getObservacoes());
            condicaoRepository.save(cond);
        }
        if (dto.getPlantas()!=null) {
            plantaRepository.findByEmpreendimentoIdOrderByOrdemAsc(emp.getId()).forEach(p -> plantaRepository.delete(p));
            int ordem=0;
            for (var pl : dto.getPlantas()) {
                if (pl.getNome()==null && pl.getMetragem()==null) continue;
                plantaRepository.save(EmpreendimentoPlanta.builder().empreendimentoId(emp.getId()).nome(pl.getNome()).tipo(pl.getTipo()).metragem(pl.getMetragem()).quartos(pl.getQuartos()).suites(pl.getSuites()).banheiros(pl.getBanheiros()).vagas(pl.getVagas()).descricao(pl.getDescricao()).ordem(ordem++).build());
            }
        }
        if (dto.getAreasComuns()!=null) {
            areaRepository.findByEmpreendimentoIdOrderByOrdemAsc(emp.getId()).forEach(a -> areaRepository.delete(a));
            int ordem=0;
            for (var a : dto.getAreasComuns()) {
                if (a.getNome()==null || a.getNome().isBlank()) continue;
                areaRepository.save(EmpreendimentoAreaComum.builder().empreendimentoId(emp.getId()).nome(a.getNome()).descricao(a.getDescricao()).icone(a.getIcone()).ordem(ordem++).build());
            }
        }
        if (dto.getDiferenciais()!=null) {
            diferencialRepository.findByEmpreendimentoIdOrderByOrdemAsc(emp.getId()).forEach(d -> diferencialRepository.delete(d));
            int ordem=0;
            for (var d : dto.getDiferenciais()) {
                if (d.getTitulo()==null || d.getTitulo().isBlank()) continue;
                diferencialRepository.save(EmpreendimentoDiferencial.builder().empreendimentoId(emp.getId()).titulo(d.getTitulo()).descricao(d.getDescricao()).ordem(ordem++).build());
            }
        }
        if (dto.getPontosReferencia()!=null) {
            pontoRepository.findByEmpreendimentoIdOrderByOrdemAsc(emp.getId()).forEach(p -> pontoRepository.delete(p));
            int ordem=0;
            for (var pr : dto.getPontosReferencia()) {
                if (pr.getNome()==null) continue;
                pontoRepository.save(EmpreendimentoPontoReferencia.builder().empreendimentoId(emp.getId()).nome(pr.getNome()).categoria(pr.getCategoria()).distancia(pr.getDistancia()).unidade(pr.getUnidade()).tempo(pr.getTempo()).lat(pr.getLat()).lng(pr.getLng()).ordem(ordem++).build());
            }
        }
        if (dto.getImagens()!=null) {
            imagemRepository.findByEmpreendimentoIdOrderByOrdemAsc(emp.getId()).forEach(i -> imagemRepository.delete(i));
            int ordem=0;
            for (var ig : dto.getImagens()) {
                if (ig.getUrl()==null && ig.getArquivoId()==null) continue;
                imagemRepository.save(EmpreendimentoImagem.builder().empreendimentoId(emp.getId()).arquivoId(ig.getArquivoId()).tipo(ig.getTipo()).legenda(ig.getLegenda()).url(ig.getUrl()).ordem(ordem++).destaque(ig.getDestaque()!=null?ig.getDestaque():false).build());
            }
        }
        if (dto.getUnidades() != null) {
            for (var uDto : dto.getUnidades()) {
                if (uDto.getNomeUnidade() == null || uDto.getNomeUnidade().isBlank()) continue;
                Unidade u = unidadeRepository.findByEmpreendimentoId(emp.getId()).stream()
                    .filter(existente -> uDto.getNomeUnidade().equalsIgnoreCase(existente.getNomeUnidade()))
                    .findFirst().orElseGet(Unidade::new);
                boolean novaUnidade = u.getId() == null;
                Long precoAnterior = u.getPreco();
                String situacaoAnterior = u.getSituacao();

                u.setEmpreendimentoId(emp.getId());
                u.setNomeUnidade(uDto.getNomeUnidade());
                if (uDto.getBloco() != null) u.setBloco(uDto.getBloco());
                if (uDto.getTipologia() != null) u.setTipologia(uDto.getTipologia());
                if (uDto.getAreaPrivativa() != null) u.setAreaPrivativa(uDto.getAreaPrivativa());
                if (uDto.getAreaComum() != null) u.setAreaComum(uDto.getAreaComum());
                if (uDto.getOutrasAreas() != null) u.setOutrasAreas(uDto.getOutrasAreas());
                if (uDto.getGaragem() != null) u.setGaragem(uDto.getGaragem());
                if (uDto.getSituacao() != null) u.setSituacao(uDto.getSituacao());
                if (uDto.getPreco() != null) u.setPreco(uDto.getPreco());
                if (uDto.getAto() != null) u.setAto(uDto.getAto());
                if (uDto.getSubsidioCohapar() != null) u.setSubsidioCohapar(uDto.getSubsidioCohapar());
                if (uDto.getFinanciamento() != null) u.setFinanciamento(uDto.getFinanciamento());
                if (uDto.getValorAvaliacao() != null) u.setValorAvaliacao(uDto.getValorAvaliacao());
                if (uDto.getObservacoes() != null) u.setObservacoes(uDto.getObservacoes());
                if (uDto.getDocumentoOrigemId() != null) u.setDocumentoOrigemId(uDto.getDocumentoOrigemId());
                if (uDto.getLinhaOrigem() != null) u.setLinhaOrigem(uDto.getLinhaOrigem());
                u.setStatusValidacao(uDto.getStatusValidacao() != null ? uDto.getStatusValidacao() : "confirmado");
                u = unidadeRepository.save(u);

                if (!novaUnidade) {
                    if (uDto.getPreco() != null && !uDto.getPreco().equals(precoAnterior)) {
                        unidadeHistoricoRepository.save(UnidadeHistorico.builder()
                            .unidadeId(u.getId()).campo("preco")
                            .valorAnterior(precoAnterior != null ? String.valueOf(precoAnterior) : null)
                            .valorNovo(String.valueOf(uDto.getPreco()))
                            .usuario(usuarioId).documentoOrigemId(uDto.getDocumentoOrigemId())
                            .origem("usuario").build());
                    }
                    if (uDto.getSituacao() != null && !uDto.getSituacao().equalsIgnoreCase(situacaoAnterior)) {
                        unidadeHistoricoRepository.save(UnidadeHistorico.builder()
                            .unidadeId(u.getId()).campo("situacao")
                            .valorAnterior(situacaoAnterior)
                            .valorNovo(uDto.getSituacao())
                            .usuario(usuarioId).documentoOrigemId(uDto.getDocumentoOrigemId())
                            .origem("usuario").build());
                    }
                }
            }
        }

        // recalcula agregados do empreendimento a partir das unidades realmente persistidas
        // (nunca inventa: reflete só o que está no banco após esta confirmação)
        EmpreendimentoDetalheDTO.UnidadesResumoDTO resumo = calcularResumoUnidades(emp.getId());
        if (resumo != null) {
            emp.setTotal(resumo.getTotal());
            emp.setDisponiveis(resumo.getDisponiveis());
            emp.setReservadas(resumo.getReservadas());
            emp.setVendidas(resumo.getVendidas());
            emp.setEmProcesso(resumo.getEmProcesso());
            if (resumo.getMetragemMin() != null) emp.setMetragemMin(resumo.getMetragemMin());
            if (resumo.getMetragemMax() != null) emp.setMetragemMax(resumo.getMetragemMax());
            if (resumo.getPrecoMin() != null) emp.setPrecoMin(resumo.getPrecoMin());
            if (resumo.getPrecoMax() != null) emp.setPrecoMax(resumo.getPrecoMax());
            if (resumo.getTipologias() != null && !resumo.getTipologias().isEmpty()) {
                try { emp.setTiposJson(objectMapper.writeValueAsString(resumo.getTipologias())); }
                catch (Exception ex) { log.warn("Falha ao serializar tipologias do empreendimento {}: {}", emp.getId(), ex.getMessage()); }
            }
            empreendimentoRepository.save(emp);
        }

        return toDetalhe(emp);
    }

    /** Estatísticas derivadas das unidades realmente cadastradas — nunca calculadas a partir de suposições. */
    private EmpreendimentoDetalheDTO.UnidadesResumoDTO calcularResumoUnidades(Long empreendimentoId) {
        List<Unidade> unidades = unidadeRepository.findByEmpreendimentoId(empreendimentoId);
        if (unidades.isEmpty()) return null;
        return EmpreendimentoDetalheDTO.UnidadesResumoDTO.builder()
            .total(unidades.size())
            .disponiveis((int) unidades.stream().filter(u -> "disponivel".equals(u.getSituacao())).count())
            .reservadas((int) unidades.stream().filter(u -> "reservada".equals(u.getSituacao())).count())
            .vendidas((int) unidades.stream().filter(u -> "vendida".equals(u.getSituacao())).count())
            .emProcesso((int) unidades.stream().filter(u -> "em_processo".equals(u.getSituacao())).count())
            .metragemMin(minimo(unidades, Unidade::getAreaPrivativa))
            .metragemMax(maximo(unidades, Unidade::getAreaPrivativa))
            .precoMin(minimoLong(unidades, Unidade::getPreco))
            .precoMax(maximoLong(unidades, Unidade::getPreco))
            .atoMin(minimoLong(unidades, Unidade::getAto))
            .atoMax(maximoLong(unidades, Unidade::getAto))
            .subsidioMin(minimoLong(unidades, Unidade::getSubsidioCohapar))
            .subsidioMax(maximoLong(unidades, Unidade::getSubsidioCohapar))
            .financiamentoMin(minimoLong(unidades, Unidade::getFinanciamento))
            .financiamentoMax(maximoLong(unidades, Unidade::getFinanciamento))
            .valorAvaliacaoMin(minimoLong(unidades, Unidade::getValorAvaliacao))
            .valorAvaliacaoMax(maximoLong(unidades, Unidade::getValorAvaliacao))
            .tipologias(valoresDistintos(unidades, Unidade::getTipologia))
            .blocos(valoresDistintos(unidades, Unidade::getBloco))
            .build();
    }

    private Double minimo(List<Unidade> us, Function<Unidade, Double> f) {
        return us.stream().map(f).filter(Objects::nonNull).min(Double::compareTo).orElse(null);
    }
    private Double maximo(List<Unidade> us, Function<Unidade, Double> f) {
        return us.stream().map(f).filter(Objects::nonNull).max(Double::compareTo).orElse(null);
    }
    private Long minimoLong(List<Unidade> us, Function<Unidade, Long> f) {
        return us.stream().map(f).filter(Objects::nonNull).min(Long::compareTo).orElse(null);
    }
    private Long maximoLong(List<Unidade> us, Function<Unidade, Long> f) {
        return us.stream().map(f).filter(Objects::nonNull).max(Long::compareTo).orElse(null);
    }
    private List<String> valoresDistintos(List<Unidade> us, Function<Unidade, String> f) {
        return us.stream().map(f).filter(v -> v != null && !v.isBlank()).distinct().sorted().toList();
    }

    @Transactional(readOnly = true)
    public Page<UnidadeDTO> listarUnidades(Long empreendimentoId, String situacao, String bloco, String tipologia,
                                            String busca, Long precoMin, Long precoMax, Double areaMin, Double areaMax,
                                            Pageable pageable) {
        List<Unidade> filtradas = unidadeRepository.findByEmpreendimentoId(empreendimentoId).stream()
            .filter(u -> situacao == null || situacao.isBlank() || situacao.equalsIgnoreCase(u.getSituacao()))
            .filter(u -> bloco == null || bloco.isBlank() || bloco.equalsIgnoreCase(u.getBloco()))
            .filter(u -> tipologia == null || tipologia.isBlank() || tipologia.equalsIgnoreCase(u.getTipologia()))
            .filter(u -> busca == null || busca.isBlank() || (u.getNomeUnidade() != null && u.getNomeUnidade().toLowerCase().contains(busca.toLowerCase())))
            .filter(u -> precoMin == null || (u.getPreco() != null && u.getPreco() >= precoMin))
            .filter(u -> precoMax == null || (u.getPreco() != null && u.getPreco() <= precoMax))
            .filter(u -> areaMin == null || (u.getAreaPrivativa() != null && u.getAreaPrivativa() >= areaMin))
            .filter(u -> areaMax == null || (u.getAreaPrivativa() != null && u.getAreaPrivativa() <= areaMax))
            .collect(Collectors.toCollection(ArrayList::new));
        if (pageable.getSort().isSorted()) {
            for (Sort.Order order : pageable.getSort()) {
                Comparator<Unidade> cmp = comparadorUnidade(order.getProperty());
                if (cmp != null) {
                    filtradas.sort(order.isDescending() ? cmp.reversed() : cmp);
                    break;
                }
            }
        }
        int start = Math.min((int) pageable.getOffset(), filtradas.size());
        int end = Math.min(start + pageable.getPageSize(), filtradas.size());
        List<UnidadeDTO> pagina = filtradas.subList(start, end).stream().map(this::toUnidadeDTO).toList();
        return new PageImpl<>(pagina, pageable, filtradas.size());
    }

    private Comparator<Unidade> comparadorUnidade(String campo) {
        return switch (campo) {
            case "preco" -> Comparator.comparing(Unidade::getPreco, Comparator.nullsLast(Comparator.naturalOrder()));
            case "areaPrivativa" -> Comparator.comparing(Unidade::getAreaPrivativa, Comparator.nullsLast(Comparator.naturalOrder()));
            case "nomeUnidade" -> Comparator.comparing(Unidade::getNomeUnidade, Comparator.nullsLast(Comparator.naturalOrder()));
            case "bloco" -> Comparator.comparing(Unidade::getBloco, Comparator.nullsLast(Comparator.naturalOrder()));
            case "tipologia" -> Comparator.comparing(Unidade::getTipologia, Comparator.nullsLast(Comparator.naturalOrder()));
            case "situacao" -> Comparator.comparing(Unidade::getSituacao, Comparator.nullsLast(Comparator.naturalOrder()));
            default -> null;
        };
    }

    /** Rastreabilidade completa dos campos do empreendimento (§9/§12 do spec) — documento, página, trecho, confiança. */
    @Transactional(readOnly = true)
    public List<EmpreendimentoExtracaoDTO.FonteDTO> listarFontes(Long empreendimentoId) {
        return fonteRepository.findByEmpreendimentoId(empreendimentoId).stream()
            .filter(f -> !"_alerta".equals(f.getCampo()))
            .filter(f -> f.getCampo() == null || !f.getCampo().startsWith("unidades["))
            .map(f -> EmpreendimentoExtracaoDTO.FonteDTO.builder()
                .id(f.getId()).documentoId(f.getDocumentoId()).documentoNome(f.getDocumentoNome())
                .campo(f.getCampo()).valorExtraido(f.getValorExtraido()).pagina(f.getPagina())
                .trecho(f.getTrecho()).confianca(f.getConfianca()).build())
            .sorted(Comparator.comparing(EmpreendimentoExtracaoDTO.FonteDTO::getCampo))
            .toList();
    }

    private UnidadeDTO toUnidadeDTO(Unidade u) {
        return UnidadeDTO.builder()
            .id(u.getId()).nomeUnidade(u.getNomeUnidade()).bloco(u.getBloco()).tipologia(u.getTipologia())
            .areaPrivativa(u.getAreaPrivativa()).areaComum(u.getAreaComum()).outrasAreas(u.getOutrasAreas())
            .garagem(u.getGaragem()).situacao(u.getSituacao()).preco(u.getPreco()).ato(u.getAto())
            .subsidioCohapar(u.getSubsidioCohapar()).financiamento(u.getFinanciamento()).valorAvaliacao(u.getValorAvaliacao())
            .observacoes(u.getObservacoes()).documentoOrigemId(u.getDocumentoOrigemId()).linhaOrigem(u.getLinhaOrigem())
            .statusValidacao(u.getStatusValidacao())
            .build();
    }

    public List<EmpreendimentoCardDTO> buscarDuplicados(String nome, String codigo, String endereco) {
        List<Empreendimento> all = empreendimentoRepository.findAll();
        List<EmpreendimentoCardDTO> cands = new ArrayList<>();
        for (Empreendimento e : all) {
            int sim = 0;
            if (nome!=null && e.getNome()!=null) sim = Math.max(sim, similaridade(nome, e.getNome()));
            if (codigo!=null && e.getCodigoExterno()!=null && codigo.equalsIgnoreCase(e.getCodigoExterno())) sim = 100;
            if (endereco!=null && e.getEndereco()!=null && endereco.equalsIgnoreCase(e.getEndereco())) sim = Math.max(sim, 80);
            if (sim >= 70) cands.add(toCard(e));
        }
        return cands.stream().limit(5).toList();
    }

    private int similaridade(String a, String b) {
        a=a.toLowerCase(); b=b.toLowerCase();
        if (a.equals(b)) return 100;
        if (b.contains(a) || a.contains(b)) return 87;
        int[][] dp = new int[a.length()+1][b.length()+1];
        for (int i=0;i<=a.length();i++) dp[i][0]=i;
        for (int j=0;j<=b.length();j++) dp[0][j]=j;
        for (int i=1;i<=a.length();i++) for(int j=1;j<=b.length();j++) dp[i][j]= a.charAt(i-1)==b.charAt(j-1)?dp[i-1][j-1]:1+Math.min(dp[i-1][j], Math.min(dp[i][j-1], dp[i-1][j-1]));
        int dist = dp[a.length()][b.length()];
        int max = Math.max(a.length(), b.length());
        return max==0?100: (int)((1 - (double)dist/max)*100);
    }

    private EmpreendimentoCardDTO toCard(Empreendimento e) {
        EmpreendimentoCaracteristica car = caracteristicaRepository.findByEmpreendimentoId(e.getId()).orElse(null);
        return EmpreendimentoCardDTO.builder()
            .id(e.getId()).nome(e.getNome()).slug(e.getSlug()).codigoExterno(e.getCodigoExterno()).codigoCrm(e.getCodigoCrm())
            .status(e.getStatus()).cidade(e.getCidade()).bairro(e.getBairro()).uf(e.getUf())
            .metragemMin(car!=null?car.getMetragemMin():e.getMetragemMin())
            .metragemMax(car!=null?car.getMetragemMax():e.getMetragemMax())
            .quartosMin(car!=null?car.getQuartosMin():null).quartosMax(car!=null?car.getQuartosMax():null)
            .vagasMin(car!=null?car.getVagasMin():null).vagasMax(car!=null?car.getVagasMax():null)
            .precoMin(e.getPrecoMin()).precoMax(e.getPrecoMax())
            .imagemUrl(e.getImagemUrl()).disponiveis(e.getDisponiveis()).ativo(e.getAtivo())
            .build();
    }

    private EmpreendimentoDetalheDTO toDetalhe(Empreendimento e) {
        var car = caracteristicaRepository.findByEmpreendimentoId(e.getId()).orElse(null);
        var precos = precoRepository.findByEmpreendimentoIdOrderByDataReferenciaDesc(e.getId()).stream().map(p -> EmpreendimentoDetalheDTO.PrecoDTO.builder()
            .id(p.getId()).tipo(p.getTipo()).valorMin(p.getValorMin()).valorMax(p.getValorMax()).moeda(p.getMoeda())
            .dataReferencia(p.getDataReferencia()!=null?p.getDataReferencia().toString():null).observacao(p.getObservacao()).build()).toList();
        var cond = condicaoRepository.findByEmpreendimentoId(e.getId()).orElse(null);
        var plantas = plantaRepository.findByEmpreendimentoIdOrderByOrdemAsc(e.getId()).stream().map(p -> EmpreendimentoDetalheDTO.PlantaDTO.builder()
            .id(p.getId()).nome(p.getNome()).tipo(p.getTipo()).metragem(p.getMetragem()).quartos(p.getQuartos()).suites(p.getSuites()).banheiros(p.getBanheiros()).vagas(p.getVagas()).descricao(p.getDescricao()).arquivoId(p.getArquivoId()).ordem(p.getOrdem()).build()).toList();
        var areas = areaRepository.findByEmpreendimentoIdOrderByOrdemAsc(e.getId()).stream().map(a -> EmpreendimentoDetalheDTO.AreaComumDTO.builder().id(a.getId()).nome(a.getNome()).descricao(a.getDescricao()).icone(a.getIcone()).ordem(a.getOrdem()).build()).toList();
        var difs = diferencialRepository.findByEmpreendimentoIdOrderByOrdemAsc(e.getId()).stream().map(d -> EmpreendimentoDetalheDTO.DiferencialDTO.builder().id(d.getId()).titulo(d.getTitulo()).descricao(d.getDescricao()).ordem(d.getOrdem()).build()).toList();
        var pontos = pontoRepository.findByEmpreendimentoIdOrderByOrdemAsc(e.getId()).stream().map(p -> EmpreendimentoDetalheDTO.PontoReferenciaDTO.builder().id(p.getId()).nome(p.getNome()).categoria(p.getCategoria()).distancia(p.getDistancia()).unidade(p.getUnidade()).tempo(p.getTempo()).lat(p.getLat()).lng(p.getLng()).ordem(p.getOrdem()).build()).toList();
        var imagens = imagemRepository.findByEmpreendimentoIdOrderByOrdemAsc(e.getId()).stream().map(i -> EmpreendimentoDetalheDTO.ImagemDTO.builder().id(i.getId()).arquivoId(i.getArquivoId()).tipo(i.getTipo()).legenda(i.getLegenda()).url(i.getUrl()).ordem(i.getOrdem()).destaque(i.getDestaque()).build()).toList();
        var docs = documentoRepository.findByEmpreendimentoId(e.getId()).stream().map(d -> EmpreendimentoDetalheDTO.DocumentoDTO.builder().id(d.getId()).nomeOriginal(d.getNomeOriginal()).tipo(d.getTipo()).caminho(d.getCaminho()).hash(d.getHash()).mime(d.getMime()).tamanho(d.getTamanho()).statusProcessamento(d.getStatusProcessamento()).dataUpload(d.getDataUpload()).build()).toList();
        var unidadesResumo = calcularResumoUnidades(e.getId());
        EmpreendimentoDetalheDTO.CaracteristicaDTO carDto = null;
        if (car != null) carDto = EmpreendimentoDetalheDTO.CaracteristicaDTO.builder()
            .metragemMin(car.getMetragemMin()).metragemMax(car.getMetragemMax())
            .quartosMin(car.getQuartosMin()).quartosMax(car.getQuartosMax()).suitesMin(car.getSuitesMin()).suitesMax(car.getSuitesMax())
            .banheirosMin(car.getBanheirosMin()).banheirosMax(car.getBanheirosMax()).vagasMin(car.getVagasMin()).vagasMax(car.getVagasMax())
            .pavimentos(car.getPavimentos()).unidadesPorAndar(car.getUnidadesPorAndar()).qtdTorres(car.getQtdTorres()).possuiElevador(car.getPossuiElevador()).build();
        EmpreendimentoDetalheDTO.CondicaoDTO condDto = null;
        if (cond != null) condDto = EmpreendimentoDetalheDTO.CondicaoDTO.builder()
            .entrada(cond.getEntrada()).ato(cond.getAto()).parcelas(cond.getParcelas()).valorParcela(cond.getValorParcela())
            .baloes(cond.getBaloes()).financiamento(cond.getFinanciamento()).subsidio(cond.getSubsidio()).fgts(cond.getFgts())
            .correcao(cond.getCorrecao()).condicoesEspeciais(cond.getCondicoesEspeciais()).observacoes(cond.getObservacoes()).build();
        return EmpreendimentoDetalheDTO.builder()
            .id(e.getId()).nome(e.getNome()).slug(e.getSlug()).codigoExterno(e.getCodigoExterno()).codigoCrm(e.getCodigoCrm())
            .status(e.getStatus()).ativo(e.getAtivo()).descricaoCurta(e.getDescricaoCurta()).descricaoCompleta(e.getDescricaoCompleta())
            .incorporadora(e.getIncorporadora()).construtora(e.getConstrutora())
            .endereco(e.getEndereco()).numero(e.getNumero()).complemento(e.getComplemento()).bairro(e.getBairro()).cidade(e.getCidade()).uf(e.getUf()).cep(e.getCep())
            .lat(e.getLat()).lng(e.getLng()).imagemUrl(e.getImagemUrl()).dataCadastro(e.getDataCadastro()).dataAtualizacao(e.getDataAtualizacao())
            .caracteristica(carDto).precos(precos).precoAtual(precos.isEmpty()?null:precos.get(0)).condicao(condDto)
            .plantas(plantas).areasComuns(areas).diferenciais(difs).pontosReferencia(pontos).imagens(imagens).documentos(docs)
            .unidadesResumo(unidadesResumo)
            .build();
    }
}
