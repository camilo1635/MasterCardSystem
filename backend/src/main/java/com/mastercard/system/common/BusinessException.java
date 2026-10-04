package com.mastercard.system.common;

/** Error de regla de negocio (HTTP 400). */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
