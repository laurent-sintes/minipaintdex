export type ManufacturerInfo = {
  manufacturerUrl: string;
  manufacturerImage: string;
  manufacturerImageSource: string;
  manufacturerImageCredit: string;
  manufacturerImageQuality: 'official_photo' | 'retailer_photo' | 'owned_photo' | 'generic_visual' | 'color_swatch' | 'none';
  manufacturerImageQualityRank: number;
  manufacturerImageQualityVerifiedAt: string;
  manufacturerImageQualityLimitationCode: string;
  manufacturerImageQualityLimitationDetail: string;
  manufacturerImageQualityLimitationObservedAt: string;
  volumeMl: number;
  colorFamily: string;
  manufacturerDescription: string;
  recommendedUses: string[];
  usageInstructions: { summary: string; steps: string[]; tips: string[]; instructionStatus: string; reviewRequired: boolean };
  manufacturerVerifiedAt: string;
  resultImage: string;
  resultImageCredit: string;
  resultImageSource: string;
  resultImageLicense: string;
  resultReferenceUrl: string;
};

export type Paint = ManufacturerInfo & {
  catalogMemberships: Array<{ catalogEditionId: string; title: string; editionLabel: string; publicationYear: number | null; sourceUrl: string; locator: string }>;
  id: string;
  brand: string;
  manufacturer: string;
  brandAliases: string[];
  range: string;
  profile: PaintProfile;
  reference: string;
  name: string;
  colorHex: string;
  lifecycleStatus: string;
  quantity?: number;
  status: string;
  warnings: string;
  tags: string[];
  notes: string;
  createdAt: string;
  updatedAt: string;
};

export type PaintProfile = {
  roles: string[];
  applicationMethods: string[];
  applicationSystem: string;
  coverage: string;
  finish: string;
  effects: string[];
  undercoatTone: string;
  preHighlightRecommended: boolean;
  medium: string;
};

export type PaintModelFilter = {
  id: string;
  queryParameter: string;
  facetId?: string;
  labelKey: string;
  vocabularyId?: string;
  control: 'checkbox' | 'toggle';
  group: 'catalog' | 'primary' | 'advanced';
  order: number;
};

export type PaintSortOption = {
  id: string;
  queryValue: string;
  labelKey: string;
  order: number;
};

export type PaintModelSchema = {
  $schema: string;
  $id: string;
  title: string;
  'x-model-version': number;
  'x-filters': PaintModelFilter[];
  'x-sort-options': PaintSortOption[];
  'x-vocabularies': Record<string, string[]>;
  'x-image-quality-ranks': Record<string, number>;
  properties: Record<string, unknown>;
};

export type PaintFacetValue = { value: string; count: number; label: string; parentValue: string | null };

export type PaintFacets = {
  total: number;
  facets: Array<{ id: string; values: PaintFacetValue[] }>;
};

export type PaintCatalogQuality = {
  total: number;
  missingColorHex: number;
  missingColorFamily: number;
  unknownFinish: number;
  unknownCoverage: number;
  technicalReviewRequired: number;
  sourcedImagesWithoutLicense: number;
  realResultImages: number;
  imageQualities: Array<{ quality: string; count: number }>;
  imageLimitations: Array<{ brand: string; code: string; count: number }>;
};

export type ShoppingItem = {
  id: string;
  brand: string;
  name: string;
  reference: string;
  colorHex: string;
  reason: string;
  priority: 'high' | 'medium' | 'low';
  kind: 'required' | 'planned';
  planned: boolean;
  marketPaintId: string;
  sourceProductIds: string[];
  sourceProductNames: string[];
  checked: boolean;
};
