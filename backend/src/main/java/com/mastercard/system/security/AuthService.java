package com.mastercard.system.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Login, rotación de refresh tokens y bloqueo por intentos fallidos. */
@Service
@Slf4j
public class AuthService {
    static final String BAD_CREDENTIALS = "Usuario o contraseña incorrectos";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AppUserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final int maxAttempts;
    private final Duration lockDuration;
    private final Duration refreshTtl;
    /** Hash de relleno para igualar el tiempo de respuesta cuando el usuario no existe. */
    private final String dummyHash;

    public AuthService(AppUserRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder encoder,
                       JwtService jwt,
                       @Value("${app.security.max-failed-attempts:5}") int maxAttempts,
                       @Value("${app.security.lock-minutes:15}") long lockMinutes,
                       @Value("${app.jwt.refresh-hours:8}") long refreshHours) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.encoder = encoder;
        this.jwt = jwt;
        this.maxAttempts = maxAttempts;
        this.lockDuration = Duration.ofMinutes(lockMinutes);
        this.refreshTtl = Duration.ofHours(refreshHours);
        this.dummyHash = encoder.encode("dummy-password-for-timing");
    }

    public record Session(String accessToken, long expiresIn, String refreshToken, Duration refreshMaxAge,
                          AppUser user) {}

    public Session login(String rawUsername, String password) {
        AppUser u = users.findByUsername(normalize(rawUsername)).orElse(null);
        if (u == null) {
            encoder.matches(password, dummyHash);
            throw new AuthException(HttpStatus.UNAUTHORIZED, BAD_CREDENTIALS);
        }
        if (u.isLocked()) {
            long min = Math.max(1, Duration.between(LocalDateTime.now(), u.getLockedUntil()).toMinutes() + 1);
            throw new AuthException(HttpStatus.LOCKED,
                    "Cuenta bloqueada temporalmente por intentos fallidos. Intente de nuevo en " + min + " minuto(s)");
        }
        boolean ok = encoder.matches(password, u.getPasswordHash());
        if (!ok || !u.isActive()) {
            if (!ok) registerFailure(u);
            throw new AuthException(HttpStatus.UNAUTHORIZED, BAD_CREDENTIALS);
        }
        if (u.getFailedAttempts() != 0 || u.getLockedUntil() != null) {
            u.setFailedAttempts(0);
            u.setLockedUntil(null);
            users.save(u);
        }
        log.info("Inicio de sesión: {}", u.getUsername());
        return newSession(u, LocalDateTime.now().plus(refreshTtl));
    }

    private void registerFailure(AppUser u) {
        int n = u.getFailedAttempts() + 1;
        if (n >= maxAttempts) {
            u.setFailedAttempts(0);
            u.setLockedUntil(LocalDateTime.now().plus(lockDuration));
            log.warn("Cuenta bloqueada por intentos fallidos: {}", u.getUsername());
        } else {
            u.setFailedAttempts(n);
        }
        users.save(u);
    }

    /** Rota el refresh token. La sesión conserva su vencimiento absoluto original. */
    @Transactional(noRollbackFor = AuthException.class)
    public Session refresh(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "Sesión no válida");
        }
        RefreshToken t = refreshTokens.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "Sesión no válida"));
        if (t.isRevoked()) {
            // Reutilización de un token ya rotado: se asume robo y se cierran todas las sesiones del usuario.
            refreshTokens.revokeAllByUser(t.getUserId());
            log.warn("Reutilización de refresh token detectada (usuario id {})", t.getUserId());
            throw new AuthException(HttpStatus.UNAUTHORIZED, "Sesión no válida");
        }
        AppUser u = users.findById(t.getUserId()).orElse(null);
        if (t.getExpiresAt().isBefore(LocalDateTime.now()) || u == null || !u.isActive()) {
            t.setRevoked(true);
            throw new AuthException(HttpStatus.UNAUTHORIZED, "Sesión expirada");
        }
        t.setRevoked(true);
        return newSession(u, t.getExpiresAt());
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return;
        refreshTokens.findByTokenHash(hash(rawToken)).ifPresent(t -> t.setRevoked(true));
    }

    @Transactional
    public void revokeAll(Long userId) {
        refreshTokens.revokeAllByUser(userId);
    }

    private Session newSession(AppUser u, LocalDateTime refreshExpires) {
        byte[] buf = new byte[32];
        RANDOM.nextBytes(buf);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
        RefreshToken rt = new RefreshToken();
        rt.setUserId(u.getId());
        rt.setTokenHash(hash(raw));
        rt.setExpiresAt(refreshExpires);
        refreshTokens.save(rt);
        Duration maxAge = Duration.between(LocalDateTime.now(), refreshExpires);
        return new Session(jwt.issue(u), jwt.accessTtlSeconds(), raw, maxAge, u);
    }

    static String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }

    static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
