package com.minipaintdex.adapter.file;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileMiniPaintDexRepositoryTest {
    @Test
    void loadsMigratedCatalogsAndPhysicalItems() {
        var repository = new FileMiniPaintDexRepository(Path.of("../..").toAbsolutePath().normalize());
        var snapshot = repository.load();

        assertEquals(47, snapshot.marketPaints().size());
        assertEquals(1, snapshot.games().size());
        assertEquals(199, snapshot.events().size());
    }
}
