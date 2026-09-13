package crm_imobiliario.back.model.repository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import crm_imobiliario.back.model.entity.EmpreendimentoPlanta;
@Repository
public interface EmpreendimentoPlantaRepository extends JpaRepository<EmpreendimentoPlanta, Long> {
    List<EmpreendimentoPlanta> findByEmpreendimentoIdOrderByOrdemAsc(Long empreendimentoId);
}
