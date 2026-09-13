package crm_imobiliario.back.model.repository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import crm_imobiliario.back.model.entity.EmpreendimentoDiferencial;
@Repository
public interface EmpreendimentoDiferencialRepository extends JpaRepository<EmpreendimentoDiferencial, Long> {
    List<EmpreendimentoDiferencial> findByEmpreendimentoIdOrderByOrdemAsc(Long empreendimentoId);
}
