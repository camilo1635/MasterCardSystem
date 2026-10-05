package com.mastercard.system.security;

import com.mastercard.system.common.BusinessException;
import com.mastercard.system.common.NotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Gestión de usuarios: solo ADMIN. Nunca expone el hash de la contraseña. */
@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class UserController {
    static final int MIN_PASSWORD = 10;

    private final AppUserRepository users;
    private final AuthService auth;
    private final PasswordEncoder encoder;

    public record UserDto(Long id, String username, Role role, boolean active, boolean locked,
                          LocalDateTime createdAt) {
        static UserDto of(AppUser u) {
            return new UserDto(u.getId(), u.getUsername(), u.getRole(), u.isActive(), u.isLocked(), u.getCreatedAt());
        }
    }

    public record CreateUserRequest(
            @NotBlank @Size(min = 3, max = 50) @Pattern(regexp = "[A-Za-z0-9._-]+",
                    message = "solo letras, números, punto, guion y guion bajo") String username,
            @NotBlank @Size(min = MIN_PASSWORD, max = 72, message = "debe tener entre 10 y 72 caracteres") String password,
            @NotNull Role role) {}

    /** password opcional: si viene, restablece la contraseña. */
    public record UpdateUserRequest(
            @NotNull Role role,
            boolean active,
            @Size(min = MIN_PASSWORD, max = 72, message = "debe tener entre 10 y 72 caracteres") String password) {}

    @GetMapping
    public List<UserDto> list() {
        return users.findAllByOrderByUsernameAsc().stream().map(UserDto::of).toList();
    }

    @GetMapping("/{id}")
    public UserDto get(@PathVariable Long id) {
        return UserDto.of(find(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto create(@Valid @RequestBody CreateUserRequest r, Authentication who) {
        String username = AuthService.normalize(r.username());
        if (users.existsByUsername(username)) throw new BusinessException("El usuario ya existe");
        AppUser u = new AppUser();
        u.setUsername(username);
        u.setPasswordHash(encoder.encode(r.password()));
        u.setRole(r.role());
        AppUser saved = users.save(u);
        log.info("Usuario {} creado por {}", username, who.getName());
        return UserDto.of(users.findById(saved.getId()).orElse(saved));
    }

    @PutMapping("/{id}")
    @Transactional
    public UserDto update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest r, Authentication who) {
        AppUser u = find(id);
        boolean self = u.getUsername().equals(who.getName());
        if (self && (!r.active() || r.role() != u.getRole())) {
            throw new BusinessException("No puede desactivarse ni cambiar su propio rol");
        }
        boolean losesAdmin = u.getRole() == Role.ADMIN && u.isActive() && (!r.active() || r.role() != Role.ADMIN);
        if (losesAdmin && users.countByRoleAndActiveTrue(Role.ADMIN) <= 1) {
            throw new BusinessException("Debe existir al menos un administrador activo");
        }
        boolean closeSessions = !r.active() || r.role() != u.getRole() || r.password() != null;
        u.setRole(r.role());
        u.setActive(r.active());
        if (r.password() != null) u.setPasswordHash(encoder.encode(r.password()));
        u.setFailedAttempts(0);
        u.setLockedUntil(null);
        users.save(u);
        if (closeSessions) auth.revokeAll(u.getId());
        log.info("Usuario {} actualizado por {}", u.getUsername(), who.getName());
        return UserDto.of(u);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication who) {
        AppUser u = find(id);
        if (u.getUsername().equals(who.getName())) throw new BusinessException("No puede eliminar su propio usuario");
        if (u.getRole() == Role.ADMIN && u.isActive() && users.countByRoleAndActiveTrue(Role.ADMIN) <= 1) {
            throw new BusinessException("Debe existir al menos un administrador activo");
        }
        users.delete(u);
        log.info("Usuario {} eliminado por {}", u.getUsername(), who.getName());
    }

    private AppUser find(Long id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("Usuario", id));
    }
}
