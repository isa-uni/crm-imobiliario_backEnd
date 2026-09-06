package crm_imobiliario.back.model.service;

import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import crm_imobiliario.back.model.dto.MetaCreateDTO;
import crm_imobiliario.back.model.dto.MetaDTO;
import crm_imobiliario.back.model.entity.Meta;
import crm_imobiliario.back.model.entity.Usuario;
import crm_imobiliario.back.model.repository.MetaRepository;
import crm_imobiliario.back.model.repository.UsuarioRepository;

@Service
public class MetaService {

    @Autowired
    private MetaRepository metaRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;

    @Transactional
    public MetaDTO criarOuAtualizar(MetaCreateDTO dto) {
        Usuario usuario = usuarioRepository.findById(dto.getUsuarioId())
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));
        LocalDate ref = dto.getMesReferencia().withDayOfMonth(1);
        Meta meta = metaRepository.findByUsuarioIdAndMesReferencia(dto.getUsuarioId(), ref)
                .orElse(new Meta());
        meta.setUsuario(usuario);
        meta.setMesReferencia(ref);
        meta.setMetaContratos(dto.getMetaContratos());
        // dataCriacao/dataAtualizacao gerenciadas por @PrePersist/@PreUpdate
        Meta saved = metaRepository.save(meta);
        // garante que nova meta tenha datas preenchidas mesmo se save não disparou callback em testes
        return MetaDTO.from(saved);
    }

    @Transactional(readOnly = true)
    public MetaDTO buscarPorUsuarioEMes(Long usuarioId, LocalDate mesReferencia) {
        LocalDate ref = mesReferencia.withDayOfMonth(1);
        return metaRepository.findByUsuarioIdAndMesReferencia(usuarioId, ref)
                .map(MetaDTO::from)
                .orElse(null);
    }
}
