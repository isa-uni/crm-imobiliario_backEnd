package crm_imobiliario.back.model.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import crm_imobiliario.back.model.entity.Tramitacao;

@Repository
public interface TramitacaoRepository extends JpaRepository<Tramitacao, Long> {

    List<Tramitacao> findByLeadIdOrderByDataMovimentacaoAsc(Long leadId);

    @Query(value = "SELECT TO_CHAR(t.data_movimentacao, 'YYYY-MM') as mes, COUNT(DISTINCT t.lead_id) FROM tramitacao_status t JOIN lead l ON l.id = t.lead_id WHERE l.corretor_id = :corretorId AND t.status_atual = 'contrato' AND t.data_movimentacao >= :inicio AND t.data_movimentacao <= :fim GROUP BY mes ORDER BY mes", nativeQuery = true)
    List<Object[]> contarContratosPorMes(@Param("corretorId") Long corretorId, @Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);
}