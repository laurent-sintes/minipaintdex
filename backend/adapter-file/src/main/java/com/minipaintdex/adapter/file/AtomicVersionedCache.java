package com.minipaintdex.adapter.file;

import com.minipaintdex.application.port.VersionedCache;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** In-process cache whose readers always observe one complete published generation. */
final class AtomicVersionedCache<T> implements VersionedCache<T> {
    private final String name;
    private final AtomicReference<VersionedValue<T>> current = new AtomicReference<>();

    AtomicVersionedCache(String name) {
        this.name = Objects.requireNonNull(name);
    }

    @Override
    public VersionedValue<T> current() {
        var value = current.get();
        if (value == null) {
            throw new FileStorageException("Persistence cache is not initialized: " + name, null);
        }
        return value;
    }

    @Override
    public void publish(long generation, T value) {
        current.set(new VersionedValue<>(generation, Objects.requireNonNull(value)));
    }

    @Override
    public void invalidate() {
        current.set(null);
    }
}
