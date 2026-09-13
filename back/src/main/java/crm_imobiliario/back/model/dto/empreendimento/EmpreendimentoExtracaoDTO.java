package crm_imobiliario.back.model.dto.empreendimento;

import java.time.Instant;
import java.util.List;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EmpreendimentoExtracaoDTO {
    private Long id;
    private Long empreendimentoId;
    private String status;
    private String modeloIa;
    private Instant dataProcessamento;
    private Instant dataConclusao;
    private String erro;
    private Object resultado;
    private List<Long> documentoIds;
    private List<FonteDTO> fontes;
    private List<ConflitoDTO> conflitos;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class FonteDTO {
        private Long id;
        private Long documentoId;
        private String documentoNome;
        private String campo;
        private String valorExtraido;
        private Integer pagina;
        private String trecho;
        private Integer confianca;
    }
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ConflitoDTO {
        private String campo;
        private List<FonteDTO> valores;
    }
}
