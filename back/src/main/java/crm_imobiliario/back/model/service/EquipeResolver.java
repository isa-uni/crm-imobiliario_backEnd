package crm_imobiliario.back.model.service;

import crm_imobiliario.back.model.entity.Equipe;
import crm_imobiliario.back.model.entity.Usuario;
import crm_imobiliario.back.model.repository.EquipeRepository;

/**
 * Regra única de "qual é a equipe deste usuário", compartilhada por LeadsService e
 * LeadAtribuicaoService (antes duplicada nos dois). Ordem: equipe direta do usuário → equipe do
 * gestor dele → equipe em que o próprio usuário é gestor.
 */
public final class EquipeResolver {

    private EquipeResolver() {}

    public static Equipe resolver(Usuario u, EquipeRepository equipeRepository) {
        if (u == null) return null;
        if (u.getEquipe() != null) return u.getEquipe();
        if (u.getGestor() != null) {
            if (u.getGestor().getEquipe() != null) return u.getGestor().getEquipe();
            return equipeRepository.findByGestorId(u.getGestor().getId()).orElse(null);
        }
        return equipeRepository.findByGestorId(u.getId()).orElse(null);
    }

    /** Nome do papel do usuário ("" se não tiver). */
    public static String papel(Usuario u) {
        return u != null && u.getPapel() != null ? u.getPapel().getPapel() : "";
    }
}
