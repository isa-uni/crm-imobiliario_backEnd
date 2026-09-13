package crm_imobiliario.back.model.service.empreendimento.extracao;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class UnidadeHeaderNormalizerTest {

    private final UnidadeHeaderNormalizer normalizer = new UnidadeHeaderNormalizer();

    @Test
    void reconheceSinonimosComAcentosEEspacos() {
        assertEquals(CampoUnidade.AREA_PRIVATIVA, normalizer.reconhecer("Área Privativa").get().campo);
        assertEquals(CampoUnidade.AREA_PRIVATIVA, normalizer.reconhecer("  ÁREA   PRIVATIVA  ").get().campo);
        assertEquals(CampoUnidade.AREA_PRIVATIVA, normalizer.reconhecer("area privativa").get().campo);
    }

    @Test
    void reconheceBlocoETorreComoCamposDistintos() {
        assertEquals(CampoUnidade.BLOCO, normalizer.reconhecer("Bloco").get().campo);
        assertEquals(CampoUnidade.TORRE, normalizer.reconhecer("Torre").get().campo);
    }

    @Test
    void extraiMultiplicadorEDataDoCabecalho() {
        var r = normalizer.reconhecer("ATO (1x)\n10/10/2026");
        assertTrue(r.isPresent());
        assertEquals(CampoUnidade.ATO, r.get().campo);
        assertEquals(1, r.get().multiplicador);
        assertEquals("10/10/2026", r.get().dataReferencia);
    }

    @Test
    void naoReconheceCabecalhoDesconhecido() {
        assertTrue(normalizer.reconhecer("Observações Especiais").isEmpty());
        assertTrue(normalizer.reconhecer(null).isEmpty());
        assertTrue(normalizer.reconhecer("").isEmpty());
    }

    @Test
    void colunasVariaveisEntreDocumentosReaisSaoReconhecidas() {
        // Marbella tem "Área Comum"; London Plaza não tem — ambas devem reconhecer normalmente
        assertEquals(CampoUnidade.AREA_COMUM, normalizer.reconhecer("ÁREA COMUM").get().campo);
        assertEquals(CampoUnidade.SUBSIDIO_COHAPAR, normalizer.reconhecer("SUBSÍDIO COHAPAR (1x)\n30/04/2028").get().campo);
    }
}
