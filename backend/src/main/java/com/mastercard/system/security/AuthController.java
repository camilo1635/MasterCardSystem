package com.mastercard.system.security;

import com.mastercard.system.security.AuthService.Session;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private static final String COOKIE_PATH = "/api/auth";

    private final AuthService auth;
    private final AppUserRepository users;

    @Value("${app.cookie.secure:true}")
    private boolean cookieSecure;

    @Value("${app.cookie.name:refresh_token}")
    private String cookieName;

    public record LoginRequest(@NotBlank @Size(max = 50) String username,
                               @NotBlank @Size(max = 100) String password) {}

    public record UserInfo(Long id, String username, Role role) {}

    public record TokenResponse(String accessToken, String tokenType, long expiresIn, UserInfo user) {}

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest r) {
        return respond(auth.login(r.username(), r.password()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(
            @CookieValue(name = "${app.cookie.name:refresh_token}", required = false) String token) {
        return respond(auth.refresh(token));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "${app.cookie.name:refresh_token}", required = false) String token) {
        auth.logout(token);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie("", 0).toString()).build();
    }

    @GetMapping("/me")
    public UserInfo me(Authentication authentication) {
        AppUser u = users.findByUsername(authentication.getName())
                .filter(AppUser::isActive)
                .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "Sesión no válida"));
        return new UserInfo(u.getId(), u.getUsername(), u.getRole());
    }

    private ResponseEntity<TokenResponse> respond(Session s) {
        UserInfo info = new UserInfo(s.user().getId(), s.user().getUsername(), s.user().getRole());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie(s.refreshToken(), s.refreshMaxAge().toSeconds()).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new TokenResponse(s.accessToken(), "Bearer", s.expiresIn(), info));
    }

    private ResponseCookie cookie(String value, long maxAgeSeconds) {
        return ResponseCookie.from(cookieName, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(maxAgeSeconds)
                .build();
    }
}
