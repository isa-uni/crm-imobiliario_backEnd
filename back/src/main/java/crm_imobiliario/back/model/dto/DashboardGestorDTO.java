package crm_imobiliario.back.model.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardGestorDTO {
    private KpiDTO kpis;
    private List<PipelineEtapaDTO> pipeline;
    private List<RankingCorretorDTO> rankingCorretores;
    private MetaEquipeDTO metas;
    private TempoMedioDTO tempoMedio;
    private List<OrigemDTO> origens;
    private List<HistoricoDTO> historico;
    private List<ImovelInteresseDTO> imoveisMaisProcurados;
    private List<AlertaDTO> alertas;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class KpiDTO {
        private long leadsRecebidos;
        private long negociosFechados;
        private double valorVendido;
        private double taxaConversao;
        private double ticketMedio;
        private double tempoMedioDias;
        private long leadsRecebidosAnterior;
        private double variacaoLeads;
        private long negociosAnterior;
        private double variacaoNegocios;
        private double valorAnterior;
        private double variacaoValor;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class PipelineEtapaDTO {
        private String status;
        private String label;
        private long quantidade;
        private double percentual;
        private double valorPotencial;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class RankingCorretorDTO {
        private Long corretorId;
        private String nome;
        private long leads;
        private long contatos; // tramitacoes count
        private long propostas; // status visita-realizada/pasta/aprovado
        private long negocios;
        private double conversao;
        private String statusAtencao; // "ok", "atencao", "critico"
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class MetaEquipeDTO {
        private Integer metaContratosTotal;
        private long realizadoContratos;
        private double percentualContratos;
        private long faltanteContratos;
        private List<MetaCorretorDTO> porCorretor;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class MetaCorretorDTO {
        private Long corretorId;
        private String nome;
        private Integer metaContratos;
        private long realizadoContratos;
        private double percentualContratos;
        private String status; // atingida, proxima, abaixo, sem_meta
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class TempoMedioDTO {
        private double mediaGeralDias;
        private List<TempoCorretorDTO> porCorretor;
        private List<TempoEvolucaoDTO> evolucao;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class TempoCorretorDTO {
        private Long corretorId;
        private String nome;
        private double mediaDias;
        private long totalNegocios;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class TempoEvolucaoDTO {
        private String mes; // yyyy-MM
        private double mediaDias;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class OrigemDTO {
        private String origem;
        private String label;
        private long quantidade;
        private double percentual;
        private long conversoes;
        private long negocios;
        private double taxaConversao;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class HistoricoDTO {
        private String periodo; // yyyy-MM-dd or yyyy-MM
        private long recebidos;
        private long contratos;
        private long descartes;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ImovelInteresseDTO {
        private Long imovelId;
        private String titulo;
        private long interessados;
        private long propostas;
        private long negocios;
        private double conversao;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class AlertaDTO {
        private String tipo; // baixa_conversao, sem_atividade, pipeline_parado, meta_distante
        private Long corretorId;
        private String corretorNome;
        private String mensagem;
        private String severidade; // info, warning, critical
    }
}
