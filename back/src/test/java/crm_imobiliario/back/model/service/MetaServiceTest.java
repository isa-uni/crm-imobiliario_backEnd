package crm_imobiliario.back.model.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import crm_imobiliario.back.model.dto.MetaCreateDTO;
import crm_imobiliario.back.model.dto.MetaDTO;
import crm_imobiliario.back.model.dto.MetaResumoDTO;
import crm_imobiliario.back.model.entity.Meta;
import crm_imobiliario.back.model.entity.OrigemMeta;
import crm_imobiliario.back.model.entity.Usuario;
import crm_imobiliario.back.model.repository.MetaRepository;
import crm_imobiliario.back.model.repository.UsuarioRepository;

/**
 * Regra: a meta própria do corretor tem prioridade sobre a meta atribuída pelo gestor. Na
 * ausência de uma meta própria, vale a do gestor. As duas devem poder coexistir (para exibição),
 * uma por origem, no mesmo usuário/mês.
 */
@ExtendWith(MockitoExtension.class)
class MetaServiceTest {

    @Mock private MetaRepository metaRepository;
    @Mock private UsuarioRepository usuarioRepository;

    private MetaService metaService;

    private static final LocalDate MES = LocalDate.of(2026, 9, 1);

    @BeforeEach
    void setUp() {
        metaService = new MetaService();
        setField("metaRepository", metaRepository);
        setField("usuarioRepository", usuarioRepository);
    }

    private void setField(String nome, Object valor) {
        try {
            var field = MetaService.class.getDeclaredField(nome);
            field.setAccessible(true);
            field.set(metaService, valor);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private Usuario usuario(Long id, String nome) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setNome(nome);
        return u;
    }

    private Meta meta(Long id, Usuario usuario, Integer contratos, OrigemMeta origem) {
        Meta m = new Meta();
        m.setId(id);
        m.setUsuario(usuario);
        m.setMesReferencia(MES);
        m.setMetaContratos(contratos);
        m.setOrigem(origem);
        return m;
    }

    @Test
    void semNenhumaMetaResumoFicaTodoNulo() {
        when(metaRepository.findByUsuarioIdAndMesReferenciaAndOrigem(10L, MES, OrigemMeta.CORRETOR)).thenReturn(Optional.empty());
        when(metaRepository.findByUsuarioIdAndMesReferenciaAndOrigem(10L, MES, OrigemMeta.GESTOR)).thenReturn(Optional.empty());

        MetaResumoDTO resumo = metaService.buscarResumo(10L, MES);

        assertNull(resumo.getMetaPropria());
        assertNull(resumo.getMetaGestor());
        assertNull(resumo.getMetaEfetiva());
    }

    @Test
    void semMetaPropriaAMetaDoGestorEQueVale() {
        Usuario corretor = usuario(10L, "Corretor Teste");
        Meta metaGestor = meta(1L, corretor, 5, OrigemMeta.GESTOR);
        when(metaRepository.findByUsuarioIdAndMesReferenciaAndOrigem(10L, MES, OrigemMeta.CORRETOR)).thenReturn(Optional.empty());
        when(metaRepository.findByUsuarioIdAndMesReferenciaAndOrigem(10L, MES, OrigemMeta.GESTOR)).thenReturn(Optional.of(metaGestor));

        MetaResumoDTO resumo = metaService.buscarResumo(10L, MES);

        assertNull(resumo.getMetaPropria());
        assertNotNull(resumo.getMetaGestor());
        assertNotNull(resumo.getMetaEfetiva());
        assertEquals(5, resumo.getMetaEfetiva().getMetaContratos());
        assertEquals("GESTOR", resumo.getMetaEfetiva().getOrigem());
    }

    @Test
    void comMetaPropriaDefinidaElaPrevaleceSobreADoGestor() {
        Usuario corretor = usuario(10L, "Corretor Teste");
        Meta metaGestor = meta(1L, corretor, 5, OrigemMeta.GESTOR);
        Meta metaPropria = meta(2L, corretor, 8, OrigemMeta.CORRETOR);
        when(metaRepository.findByUsuarioIdAndMesReferenciaAndOrigem(10L, MES, OrigemMeta.CORRETOR)).thenReturn(Optional.of(metaPropria));
        when(metaRepository.findByUsuarioIdAndMesReferenciaAndOrigem(10L, MES, OrigemMeta.GESTOR)).thenReturn(Optional.of(metaGestor));

        MetaResumoDTO resumo = metaService.buscarResumo(10L, MES);

        assertNotNull(resumo.getMetaPropria());
        assertNotNull(resumo.getMetaGestor());
        assertEquals(8, resumo.getMetaEfetiva().getMetaContratos());
        assertEquals("CORRETOR", resumo.getMetaEfetiva().getOrigem());
        // a meta do gestor continua visível mesmo não sendo a que vale
        assertEquals(5, resumo.getMetaGestor().getMetaContratos());
    }

    @Test
    void criarOuAtualizarGravaComAOrigemInformadaSemAfetarAOutra() {
        Usuario corretor = usuario(10L, "Corretor Teste");
        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(corretor));
        when(metaRepository.findByUsuarioIdAndMesReferenciaAndOrigem(10L, MES, OrigemMeta.CORRETOR)).thenReturn(Optional.empty());
        when(metaRepository.save(any(Meta.class))).thenAnswer(inv -> {
            Meta m = inv.getArgument(0);
            m.setId(99L);
            return m;
        });

        MetaCreateDTO dto = new MetaCreateDTO(10L, MES, 7);
        MetaDTO salvo = metaService.criarOuAtualizar(dto, OrigemMeta.CORRETOR);

        assertEquals("CORRETOR", salvo.getOrigem());
        assertEquals(7, salvo.getMetaContratos());
    }
}
