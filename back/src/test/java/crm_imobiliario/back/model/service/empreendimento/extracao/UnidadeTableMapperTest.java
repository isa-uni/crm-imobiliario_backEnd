package crm_imobiliario.back.model.service.empreendimento.extracao;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

class UnidadeTableMapperTest {

    private final UnidadeTableMapper mapper = new UnidadeTableMapper(new UnidadeHeaderNormalizer());

    private TabelaBruta.Linha linha(String... celulas) {
        TabelaBruta.Linha l = new TabelaBruta.Linha();
        l.celulas = List.of(celulas);
        l.linhaOrigem = 1;
        return l;
    }

    @Test
    void mapeiaLinhaCompletaComTodosOsCamposReconhecidos() {
        TabelaBruta tabela = new TabelaBruta();
        tabela.cabecalhos = List.of("BLOCO", "UNIDADE", "ÁREA PRIVATIVA", "TIPOLOGIA", "SITUAÇÃO", "VALOR TOTAL");
        tabela.linhas.add(linha("BLOCO 11", "B11.102", "35,81 m²", "2Q - Garden", "Disponível", "R$ 239.200,00"));

        UnidadeTableMapper.Resultado r = mapper.mapear(tabela, 1L, "doc.pdf");
        assertEquals(1, r.unidades.size());
        UnidadeExtraida u = r.unidades.get(0);
        assertEquals("B11.102", u.chaveIdentidade());
        assertEquals("35.81", u.valor(CampoUnidade.AREA_PRIVATIVA));
        assertEquals("2Q - Garden", u.valor(CampoUnidade.TIPOLOGIA));
        assertEquals("disponivel", u.valor(CampoUnidade.SITUACAO));
        assertEquals("239200.00", u.valor(CampoUnidade.VALOR_TOTAL));
        assertTrue(r.alertas.isEmpty());
    }

    @Test
    void colunaAusenteNaoInventaValor() {
        // London Plaza real não tem coluna "Área Comum" — o campo deve ficar simplesmente ausente
        TabelaBruta tabela = new TabelaBruta();
        tabela.cabecalhos = List.of("BLOCO", "UNIDADE", "SITUAÇÃO");
        tabela.linhas.add(linha("BLOCO 02", "B02.204", "Disponível"));

        UnidadeExtraida u = mapper.mapear(tabela, 1L, "doc.pdf").unidades.get(0);
        assertNull(u.valor(CampoUnidade.AREA_COMUM));
        assertNull(u.valor(CampoUnidade.GARAGEM));
    }

    @Test
    void placeholderVazioNaoViraValorLiteral() {
        // "- -" no documento real significa ausência, não deve virar o texto "- -" no campo
        TabelaBruta tabela = new TabelaBruta();
        tabela.cabecalhos = List.of("BLOCO", "UNIDADE", "OUTRAS ÁREAS");
        tabela.linhas.add(linha("BLOCO 02", "B02.204", "- -"));

        UnidadeExtraida u = mapper.mapear(tabela, 1L, "doc.pdf").unidades.get(0);
        assertNull(u.valor(CampoUnidade.OUTRAS_AREAS));
    }

    @Test
    void colunaDesconhecidaGeraAlertaParaMapeamentoManual() {
        TabelaBruta tabela = new TabelaBruta();
        tabela.cabecalhos = List.of("BLOCO", "UNIDADE", "Observações Especiais");
        tabela.linhas.add(linha("BLOCO 02", "B02.204", "texto qualquer"));

        UnidadeTableMapper.Resultado r = mapper.mapear(tabela, 1L, "doc.pdf");
        assertTrue(r.alertas.stream().anyMatch(a -> a.contains("Observações Especiais")));
    }

    @Test
    void garagemComoCodigoDeVagaNaoEhInterpretadaComoQuantidade() {
        // Mendoza real: GARAGEM = código da vaga ("VR25"), vendida separadamente — nunca reinterpretar
        TabelaBruta tabela = new TabelaBruta();
        tabela.cabecalhos = List.of("BLOCO", "UNIDADE", "GARAGEM");
        tabela.linhas.add(linha("TORRE 02", "T02.202", "VR25"));

        UnidadeExtraida u = mapper.mapear(tabela, 1L, "doc.pdf").unidades.get(0);
        assertEquals("VR25", u.valor(CampoUnidade.GARAGEM));
    }

    @Test
    void semColunaDeIdentificacaoTabelaEIgnorada() {
        TabelaBruta tabela = new TabelaBruta();
        tabela.cabecalhos = List.of("SITUAÇÃO", "VALOR TOTAL");
        tabela.linhas.add(linha("Disponível", "R$ 200.000,00"));

        UnidadeTableMapper.Resultado r = mapper.mapear(tabela, 1L, "doc.pdf");
        assertTrue(r.unidades.isEmpty());
        assertFalse(r.alertas.isEmpty());
    }
}
