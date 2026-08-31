package com.minipaintdex.domain.workshop;

import com.minipaintdex.domain.event.DomainEvent;

public sealed interface WorkshopRecipeEvent extends DomainEvent permits WorkshopRecipeCreated,
        WorkshopRecipeValidated, WorkshopRecipeActivated, WorkshopRecipeSuperseded, WorkshopRecipeArchived {
    @Override default String aggregateType() { return "workshop_recipe"; }
}
