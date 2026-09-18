package crm_imobiliario.back.model.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import crm_imobiliario.back.model.dto.LeadAtualizacaoDTO;
import crm_imobiliario.back.model.entity.Empreendimento;
import crm_imobiliario.back.model.entity.Lead;
import crm_imobiliario.back.model.entity.Papel;
import crm_imobiliario.back.model.entity.Usuario;
import crm_imobiliario.back.model.repository.EmpreendimentoRepository;
import crm_imobiliario.back.model.repository.EquipeRepository;
import crm_imobiliario.back.model.repository.LeadRepository;
import crm_imobiliario.back.model.repository.LeadResponsavelHistoricoRepository;
import crm_imobiliario.back.model.repository.TramitacaoRepository;

/**
 * Regressão: atualizar o lead (ex.: só trocar o status pelo dropdown rápido da listagem) não pode
 * apagar o empreendimento de interesse já vinculado. O bug real era o "else" que zerava o vínculo
 * sempre que o DTO chegava sem empreendimentoId — inclusive numa atualização parcial que nunca
 * pretendeu mexer nesse campo.
 */
@ExtendWith(MockitoExtension.class)
class LeadsServiceTest {

    @Mock private LeadRepository leadRepository;
    @Mock private EmpreendimentoRepository empreendimentoRepository;
    @Mock private TramitacaoRepository tramitacaoRepository;
    @Mock private UsuarioService usuarioService;
    @Mock private LeadResponsavelHistoricoRepository historicoRepository;
    @Mock private EquipeRepository equipeRepository;

    private LeadsService leadsService;

    private static final String EMAIL_ADMIN = "admin@teste.com";

    @BeforeEach
    void setUp() {
        leadsService = new LeadsService();
        setField("leadRepository", leadRepository);
        setField("empreendimentoRepository", empreendimentoRepository);
        setField("tramitacaoRepository", tramitacaoRepository);
        setField("usuarioService", usuarioService);
        setField("historicoRepository", historicoRepository);
        setField("equipeRepository", equipeRepository);

        Papel papelAdmin = new Papel(1L, "admin", true);
        Usuario admin = new Usuario();
        admin.setId(1L);
        admin.setEmail(EMAIL_ADMIN);
        admin.setPapel(papelAdmin);
        when(usuarioService.buscarPorEmail(EMAIL_ADMIN)).thenReturn(admin);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(EMAIL_ADMIN, null, List.of()));

        when(leadRepository.save(any(Lead.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setField(String nome, Object valor) {
        try {
            var field = LeadsService.class.getDeclaredField(nome);
            field.setAccessible(true);
            field.set(leadsService, valor);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private Lead leadComEmpreendimento(Long empreendimentoId) {
        Empreendimento emp = new Empreendimento();
        emp.setId(empreendimentoId);
        emp.setNome("Residencial Teste");
        Lead lead = new Lead();
        lead.setId(10L);
        lead.setStatus("lead");
        lead.setEmpreendimento(emp);
        return lead;
    }

    private LeadAtualizacaoDTO dto(String status, Long empreendimentoId, Boolean limparEmpreendimento) {
        return new LeadAtualizacaoDTO(null, null, null, null, null, status, null,
                empreendimentoId, limparEmpreendimento, null, null);
    }

    @Test
    void atualizacaoParcialDeStatusNaoRemoveEmpreendimentoJaVinculado() {
        Lead lead = leadComEmpreendimento(5L);
        when(leadRepository.findById(10L)).thenReturn(Optional.of(lead));

        Lead atualizado = leadsService.atualizarLeads(10L, dto("oportunidade", null, null));

        assertEquals("oportunidade", atualizado.getStatus());
        assertNotNull(atualizado.getEmpreendimento(), "empreendimento não pode sumir só porque o DTO não enviou o campo");
        assertEquals(5L, atualizado.getEmpreendimento().getId());
    }

    @Test
    void limparEmpreendimentoExplicitoRemoveOVinculo() {
        Lead lead = leadComEmpreendimento(5L);
        when(leadRepository.findById(10L)).thenReturn(Optional.of(lead));

        Lead atualizado = leadsService.atualizarLeads(10L, dto(null, null, true));

        assertNull(atualizado.getEmpreendimento(), "com limparEmpreendimento=true o vínculo deve ser removido");
    }

    @Test
    void definirNovoEmpreendimentoAtualizaOVinculo() {
        Lead lead = leadComEmpreendimento(5L);
        when(leadRepository.findById(10L)).thenReturn(Optional.of(lead));
        Empreendimento novo = new Empreendimento();
        novo.setId(9L);
        novo.setNome("Outro Empreendimento");
        when(empreendimentoRepository.findById(9L)).thenReturn(Optional.of(novo));

        Lead atualizado = leadsService.atualizarLeads(10L, dto(null, 9L, null));

        assertNotNull(atualizado.getEmpreendimento());
        assertEquals(9L, atualizado.getEmpreendimento().getId());
    }
}
