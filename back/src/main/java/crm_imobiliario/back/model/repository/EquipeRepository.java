package crm_imobiliario.back.model.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import crm_imobiliario.back.model.entity.Equipe;

@Repository
public interface EquipeRepository extends JpaRepository<Equipe, Long> {
    Optional<Equipe> findByNome(String nome);
    List<Equipe> findByAtivoTrue();
    Optional<Equipe> findByGestorId(Long gestorId);
}
