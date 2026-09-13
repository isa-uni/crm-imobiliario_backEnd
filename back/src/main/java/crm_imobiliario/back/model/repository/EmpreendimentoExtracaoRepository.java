package crm_imobiliario.back.model.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import crm_imobiliario.back.model.entity.EmpreendimentoExtracao;

@Repository
public interface EmpreendimentoExtracaoRepository extends JpaRepository<EmpreendimentoExtracao, Long> {
    List<EmpreendimentoExtracao> findByEmpreendimentoId(Long empreendimentoId);
    Optional<EmpreendimentoExtracao> findTopByEmpreendimentoIdOrderByCriadoEmDesc(Long empreendimentoId);
    List<EmpreendimentoExtracao> findByStatus(String status);
}
