package crm_imobiliario.back.model.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import crm_imobiliario.back.model.entity.EmpreendimentoDocumento;

@Repository
public interface EmpreendimentoDocumentoRepository extends JpaRepository<EmpreendimentoDocumento, Long> {
    List<EmpreendimentoDocumento> findByEmpreendimentoId(Long empreendimentoId);
}
