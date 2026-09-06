package crm_imobiliario.back.model.dto.empreendimento;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmpreendimentoDTO {

    private Long id;
    private String codigoCrm;
    private String nome;
    private String slug;
    private String cidade;
    private String uf;
    private String regiao;
    private String bairro;
    private String endereco;

    private CapaDTO capa;

    private DisponibilidadeDTO disponibilidade;

    private InformacoesDTO informacoes;

    private PrecosDTO precos;

    private Boolean enriquecido;
    private String status;
    private Instant atualizadoEm;

    // Detalhe opcional
    private List<UnidadeDTO> unidades;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CapaDTO {
        private String descricao;
        private List<String> resumo10s;
        private ImagemDTO imagem;
        private List<String> diferenciais;
        private List<String> areasComuns;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ImagemDTO {
        private String url;
        private String legenda;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class DisponibilidadeDTO {
        private Boolean possuiImoveisDisponiveis;
        private Integer quantidade;
        private Integer total;
        private Integer reservadas;
        private Integer vendidas;
        private Integer emProcesso;
        private Double andamento;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class InformacoesDTO {
        private String endereco;
        private String bairro;
        private String cidade;
        private String uf;
        private String regiao;
        private GeoDTO geo;
        private Double metragemMin;
        private Double metragemMax;
        private List<String> tipos;
        private List<Integer> quartos;
        private List<String> pontosReferencia;
        private String plantas;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class GeoDTO {
        private Double lat;
        private Double lng;
        private Integer zoom;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class PrecosDTO {
        private Long min;
        private Long max;
        private Integer parcelamentoMax;
        private String condicoes;
        private TabelaDTO tabela;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class TabelaDTO {
        private String referencia;
        private String validade;
        private String hash;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class UnidadeDTO {
        private String nomeUnidade;
        private String bloco;
        private Integer andar;
        private Integer coluna;
        private Double areaPrivativa;
        private String tipologia;
        private String situacao;
        private Long preco;
    }
}
