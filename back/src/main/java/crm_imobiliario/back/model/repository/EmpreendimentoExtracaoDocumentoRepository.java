package crm_imobiliario.back.model.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import crm_imobiliario.back.model.entity.EmpreendimentoExtracaoDocumento;
import crm_imobiliario.back.model.entity.EmpreendimentoExtracaoDocumentoId;

public interface EmpreendimentoExtracaoDocumentoRepository extends JpaRepository<EmpreendimentoExtracaoDocumento, EmpreendimentoExtracaoDocumentoId> {
    List<EmpreendimentoExtracaoDocumento> findByExtracaoId(Long extracaoId);
    List<EmpreendimentoExtracaoDocumento> findByDocumentoId(Long documentoId);
}
