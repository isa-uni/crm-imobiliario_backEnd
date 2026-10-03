package crm_imobiliario.back.model.service;

import crm_imobiliario.back.util.RecursoNaoEncontradoException;
import crm_imobiliario.back.util.RegraNegocioException;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import crm_imobiliario.back.model.entity.Equipe;
import crm_imobiliario.back.model.entity.Lead;
import crm_imobiliario.back.model.entity.Usuario;
import crm_imobiliario.back.model.repository.EquipeRepository;
import crm_imobiliario.back.model.repository.LeadRepository;
import crm_imobiliario.back.model.repository.UsuarioRepository;

@Service
public class EquipeService {

    @Autowired
    private EquipeRepository equipeRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private LeadRepository leadRepository;

    public List<Equipe> listar() {
        return equipeRepository.findAll();
    }

    public Equipe buscarPorId(Long id) {
        return equipeRepository.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("A equipe solicitada não foi encontrada. Ela pode ter sido removida."));
    }

    @Transactional
    public Equipe criar(String nome, String descricao, Long gestorId) {
        if (equipeRepository.findByNome(nome).isPresent()) throw new RegraNegocioException("Já existe uma equipe chamada \"" + nome + "\". Escolha outro nome.", "nome", "DUPLICATE_EQUIPE");
        Equipe e = Equipe.builder().nome(nome).descricao(descricao).ativo(true).build();
        if (gestorId != null) {
            Usuario gestor = usuarioRepository.findById(gestorId).orElseThrow(() -> new RecursoNaoEncontradoException("O gestor selecionado não foi encontrado. Atualize a página e escolha outro."));
            validarGestor(gestor);
            e.setGestor(gestor);
        }
        Equipe salva = equipeRepository.save(e);
        if (gestorId != null) {
            Usuario gestor = usuarioRepository.findById(gestorId).orElseThrow(() -> new RecursoNaoEncontradoException("O gestor selecionado não foi encontrado. Atualize a página e escolha outro."));
            // garante consistência do próprio gestor
            if (gestor.getEquipe() == null || !gestor.getEquipe().getId().equals(salva.getId())) {
                gestor.setEquipe(salva);
                usuarioRepository.save(gestor);
            }
            sincronizarLiderados(salva, gestorId);
        }
        return salva;
    }

    @Transactional
    public Equipe atribuirGestor(Long equipeId, Long novoGestorId, Usuario solicitante) {
        Equipe equipe = buscarPorId(equipeId);
        String papel = solicitante.getPapel() != null ? solicitante.getPapel().getPapel() : "";
        if (!"admin".equals(papel)) throw new AccessDeniedException("Somente administradores podem alterar o gestor de uma equipe.");
        if (novoGestorId == null) {
            equipe.setGestor(null);
            Equipe salva = equipeRepository.save(equipe);
            return salva;
        } else {
            Usuario novoGestor = usuarioRepository.findById(novoGestorId).orElseThrow(() -> new RecursoNaoEncontradoException("O gestor selecionado não foi encontrado. Atualize a página e escolha outro."));
            validarGestor(novoGestor);
            if (!novoGestor.isAtivo()) throw new RegraNegocioException("Não é possível definir " + novoGestor.getNome() + " como gestor porque o usuário está inativo.", "gestorId", "GESTOR_INACTIVE");
            equipe.setGestor(novoGestor);
            if (novoGestor.getEquipe() == null || !novoGestor.getEquipe().getId().equals(equipe.getId())) {
                novoGestor.setEquipe(equipe);
                usuarioRepository.save(novoGestor);
            }
            Equipe salva = equipeRepository.save(equipe);
            sincronizarLiderados(salva, novoGestorId);
            return salva;
        }
    }

    @Transactional
    public void desvincularGestor(Long gestorId) {
        List<Equipe> equipes = equipeRepository.findAllByGestorId(gestorId);
        for (Equipe e : equipes) {
            e.setGestor(null);
            equipeRepository.save(e);
        }
    }

    @Transactional
    public int sincronizarEquipe(Long equipeId) {
        Equipe equipe = buscarPorId(equipeId);
        if (equipe.getGestor() == null) throw new RegraNegocioException("Não é possível sincronizar a equipe " + equipe.getNome() + " porque ela não tem gestor. Defina um gestor antes de sincronizar.", null, "TEAM_WITHOUT_MANAGER");
        return sincronizarLiderados(equipe, equipe.getGestor().getId());
    }

    private int sincronizarLiderados(Equipe equipe, Long gestorId) {
        List<Usuario> liderados = usuarioRepository.findByGestorId(gestorId);
        int count = 0;
        for (Usuario u : liderados) {
            if (!u.isAtivo()) continue;
            if (u.getEquipe() == null || !u.getEquipe().getId().equals(equipe.getId())) {
                u.setEquipe(equipe);
                usuarioRepository.save(u);
                count++;
                // também sincroniza leads ATRIBUIDO desse corretor que estão com equipe divergente ou nula
                try {
                    List<Lead> leads = leadRepository.findByCorretorId(u.getId());
                    for (Lead l : leads) {
                        if ("ATRIBUIDO".equals(l.getStatusAtribuicao()) && (l.getEquipe() == null || !l.getEquipe().getId().equals(equipe.getId()))) {
                            l.setEquipe(equipe);
                            leadRepository.save(l);
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
        return count;
    }

    private void validarGestor(Usuario u) {
        String papel = u.getPapel() != null ? u.getPapel().getPapel() : "";
        if (!"gestor".equals(papel) && !"admin".equals(papel)) throw new RegraNegocioException(u.getNome() + " não pode liderar uma equipe porque tem o papel \"" + papel + "\". Somente gestores e administradores podem ser gestores de equipe.", "gestorId", "NOT_A_MANAGER");
    }
}
