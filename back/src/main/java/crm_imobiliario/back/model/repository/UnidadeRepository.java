package crm_imobiliario.back.model.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import crm_imobiliario.back.model.entity.Unidade;

@Repository
public interface UnidadeRepository extends JpaRepository<Unidade, Long> {

    List<Unidade> findByEmpreendimentoId(Long empreendimentoId);

    List<Unidade> findByEmpreendimentoIdAndSituacao(Long empreendimentoId, String situacao);

    void deleteByEmpreendimentoId(Long empreendimentoId);

    long countByEmpreendimentoIdAndSituacao(Long empreendimentoId, String situacao);
}
