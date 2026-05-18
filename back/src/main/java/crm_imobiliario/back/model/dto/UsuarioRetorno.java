package crm_imobiliario.back.model.dto;

public record UsuarioRetorno (
        Long id,
        String nome,
        String email,
        String papel
) {}
