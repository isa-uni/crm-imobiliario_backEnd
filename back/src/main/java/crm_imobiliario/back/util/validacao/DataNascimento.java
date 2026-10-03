package crm_imobiliario.back.util.validacao;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.LocalDate;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * Data de nascimento plausível: não pode ser hoje nem no futuro, nem anterior a 1900 (erro de digitação,
 * ex.: ano 0198). Não impõe idade mínima — essa regra de negócio não existe no projeto.
 */
@Documented
@Constraint(validatedBy = DataNascimento.Validador.class)
@Target({ ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
public @interface DataNascimento {
    String message() default "Informe uma data de nascimento válida.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    LocalDate MINIMA = LocalDate.of(1900, 1, 1);

    class Validador implements ConstraintValidator<DataNascimento, LocalDate> {
        @Override
        public boolean isValid(LocalDate valor, ConstraintValidatorContext ctx) {
            if (valor == null) return true;
            String problema = !valor.isBefore(LocalDate.now()) ? "A data de nascimento deve ser anterior a hoje."
                    : valor.isBefore(MINIMA) ? "A data de nascimento não pode ser anterior a 01/01/1900. Confira o ano."
                    : null;
            if (problema == null) return true;
            ctx.disableDefaultConstraintViolation();
            ctx.buildConstraintViolationWithTemplate(problema).addConstraintViolation();
            return false;
        }
    }
}
