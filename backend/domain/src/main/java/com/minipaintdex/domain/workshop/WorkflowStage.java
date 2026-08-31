package com.minipaintdex.domain.workshop;

import com.minipaintdex.domain.shared.DomainException;

public enum WorkflowStage {
    PREPARATION("preparation", false),
    PRIMING("priming", false),
    PRE_HIGHLIGHT("pre_highlight", true),
    PAINTING("painting", false),
    FINISHING("finishing", true),
    BASING("basing", true);

    private final String id;
    private final boolean skippable;

    WorkflowStage(String id, boolean skippable) {
        this.id = id;
        this.skippable = skippable;
    }

    public String id() {
        return id;
    }

    public boolean skippable() {
        return skippable;
    }

    public static WorkflowStage fromId(String id) {
        for (var stage : values()) if (stage.id.equals(id)) return stage;
        throw new DomainException("invalid_input", "Unknown workflow stage: " + id);
    }
}
