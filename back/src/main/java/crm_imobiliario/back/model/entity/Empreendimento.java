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
@Table(name = "empreendimento", indexes = {
    @Index(name = "idx_emp_slug", columnList = "slug", unique = true),
    @Index(name = "idx_emp_disponiveis", columnList = "disponiveis"),
    @Index(name = "idx_emp_cidade", columnList = "cidade")
})
public class Empreendimento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ID numérico estável do CV CRM (ex: 29 = LONDON PLAZA). Único quando presente. */
    @Column(unique = true)
    private String codigoCrm;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false, unique = true)
    private String slug;

    private String cidade;
    private String uf;
    private String regiao;
    private String bairro;
    private String endereco;

    private Double lat;
    private Double lng;
    private Integer zoom;

    /** % andamento da obra (ex: 77.55) — .preload .barra width% */
    private Double andamento;

    private Integer total;
    private Integer disponiveis;
    private Integer reservadas;
    private Integer vendidas;
    private Integer emProcesso;

    private Double metragemMin;
    private Double metragemMax;

    private Long precoMin;
    private Long precoMax;

    /** JSON array string: ["2Q","2Q - Garden"] */
    @Column(columnDefinition = "TEXT")
    private String tiposJson;

    /** JSON array int: [2] */
    @Column(columnDefinition = "TEXT")
    private String quartosJson;

    private String status; // em_obras, pronto, etc

    private String tabelaReferencia; // agosto/2026
    private String tabelaValidade;   // 2026-08-31
    private String tabelaHash;       // SHA256 do PDF

    @Column(columnDefinition = "TEXT")
    private String imagemUrl; // CDN — nunca base64

    @Column(columnDefinition = "TEXT")
    private String descricaoResumo;

    @Column(columnDefinition = "TEXT")
    private String condicoesComerciais;

    private String previsaoEntrega;
    private String entregaContratual;
    private Integer parcelamentoMax;

    private Boolean enriquecido = false;
    private Boolean ativo = true;

    @Column(columnDefinition = "TEXT")
    private String unmatchedReason;

    private Instant ultimaSincronizacao;

    @CreationTimestamp
    private Instant dataCadastro;

    @UpdateTimestamp
    private Instant dataAtualizacao;
}
