package crm_imobiliario.back.model.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import crm_imobiliario.back.model.entity.RefreshToken;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByJti(String jti);
    List<RefreshToken> findByUsuarioId(Long usuarioId);

    // exclusões derivadas exigem transação; sem @Transactional a chamada falhava
    // (TransactionRequiredException) e os refresh tokens nunca eram apagados
    @Modifying
    @Transactional
    void deleteByUsuarioId(Long usuarioId);

    @Modifying
    @Transactional
    long deleteByExpiresAtBefore(Instant limite);
}
