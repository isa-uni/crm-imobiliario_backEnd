package crm_imobiliario.back.model.dto;

import java.time.LocalDateTime;

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
public class EquipeDTO {
    private Long id;
    private String nome;
    private String descricao;
    private Long gestorId;
    private String gestorNome;
    private Boolean ativo;
    private LocalDateTime dataCriacao;

    public static EquipeDTO from(crm_imobiliario.back.model.entity.Equipe e) {
        return EquipeDTO.builder()
                .id(e.getId())
                .nome(e.getNome())
                .descricao(e.getDescricao())
                .gestorId(e.getGestor() != null ? e.getGestor().getId() : null)
                .gestorNome(e.getGestor() != null ? e.getGestor().getNome() : null)
                .ativo(e.getAtivo())
                .dataCriacao(e.getDataCriacao())
                .build();
    }
}
