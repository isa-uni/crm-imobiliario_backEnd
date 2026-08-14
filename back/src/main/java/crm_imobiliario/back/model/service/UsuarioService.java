package crm_imobiliario.back.model.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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

    // public String normalizarCpf(String valor) {
    //     if (valor == null || valor.isBlank()) {
    //         return null;
    //     }
    //     return valor.replaceAll("\\D", "");
    // }

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

        return usuarioRepository.save(usuario);
    }

    public void trocarSenha(String email, String senhaAtual, String novaSenha) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

        if (!passwordEncoder.matches(senhaAtual, usuario.getSenha())) {
            throw new RuntimeException("Senha atual incorreta");
        }

        usuario.setSenha(passwordEncoder.encode(novaSenha));
        usuario.setTrocarSenha(false);
        usuarioRepository.save(usuario);
    }

    public void trocarSenhaAdmin(Long id, String novaSenha) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

        usuario.setSenha(passwordEncoder.encode(novaSenha));
        usuario.setTrocarSenha(true);
        usuarioRepository.save(usuario);
    }

    public Usuario inativarUsuario(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuário com id " + id + " não encontrado"));
        usuario.setAtivo(false);
        return usuarioRepository.save(usuario);
    }

    public Usuario ativarUsuario(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuário com id " + id + " não encontrado"));
        usuario.setAtivo(true);
        return usuarioRepository.save(usuario);
    }
}
