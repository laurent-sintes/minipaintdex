package com.minipaintdex.domain.workshop;

import com.minipaintdex.domain.shared.DomainException;

public enum WorkflowStage {
    PREPARATION("preparation"),
    PRIMING("priming"),
    PRE_HIGHLIGHT("pre_highlight"),
    PAINTING("painting"),
    FINISHING("finishing"),
    BASING("basing");

    private final String id;

    WorkflowStage(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static WorkflowStage fromId(String id) {
        for (var stage : values()) if (stage.id.equals(id)) return stage;
        throw new DomainException("invalid_input", "Unknown workflow stage: " + id);
    }
}
