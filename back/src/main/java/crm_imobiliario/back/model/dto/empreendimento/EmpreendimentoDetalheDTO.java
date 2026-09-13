package crm_imobiliario.back.model.dto.empreendimento;

import java.time.Instant;
import java.util.List;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EmpreendimentoDetalheDTO {
    private Long id;
    private String nome;
    private String slug;
    private String codigoExterno;
    private String codigoCrm;
    private String status;
    private Boolean ativo;
    private String descricaoCurta;
    private String descricaoCompleta;
    private String incorporadora;
    private String construtora;
    // endereco
    private String endereco;
    private String numero;
    private String complemento;
    private String bairro;
    private String cidade;
    private String uf;
    private String cep;
    private Double lat;
    private Double lng;
    private String imagemUrl;
    private Instant dataCadastro;
    private Instant dataAtualizacao;
    // caracteristicas
    private CaracteristicaDTO caracteristica;
    // precos historico
    private List<PrecoDTO> precos;
    private PrecoDTO precoAtual;
    // condicao
    private CondicaoDTO condicao;
    // listas
    private List<PlantaDTO> plantas;
    private List<AreaComumDTO> areasComuns;
    private List<DiferencialDTO> diferenciais;
    private List<PontoReferenciaDTO> pontosReferencia;
    private List<ImagemDTO> imagens;
    private List<DocumentoDTO> documentos;
    // agregados calculados a partir das unidades realmente cadastradas (nunca inventados)
    private UnidadesResumoDTO unidadesResumo;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CaracteristicaDTO {
        private Double metragemMin, metragemMax;
        private Integer quartosMin, quartosMax, suitesMin, suitesMax, banheirosMin, banheirosMax, vagasMin, vagasMax;
        private Integer pavimentos, unidadesPorAndar, qtdTorres;
        private Boolean possuiElevador;
    }
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class PrecoDTO {
        private Long id;
        private String tipo;
        private Long valorMin, valorMax;
        private String moeda;
        private String dataReferencia;
        private String observacao;
    }
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CondicaoDTO {
        private Double entrada, ato, valorParcela, subsidio;
        private Integer parcelas;
        private String baloes, financiamento, correcao, condicoesEspeciais, observacoes;
        private Boolean fgts;
    }
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class PlantaDTO {
        private Long id; private String nome, tipo; private Double metragem; private Integer quartos, suites, banheiros, vagas; private String descricao; private Long arquivoId; private Integer ordem;
    }
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class AreaComumDTO { private Long id; private String nome, descricao, icone; private Integer ordem; }
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class DiferencialDTO { private Long id; private String titulo, descricao; private Integer ordem; }
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class PontoReferenciaDTO { private Long id; private String nome, categoria, unidade; private Double distancia; private Integer tempo; private Double lat, lng; private Integer ordem; }
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ImagemDTO { private Long id; private Long arquivoId; private String tipo, legenda, url; private Integer ordem; private Boolean destaque; }
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class DocumentoDTO { private Long id; private String nomeOriginal, tipo, caminho, hash, mime; private Long tamanho; private String statusProcessamento; private Instant dataUpload; }

    /** Estatísticas derivadas das unidades persistidas (§10/§13 — a API deve retornar dados estruturados, não texto único). */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class UnidadesResumoDTO {
        private Integer total, disponiveis, reservadas, vendidas, emProcesso;
        private Double metragemMin, metragemMax;
        private Long precoMin, precoMax;
        private Long atoMin, atoMax;
        private Long subsidioMin, subsidioMax;
        private Long financiamentoMin, financiamentoMax;
        private Long valorAvaliacaoMin, valorAvaliacaoMax;
        private List<String> tipologias;
        private List<String> blocos;
    }
}
