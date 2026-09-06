package crm_imobiliario.back.model.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import crm_imobiliario.back.model.entity.LeadResponsavelHistorico;

@Repository
public interface LeadResponsavelHistoricoRepository extends JpaRepository<LeadResponsavelHistorico, Long> {
    List<LeadResponsavelHistorico> findByLeadIdOrderByDataInicioDesc(Long leadId);
    List<LeadResponsavelHistorico> findByLeadIdOrderByDataInicioAsc(Long leadId);
    List<LeadResponsavelHistorico> findByCorretorId(Long corretorId);
    List<LeadResponsavelHistorico> findByLeadIdInOrderByDataInicioAsc(java.util.Collection<Long> leadIds);
}
