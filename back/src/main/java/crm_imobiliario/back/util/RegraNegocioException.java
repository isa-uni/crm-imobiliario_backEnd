package crm_imobiliario.back.util;

/**
 * Violação de regra de negócio (dado válido no formato, mas não permitido) — mapeada para HTTP 400.
 *
 * A mensagem é exibida ao usuário: deve dizer o que impediu a ação e, quando possível, o que fazer.
 * Quando a regra se refere a um campo de formulário, informe {@code campo} para o frontend destacar
 * o campo certo; {@code codigo} identifica o motivo de forma estável (ex.: SENHA_ATUAL_INCORRETA).
 */
public class RegraNegocioException extends RuntimeException {

    private final String campo;
    private final String codigo;

    public RegraNegocioException(String message) {
        this(message, null, null);
    }

    public RegraNegocioException(String message, String campo) {
        this(message, campo, null);
    }

    public RegraNegocioException(String message, String campo, String codigo) {
        super(message);
        this.campo = campo;
        this.codigo = codigo;
    }

    public String getCampo() { return campo; }

    public String getCodigo() { return codigo; }
}
