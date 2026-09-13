package crm_imobiliario.back.model.entity;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
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
@Entity
@Table(name = "unidade", indexes = {
    @Index(name = "idx_unid_emp", columnList = "empreendimentoId"),
    @Index(name = "idx_unid_situacao", columnList = "situacao"),
    @Index(name = "idx_unid_nome", columnList = "nomeUnidade")
})
public class Unidade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long empreendimentoId;

    /** B01.101 etc — chave de matching com PDF */
    private String nomeUnidade;

    private String idUnidadeCrm;

    private String bloco;
    private String etapa;
    private Integer andar;
    private Integer coluna;
    private String posicao;

    private Double areaPrivativa;
    private Double areaComum;
    private Double outrasAreas;

    /** 2Q - Garden */
    private String tipologia;

    /** disponivel | reservada | vendida | em_processo */
    private String situacao;
    private String situacaoNome;

    private Long preco;

    @Column(columnDefinition = "TEXT")
    private String precoHash;

    /** Raw — pode ser código de vaga (ex.: "VR25"), quantidade ou descrição; significado varia por documento (§6 do spec de importação), nunca reinterpretado automaticamente. */
    private String garagem;

    private Long ato;
    private Long subsidioCohapar;
    private Long financiamento;
    private Long valorAvaliacao;

    @Column(columnDefinition = "TEXT")
    private String observacoes;

    /** Documento (upload) de onde esta unidade foi extraída pela última vez. */
    private Long documentoOrigemId;

    /** Linha na tabela de origem (1-based) — rastreabilidade (§12 do spec). */
    private Integer linhaOrigem;

    /** confirmado | revisao — unidades com campo de baixa confiança ou conflito entram como 'revisao'. */
    @Builder.Default
    private String statusValidacao = "confirmado";

    private Instant ultimaSincronizacao;

    @CreationTimestamp
    private Instant dataCadastro;

    @UpdateTimestamp
    private Instant dataAtualizacao;
}
