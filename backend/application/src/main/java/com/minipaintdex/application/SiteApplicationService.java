package com.minipaintdex.application;

import com.minipaintdex.application.usecase.SiteQueries;
import com.minipaintdex.application.view.DashboardView;
import com.minipaintdex.application.view.SiteConfigurationView;

import java.util.Objects;

/** Cohesive application service for lightweight site configuration and counters. */
public final class SiteApplicationService implements SiteQueries {
    private final MiniPaintDexService kernel;

    public SiteApplicationService(MiniPaintDexService kernel) {
        this.kernel = Objects.requireNonNull(kernel);
    }

    @Override public SiteConfigurationView siteConfiguration() { return kernel.siteConfiguration(); }
    @Override public DashboardView dashboard() { return kernel.dashboard(); }
}
