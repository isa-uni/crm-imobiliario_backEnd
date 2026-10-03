package crm_imobiliario.back.util.validacao;

import java.util.Set;

/**
 * Regras de conteúdo de documentos e contatos, compartilhadas pelas anotações de validação
 * ({@link Cpf}, {@link Telefone}) e pelos services. As mesmas regras existem no frontend em
 * {@code lib/validacao.ts}, para que a mensagem apareça antes do envio.
 */
public final class Documentos {

    private Documentos() {}

    /** Formato aceito para e-mail: algo@dominio.ext (o {@code @Email} padrão aceita "a@b", sem extensão). */
    public static final String EMAIL_REGEX = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$";

    /** DDDs válidos no Brasil (ANATEL). Ex.: 20, 26 e 30 não existem. */
    public static final Set<Integer> DDDS = Set.of(
            11, 12, 13, 14, 15, 16, 17, 18, 19,
            21, 22, 24, 27, 28,
            31, 32, 33, 34, 35, 37, 38,
            41, 42, 43, 44, 45, 46, 47, 48, 49,
            51, 53, 54, 55,
            61, 62, 63, 64, 65, 66, 67, 68, 69,
            71, 73, 74, 75, 77, 79,
            81, 82, 83, 84, 85, 86, 87, 88, 89,
            91, 92, 93, 94, 95, 96, 97, 98, 99);

    public static String somenteDigitos(String valor) {
        return valor == null ? "" : valor.replaceAll("\\D", "");
    }

    /** CPF com 11 dígitos, dígitos verificadores corretos e sem sequência repetida (111.111.111-11). */
    public static boolean cpfValido(String valor) {
        String cpf = somenteDigitos(valor);
        if (cpf.length() != 11 || cpf.matches("(\\d)\\1{10}")) return false;
        for (int posicao = 9; posicao <= 10; posicao++) {
            int soma = 0;
            for (int i = 0; i < posicao; i++) soma += (cpf.charAt(i) - '0') * (posicao + 1 - i);
            int digito = (soma * 10) % 11;
            if (digito == 10) digito = 0;
            if (digito != cpf.charAt(posicao) - '0') return false;
        }
        return true;
    }

    /**
     * Motivo pelo qual o telefone não é válido, ou {@code null} se for válido.
     * Aceita a forma com máscara "(43) 99999-9999" ou só dígitos.
     */
    public static String problemaTelefone(String valor) {
        if (valor == null || valor.isBlank()) return null; // presença é verificada por @NotBlank
        if (!valor.matches("[\\d\\s()+\\-.]+")) return "O telefone deve conter apenas números.";
        String digitos = somenteDigitos(valor);
        if (digitos.length() != 10 && digitos.length() != 11) return "Informe o telefone com DDD (10 ou 11 dígitos).";
        if (!DDDS.contains(Integer.parseInt(digitos.substring(0, 2)))) return "O DDD " + digitos.substring(0, 2) + " não existe. Confira o código de área.";
        if (digitos.length() == 11 && digitos.charAt(2) != '9') return "Celular com 11 dígitos deve começar com 9 após o DDD.";
        if (digitos.length() == 10 && (digitos.charAt(2) == '0' || digitos.charAt(2) == '1')) return "Telefone fixo não pode começar com 0 ou 1 após o DDD.";
        return null;
    }
}
