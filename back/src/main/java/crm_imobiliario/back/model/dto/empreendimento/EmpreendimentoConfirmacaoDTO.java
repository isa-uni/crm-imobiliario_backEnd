package crm_imobiliario.back.model.dto.empreendimento;

import java.util.List;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EmpreendimentoConfirmacaoDTO {
    private Long extracaoId;
    private Long empreendimentoExistenteId; // para atualizar existente

    @NotBlank(message = "Informe o nome do empreendimento.")
    @jakarta.validation.constraints.Size(max = 255, message = "O nome do empreendimento deve ter no máximo 255 caracteres.")
    private String nome;
    @jakarta.validation.constraints.Size(max = 100, message = "O código externo deve ter no máximo 100 caracteres.")
    private String codigoExterno;
    private String status;
    private String descricaoCurta;
    private String descricaoCompleta;
    private String incorporadora;
    private String construtora;
    private String endereco;
    @jakarta.validation.constraints.Size(max = 20, message = "O número do endereço deve ter no máximo 20 caracteres.")
    private String numero;
    private String complemento;
    private String bairro;
    private String regiao;
    private String cidade;
    // vazio é aceito (o documento pode não informar); preenchido, precisa ter o formato correto
    @jakarta.validation.constraints.Pattern(regexp = "^$|^[A-Za-z]{2}$", message = "Informe a UF com 2 letras, por exemplo PR.")
    private String uf;
    @jakarta.validation.constraints.Pattern(regexp = "^$|^[0-9]{5}-?[0-9]{3}$", message = "Informe o CEP com 8 dígitos, no formato 00000-000.")
    private String cep;
    @jakarta.validation.constraints.DecimalMin(value = "-90", message = "A latitude deve estar entre -90 e 90.")
    @jakarta.validation.constraints.DecimalMax(value = "90", message = "A latitude deve estar entre -90 e 90.")
    private Double lat;
    @jakarta.validation.constraints.DecimalMin(value = "-180", message = "A longitude deve estar entre -180 e 180.")
    @jakarta.validation.constraints.DecimalMax(value = "180", message = "A longitude deve estar entre -180 e 180.")
    private Double lng;
    private String imagemUrl;

    private EmpreendimentoDetalheDTO.CaracteristicaDTO caracteristica;
    private List<EmpreendimentoDetalheDTO.PrecoDTO> precos;
    private EmpreendimentoDetalheDTO.CondicaoDTO condicao;
    private List<EmpreendimentoDetalheDTO.PlantaDTO> plantas;
    private List<EmpreendimentoDetalheDTO.AreaComumDTO> areasComuns;
    private List<EmpreendimentoDetalheDTO.DiferencialDTO> diferenciais;
    private List<EmpreendimentoDetalheDTO.PontoReferenciaDTO> pontosReferencia;
    private List<EmpreendimentoDetalheDTO.ImagemDTO> imagens;
    private List<@jakarta.validation.Valid UnidadeDTO> unidades;

    private Long usuarioId; // preenchido no backend via auth
}
