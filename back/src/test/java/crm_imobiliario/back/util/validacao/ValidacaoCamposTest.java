package crm_imobiliario.back.util.validacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import crm_imobiliario.back.model.dto.ImovelAtualizacaoDTO;
import crm_imobiliario.back.model.dto.ImovelDTO;
import crm_imobiliario.back.model.dto.LeadAtualizacaoDTO;
import crm_imobiliario.back.model.dto.LeadsDTO;
import crm_imobiliario.back.model.dto.PerfilDTO;
import crm_imobiliario.back.model.dto.UsuarioDTO;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.groups.Default;

/** Regras de campo do backend: a garantia de integridade mesmo se o frontend for contornado. */
class ValidacaoCamposTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void criar() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void fechar() { factory.close(); }

    /** campo → mensagens (um campo pode violar mais de uma regra) */
    private static <T> Map<String, Set<String>> erros(T obj, Class<?>... grupos) {
        Set<ConstraintViolation<T>> v = grupos.length == 0 ? validator.validate(obj) : validator.validate(obj, grupos);
        return v.stream().collect(Collectors.groupingBy(c -> c.getPropertyPath().toString(),
                Collectors.mapping(ConstraintViolation::getMessage, Collectors.toSet())));
    }

    private static UsuarioDTO usuario(String cpf, String telefone, String email, LocalDate nascimento) {
        return new UsuarioDTO("Ana Souza", email, cpf, "F", telefone, nascimento, 1L, null);
    }

    // ------------------------------------------------------------------ CPF

    @Test
    void cpf_valido_com_ou_sem_mascara() {
        assertThat(Documentos.cpfValido("529.982.247-25")).isTrue();
        assertThat(Documentos.cpfValido("52998224725")).isTrue();
    }

    @Test
    void cpf_invalido_sequencia_repetida_digito_errado_ou_tamanho() {
        assertThat(Documentos.cpfValido("111.111.111-11")).isFalse();
        assertThat(Documentos.cpfValido("529.982.247-26")).isFalse();
        assertThat(Documentos.cpfValido("123")).isFalse();
    }

    @Test
    void cadastro_de_usuario_valida_cpf_junto_com_os_demais_campos() {
        UsuarioDTO dto = new UsuarioDTO("", "email-invalido", "111.111.111-11", "F", "(43) 99999-9999", LocalDate.of(1990, 1, 1), 1L, null);
        Map<String, Set<String>> e = erros(dto, Default.class, Cadastro.class);
        // antes o CPF só era verificado depois que os outros campos passavam — o erro aparecia "em etapas"
        assertThat(e).containsKeys("nome", "email", "cpf");
        assertThat(e.get("cpf")).containsExactly("O CPF informado não é válido. Confira os 11 dígitos.");
    }

    @Test
    void cpf_com_letras_ou_incompleto_tem_mensagem_especifica() {
        assertThat(erros(usuario("529.982.24A-25", "(43) 99999-9999", "a@b.com", LocalDate.of(1990, 1, 1)), Cadastro.class).get("cpf"))
                .containsExactly("O CPF deve conter apenas números.");
        assertThat(erros(usuario("529.982.247", "(43) 99999-9999", "a@b.com", LocalDate.of(1990, 1, 1)), Cadastro.class).get("cpf"))
                .containsExactly("O CPF deve ter 11 dígitos.");
    }

    @Test
    void edicao_de_usuario_nao_revalida_cpf_que_nao_pode_ser_alterado() {
        assertThat(erros(usuario("11111111111", "(43) 99999-9999", "ana@empresa.com", LocalDate.of(1990, 1, 1)))).isEmpty();
    }

    // ------------------------------------------------------------------ telefone

    @Test
    void telefone_valido_fixo_e_celular_com_ou_sem_mascara() {
        assertThat(Documentos.problemaTelefone("(43) 99999-9999")).isNull();
        assertThat(Documentos.problemaTelefone("4333334444")).isNull();
    }

    @Test
    void telefone_invalido_explica_o_motivo() {
        assertThat(Documentos.problemaTelefone("(43) 9999-999")).isEqualTo("Informe o telefone com DDD (10 ou 11 dígitos).");
        assertThat(Documentos.problemaTelefone("(20) 99999-9999")).isEqualTo("O DDD 20 não existe. Confira o código de área.");
        assertThat(Documentos.problemaTelefone("(43) 89999-9999")).isEqualTo("Celular com 11 dígitos deve começar com 9 após o DDD.");
        assertThat(Documentos.problemaTelefone("43 9999a9999")).isEqualTo("O telefone deve conter apenas números.");
    }

    // ------------------------------------------------------------------ e-mail, nome, datas

    @Test
    void email_sem_extensao_de_dominio_e_recusado() {
        assertThat(erros(usuario("52998224725", "(43) 99999-9999", "ana@empresa", LocalDate.of(1990, 1, 1))))
                .containsKey("email");
    }

    @Test
    void data_de_nascimento_futura_ou_antes_de_1900_e_recusada() {
        assertThat(erros(usuario("52998224725", "(43) 99999-9999", "a@b.com", LocalDate.now().plusDays(1))).get("dataNascimento"))
                .containsExactly("A data de nascimento deve ser anterior a hoje.");
        assertThat(erros(usuario("52998224725", "(43) 99999-9999", "a@b.com", LocalDate.of(198, 5, 1))).get("dataNascimento"))
                .containsExactly("A data de nascimento não pode ser anterior a 01/01/1900. Confira o ano.");
    }

    @Test
    void perfil_usa_as_mesmas_regras_do_cadastro() {
        PerfilDTO dto = new PerfilDTO("1", "ana@empresa", "X", "(20) 99999-9999", LocalDate.now());
        assertThat(erros(dto)).containsKeys("nome", "email", "genero", "telefone", "dataNascimento");
    }

    // ------------------------------------------------------------------ lead

    @Test
    void lead_novo_valida_telefone_email_e_valor() {
        LeadsDTO dto = new LeadsDTO("Carlos", "carlos@", "(43) 1234", "espontaneo", "primeiro_contato", "lead", -1L, null, null);
        assertThat(erros(dto)).containsKeys("email", "telefone", "valorInteresse");
    }

    @Test
    void edicao_parcial_de_lead_aceita_campos_ausentes_mas_recusa_valores_invalidos() {
        assertThat(erros(new LeadAtualizacaoDTO(null, null, null, null, null, "contrato", null, null, null, null, null))).isEmpty();
        // antes a edição gravava nome vazio, telefone com letras e valor negativo
        Map<String, Set<String>> e = erros(new LeadAtualizacaoDTO("  ", null, "telefone", null, null, null, -5L, null, null, null, null));
        assertThat(e).containsKeys("nome", "telefone", "valorInteresse");
    }

    @Test
    void observacao_acima_do_limite_do_banco_e_recusada() {
        LeadAtualizacaoDTO dto = new LeadAtualizacaoDTO(null, null, null, null, null, null, null, null, null, "x".repeat(256), null);
        assertThat(erros(dto).get("observacao")).containsExactly("As observações devem ter no máximo 255 caracteres.");
    }

    // ------------------------------------------------------------------ imóvel

    @Test
    void imovel_valor_de_venda_deve_ser_maior_que_zero_e_contagens_nao_negativas() {
        ImovelDTO dto = new ImovelDTO("Casa", "disponivel", "Rua A", "Centro", "Londrina", null, 0L, -1L, 0L, 0L, 50L);
        Map<String, Set<String>> e = erros(dto);
        assertThat(e.get("valorVenda")).containsExactly("Informe um valor de venda maior que zero.");
        assertThat(e).containsKey("quartos");
    }

    @Test
    void imovel_status_fora_da_lista_e_recusado_tambem_na_edicao() {
        ImovelAtualizacaoDTO dto = new ImovelAtualizacaoDTO(null, "alugado", null, null, null, null, null, null, null, null, null);
        assertThat(erros(dto)).containsKey("status");
    }
}
