package com.minipaintdex.application.port;

/** Technology-neutral failure reported by a persistence output adapter. */
public class PersistenceException extends RuntimeException {
    public PersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
