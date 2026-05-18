package crm_imobiliario.back.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter
public class LeadAtualizacaoDTO {

    private String nome;
    private String email;
    private String telefone;
    private String origem;
    private String status;
    private Long valorInteresse;
    private Long imovelId;
    private String observacao;
    private String motivoDescarte;
}

