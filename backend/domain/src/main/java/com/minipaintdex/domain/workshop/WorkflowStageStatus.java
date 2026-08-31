package com.minipaintdex.domain.workshop;

public enum WorkflowStageStatus {
    PENDING("pending"),
    IN_PROGRESS("in_progress"),
    COMPLETED("completed"),
    SKIPPED("skipped");

    private final String id;

    WorkflowStageStatus(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
