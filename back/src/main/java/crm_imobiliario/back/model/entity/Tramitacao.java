package crm_imobiliario.back.model.entity;


import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;


import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity(name = "tramitacao_status")
@Table(name = "tramitacao_status", indexes = {
        @Index(name = "idx_tramitacao_lead_data", columnList = "lead_id, dataMovimentacao")
})
public class Tramitacao {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    @ManyToOne
    @JoinColumn(name = "lead_id")
    private Lead lead;
    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;
    private String status_anterior;
    private String status_atual;
    @CreationTimestamp
    private LocalDateTime dataMovimentacao;
}