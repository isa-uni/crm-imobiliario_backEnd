package crm_imobiliario.back.model.service;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import crm_imobiliario.back.model.dto.PerfilDTO;
import crm_imobiliario.back.model.dto.UsuarioDTO;
import crm_imobiliario.back.model.dto.UsuarioResponse;
import crm_imobiliario.back.model.entity.Papel;
import crm_imobiliario.back.model.entity.Usuario;
import crm_imobiliario.back.model.repository.PapelRepository;
import crm_imobiliario.back.model.repository.UsuarioRepository;
import crm_imobiliario.back.util.RecursoNaoEncontradoException;
import crm_imobiliario.back.util.RegraNegocioException;

@Service
public class UsuarioService {

    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private PapelRepository papelRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired(required = false)
    private crm_imobiliario.back.model.service.LeadAtribuicaoService leadAtribuicaoService;
    @Autowired(required = false)
    private crm_imobiliario.back.model.service.EquipeService equipeService;
    @Autowired(required = false)
    private crm_imobiliario.back.model.repository.EquipeRepository equipeRepository;

    /** Usuário recém-criado + a senha temporária em texto puro, que só existe neste momento. */
    public record UsuarioCriado(Usuario usuario, String senhaTemporaria) {}

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String MAIUSCULAS = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String MINUSCULAS = "abcdefghijkmnpqrstuvwxyz";
    private static final String DIGITOS = "23456789";
    private static final String ESPECIAIS = "@#$%&*!?";

    /**
     * Senha temporária aleatória (12 caracteres, com maiúscula, minúscula, número e especial).
     * Antes era os 4 últimos dígitos do CPF — previsível e com só 10.000 combinações.
     */
    String gerarSenhaTemporaria() {
        String todos = MAIUSCULAS + MINUSCULAS + DIGITOS + ESPECIAIS;
        char[] senha = new char[12];
        senha[0] = MAIUSCULAS.charAt(RANDOM.nextInt(MAIUSCULAS.length()));
        senha[1] = MINUSCULAS.charAt(RANDOM.nextInt(MINUSCULAS.length()));
        senha[2] = DIGITOS.charAt(RANDOM.nextInt(DIGITOS.length()));
        senha[3] = ESPECIAIS.charAt(RANDOM.nextInt(ESPECIAIS.length()));
        for (int i = 4; i < senha.length; i++) senha[i] = todos.charAt(RANDOM.nextInt(todos.length()));
        for (int i = senha.length - 1; i > 0; i--) { // embaralha para as categorias não ficarem em posição fixa
            int j = RANDOM.nextInt(i + 1);
            char t = senha[i]; senha[i] = senha[j]; senha[j] = t;
        }
        return new String(senha);
    }

    /** Matrícula = ano + 4 últimos dígitos do CPF; em caso de colisão, acrescenta sufixo sequencial. */
    private String gerarMatricula(String cpfLimpo) {
        String base = LocalDate.now().getYear() + cpfLimpo.substring(cpfLimpo.length() - 4);
        String matricula = base;
        int sufixo = 1;
        while (usuarioRepository.existsByMatricula(matricula)) {
            matricula = base + "-" + sufixo++;
        }
        return matricula;
    }

    public UsuarioCriado cadastrarUsuario(UsuarioDTO dto) {

        Usuario usuario = new Usuario();

        String cpfLimpo = dto.getCpf().replaceAll("\\D", "");

        if (!validarCPF(cpfLimpo)) {
            throw new RegraNegocioException("O CPF informado não é válido. Confira os 11 dígitos.", "cpf", "INVALID_CPF");
        }

        if (usuarioRepository.existsByCpf(cpfLimpo)) {
            throw new RegraNegocioException("Já existe um usuário cadastrado com este CPF.", "cpf", "DUPLICATE_CPF");
        }

        if (usuarioRepository.existsByEmail(dto.getEmail())) {
            throw new RegraNegocioException("Já existe um usuário cadastrado com este e-mail.", "email", "DUPLICATE_EMAIL");
        }

        String matricula = gerarMatricula(cpfLimpo);
        String senhaTemporaria = gerarSenhaTemporaria();

        usuario.setEmail(dto.getEmail());
        usuario.setCpf(cpfLimpo);
        usuario.setNome(dto.getNome());
        usuario.setGenero(dto.getGenero());
        usuario.setTelefone(dto.getTelefone());
        usuario.setMatricula(matricula);
        usuario.setDataNascimento(dto.getDataNascimento());
        usuario.setAtivo(true);
        usuario.setTrocarSenha(true);

        Papel papel = papelRepository.findById(dto.getPapelId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("O papel selecionado não existe mais. Atualize a página e escolha outro papel."));

        usuario.setPapel(papel);
        if (dto.getGestorId() != null) {
            Usuario gestor = usuarioRepository.findById(dto.getGestorId())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("O gestor selecionado não foi encontrado. Atualize a página e escolha outro gestor."));
            usuario.setGestor(gestor);
            // resolve equipe via gestor.equipe ou equipe.gestor_id (fallback)
            crm_imobiliario.back.model.entity.Equipe equipeGestor = gestor.getEquipe();
            if (equipeGestor == null && equipeRepository != null) {
                equipeGestor = equipeRepository.findByGestorId(gestor.getId()).orElse(null);
            }
            if (equipeGestor != null) usuario.setEquipe(equipeGestor);
        }
        if (usuario.getEquipe() == null) {
            try {
                if (usuario.getGestor() != null) {
                    crm_imobiliario.back.model.entity.Equipe eg = usuario.getGestor().getEquipe();
                    if (eg == null && equipeRepository != null) eg = equipeRepository.findByGestorId(usuario.getGestor().getId()).orElse(null);
                    if (eg != null) usuario.setEquipe(eg);
                    else if (equipeRepository != null) equipeRepository.findByNome("Equipe Geral").ifPresent(usuario::setEquipe);
                } else if (equipeRepository != null) {
                    equipeRepository.findByNome("Equipe Geral").ifPresent(usuario::setEquipe);
                }
            } catch (Exception ignored) {}
        }
        usuario.setSenha(passwordEncoder.encode(senhaTemporaria));

        // duplicidade de e-mail/CPF/matrícula detectada pelo banco é traduzida pelo GlobalExceptionHandler,
        // que informa exatamente qual dado já está cadastrado
        return new UsuarioCriado(usuarioRepository.save(usuario), senhaTemporaria);
    }
    
    public List<UsuarioResponse> ConsultarUsuarios() {
        return usuarioRepository.findAll().stream()
                .map(UsuarioResponse::from)
                .toList();
    }

    public Usuario buscarPorEmail(String email) {
        return usuarioRepository.findByEmail(email) .orElseThrow(() ->
            new RecursoNaoEncontradoException("Não encontramos um usuário com o e-mail " + email + "."));
    }

    public Usuario buscarPorId(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("O usuário solicitado não foi encontrado. Ele pode ter sido removido."));
    }

    /** Mesma regra da anotação {@code @Cpf} (dígitos verificadores, 11 dígitos, sem sequência repetida). */
    public boolean validarCPF(String cpf) {
        return crm_imobiliario.back.util.validacao.Documentos.cpfValido(cpf);
    }

    public Usuario atualizarUsuario(Long id, UsuarioDTO dto) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("O usuário solicitado não foi encontrado."));

        Papel papel = papelRepository.findById(dto.getPapelId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("O papel selecionado não existe mais. Atualize a página e escolha outro papel."));

        usuario.setEmail(dto.getEmail());
        usuario.setNome(dto.getNome());
        usuario.setGenero(dto.getGenero());
        usuario.setTelefone(dto.getTelefone());
        usuario.setDataNascimento(dto.getDataNascimento());
        usuario.setPapel(papel);
        if (dto.getGestorId() != null) {
            Usuario gestor = usuarioRepository.findById(dto.getGestorId())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("O gestor selecionado não foi encontrado. Atualize a página e escolha outro gestor."));
            usuario.setGestor(gestor);
            crm_imobiliario.back.model.entity.Equipe eg = gestor.getEquipe();
            if (eg == null && equipeRepository != null) eg = equipeRepository.findByGestorId(gestor.getId()).orElse(null);
            if (eg != null) usuario.setEquipe(eg);
            else if (usuario.getEquipe() == null && equipeRepository != null) equipeRepository.findByNome("Equipe Geral").ifPresent(usuario::setEquipe);
        } else {
            usuario.setGestor(null);
            // mantém equipe existente; se quiser desvincular, admin faz via equipe
        }

        return usuarioRepository.save(usuario);
    }

    public Usuario atualizarPerfil(String email, PerfilDTO dto) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RecursoNaoEncontradoException("O usuário solicitado não foi encontrado."));

        if (!usuario.getEmail().equals(dto.email()) && usuarioRepository.existsByEmail(dto.email())) {
            throw new RegraNegocioException("Já existe um usuário cadastrado com este e-mail.", "email", "DUPLICATE_EMAIL");
        }

        usuario.setNome(dto.nome());
        usuario.setEmail(dto.email());
        usuario.setGenero(dto.genero());
        usuario.setTelefone(dto.telefone());
        usuario.setDataNascimento(dto.dataNascimento());

        return usuarioRepository.save(usuario);
    }

    public void validarForcaSenha(String senha) {
        if (senha == null || senha.length() < 8) throw new RegraNegocioException("A nova senha deve ter pelo menos 8 caracteres.", "novaSenha", "WEAK_PASSWORD");
        // exige ao menos 3 de 4: maiúscula, minúscula, número, especial
        int score = 0;
        if (senha.matches(".*[A-Z].*")) score++;
        if (senha.matches(".*[a-z].*")) score++;
        if (senha.matches(".*\\d.*")) score++;
        if (senha.matches(".*[^A-Za-z0-9].*")) score++;
        if (score < 3) throw new RegraNegocioException("A nova senha é fraca. Combine pelo menos 3 destes tipos: letra maiúscula, letra minúscula, número e caractere especial.", "novaSenha", "WEAK_PASSWORD");
    }

    public void trocarSenha(String email, String senhaAtual, String novaSenha) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RecursoNaoEncontradoException("O usuário solicitado não foi encontrado."));

        if (!passwordEncoder.matches(senhaAtual, usuario.getSenha())) {
            throw new RegraNegocioException("A senha atual informada está incorreta.", "senhaAtual", "WRONG_CURRENT_PASSWORD");
        }

        validarForcaSenha(novaSenha);
        usuario.setSenha(passwordEncoder.encode(novaSenha));
        usuario.setTrocarSenha(false);
        usuario.setTokenVersion((usuario.getTokenVersion() != null ? usuario.getTokenVersion() : 0) + 1);
        usuarioRepository.save(usuario);
    }

    public void trocarSenhaAdmin(Long id, String novaSenha) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("O usuário solicitado não foi encontrado."));

        validarForcaSenha(novaSenha);
        usuario.setSenha(passwordEncoder.encode(novaSenha));
        usuario.setTrocarSenha(true);
        usuario.setTokenVersion((usuario.getTokenVersion() != null ? usuario.getTokenVersion() : 0) + 1);
        usuarioRepository.save(usuario);
    }

    /**
     * O que a tela precisa saber antes de inativar: se é corretor, se tem gestor ativo e quantos leads
     * ficarão aguardando redistribuição. Quando há leads e não há gestor, o administrador precisa decidir
     * (vincular um gestor ou assumir a redistribuição) — ver {@link #inativarUsuario(Long, String, Long, boolean)}.
     */
    public record PreviaInativacao(Long usuarioId, String nome, String papel, Long gestorId, String gestorNome,
                                   long leadsAtribuidos, boolean exigeDecisaoSobreGestor) {}

    public PreviaInativacao previaInativacao(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("O usuário solicitado não foi encontrado. Ele pode ter sido removido."));
        String papel = usuario.getPapel() != null ? usuario.getPapel().getPapel() : "";
        Usuario gestor = LeadAtribuicaoService.gestorAtivo(usuario);
        long leads = "corretor".equals(papel) && leadAtribuicaoService != null ? leadAtribuicaoService.contarLeadsAtribuidos(id) : 0;
        return new PreviaInativacao(usuario.getId(), usuario.getNome(), papel,
                gestor != null ? gestor.getId() : null, gestor != null ? gestor.getNome() : null,
                leads, "corretor".equals(papel) && gestor == null && leads > 0);
    }

    /** Inativação sem decisão sobre gestor (corretor com gestor, gestor, admin, ou corretor sem leads). */
    public Usuario inativarUsuario(Long id, String solicitanteEmail) {
        return inativarUsuario(id, solicitanteEmail, null, false);
    }

    /**
     * Inativa o usuário. Para corretor com leads e sem gestor ativo, exige uma decisão, para que a
     * redistribuição nunca fique sem responsável:
     * - {@code vincularGestorId}: vincula esse gestor ao corretor antes de inativar (ele passa a ser o responsável);
     * - {@code prosseguirSemGestor}: segue sem gestor e quem fez a inativação fica responsável.
     */
    @org.springframework.transaction.annotation.Transactional
    public Usuario inativarUsuario(Long id, String solicitanteEmail, Long vincularGestorId, boolean prosseguirSemGestor) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("O usuário solicitado não foi encontrado. Ele pode ter sido removido."));
        String papelAlvo = usuario.getPapel() != null ? usuario.getPapel().getPapel() : "";
        if ("corretor".equals(papelAlvo) && LeadAtribuicaoService.gestorAtivo(usuario) == null) {
            if (vincularGestorId != null) {
                aplicarGestor(usuario, buscarGestorValido(vincularGestorId, usuario));
            } else if (!prosseguirSemGestor && leadAtribuicaoService != null && leadAtribuicaoService.contarLeadsAtribuidos(id) > 0) {
                throw new RegraNegocioException(usuario.getNome() + " não tem gestor vinculado. Vincule um gestor para cuidar da redistribuição dos leads "
                        + "ou confirme que você mesmo ficará responsável por ela.", "gestorId", "CORRETOR_SEM_GESTOR");
            }
        }
        usuario.setAtivo(false);
        usuario.setTokenVersion((usuario.getTokenVersion() != null ? usuario.getTokenVersion() : 0) + 1);
        Usuario salvo = usuarioRepository.save(usuario);

        String papel = salvo.getPapel() != null ? salvo.getPapel().getPapel() : "";
        if ("corretor".equals(papel)) {
            try {
                Usuario solicitante = solicitanteEmail != null ? usuarioRepository.findByEmail(solicitanteEmail).orElse(null) : null;
                if (leadAtribuicaoService != null) {
                    // responsável pela redistribuição: gestor do corretor ou, sem gestor, quem fez a inativação
                    leadAtribuicaoService.desligamentoCorretor(id, solicitante);
                }
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(UsuarioService.class).error("Falha ao redistribuir clientes do corretor {}: {}", id, e.getMessage(), e);
                throw e;
            }
        } else if ("gestor".equals(papel)) {
            try {
                if (equipeService != null) equipeService.desvincularGestor(id);
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(UsuarioService.class).warn("Falha ao desvincular gestor {}: {}", id, e.getMessage());
            }
        }
        return salvo;
    }

    public Usuario inativarUsuario(Long id) {
        return inativarUsuario(id, null);
    }

    /** Gestor que pode ser vinculado: existe, está ativo, tem papel gestor ou admin e não é o próprio usuário. */
    private Usuario buscarGestorValido(Long gestorId, Usuario usuario) {
        Usuario gestor = usuarioRepository.findById(gestorId)
                .orElseThrow(() -> new RegraNegocioException("O gestor selecionado não foi encontrado. Atualize a página e escolha outro.", "gestorId", "GESTOR_NOT_FOUND"));
        String papel = gestor.getPapel() != null ? gestor.getPapel().getPapel() : "";
        if (gestor.getId().equals(usuario.getId())) {
            throw new RegraNegocioException("O usuário não pode ser gestor de si mesmo.", "gestorId", "INVALID_GESTOR");
        }
        if (!gestor.isAtivo()) {
            throw new RegraNegocioException(gestor.getNome() + " está inativo e não pode ser vinculado como gestor.", "gestorId", "INVALID_GESTOR");
        }
        if (!"gestor".equals(papel) && !"admin".equals(papel)) {
            throw new RegraNegocioException(gestor.getNome() + " não tem papel de gestor ou administrador.", "gestorId", "INVALID_GESTOR");
        }
        return gestor;
    }

    /** Mesma regra do cadastro/edição: o corretor passa para a equipe do gestor (ou mantém a atual). */
    private void aplicarGestor(Usuario usuario, Usuario gestor) {
        usuario.setGestor(gestor);
        crm_imobiliario.back.model.entity.Equipe eg = gestor.getEquipe();
        if (eg == null && equipeRepository != null) eg = equipeRepository.findByGestorId(gestor.getId()).orElse(null);
        if (eg != null) usuario.setEquipe(eg);
        usuarioRepository.save(usuario);
    }

    public Usuario ativarUsuario(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("O usuário solicitado não foi encontrado. Ele pode ter sido removido."));
        usuario.setAtivo(true);
        usuario.setTokenVersion((usuario.getTokenVersion() != null ? usuario.getTokenVersion() : 0) + 1);
        Usuario salvo = usuarioRepository.save(usuario);
        // Ativação é sensível: revogar refresh tokens requer re-auth se for o próprio usuário
        return salvo;
    }

    public Usuario forcarIncrementoVersion(Usuario usuario) {
        usuario.setTokenVersion((usuario.getTokenVersion() != null ? usuario.getTokenVersion() : 0) + 1);
        return usuarioRepository.save(usuario);
    }

    public Usuario forcarIncrementoVersion(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("O usuário solicitado não foi encontrado. Ele pode ter sido removido."));
        return forcarIncrementoVersion(usuario);
    }
}
