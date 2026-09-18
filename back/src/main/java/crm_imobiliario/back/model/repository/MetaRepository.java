package crm_imobiliario.back.model.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import crm_imobiliario.back.model.entity.Meta;
import crm_imobiliario.back.model.entity.OrigemMeta;

@Repository
public interface MetaRepository extends JpaRepository<Meta, Long> {
    List<Meta> findByUsuarioIdAndMesReferencia(Long usuarioId, LocalDate mesReferencia);
    Optional<Meta> findByUsuarioIdAndMesReferenciaAndOrigem(Long usuarioId, LocalDate mesReferencia, OrigemMeta origem);
    List<Meta> findByMesReferencia(LocalDate mesReferencia);
    List<Meta> findByUsuario_Gestor_IdAndMesReferencia(Long gestorId, LocalDate mesReferencia);
    List<Meta> findByUsuarioId(Long usuarioId);
}
