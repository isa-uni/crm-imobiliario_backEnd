package crm_imobiliario.back.model.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
@Table(name = "sincronizacao")
public class Sincronizacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Instant inicio;
    private Instant fim;

    /** total de empreendimentos no catálogo */
    private Integer total;

    private Integer sucesso;
    private Integer falha;

    /** disponiveis encontrados */
    private Integer disponiveis;

    @Column(columnDefinition = "TEXT")
    private String erro;

    private String tipo; // COMPLETA, POR_ID, CAPA_RAPIDA

    private String codigoCrmAlvo; // null = todos
}
