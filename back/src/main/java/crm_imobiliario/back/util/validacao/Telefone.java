package crm_imobiliario.back.util.validacao;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * Telefone brasileiro com DDD existente: fixo (10 dígitos) ou celular (11 dígitos, começando com 9).
 * A mensagem diz exatamente o que está errado. Vazio/nulo passa: a presença é verificada por {@code @NotBlank}.
 */
@Documented
@Constraint(validatedBy = Telefone.Validador.class)
@Target({ ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
public @interface Telefone {
    String message() default "Informe um telefone válido com DDD.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<Telefone, String> {
        @Override
        public boolean isValid(String valor, ConstraintValidatorContext ctx) {
            String problema = Documentos.problemaTelefone(valor);
            if (problema == null) return true;
            ctx.disableDefaultConstraintViolation();
            ctx.buildConstraintViolationWithTemplate(problema).addConstraintViolation();
            return false;
        }
    }
}
