package com.minipaintdex.domain.workflow;

public enum StageAction {
    START("start", "workflow.stage.started"),
    COMPLETE("complete", "workflow.stage.completed"),
    SKIP("skip", "workflow.stage.skipped"),
    REOPEN("reopen", "workflow.stage.reopened");

    private final String id;
    private final String eventType;

    StageAction(String id, String eventType) {
        this.id = id;
        this.eventType = eventType;
    }

    public String id() {
        return id;
    }

    public String eventType() {
        return eventType;
    }

    public static StageAction fromId(String id) {
        for (var action : values()) if (action.id.equals(id)) return action;
        throw new DomainException("invalid_input", "Unknown stage action: " + id);
    }
}
