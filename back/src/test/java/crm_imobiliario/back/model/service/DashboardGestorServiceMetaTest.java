package crm_imobiliario.back.model.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import crm_imobiliario.back.model.dto.DashboardGestorDTO;
import crm_imobiliario.back.model.entity.Meta;
import crm_imobiliario.back.model.entity.OrigemMeta;
import crm_imobiliario.back.model.entity.Usuario;
import crm_imobiliario.back.model.repository.MetaRepository;

/**
 * Regressão: "Meta da equipe" no dashboard do gestor precisa expor a meta atribuída pelo gestor e
 * a meta própria do corretor separadamente (não só a efetiva), conforme pedido do usuário.
 */
@ExtendWith(MockitoExtension.class)
class DashboardGestorServiceMetaTest {

    @Mock private MetaRepository metaRepository;

    private DashboardGestorService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new DashboardGestorService();
        var field = DashboardGestorService.class.getDeclaredField("metaRepository");
        field.setAccessible(true);
        field.set(service, metaRepository);
    }

    @SuppressWarnings("unchecked")
    private DashboardGestorDTO.MetaEquipeDTO invocarCalcularMetas(List<Usuario> equipe, LocalDateTime inicio) throws Exception {
        Method m = DashboardGestorService.class.getDeclaredMethod("calcularMetas", List.class, List.class, LocalDateTime.class);
        m.setAccessible(true);
        return (DashboardGestorDTO.MetaEquipeDTO) m.invoke(service, equipe, List.of(), inicio);
    }

    private Meta meta(Usuario u, LocalDate mesRef, Integer contratos, OrigemMeta origem) {
        Meta meta = new Meta();
        meta.setUsuario(u);
        meta.setMesReferencia(mesRef);
        meta.setMetaContratos(contratos);
        meta.setOrigem(origem);
        return meta;
    }

    @Test
    void expoeMetaGestorEMetaPropriaSeparadamenteQuandoAmbasExistem() throws Exception {
        Usuario u = new Usuario();
        u.setId(1L);
        u.setNome("Fulano");
        LocalDate mesRef = LocalDate.of(2026, 9, 1);
        when(metaRepository.findByUsuarioIdAndMesReferencia(1L, mesRef)).thenReturn(List.of(
                meta(u, mesRef, 1, OrigemMeta.CORRETOR),
                meta(u, mesRef, 5, OrigemMeta.GESTOR)
        ));

        DashboardGestorDTO.MetaEquipeDTO resultado = invocarCalcularMetas(List.of(u), LocalDateTime.of(2026, 9, 10, 0, 0));

        DashboardGestorDTO.MetaCorretorDTO linha = resultado.getPorCorretor().get(0);
        assertEquals(5, linha.getMetaGestor());
        assertEquals(1, linha.getMetaPropria());
        assertEquals(1, linha.getMetaContratos(), "meta efetiva = a própria, que tem prioridade sobre a do gestor");
    }

    @Test
    void expoeApenasMetaGestorQuandoCorretorNaoDefiniuAPropria() throws Exception {
        Usuario u = new Usuario();
        u.setId(2L);
        u.setNome("Ciclana");
        LocalDate mesRef = LocalDate.of(2026, 9, 1);
        when(metaRepository.findByUsuarioIdAndMesReferencia(2L, mesRef)).thenReturn(List.of(
                meta(u, mesRef, 7, OrigemMeta.GESTOR)
        ));

        DashboardGestorDTO.MetaEquipeDTO resultado = invocarCalcularMetas(List.of(u), LocalDateTime.of(2026, 9, 10, 0, 0));

        DashboardGestorDTO.MetaCorretorDTO linha = resultado.getPorCorretor().get(0);
        assertEquals(7, linha.getMetaGestor());
        assertNull(linha.getMetaPropria());
        assertEquals(7, linha.getMetaContratos(), "sem meta própria, a do gestor é a efetiva");
    }

    @Test
    void semNenhumaMetaAmbosOsCamposFicamNulos() throws Exception {
        Usuario u = new Usuario();
        u.setId(3L);
        u.setNome("Beltrano");
        LocalDate mesRef = LocalDate.of(2026, 9, 1);
        when(metaRepository.findByUsuarioIdAndMesReferencia(3L, mesRef)).thenReturn(List.of());

        DashboardGestorDTO.MetaEquipeDTO resultado = invocarCalcularMetas(List.of(u), LocalDateTime.of(2026, 9, 10, 0, 0));

        DashboardGestorDTO.MetaCorretorDTO linha = resultado.getPorCorretor().get(0);
        assertNull(linha.getMetaGestor());
        assertNull(linha.getMetaPropria());
        assertNull(linha.getMetaContratos());
        assertEquals("sem_meta", linha.getStatus());
    }
}
