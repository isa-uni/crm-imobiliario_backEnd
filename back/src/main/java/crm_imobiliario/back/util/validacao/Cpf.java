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

/** CPF matematicamente válido. Vazio/nulo passa: a presença é verificada por {@code @NotBlank}. */
@Documented
@Constraint(validatedBy = Cpf.Validador.class)
@Target({ ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
public @interface Cpf {
    String message() default "O CPF informado não é válido. Confira os 11 dígitos.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<Cpf, String> {
        @Override
        public boolean isValid(String valor, ConstraintValidatorContext ctx) {
            if (valor == null || valor.isBlank()) return true;
            if (!valor.matches("[\\d.\\-\\s]+")) {
                ctx.disableDefaultConstraintViolation();
                ctx.buildConstraintViolationWithTemplate("O CPF deve conter apenas números.").addConstraintViolation();
                return false;
            }
            if (Documentos.somenteDigitos(valor).length() != 11) {
                ctx.disableDefaultConstraintViolation();
                ctx.buildConstraintViolationWithTemplate("O CPF deve ter 11 dígitos.").addConstraintViolation();
                return false;
            }
            return Documentos.cpfValido(valor);
        }
    }
}
