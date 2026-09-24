package crm_imobiliario.back.model.service;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import crm_imobiliario.back.model.dto.LeadListaDTO;

/**
 * Regressão: a agregação do funil precisa reproduzir exatamente o algoritmo cumulativo de
 * components/FunilDashboard.tsx — um lead em "pasta" conta em todas as etapas até "pasta"
 * inclusive, mas não em "aprovado"/"contrato".
 */
class LeadExportServiceTest {

    private static final List<String> FUNIL_ORDEM = LeadExportService.FUNIL_ORDEM;

    private LeadListaDTO leadComStatus(String status) {
        return leadCompleto(status, null, null);
    }

    private LeadListaDTO leadCompleto(String status, String origem, String historico) {
        return new LeadListaDTO(1L, "Fulano", "11999999999", "fulano@teste.com", origem, historico, status,
                100000L, null, null, true, LocalDateTime.now(), LocalDateTime.now(),
                null, null, null, null, null, null, null, null);
    }

    @Test
    void leadEmPastaContaEmTodasAsEtapasAteContrato_excluidoAcima() {
        List<LeadListaDTO> leads = List.of(leadComStatus("pasta"));
        Map<String, Long> contagens = LeadExportService.contagemFunilCumulativa(leads, FUNIL_ORDEM);

        assertEquals(1L, contagens.get("lead"));
        assertEquals(1L, contagens.get("oportunidade"));
        assertEquals(1L, contagens.get("visita-agendada"));
        assertEquals(1L, contagens.get("visita-realizada"));
        assertEquals(1L, contagens.get("pasta"));
        assertEquals(0L, contagens.get("aprovado"));
        assertEquals(0L, contagens.get("contrato"));
    }

    @Test
    void descarteContaSoNoTopoDoFunilENaoNasDemaisEtapas() {
        List<LeadListaDTO> leads = List.of(leadComStatus("descarte"));
        Map<String, Long> contagens = LeadExportService.contagemFunilCumulativa(leads, FUNIL_ORDEM);

        assertEquals(1L, contagens.get("lead"), "topo do funil conta todo mundo, até descarte");
        assertEquals(0L, contagens.get("oportunidade"), "descarte não avançou nenhuma etapa do funil");
        assertEquals(0L, contagens.get("contrato"));
    }

    @Test
    void leadEmContratoContaEmTodasAsEtapas() {
        List<LeadListaDTO> leads = List.of(leadComStatus("contrato"));
        Map<String, Long> contagens = LeadExportService.contagemFunilCumulativa(leads, FUNIL_ORDEM);

        for (String etapa : FUNIL_ORDEM) {
            assertEquals(1L, contagens.get(etapa), "contrato é a última etapa, deve contar em todas");
        }
    }

    @Test
    void listaVaziaZeraTodasAsEtapas() {
        Map<String, Long> contagens = LeadExportService.contagemFunilCumulativa(new ArrayList<>(), FUNIL_ORDEM);
        for (String etapa : FUNIL_ORDEM) {
            assertEquals(0L, contagens.get(etapa));
        }
    }

    @Test
    void contagemPorCampoAgrupaEOrdenaDoMaiorParaOMenor() {
        List<LeadListaDTO> leads = List.of(
                leadCompleto("lead", "digital_lead", "novo"),
                leadCompleto("lead", "digital_lead", "reaquecido"),
                leadCompleto("lead", "espontaneo", "novo"));

        Map<String, Long> porOrigem = LeadExportService.contagemPorCampo(leads, LeadListaDTO::origem, "Sem origem");

        assertEquals(2, porOrigem.size());
        assertEquals(2L, porOrigem.get("digital_lead"));
        assertEquals(1L, porOrigem.get("espontaneo"));
        // maior contagem deve vir primeiro (mesma ordenação do LeadOrigemChart.tsx)
        assertEquals("digital_lead", porOrigem.keySet().iterator().next());
    }

    @Test
    void contagemPorCampoUsaValorPadraoQuandoCampoEhNuloOuVazio() {
        List<LeadListaDTO> leads = List.of(leadCompleto("lead", null, ""));
        Map<String, Long> porOrigem = LeadExportService.contagemPorCampo(leads, LeadListaDTO::origem, "Sem origem");
        assertEquals(1L, porOrigem.get("Sem origem"));
    }
}
