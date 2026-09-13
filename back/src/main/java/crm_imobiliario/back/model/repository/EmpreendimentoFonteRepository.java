package crm_imobiliario.back.model.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import crm_imobiliario.back.model.entity.EmpreendimentoFonte;

@Repository
public interface EmpreendimentoFonteRepository extends JpaRepository<EmpreendimentoFonte, Long> {
    List<EmpreendimentoFonte> findByExtracaoId(Long extracaoId);
    List<EmpreendimentoFonte> findByEmpreendimentoId(Long empreendimentoId);
    List<EmpreendimentoFonte> findByCampo(String campo);
    List<EmpreendimentoFonte> findByExtracaoIdAndCampo(Long extracaoId, String campo);
}
