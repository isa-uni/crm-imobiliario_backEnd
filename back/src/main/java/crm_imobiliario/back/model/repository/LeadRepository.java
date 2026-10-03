package crm_imobiliario.back.model.repository;


import java.util.List;
import java.util.Optional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import crm_imobiliario.back.model.entity.Lead;

@Repository
public interface LeadRepository extends JpaRepository<Lead, Long>, JpaSpecificationExecutor<Lead>{
    Optional<Lead> findByEmail(String email);

    List<Lead> findByAtivoTrue();
    /** Leads criados no intervalo (inclusivo) — o dashboard do gestor filtra no banco em vez de carregar a base inteira. */
    List<Lead> findByDataCriacaoBetween(LocalDateTime inicio, LocalDateTime fim);
    List<Lead> findByCorretorId(Long corretorId);
    org.springframework.data.domain.Page<Lead> findByCorretorId(Long corretorId, org.springframework.data.domain.Pageable pageable);
    List<Lead> findByEquipeId(Long equipeId);
    org.springframework.data.domain.Page<Lead> findByEquipeId(Long equipeId, org.springframework.data.domain.Pageable pageable);
    List<Lead> findByStatusAtribuicao(String statusAtribuicao);
    org.springframework.data.domain.Page<Lead> findByStatusAtribuicao(String statusAtribuicao, org.springframework.data.domain.Pageable pageable);
    org.springframework.data.domain.Page<Lead> findByStatusAtribuicaoAndEquipeId(String statusAtribuicao, Long equipeId, org.springframework.data.domain.Pageable pageable);

    /** Leads aguardando redistribuição da equipe informada ou sob responsabilidade do usuário informado. */
    @Query("select l from lead l left join l.equipe e left join l.responsavelRedistribuicao r "
            + "where l.statusAtribuicao = 'AGUARDANDO_REDISTRIBUICAO' and (e.id = :equipeId or r.id = :usuarioId)")
    org.springframework.data.domain.Page<Lead> findAguardandoDaEquipeOuDoResponsavel(@org.springframework.data.repository.query.Param("equipeId") Long equipeId,
            @org.springframework.data.repository.query.Param("usuarioId") Long usuarioId, org.springframework.data.domain.Pageable pageable);

    @Query(value = "SELECT TO_CHAR(l.data_criacao, 'YYYY-MM') as mes, COUNT(*) FROM lead l WHERE l.corretor_id = :corretorId AND l.data_criacao >= :inicio AND l.data_criacao <= :fim GROUP BY mes ORDER BY mes", nativeQuery = true)
    List<Object[]> contarLeadsPorMes(@Param("corretorId") Long corretorId, @Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);
}
