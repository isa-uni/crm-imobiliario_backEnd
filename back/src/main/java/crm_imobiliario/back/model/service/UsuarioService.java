package crm_imobiliario.back.model.service;

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

    public Usuario cadastrarUsuario(UsuarioDTO dto) {

        Usuario usuario = new Usuario();

        String cpfLimpo = dto.getCpf().replaceAll("\\D", "");

        if (!validarCPF(cpfLimpo)) {
            throw new RuntimeException("CPF inválido");
        }

        if (usuarioRepository.existsByCpf(cpfLimpo)) {
            throw new RuntimeException("Já existe um usuario com esse CPF");
        }

        if (usuarioRepository.existsByEmail(dto.getEmail())) {
            throw new RuntimeException("Já existe um usuário com esse e-mail");
        }

        String ano = String.valueOf(LocalDate.now().getYear());
        String matricula = ano + cpfLimpo.substring(cpfLimpo.length() - 4);
        String senhaInicial = cpfLimpo.substring(cpfLimpo.length() - 4);

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
                .orElseThrow(() -> new RuntimeException("Papel não encontrado"));

        usuario.setPapel(papel);
        if (dto.getGestorId() != null) {
            Usuario gestor = usuarioRepository.findById(dto.getGestorId())
                    .orElseThrow(() -> new RuntimeException("Gestor não encontrado"));
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
        usuario.setSenha(passwordEncoder.encode(senhaInicial));

        try {
            return usuarioRepository.save(usuario);

        } catch (DataIntegrityViolationException e) {
            throw new RuntimeException("usuario já cadastrado");
        }
    }

    public void deletarUsuario(Long id){
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(
                        "Cliente com id " + id + " não encontrado"
                ));
        usuarioRepository.delete(usuario);
    }
    
    public List<UsuarioResponse> ConsultarUsuarios() {
        return usuarioRepository.findAll().stream()
                .map(UsuarioResponse::from)
                .toList();
    }

    public Usuario buscarPorEmail(String email) {
        return usuarioRepository.findByEmail(email) .orElseThrow(() ->
            new RuntimeException("Usuário com E-mail "+email+" não encontrado"));
    }

    public Usuario buscarPorId(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuário com id " + id + " não encontrado"));
    }

    public boolean validarCPF(String cpf) {
        if (cpf == null) return false;

        cpf = cpf.replaceAll("\\D", "");

        if (cpf.length() != 11) return false;

        // Rejeita CPFs com todos os dígitos iguais
        if (cpf.matches("(\\d)\\1{10}")) return false;

        try {
            int soma = 0;
            for (int i = 0; i < 9; i++) {
                soma += (cpf.charAt(i) - '0') * (10 - i);
            }

            int primeiroDigito = 11 - (soma % 11);
            if (primeiroDigito >= 10) primeiroDigito = 0;

            if (primeiroDigito != (cpf.charAt(9) - '0')) return false;

            soma = 0;
            for (int i = 0; i < 10; i++) {
                soma += (cpf.charAt(i) - '0') * (11 - i);
            }

            int segundoDigito = 11 - (soma % 11);
            if (segundoDigito >= 10) segundoDigito = 0;

            return segundoDigito == (cpf.charAt(10) - '0');

        } catch (Exception e) {
            return false;
        }
    }

    public Usuario atualizarUsuario(Long id, UsuarioDTO dto) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

        Papel papel = papelRepository.findById(dto.getPapelId())
                .orElseThrow(() -> new RuntimeException("Papel não encontrado"));

        usuario.setEmail(dto.getEmail());
        usuario.setNome(dto.getNome());
        usuario.setGenero(dto.getGenero());
        usuario.setTelefone(dto.getTelefone());
        usuario.setDataNascimento(dto.getDataNascimento());
        usuario.setPapel(papel);
        if (dto.getGestorId() != null) {
            Usuario gestor = usuarioRepository.findById(dto.getGestorId())
                    .orElseThrow(() -> new RuntimeException("Gestor não encontrado"));
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
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

        if (!usuario.getEmail().equals(dto.email()) && usuarioRepository.existsByEmail(dto.email())) {
            throw new RuntimeException("Já existe um usuário com esse e-mail");
        }

        usuario.setNome(dto.nome());
        usuario.setEmail(dto.email());
        usuario.setGenero(dto.genero());
        usuario.setTelefone(dto.telefone());
        usuario.setDataNascimento(dto.dataNascimento());

        return usuarioRepository.save(usuario);
    }

    public void validarForcaSenha(String senha) {
        if (senha == null || senha.length() < 8) throw new RuntimeException("Senha deve ter no mínimo 8 caracteres");
        // exige ao menos 3 de 4: maiúscula, minúscula, número, especial
        int score = 0;
        if (senha.matches(".*[A-Z].*")) score++;
        if (senha.matches(".*[a-z].*")) score++;
        if (senha.matches(".*\\d.*")) score++;
        if (senha.matches(".*[^A-Za-z0-9].*")) score++;
        if (score < 3) throw new RuntimeException("Senha fraca: use maiúscula, minúscula, número ou caractere especial");
    }

    public void trocarSenha(String email, String senhaAtual, String novaSenha) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

        if (!passwordEncoder.matches(senhaAtual, usuario.getSenha())) {
            throw new RuntimeException("Senha atual incorreta");
        }

        validarForcaSenha(novaSenha);
        usuario.setSenha(passwordEncoder.encode(novaSenha));
        usuario.setTrocarSenha(false);
        usuario.setTokenVersion((usuario.getTokenVersion() != null ? usuario.getTokenVersion() : 0) + 1);
        usuarioRepository.save(usuario);
    }

    public void trocarSenhaAdmin(Long id, String novaSenha) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

        validarForcaSenha(novaSenha);
        usuario.setSenha(passwordEncoder.encode(novaSenha));
        usuario.setTrocarSenha(true);
        usuario.setTokenVersion((usuario.getTokenVersion() != null ? usuario.getTokenVersion() : 0) + 1);
        usuarioRepository.save(usuario);
    }

    @org.springframework.transaction.annotation.Transactional
    public Usuario inativarUsuario(Long id, String solicitanteEmail) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuário com id " + id + " não encontrado"));
        usuario.setAtivo(false);
        usuario.setTokenVersion((usuario.getTokenVersion() != null ? usuario.getTokenVersion() : 0) + 1);
        Usuario salvo = usuarioRepository.save(usuario);

        String papel = salvo.getPapel() != null ? salvo.getPapel().getPapel() : "";
        if ("corretor".equals(papel)) {
            try {
                Usuario solicitante = solicitanteEmail != null ? usuarioRepository.findByEmail(solicitanteEmail).orElse(null) : null;
                if (leadAtribuicaoService != null) {
                    leadAtribuicaoService.desligamentoCorretor(id, solicitante != null ? solicitante : salvo);
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

    public Usuario ativarUsuario(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuário com id " + id + " não encontrado"));
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
                .orElseThrow(() -> new RuntimeException("Usuário com id " + id + " não encontrado"));
        return forcarIncrementoVersion(usuario);
    }
}
