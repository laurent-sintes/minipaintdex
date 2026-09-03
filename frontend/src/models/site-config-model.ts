export type SiteConfig = {
  paintPots: {
    title: string; description: string; add: string; all: string; back: string; empty: string;
    includeRemoved: string; personalPhoto: string; catalogPhoto: string; noPhoto: string;
    condition: string; remaining: string; possession: string; available: string; unavailable: string; availableCount: string;
    observationHelp: string; save: string; opened: string; acquired: string; unknown: string; open: string;
    notes: string; addNote: string; photos: string; addPhoto: string; caption: string; addHelp: string;
    photoHelp: string; removeBackground: string; originalPhoto: string; cutoutPhoto: string; processingPhoto: string;
    previewFailed: string; previewUnavailable: string; photoNeedsPot: string; choosePot: string;
    saved: string; pending: string; failed: string; previous: string; next: string;
    conditions: Record<string,string>; levels: Record<string,string>; possessions: Record<string,string>;
  };
  metadata: { title: string; shortTitle: string; description: string };
  brand: { name: string; subtitle: string };
  units: { colorSingular: string; colorPlural: string; paintSingular: string; paintPlural: string };
  navigation: {
    ariaLabel: string; mobileAriaLabel: string; home: string; marketSection: string;
    paintProducts: string; marketPaintableProducts: string; workshopSection: string;
    workshopPaints: string; workshopAdmin: string; shopping: string; aboutSection: string;
    userDocumentation: string; adminDocumentation: string; paintModel: string; restApi: string; version: string;
  };
  header: { searchPlaceholder: string; searchShortPlaceholder: string; searchAriaLabel: string; workshopPrefix: string };
  home: {
    eyebrow: string; title: string; description: string; servicesTitle: string; servicesDescription: string;
    paintProducts: ServiceLabel; marketPaintableProducts: ServiceLabel;
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
    paintModelTitle: string; paintModelDescription: string; modelVersion: string; filterFields: string;
    vocabularies: string; openPaintSchema: string; qualityTitle: string; qualityDescription: string;
    missingColorHex: string; missingColorFamily: string; unknownFinish: string; unknownCoverage: string;
    technicalReviewRequired: string; sourcedImagesWithoutLicense: string; realResultImages: string; imageQualityBreakdown: string;
    imageQualityLimitations: string; noImageQualityLimitations: string;
    apiTitle: string; apiDescription: string; versionTitle: string; versionDescription: string; versionLabel: string; authorLabel: string;
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
    suggestions: string; noSuggestions: string; suggestionsHint: string; suggestionsFailed: string; sortRelevance: string;
    brandRangeFilter: string; catalogFilterHint: string; filterLogicHint: string; advancedFilters: string;
    showMore: string; showLess: string; closeFilters: string; showResults: string;
    pagination: string; previousPage: string; nextPage: string;
    title: string; description: string; allBrands: string; manufacturerFilter: string; allManufacturers: string;
    potsMetric: string; colorsMetric: string; sheetsMetric: string; resultsTitle: string; allPaintsTitle: string;
    emptyTitle: string; emptyHint: string; manufacturerSheet: string; filters: string; filterPanelTitle: string;
    filterPanelDescription: string; filterAriaLabel: string; searchFilter: string; typeFilter: string;
    allTypes: string; colorFilter: string; allColors: string; brandFilter: string; rangeFilter: string;
    allRanges: string; finishFilter: string; allFinishes: string; mediumFilter: string; allMediums: string;
    roleFilter: string; applicationMethodFilter: string; applicationSystemFilter: string; coverageFilter: string;
    effectFilter: string; undercoatFilter: string; allValues: string;
    opacityFilter: string; allOpacities: string; lifecycleFilter: string; allLifecycles: string;
    volumeFilter: string; allVolumes: string; tagFilter: string; allTags: string;
    manufacturerSheetOnly: string; realResultOnly: string; resetFilters: string; activeFilters: string;
    removeFilter: string; noActiveFilter: string; loadMore: string; sort: string; displayedMetric: string;
    sortNameAscending: string; sortNameDescending: string; sortBrandAscending: string; sortBrandDescending: string;
    sortRangeAscending: string; sortRangeDescending: string; sortReferenceAscending: string; sortReferenceDescending: string;
    sortVerifiedNewest: string; sortVerifiedOldest: string; valueLabels: Record<string, string>;
  };
  shopping: {
    title: string; description: string; ready: string; remainingSuffix: string;
    priorities: { high: string; medium: string; low: string };
    requiredTitle: string; plannedTitle: string; requiredBy: string; derivedHint: string; plannedHint: string;
  };
  paintDetail: {
    catalogEditions: string;
    characteristics: string; documentLanguage: string; french: string; original: string;
    sharedGuide: string; revision: string; sources: string; source: string; knowledgeLabels: Record<string, string>;
    translationReviewRequired: string; translationMissing: string; translationStale: string;
    guideLoadFailed: string; retry: string; noUsageGuide: string; specificInstructions: string;
    previous: string; next: string; imageProvenance: string;
    sheet: string; close: string; colorFamily: string; toQualify: string; manufacturerFeatures: string;
    noManufacturerDescription: string; recommendedUses: string; usageInstructions: string; usageSteps: string;
    usageTips: string; openSheet: string; openManufacturerSheet: string; verifiedOn: string;
    productVisual: string; appliedResult: string; digitalPreview: string; digitalPreviewHelp: string;
    noProductVisual: string; realResultSource: string; referenceLabel: string; volumeLabel: string;
    instructionsReviewRequired: string;
    imageQuality: string; imageQualityLimitation: string; imageQualityLimitationObservedOn: string;
    imageQualityLabels: Record<string, string>;
  };
  errors: { loading: string; productNotFound: string; catalogItemNotFound: string; paintNotFound: string; requestFailed: string; connectionLost: string; connectionDetail: string };
};

type ServiceLabel = { title: string; description: string; action: string };
