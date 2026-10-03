package crm_imobiliario.back.model.dto;

/**
 * Corpo da resposta de login/refresh. Os tokens vão apenas nos cookies httpOnly (Set-Cookie) —
 * nunca no corpo — para que não fiquem acessíveis ao JavaScript do navegador.
 */
public record SessaoResponse(UsuarioRetorno usuario) {
}
