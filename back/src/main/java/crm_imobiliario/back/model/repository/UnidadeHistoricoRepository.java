package crm_imobiliario.back.model.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import crm_imobiliario.back.model.entity.UnidadeHistorico;

@Repository
public interface UnidadeHistoricoRepository extends JpaRepository<UnidadeHistorico, Long> {
    List<UnidadeHistorico> findByUnidadeIdOrderByCriadoEmDesc(Long unidadeId);
}
