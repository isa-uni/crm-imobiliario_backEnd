package crm_imobiliario.back.model.repository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import crm_imobiliario.back.model.entity.EmpreendimentoHistorico;
@Repository
public interface EmpreendimentoHistoricoRepository extends JpaRepository<EmpreendimentoHistorico, Long> {
    List<EmpreendimentoHistorico> findByEmpreendimentoIdOrderByCriadoEmDesc(Long empreendimentoId);
}
