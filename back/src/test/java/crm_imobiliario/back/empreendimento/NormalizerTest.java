package crm_imobiliario.back.empreendimento;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import crm_imobiliario.back.model.service.empreendimento.Normalizer;

public class NormalizerTest {

    private final Normalizer n = new Normalizer();

    @Test
    void normalizaRemoveAcentoESufixo() {
        assertEquals("london plaza", n.normalizar("RESIDENCIAL LONDON PLAZA"));
        assertEquals("london plaza - torre a", n.normalizar("London Plaza - Torre A"));
        assertEquals("cordoba", n.normalizar("CÓRDOBA RESIDENCIAL"));
    }

    @Test
    void slugify() {
        assertEquals("london-plaza", n.slugify("Residencial London Plaza"));
        assertEquals("solare-essenza", n.slugify("Solare Essenza"));
        assertEquals("cordoba", n.slugify("CÓRDOBA RESIDENCIAL"));
    }

    @Test
    void casaTolerante() {
        assertTrue(n.casa("LONDON PLAZA", "london plaza"));
        assertTrue(n.casa("RESIDENCIAL LONDON PLAZA", "London Plaza"));
        assertTrue(n.casa("London Plaza - Torre A", "London Plaza"));
        assertFalse(n.casa("London Plaza", "London Life"));
    }
}
