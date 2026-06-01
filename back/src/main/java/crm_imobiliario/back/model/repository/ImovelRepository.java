package crm_imobiliario.back.model.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import crm_imobiliario.back.model.entity.Imovel;
import java.util.List;


@Repository
public interface ImovelRepository extends JpaRepository<Imovel, Long> {
    // List<Imovel> findByStatus(String status);
    List<Imovel> findByAtivoTrue();
    List<Imovel> findByStatusAndAtivoTrue(String status);
}

