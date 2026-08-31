package com.minipaintdex.application.view;

/** Paint required by a guide but absent from the owner's workshop inventory. */
public record MissingPaintView(String id, String name, String brand, String reference) {}
