package crm_imobiliario.back.util;

import lombok.Data;

@Data
public class DefaultResponse {
    private int code;
    private String message;
    private String exception;

    public static DefaultResponse construir(int code, String message) {
        DefaultResponse response = new DefaultResponse();
        response.setCode(code);
        response.setMessage(message);
        return response;
    }
    public static DefaultResponse construir(int code, String message, String exception) {
        DefaultResponse response = new DefaultResponse();
        response.setCode(code);
        response.setMessage(message);
        response.setException(exception);
        return response;
    }
}
