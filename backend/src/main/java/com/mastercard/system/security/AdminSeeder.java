package com.mastercard.system.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Siembra el primer ADMIN desde ADMIN_USERNAME / ADMIN_PASSWORD si aún no hay usuarios. */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminSeeder implements ApplicationRunner {
    private final AppUserRepository users;
    private final PasswordEncoder encoder;

    @Value("${app.admin.username:}")
    private String username;

    @Value("${app.admin.password:}")
    private String password;

    @Override
    public void run(ApplicationArguments args) {
        if (users.count() > 0) return;
        if (username.isBlank() || password.isBlank()) {
            log.warn("No hay usuarios y ADMIN_USERNAME/ADMIN_PASSWORD no están definidos: nadie podrá iniciar sesión.");
            return;
        }
        if (password.length() < UserController.MIN_PASSWORD || password.length() > 72) {
            throw new IllegalStateException(
                    "ADMIN_PASSWORD debe tener entre " + UserController.MIN_PASSWORD + " y 72 caracteres");
        }
        AppUser u = new AppUser();
        u.setUsername(AuthService.normalize(username));
        u.setPasswordHash(encoder.encode(password));
        u.setRole(Role.ADMIN);
        users.save(u);
        log.info("Usuario administrador inicial '{}' creado", u.getUsername());
    }
}
