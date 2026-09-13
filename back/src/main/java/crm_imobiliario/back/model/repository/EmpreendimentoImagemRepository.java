package crm_imobiliario.back.model.repository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import crm_imobiliario.back.model.entity.EmpreendimentoImagem;
@Repository
public interface EmpreendimentoImagemRepository extends JpaRepository<EmpreendimentoImagem, Long> {
    List<EmpreendimentoImagem> findByEmpreendimentoIdOrderByOrdemAsc(Long empreendimentoId);
}
