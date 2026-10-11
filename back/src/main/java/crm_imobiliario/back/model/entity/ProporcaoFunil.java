package crm_imobiliario.back.model.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Quantos clientes em cada etapa do funil equivalem a 1 contrato da meta (Relógio de Vendas).
 * Configuração global: uma única linha (id = 1), ajustável pelo admin.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity(name = "proporcao_funil")
@Table(name = "proporcao_funil")
public class ProporcaoFunil {

    public static final long ID_UNICO = 1L;

    @Id
    private Long id;

    @Column(nullable = false)
    private Integer leads;
    @Column(nullable = false)
    private Integer oportunidades;
    @Column(nullable = false)
    private Integer visitasAgendadas;
    @Column(nullable = false)
    private Integer visitasRealizadas;
    @Column(nullable = false)
    private Integer pastas;
    @Column(nullable = false)
    private Integer aprovados;

    private LocalDateTime dataAtualizacao;

    @ManyToOne
    @JoinColumn(name = "atualizado_por_id")
    private Usuario atualizadoPor;

    /** Padrão usado se a linha não existir (mesmos valores da migration V7). */
    public static ProporcaoFunil padrao() {
        return new ProporcaoFunil(ID_UNICO, 33, 20, 10, 8, 4, 2, null, null);
    }
}
