package com.mastercard.system.security;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Error de autenticación con su código HTTP (401 credenciales/sesión, 423 cuenta bloqueada). */
@Getter
public class AuthException extends RuntimeException {
    private final HttpStatus status;

    public AuthException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}
