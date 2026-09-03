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
  paintProductId?: string;
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

export type PaintableComponent = {
  id: string;
  paintableProductId: string;
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
  } | null;
};

export type WorkshopRecipe = {
  id: string;
  paintableComponentId: string;
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
  paintableComponents: PaintableComponent[];
};

export type PaintableProductSummary = {
  id: string;
  name: string;
  line: string;
  productType: string;
  scope: string;
  paintableComponentCount: number;
  expectedPaintableCount: number;
};

export type Dashboard = {
  paintStats: { total: number; owned: number; brands: number };
  paintableProductCount: number;
  workshop: Pick<WorkshopOverview, 'projectCount' | 'paintableCount' | 'completedPaintableCount' | 'progressPercentage'>;
};

export type PaintingProjectSummary = {
  paintingProjectId: string;
  paintableProductId: string;
  name: string;
  status: 'planned' | 'active' | 'completed' | 'archived';
  createdAt: string;
  updatedAt: string;
  importedAt: string;
  paintableCount: number;
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
  paintableCount: number;
  completedPaintableCount: number;
  progressPercentage: number;
  recentActivity: DomainEvent[];
};

export type WorkshopPaintable = {
  id: string;
  paintableComponentId: string;
  paintingProjectId: string;
  displayName: string;
  workflow: Record<string, 'pending' | 'in_progress' | 'completed' | 'skipped'>;
  currentStage: string | null;
  completed: boolean;
  recipeId: string;
  recipeVersion: number;
  updatedAt: string;
};

export type WorkshopPaintableDetail = WorkshopPaintable & {
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

export type PaintingProjectImportPreview = {
  paintableProductId: string;
  paintableProductName: string;
  paintableComponentCount: number;
  paintableCount: number;
  paintingGuideCount: number;
  requiredPaintCount: number;
  missingPaintCount: number;
  missingPaints: Array<{ id: string; name: string; brand: string; reference: string }>;
  pendingPaintSlotCount: number;
  alreadyImported: boolean;
};
