package crm_imobiliario.back.model.repository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import crm_imobiliario.back.model.entity.EmpreendimentoAreaComum;
@Repository
public interface EmpreendimentoAreaComumRepository extends JpaRepository<EmpreendimentoAreaComum, Long> {
    List<EmpreendimentoAreaComum> findByEmpreendimentoIdOrderByOrdemAsc(Long empreendimentoId);
}
