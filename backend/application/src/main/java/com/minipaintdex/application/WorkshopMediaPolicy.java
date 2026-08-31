package com.minipaintdex.application;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public record WorkshopMediaPolicy(long maxUploadBytes, Set<String> allowedContentTypes) {
    public WorkshopMediaPolicy {
        if (maxUploadBytes < 1) throw new IllegalArgumentException("maxUploadBytes must be positive");
        if (allowedContentTypes == null || allowedContentTypes.isEmpty()) {
            throw new IllegalArgumentException("allowedContentTypes must not be empty");
        }
        allowedContentTypes = allowedContentTypes.stream().map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }
}
