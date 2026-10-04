package com.mastercard.system.common;

public class NotFoundException extends RuntimeException {
    public NotFoundException(String what, Object id) {
        super(what + " no encontrado: " + id);
    }
}
