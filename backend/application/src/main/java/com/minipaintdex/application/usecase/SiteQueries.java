package com.minipaintdex.application.usecase;

import com.minipaintdex.application.view.DashboardView;
import com.minipaintdex.application.view.SiteConfigurationView;

/** Read use cases for application configuration and the workshop dashboard. */
public interface SiteQueries {
    /** Returns localized, file-backed presentation configuration without exposing a path. */
    SiteConfigurationView siteConfiguration();
    /** Returns lightweight market and workshop counters for the home page. */
    DashboardView dashboard();
}
