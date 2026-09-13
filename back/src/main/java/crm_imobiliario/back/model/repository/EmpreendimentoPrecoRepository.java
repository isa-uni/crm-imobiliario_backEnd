package crm_imobiliario.back.model.repository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import crm_imobiliario.back.model.entity.EmpreendimentoPreco;
@Repository
public interface EmpreendimentoPrecoRepository extends JpaRepository<EmpreendimentoPreco, Long> {
    List<EmpreendimentoPreco> findByEmpreendimentoIdOrderByDataReferenciaDesc(Long empreendimentoId);
    List<EmpreendimentoPreco> findByEmpreendimentoId(Long empreendimentoId);
}
