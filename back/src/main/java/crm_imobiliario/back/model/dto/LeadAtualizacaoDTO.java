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
    private String historico;
    private String status;
    private Long valorInteresse;
    private Long empreendimentoId;
    // true = remover o empreendimento já vinculado; ausente/false = não mexer no vínculo atual.
    // Necessário porque este DTO também é usado em atualizações parciais (ex.: só trocar o status),
    // que não devem apagar o empreendimento só por não terem enviado o campo.
    private Boolean limparEmpreendimento;
    private String observacao;
    private String motivoDescarte;
}

