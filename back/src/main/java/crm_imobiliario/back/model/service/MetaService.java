package crm_imobiliario.back.model.service;

import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import crm_imobiliario.back.model.dto.MetaCreateDTO;
import crm_imobiliario.back.model.dto.MetaDTO;
import crm_imobiliario.back.model.dto.MetaResumoDTO;
import crm_imobiliario.back.model.entity.Meta;
import crm_imobiliario.back.model.entity.OrigemMeta;
import crm_imobiliario.back.model.entity.Usuario;
import crm_imobiliario.back.model.repository.MetaRepository;
import crm_imobiliario.back.model.repository.UsuarioRepository;
import crm_imobiliario.back.util.RecursoNaoEncontradoException;

@Service
public class MetaService {

    @Autowired
    private MetaRepository metaRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;

    /**
     * Quem pode definir a meta de quem: corretor (e qualquer papel) define a própria; gestor também a
     * dos seus subordinados diretos (usuario.gestor_id); admin a de qualquer usuário.
     * Usada pelos dois endpoints de escrita (/metas e /dashboard/gestor/metas).
     */
    public void validarPermissao(Usuario solicitante, Long usuarioAlvoId) {
        if (solicitante.getId().equals(usuarioAlvoId)) return;
        String papel = solicitante.getPapel() != null ? solicitante.getPapel().getPapel() : "";
        if ("admin".equals(papel)) return;
        if ("gestor".equals(papel)) {
            Usuario alvo = usuarioRepository.findById(usuarioAlvoId)
                    .orElseThrow(() -> new RecursoNaoEncontradoException("O usuário selecionado para a meta não foi encontrado."));
            if (alvo.getGestor() != null && alvo.getGestor().getId().equals(solicitante.getId())) return;
            throw new AccessDeniedException("Você só pode definir metas para os corretores que você lidera.");
        }
        throw new AccessDeniedException("Você só pode definir a sua própria meta.");
    }

    @Transactional
    public MetaDTO criarOuAtualizar(MetaCreateDTO dto, OrigemMeta origem) {
        Usuario usuario = usuarioRepository.findById(dto.getUsuarioId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("O usuário selecionado para a meta não foi encontrado."));
        LocalDate ref = dto.getMesReferencia().withDayOfMonth(1);
        Meta meta = metaRepository.findByUsuarioIdAndMesReferenciaAndOrigem(dto.getUsuarioId(), ref, origem)
                .orElse(new Meta());
        meta.setUsuario(usuario);
        meta.setMesReferencia(ref);
        meta.setMetaContratos(dto.getMetaContratos());
        meta.setOrigem(origem);
        // dataCriacao/dataAtualizacao gerenciadas por @PrePersist/@PreUpdate
        Meta saved = metaRepository.save(meta);
        // garante que nova meta tenha datas preenchidas mesmo se save não disparou callback em testes
        return MetaDTO.from(saved);
    }

    /**
     * Meta própria do corretor e meta atribuída pelo gestor, lado a lado, mais a que efetivamente
     * vale para os cálculos: a própria quando o corretor a definiu, senão a do gestor.
     */
    @Transactional(readOnly = true)
    public MetaResumoDTO buscarResumo(Long usuarioId, LocalDate mesReferencia) {
        LocalDate ref = mesReferencia.withDayOfMonth(1);
        MetaDTO propria = metaRepository.findByUsuarioIdAndMesReferenciaAndOrigem(usuarioId, ref, OrigemMeta.CORRETOR)
                .map(MetaDTO::from).orElse(null);
        MetaDTO gestor = metaRepository.findByUsuarioIdAndMesReferenciaAndOrigem(usuarioId, ref, OrigemMeta.GESTOR)
                .map(MetaDTO::from).orElse(null);
        MetaDTO efetiva = propria != null ? propria : gestor;
        return new MetaResumoDTO(propria, gestor, efetiva);
    }
}
