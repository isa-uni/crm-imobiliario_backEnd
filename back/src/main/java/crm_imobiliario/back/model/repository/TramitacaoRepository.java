package crm_imobiliario.back.model.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import crm_imobiliario.back.model.entity.Tramitacao;

@Repository
public interface TramitacaoRepository extends JpaRepository<Tramitacao, Long> {

    List<Tramitacao> findByLeadIdOrderByDataMovimentacaoAsc(Long leadId);
}