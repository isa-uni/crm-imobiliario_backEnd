package crm_imobiliario.back.model.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import crm_imobiliario.back.model.entity.EmpreendimentoDocumento;

@Repository
public interface EmpreendimentoDocumentoRepository extends JpaRepository<EmpreendimentoDocumento, Long> {
    List<EmpreendimentoDocumento> findByEmpreendimentoId(Long empreendimentoId);
    Optional<EmpreendimentoDocumento> findByHash(String hash);
    List<EmpreendimentoDocumento> findByStatusProcessamento(String status);
}
