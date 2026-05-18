package crm_imobiliario.back.model.dto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter
public class ImovelAtualizacaoDTO {
    private String titulo;
    private String status;
    private String endereco;
    private String bairro;
    private String cidade;
    private Long valorVenda;
    private Long area;
    private Long quartos;
    private Long banheiros;
    private Long vagas;
    private String descricao;
}
