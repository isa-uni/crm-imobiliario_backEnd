package crm_imobiliario.back.model.repository;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import crm_imobiliario.back.model.entity.EmpreendimentoCaracteristica;
@Repository
public interface EmpreendimentoCaracteristicaRepository extends JpaRepository<EmpreendimentoCaracteristica, Long> {
    Optional<EmpreendimentoCaracteristica> findByEmpreendimentoId(Long empreendimentoId);
}
