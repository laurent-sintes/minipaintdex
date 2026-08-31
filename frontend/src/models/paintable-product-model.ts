export type PaintableProductSource = {
  kind: string;
  label: string;
  url: string;
};

export type ReferenceImage = {
  url: string;
  pageUrl: string;
  credit: string;
  license?: string;
};

export type GuidePaint = {
  slotId: string;
  paintId?: string;
  brand: string;
  name: string;
  role: string;
  colorHex: string;
  pendingImport?: boolean;
};

export type GuideStep = {
  title: string;
  detail: string;
};

export type PaintableCatalogItem = {
  id: string;
  productId: string;
  name: string;
  kind: string;
  quantity: number;
  assemblyRequired: boolean;
  description: string;
  referenceImages: ReferenceImage[];
  paints: GuidePaint[];
  preparation: GuideStep[];
  painting: GuideStep[];
  sources: PaintableProductSource[];
  marketGuide: {
    id: string;
    version: number;
    knowledgeStatus: 'documented' | 'observed' | 'inferred';
    sources: PaintableProductSource[];
  } | Record<string, never>;
};

export type WorkshopRecipe = {
  id: string;
  catalogItemId: string;
  basedOnGuideId: string;
  supersedesRecipeId: string;
  displayName: string;
  version: number;
  status: 'draft' | 'validated' | 'active' | 'superseded' | 'archived';
  solutions: Array<Record<string, unknown>>;
  updatedAt: string;
};

export type PaintableProduct = {
  schemaVersion: number;
  id: string;
  name: string;
  line: string;
  productType: string;
  scope: string;
  edition: { note: string; url: string };
  expectedPaintableCount: number;
  sources: PaintableProductSource[];
  items: PaintableCatalogItem[];
  inWorkshop: boolean;
};

export type PaintingProjectSummary = {
  projectId: string;
  productId: string;
  name: string;
  status: 'planned' | 'active' | 'completed' | 'archived';
  createdAt: string;
  updatedAt: string;
  importedAt: string;
  itemCount: number;
  completedCount: number;
  inProgressCount: number;
  pendingCount: number;
  progressPercentage: number;
  requiredPaintCount: number;
  missingPaintCount: number;
  missingPaints: Array<{ id: string; name: string; brand: string; reference: string }>;
  pendingPaintSlotCount: number;
  orphaned?: boolean;
};

export type WorkshopOverview = {
  id: string;
  paintingProjects: PaintingProjectSummary[];
  projectCount: number;
  itemCount: number;
  completedItemCount: number;
  progressPercentage: number;
  recentActivity: DomainEvent[];
};

export type WorkshopItem = {
  id: string;
  catalogItemId: string;
  paintingProjectId: string;
  displayName: string;
  workflow: Record<string, 'pending' | 'in_progress' | 'completed' | 'skipped'>;
  currentStage: string | null;
  completed: boolean;
  recipeId: string;
  recipeVersion: number;
  updatedAt: string;
};

export type WorkshopItemDetail = WorkshopItem & {
  activity: DomainEvent[];
};

export type DomainEvent = {
  eventId: string;
  eventType: string;
  occurredAt: string;
  recordedAt: string;
  aggregateType: string;
  aggregateId: string;
  payload: Record<string, unknown>;
};

export type PaintableProductImportPreview = {
  productId: string;
  productName: string;
  catalogItemCount: number;
  paintableItemCount: number;
  paintingGuideCount: number;
  requiredPaintCount: number;
  missingPaintCount: number;
  missingPaints: Array<{ id: string; name: string; brand: string; reference: string }>;
  pendingPaintSlotCount: number;
  alreadyImported: boolean;
};
