package crm_imobiliario.back.model.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
@Entity(name = "notificacao")
public class Notificacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false)
    private String tipo; // CLIENTE_REMOVIDO, CLIENTE_RECEBIDO

    @Column(nullable = false)
    private String mensagem;

    @Column(name = "lead_id")
    private Long leadId;

    @Column(name = "lead_nome")
    private String leadNome;

    @Column(nullable = false)
    private Boolean lida = false;

    private LocalDateTime dataCriacao = LocalDateTime.now();
}
