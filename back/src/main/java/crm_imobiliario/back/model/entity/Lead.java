package crm_imobiliario.back.model.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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
@Entity(name = "lead")
public class Lead {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private String nome;
    @Column(unique = true)
    private String email;
    private String telefone;
    private String origem;
    private String historico;
    private String status;
    private Long valorInteresse;
    private String observacao;
    private String motivoDescarte;
    private Boolean ativo = true;
    @CreationTimestamp
    private LocalDateTime dataCriacao;
    @UpdateTimestamp
    private LocalDateTime dataAtualizacao;
    private String corretor_responsavel;

    @ManyToOne
    @JoinColumn(name = "corretor_id")
    private Usuario corretor;

    @ManyToOne
    @JoinColumn(name = "equipe_id")
    private crm_imobiliario.back.model.entity.Equipe equipe;

    @Column(nullable = false)
    @Builder.Default
    private String statusAtribuicao = "ATRIBUIDO";

    @ManyToOne
    @JoinColumn(name = "imovel_id")
    private Imovel imovel;

}
