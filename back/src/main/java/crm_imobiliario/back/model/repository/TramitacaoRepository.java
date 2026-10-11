package crm_imobiliario.back.model.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import crm_imobiliario.back.model.entity.Tramitacao;

@Repository
public interface TramitacaoRepository extends JpaRepository<Tramitacao, Long> {

    List<Tramitacao> findByLeadIdOrderByDataMovimentacaoAsc(Long leadId);

    /** Tramitações de vários leads numa consulta só (evita N+1 no dashboard do gestor). */
    List<Tramitacao> findByLeadIdInOrderByDataMovimentacaoAsc(java.util.Collection<Long> leadIds);
}