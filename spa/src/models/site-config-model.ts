export type SiteConfig = {
  metadata: { title: string; shortTitle: string; description: string };
  brand: { name: string; subtitle: string };
  units: { colorSingular: string; colorPlural: string; paintSingular: string; paintPlural: string };
  navigation: { ariaLabel: string; mobileAriaLabel: string; home: string; marketSection: string; marketPaints: string; marketGames: string; workshopSection: string; workshopPaints: string; workshopProjects: string; shopping: string; imports: string };
  header: { searchPlaceholder: string; searchShortPlaceholder: string; searchAriaLabel: string; addByPhoto: string; add: string; workshopPrefix: string };
  home: {
    eyebrow: string; title: string; description: string; servicesTitle: string; servicesDescription: string;
    marketPaints: { title: string; description: string; action: string };
    marketGames: { title: string; description: string; action: string };
    workshopPaints: { title: string; description: string; action: string };
    workshopProjects: { title: string; description: string; action: string };
    shopping: { title: string; description: string; action: string };
    imports: { title: string; description: string; action: string };
  };
  workflow: { preparation: string; priming: string; preHighlight: string; painting: string; finishing: string; basing: string };
  market: { paintsTitle: string; paintsDescription: string; paintsMetric: string; brandsMetric: string; gamesTitle: string; gamesDescription: string; catalogItems: string; physicalItems: string; openWorkshop: string; source: string };
  collection: { title: string; description: string; allBrands: string; manufacturerFilter: string; allManufacturers: string; potsMetric: string; colorsMetric: string; sheetsMetric: string; resultsTitle: string; allPaintsTitle: string; emptyTitle: string; emptyHint: string; manufacturerSheet: string; filters: string; filterPanelTitle: string; filterPanelDescription: string; filterAriaLabel: string; searchFilter: string; typeFilter: string; allTypes: string; colorFilter: string; allColors: string; brandFilter: string; rangeFilter: string; allRanges: string; finishFilter: string; allFinishes: string; mediumFilter: string; allMediums: string; opacityFilter: string; allOpacities: string; lifecycleFilter: string; allLifecycles: string; volumeFilter: string; allVolumes: string; tagFilter: string; allTags: string; manufacturerSheetOnly: string; realResultOnly: string; resetFilters: string; activeFilters: string; removeFilter: string; noActiveFilter: string };
  projects: { titleFallback: string; description: string; activeProject: string; currentProject: string; editionSource: string; allSheets: string; paintsToUse: string; marketGuide: string; marketGuideHelp: string; marketGuideStatuses: { documented: string; observed: string; inferred: string }; workshopRecipe: string; workshopRecipeHelp: string; noWorkshopRecipe: string; reconciliationHelp: string; supportPreparation: string; paintingSteps: string; fullSheet: string; available: string; importPending: string; missing: string; figureUnit: string; sheetUnit: string; statuses: { completed: string; inProgress: string; pending: string } };
  shopping: { title: string; description: string; ready: string; remainingSuffix: string; export: string; priorities: { high: string; medium: string; low: string } };
  imports: { title: string; description: string; choosePhoto: string; changePhoto: string; photoHelp: string; browse: string; fromIphone: string; folderReady: string; folderStep: string; photosStep: string; returnStep: string; photoTipTitle: string; photoTip: string; previewNotice: string; photoSelected: string; newPaint: string; labelPhotoHelp: string; brand: string; range: string; paintName: string; reference: string; finish: string; approximateColor: string; quantity: string; brandPlaceholder: string; rangePlaceholder: string; paintNamePlaceholder: string; referencePlaceholder: string; finishOptions: string[]; cancel: string; saving: string; validate: string; requiredNotice: string; previewFailed: string; previewCredit: string; sessionPreviewSuffix: string };
  paintDetail: { sheet: string; close: string; colorFamily: string; toQualify: string; manufacturerFeatures: string; noManufacturerDescription: string; recommendedUses: string; usageInstructions: string; usageSteps: string; usageTips: string; openSheet: string; openManufacturerSheet: string; verifiedOn: string; productVisual: string; appliedResult: string; digitalPreview: string; digitalPreviewHelp: string; noProductVisual: string; realResultSource: string };
  miniatureDetail: { paintedReferences: string; noLicensedImage: string; externalReferences: string; paintsToUse: string; supportPreparation: string; paintingSteps: string; marketGuide: string; workshopRecipe: string };
  projectDetail: { back: string; sheets: string; figures: string; paintingSheets: string; openRecipe: string; sources: string };
  paintPage: { back: string; referencePrefix: string; stockSuffix: string; color: string };
  errors: { projectNotFound: string; miniatureNotFound: string; paintNotFound: string };
};
