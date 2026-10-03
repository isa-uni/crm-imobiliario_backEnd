package crm_imobiliario.back.model.service;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import crm_imobiliario.back.model.entity.RefreshToken;
import crm_imobiliario.back.model.entity.TokenBlacklist;
import crm_imobiliario.back.model.entity.Usuario;
import crm_imobiliario.back.model.repository.RefreshTokenRepository;
import crm_imobiliario.back.model.repository.TokenBlacklistRepository;
import io.jsonwebtoken.Claims;

/**
 * Emissão e revogação de sessões (par access/refresh JWT). Centraliza o que antes estava repetido
 * em AutenticacaoController, AuthController e UsuarioController.
 */
@Service
public class SessaoService {

    private static final Logger log = LoggerFactory.getLogger(SessaoService.class);

    private final TokenService tokenService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenBlacklistRepository blacklistRepository;

    public SessaoService(TokenService tokenService, RefreshTokenRepository refreshTokenRepository,
                         TokenBlacklistRepository blacklistRepository) {
        this.tokenService = tokenService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.blacklistRepository = blacklistRepository;
    }

    /** Gera um par novo de tokens e persiste o refresh (para poder revogá-lo depois). */
    public TokenService.TokenPair emitir(Usuario usuario) {
        var pair = tokenService.gerarTokens(usuario);
        RefreshToken rt = new RefreshToken();
        rt.setUsuario(usuario);
        rt.setJti(pair.refreshJti());
        rt.setExpiresAt(Instant.now().plusMillis(tokenService.getRefreshExpiration()));
        refreshTokenRepository.save(rt);
        return pair;
    }

    /** Coloca o access token na blacklist até a expiração natural dele. */
    public void revogarAccessToken(String accessToken) {
        if (accessToken == null) return;
        Claims claims = tokenService.parseClaims(accessToken);
        if (claims == null || claims.getId() == null) return;
        if (blacklistRepository.existsByJti(claims.getId())) return;
        TokenBlacklist bl = new TokenBlacklist();
        bl.setJti(claims.getId());
        bl.setExpiresAt(claims.getExpiration().toInstant());
        blacklistRepository.save(bl);
    }

    /** Marca um refresh token específico como revogado. */
    public void revogarRefreshToken(String refreshToken) {
        if (refreshToken == null) return;
        String jti = tokenService.getJti(refreshToken);
        if (jti == null) return;
        refreshTokenRepository.findByJti(jti).ifPresent(rt -> {
            rt.setRevoked(true);
            refreshTokenRepository.save(rt);
        });
    }

    /** Apaga todos os refresh tokens do usuário (força novo login em todos os dispositivos). */
    public void revogarRefreshTokensDoUsuario(Long usuarioId) {
        refreshTokenRepository.deleteByUsuarioId(usuarioId);
    }

    /** Remove diariamente registros de tokens já expirados — sem isso as duas tabelas só cresciam. */
    @Scheduled(cron = "${app.security.limpeza-tokens-cron:0 0 3 * * *}")
    public void limparTokensExpirados() {
        Instant agora = Instant.now();
        long bl = blacklistRepository.deleteByExpiresAtBefore(agora);
        long rt = refreshTokenRepository.deleteByExpiresAtBefore(agora);
        log.info("Limpeza de tokens expirados: {} da blacklist, {} refresh tokens", bl, rt);
    }
}
