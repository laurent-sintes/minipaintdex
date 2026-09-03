package com.minipaintdex.application.result;

import com.minipaintdex.application.event.PublicationReceipt;

public record ImportPaintPotsResult(int added, int existing, boolean applied, PublicationReceipt publication) {}
