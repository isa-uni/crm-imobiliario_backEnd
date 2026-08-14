package crm_imobiliario.back.model.entity;

import java.time.LocalDate;

import org.hibernate.annotations.CreationTimestamp;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity(name = "usuario")
public class Usuario {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    private String nome;
    @Column(unique = true)
    private String matricula;
    @Column(unique = true)
    private String email;
    private String senha;
    @Column(unique = true)
    private String cpf;
    private String genero;
    private String telefone;
    private LocalDate dataNascimento;
    @CreationTimestamp
    private LocalDate dataMatricula;
    private boolean ativo = true;
    private boolean trocarSenha = true;
    @PrePersist
    protected void onCreate() {
        this.dataMatricula = LocalDate.now();
    }
    @ManyToOne
    @JoinColumn(name = "papel_id")
    private Papel papel;
}
