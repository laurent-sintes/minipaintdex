export type SiteConfig = {
  metadata: { title: string; shortTitle: string; description: string };
  brand: { name: string; subtitle: string };
  units: { colorSingular: string; colorPlural: string; paintSingular: string; paintPlural: string };
  navigation: {
    ariaLabel: string; mobileAriaLabel: string; home: string; marketSection: string;
    marketPaints: string; marketPaintableProducts: string; workshopSection: string;
    workshopPaints: string; workshopAdmin: string; shopping: string; about: string;
  };
  header: { searchPlaceholder: string; searchShortPlaceholder: string; searchAriaLabel: string; workshopPrefix: string };
  home: {
    eyebrow: string; title: string; description: string; servicesTitle: string; servicesDescription: string;
    marketPaints: ServiceLabel; marketPaintableProducts: ServiceLabel;
    workshopPaints: ServiceLabel; workshopAdmin: ServiceLabel; shopping: ServiceLabel;
  };
  workflow: Record<string, string>;
  market: {
    paintsTitle: string; paintsDescription: string; paintsMetric: string; brandsMetric: string;
    paintableProductsTitle: string; paintableProductsDescription: string; catalogItems: string;
    paintableItems: string; viewProduct: string; inWorkshop: string; source: string;
    kindLabels: Record<string, string>;
  };
  workshop: {
    title: string; description: string; projects: string; items: string; completed: string;
    inProgress: string; pending: string; progress: string; missingPaints: string;
    noMissingPaints: string; pendingPaintSlots: string; recentActivity: string; manageProduct: string;
    emptyTitle: string; emptyDescription: string; eventLabels: Record<string, string>;
    itemDetail: string; backToProduct: string; startStage: string; completeStage: string; reopenStage: string;
    addComment: string; commentPlaceholder: string; noActivity: string; saving: string;
    addPhoto: string; photoCaption: string; noPhotos: string;
  };
  about: {
    eyebrow: string; title: string; description: string; userTitle: string; administratorTitle: string;
    versionAction: string; versionTitle: string; versionLabel: string; authorLabel: string; close: string;
    loading: string; documentTitles: Record<string, string>;
  };
  productDetail: {
    back: string; reference: string; contents: string; paintingSheets: string; sources: string;
    assemblyRequired: string; paintGuide: string; preparation: string; painting: string;
    importPreview: string; importDescription: string; requiredPaints: string; missingPaints: string;
    pendingSlots: string; importAction: string; importing: string; alreadyImported: string;
    importSuccess: string; openWorkshop: string; noLicensedImage: string; externalReferences: string;
    paintAvailable: string; paintMissing: string; paintPending: string;
  };
  collection: {
    title: string; description: string; allBrands: string; manufacturerFilter: string; allManufacturers: string;
    potsMetric: string; colorsMetric: string; sheetsMetric: string; resultsTitle: string; allPaintsTitle: string;
    emptyTitle: string; emptyHint: string; manufacturerSheet: string; filters: string; filterPanelTitle: string;
    filterPanelDescription: string; filterAriaLabel: string; searchFilter: string; typeFilter: string;
    allTypes: string; colorFilter: string; allColors: string; brandFilter: string; rangeFilter: string;
    allRanges: string; finishFilter: string; allFinishes: string; mediumFilter: string; allMediums: string;
    opacityFilter: string; allOpacities: string; lifecycleFilter: string; allLifecycles: string;
    volumeFilter: string; allVolumes: string; tagFilter: string; allTags: string;
    manufacturerSheetOnly: string; realResultOnly: string; resetFilters: string; activeFilters: string;
    removeFilter: string; noActiveFilter: string; loadMore: string;
  };
  shopping: {
    title: string; description: string; ready: string; remainingSuffix: string;
    priorities: { high: string; medium: string; low: string };
    requiredTitle: string; plannedTitle: string; requiredBy: string; derivedHint: string; plannedHint: string;
  };
  paintDetail: {
    sheet: string; close: string; colorFamily: string; toQualify: string; manufacturerFeatures: string;
    noManufacturerDescription: string; recommendedUses: string; usageInstructions: string; usageSteps: string;
    usageTips: string; openSheet: string; openManufacturerSheet: string; verifiedOn: string;
    productVisual: string; appliedResult: string; digitalPreview: string; digitalPreviewHelp: string;
    noProductVisual: string; realResultSource: string; referenceLabel: string; volumeLabel: string;
    instructionsReviewRequired: string;
  };
  errors: { productNotFound: string; catalogItemNotFound: string; paintNotFound: string; requestFailed: string };
};

type ServiceLabel = { title: string; description: string; action: string };
