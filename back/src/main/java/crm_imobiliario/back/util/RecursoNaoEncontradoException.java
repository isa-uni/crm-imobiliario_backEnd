package crm_imobiliario.back.util;

/** Recurso solicitado não existe — mapeado para HTTP 404 pelo GlobalExceptionHandler. */
public class RecursoNaoEncontradoException extends RuntimeException {
    public RecursoNaoEncontradoException(String message) {
        super(message);
    }
}
