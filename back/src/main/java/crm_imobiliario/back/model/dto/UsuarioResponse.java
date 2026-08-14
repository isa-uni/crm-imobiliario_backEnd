package crm_imobiliario.back.model.dto;

import java.time.LocalDate;

import crm_imobiliario.back.model.entity.Usuario;

public record UsuarioResponse(
        Long id,
        String nome,
        String email,
        String cpf,
        String genero,
        String telefone,
        String matricula,
        LocalDate dataNascimento,
        String papel,
        boolean ativo,
        boolean trocarSenha
) {
    public static UsuarioResponse from(Usuario usuario) {
        return new UsuarioResponse(
                usuario.getId(),
                usuario.getNome(),
                usuario.getEmail(),
                mascararCpf(usuario.getCpf()),
                usuario.getGenero(),
                usuario.getTelefone(),
                usuario.getMatricula(),
                usuario.getDataNascimento(),
                usuario.getPapel() != null ? usuario.getPapel().getPapel() : null,
                usuario.isAtivo(),
                usuario.isTrocarSenha()
        );
    }

    private static String mascararCpf(String cpf) {
        if (cpf == null || cpf.length() != 11) return cpf;
        return "***." + cpf.substring(3, 6) + "." + cpf.substring(6, 9) + "-**";
    }
}
