export type ManufacturerInfo = {
  manufacturerUrl: string;
  manufacturerImage: string;
  manufacturerImageSource: string;
  manufacturerImageCredit: string;
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
  facetId: string;
  labelKey: string;
  vocabularyId?: string;
  order: number;
};

export type PaintModelSchema = {
  $schema: string;
  $id: string;
  title: string;
  'x-model-version': number;
  'x-filters': PaintModelFilter[];
  'x-vocabularies': Record<string, string[]>;
  properties: Record<string, unknown>;
};

export type PaintFacets = {
  total: number;
  facets: Array<{ id: string; values: Array<{ value: string; count: number }> }>;
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
