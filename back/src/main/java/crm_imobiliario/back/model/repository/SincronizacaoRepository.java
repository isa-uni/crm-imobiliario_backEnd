package crm_imobiliario.back.model.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import crm_imobiliario.back.model.entity.Sincronizacao;

@Repository
public interface SincronizacaoRepository extends JpaRepository<Sincronizacao, Long> {

    Optional<Sincronizacao> findTopByOrderByInicioDesc();
}
