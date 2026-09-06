package crm_imobiliario.back.model.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class MetaDTO {
    private Long id;
    private Long usuarioId;
    private String usuarioNome;
    private LocalDate mesReferencia;
    private Integer metaContratos;
    private LocalDateTime dataCriacao;
    private LocalDateTime dataAtualizacao;

    public MetaDTO(Long id, Long usuarioId, String usuarioNome, LocalDate mesReferencia, Integer metaContratos) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.usuarioNome = usuarioNome;
        this.mesReferencia = mesReferencia;
        this.metaContratos = metaContratos;
    }

    public static MetaDTO from(crm_imobiliario.back.model.entity.Meta m) {
        return new MetaDTO(
                m.getId(),
                m.getUsuario().getId(),
                m.getUsuario().getNome(),
                m.getMesReferencia(),
                m.getMetaContratos(),
                m.getDataCriacao(),
                m.getDataAtualizacao()
        );
    }
}
