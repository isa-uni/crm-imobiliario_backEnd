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
    private String mesReferencia; // yyyy-MM
    private KpiDTO kpis;
    private List<PipelineEtapaDTO> pipeline;
    private List<RankingCorretorDTO> rankingCorretores;
    private MetaEquipeDTO metas;
    private TempoMedioDTO tempoMedio;
    private List<OrigemDTO> origens;
    private List<HistoricoDTO> historico;
    private List<EmpreendimentoInteresseDTO> empreendimentosMaisProcurados;
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
        private long quantidade;          // clientes que chegaram até esta etapa ou além (mesma regra do Relógio de Vendas)
        private double percentual;        // em relação à primeira etapa (Leads)
        private double valorPotencial;    // valor dos clientes em andamento parados nesta etapa; em Fechado, o valor vendido no mês
        private long emAndamento;
        private long contratosNoMes;
        private long descartadosNoMes;
        private long descartesNestaEtapa; // descartes do mês em que o cliente estava nesta etapa
        private List<RelogioVendasDTO.MesCadastro> porMesCadastro;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class RankingCorretorDTO {
        private Long corretorId;
        private String nome;
        private long leads;
        private long contatos; // tramitacoes count
        private long propostas; // clientes que chegaram a Oportunidade ou além
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
        private Integer metaContratos; // meta efetiva: a própria quando definida, senão a do gestor
        private Integer metaGestor; // meta atribuída pelo gestor, se houver
        private Integer metaPropria; // meta definida pelo próprio corretor, se houver
        private long realizadoContratos;
        private double percentualContratos;
        private String status; // atingida, proxima, abaixo, sem_meta
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class TempoMedioDTO {
        private double mediaGeralDias;
        private List<TempoCorretorDTO> porCorretor;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class TempoCorretorDTO {
        private Long corretorId;
        private String nome;
        private double mediaDias;
        private long totalNegocios;
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
    public static class EmpreendimentoInteresseDTO {
        private Long empreendimentoId;
        private String nome;
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
