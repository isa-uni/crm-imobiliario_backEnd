package crm_imobiliario.back.model.repository;


import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import crm_imobiliario.back.model.entity.Papel;

@Repository
public interface PapelRepository extends JpaRepository<Papel, Long> {

    List<Papel> findByAtivoTrueOrAtivoIsNull();
}
