package crm_imobiliario.back.model.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import crm_imobiliario.back.model.entity.Empreendimento;

@Repository
public interface EmpreendimentoRepository extends JpaRepository<Empreendimento, Long> {

    Optional<Empreendimento> findBySlug(String slug);

    Optional<Empreendimento> findByCodigoCrm(String codigoCrm);

    List<Empreendimento> findByAtivoTrue();

    List<Empreendimento> findByAtivoTrueAndDisponiveisGreaterThan(int disponiveis);

    @Query("SELECT e FROM Empreendimento e WHERE e.ativo = true " +
           "AND (:cidade IS NULL OR LOWER(e.cidade) = LOWER(:cidade)) " +
           "AND (:regiao IS NULL OR LOWER(e.regiao) = LOWER(:regiao)) " +
           "AND (:disponiveis IS NULL OR :disponiveis = false OR e.disponiveis > 0) " +
           "AND (:precoMin IS NULL OR e.precoMin >= :precoMin) " +
           "AND (:precoMax IS NULL OR e.precoMax <= :precoMax)")
    Page<Empreendimento> buscarComFiltros(String cidade, String regiao, Boolean disponiveis,
                                          Long precoMin, Long precoMax, Pageable pageable);

    long countByAtivoTrueAndDisponiveisGreaterThan(int disponiveis);
}
