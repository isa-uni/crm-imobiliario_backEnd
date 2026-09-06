package crm_imobiliario.back.empreendimento;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.Test;

import crm_imobiliario.back.model.service.empreendimento.PdfPrecoParser;

public class PdfPrecoParserTest {

    private final PdfPrecoParser parser = new PdfPrecoParser();

    @Test
    void parseLinhaPadrao() {
        String texto = "Etapa Única BLOCO 01 B01.101 35,74 m² 2Q - Garden Disponível R$ 227.200,00 blablabla";
        Map<String, PdfPrecoParser.PrecoLinha> map = parser.parse(texto);
        assertEquals(1, map.size());
        PdfPrecoParser.PrecoLinha l = map.get("B01.101");
        assertNotNull(l);
        assertEquals(35.74, l.area(), 0.01);
        assertEquals("2Q - Garden", l.tipologia());
        assertEquals("Disponível", l.situacao());
        assertEquals(227200L, l.preco());
    }

    @Test
    void parseMultiplasLinhas() {
        String texto = """
                Etapa Única BLOCO 01 B01.102 39,50 m² 2Q Disponível R$ 242.500,00
                Etapa Única BLOCO 01 B01.103 39,50 m² 2Q - Garden Reservada R$ 245.000,00
                linha lixo sem match
                Etapa Única BLOCO 02 B02.201 35,74 m² 2Q Disponível R$ 230.000,00
                """;
        Map<String, PdfPrecoParser.PrecoLinha> map = parser.parse(texto);
        assertEquals(3, map.size());
        assertTrue(map.containsKey("B01.102"));
        assertTrue(map.containsKey("B02.201"));
    }

    @Test
    void quartosDeTipologia() {
        assertEquals(2, parser.quartosDeTipologia("2Q - Garden"));
        assertEquals(2, parser.quartosDeTipologia("2Q"));
        assertEquals(3, parser.quartosDeTipologia("3 Dorm Garden"));
        assertEquals(0, parser.quartosDeTipologia("Studio"));
        assertNull(parser.quartosDeTipologia(null));
    }

    @Test
    void precosDisponiveisFiltraSoDisponivel() {
        String texto = """
                Etapa Única BLOCO 01 B01.101 35,74 m² 2Q Disponível R$ 200.000,00
                Etapa Única BLOCO 01 B01.102 35,74 m² 2Q Vendida R$ 210.000,00
                """;
        Map<String, PdfPrecoParser.PrecoLinha> map = parser.parse(texto);
        assertEquals(1, parser.precosDisponiveis(map).size());
        assertEquals(200000L, parser.precosDisponiveis(map).get(0));
    }
}
