package crm_imobiliario.back.model.dto.empreendimento;

import java.util.List;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EmpreendimentoConfirmacaoDTO {
    private Long extracaoId;
    private Long empreendimentoExistenteId; // para atualizar existente

    @NotBlank
    private String nome;
    private String codigoExterno;
    private String status;
    private String descricaoCurta;
    private String descricaoCompleta;
    private String incorporadora;
    private String construtora;
    private String endereco;
    private String numero;
    private String complemento;
    private String bairro;
    private String regiao;
    private String cidade;
    private String uf;
    private String cep;
    private Double lat;
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
    private List<UnidadeDTO> unidades;

    private Long usuarioId; // preenchido no backend via auth
}
