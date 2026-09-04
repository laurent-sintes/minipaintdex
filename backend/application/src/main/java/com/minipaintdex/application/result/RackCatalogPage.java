package com.minipaintdex.application.result;
public record RackCatalogPage<T>(PageResult<T> results, long catalogRevision, String correlationId) { }
