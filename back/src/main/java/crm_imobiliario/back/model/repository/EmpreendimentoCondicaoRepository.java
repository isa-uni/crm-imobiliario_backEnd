package crm_imobiliario.back.model.repository;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import crm_imobiliario.back.model.entity.EmpreendimentoCondicaoComercial;
@Repository
public interface EmpreendimentoCondicaoRepository extends JpaRepository<EmpreendimentoCondicaoComercial, Long> {
    Optional<EmpreendimentoCondicaoComercial> findByEmpreendimentoId(Long empreendimentoId);
}
