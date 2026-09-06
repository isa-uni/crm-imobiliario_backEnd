package crm_imobiliario.back.model.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import crm_imobiliario.back.model.entity.Meta;

@Repository
public interface MetaRepository extends JpaRepository<Meta, Long> {
    Optional<Meta> findByUsuarioIdAndMesReferencia(Long usuarioId, LocalDate mesReferencia);
    List<Meta> findByMesReferencia(LocalDate mesReferencia);
    List<Meta> findByUsuario_Gestor_IdAndMesReferencia(Long gestorId, LocalDate mesReferencia);
    List<Meta> findByUsuarioId(Long usuarioId);
}
