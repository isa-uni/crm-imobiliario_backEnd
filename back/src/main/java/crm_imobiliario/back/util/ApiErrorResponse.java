package crm_imobiliario.back.util;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiErrorResponse {
    private boolean success;
    private String message;
    private String code;
    private Map<String, String> fields;
    private Object details;
    private String path;
    private Instant timestamp;
    private String requestId;

    public static ApiErrorResponse of(String message, String code, String path) {
        return ApiErrorResponse.builder()
                .success(false)
                .message(message)
                .code(code)
                .path(path)
                .timestamp(Instant.now())
                .build();
    }
}
