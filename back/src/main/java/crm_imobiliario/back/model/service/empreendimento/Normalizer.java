package crm_imobiliario.back.model.service.empreendimento;

import java.text.Normalizer.Form;
import java.util.Locale;

import org.springframework.stereotype.Component;

/**
 * Normalização de nomes/slugs — §6 do spec.
 * Prioridade: ID > código > slug > nome normalizado fallback.
 */
@Component
public class Normalizer {

    public String normalizar(String nome) {
        if (nome == null) return "";
        String n = java.text.Normalizer.normalize(nome.trim().toLowerCase(Locale.ROOT), Form.NFD);
        n = n.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        n = n.replaceAll("[^a-z0-9\\s-]", " ");
        n = n.replaceAll("\\s+", " ").trim();
        // remove sufixo "residencial" para matching mais tolerante
        n = n.replaceAll("\\bresidencial\\b", "").trim();
        n = n.replaceAll("\\s+", " ").trim();
        return n;
    }

    public String slugify(String nome) {
        String n = normalizar(nome);
        n = n.replaceAll("\\s+", "-");
        n = n.replaceAll("-+", "-");
        return n.replaceAll("^-|-$", "");
    }

    public boolean casa(String a, String b) {
        if (a == null || b == null) return false;
        String na = normalizar(a);
        String nb = normalizar(b);
        if (na.equals(nb)) return true;
        // tolera "london plaza" vs "london plaza torre a" (prefix match)
        return na.startsWith(nb) || nb.startsWith(na);
    }
}
