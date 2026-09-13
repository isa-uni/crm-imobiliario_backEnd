package crm_imobiliario.back.model.repository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import crm_imobiliario.back.model.entity.EmpreendimentoPontoReferencia;
@Repository
public interface EmpreendimentoPontoReferenciaRepository extends JpaRepository<EmpreendimentoPontoReferencia, Long> {
    List<EmpreendimentoPontoReferencia> findByEmpreendimentoIdOrderByOrdemAsc(Long empreendimentoId);
}
