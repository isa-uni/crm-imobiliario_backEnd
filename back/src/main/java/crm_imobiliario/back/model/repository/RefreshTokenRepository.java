package crm_imobiliario.back.model.repository;

import java.util.Optional;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import crm_imobiliario.back.model.entity.RefreshToken;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByJti(String jti);
    void deleteByJti(String jti);
    List<RefreshToken> findByUsuarioId(Long usuarioId);
    void deleteByUsuarioId(Long usuarioId);
}
