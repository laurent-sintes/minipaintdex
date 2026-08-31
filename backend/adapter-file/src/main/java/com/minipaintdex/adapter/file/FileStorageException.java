package com.minipaintdex.adapter.file;

import com.minipaintdex.application.port.PersistenceException;

public final class FileStorageException extends PersistenceException {
    public FileStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
