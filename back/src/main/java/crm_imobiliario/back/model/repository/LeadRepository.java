package crm_imobiliario.back.model.repository;


import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import crm_imobiliario.back.model.entity.Lead;

@Repository
public interface LeadRepository extends JpaRepository<Lead, Long>{
    Optional<Lead> findByEmail(String email);

    List<Lead> findByAtivoTrue();
}
