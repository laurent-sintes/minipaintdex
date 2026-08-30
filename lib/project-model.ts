export type ProjectSource = {
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

export type ProjectPaint = {
  brand: string;
  name: string;
  role: string;
  colorHex: string;
  pendingImport?: boolean;
};

export type ProjectStep = {
  title: string;
  detail: string;
};

export type ProjectItem = {
  id: string;
  name: string;
  kind: string;
  quantity: number;
  status: string;
  description: string;
  referenceImages: ReferenceImage[];
  paints: ProjectPaint[];
  preparation: ProjectStep[];
  painting: ProjectStep[];
  sources: ProjectSource[];
};

export type PaintingProject = {
  schemaVersion: number;
  id: string;
  name: string;
  game: string;
  scope: string;
  edition: { note: string; url: string };
  sources: ProjectSource[];
  items: ProjectItem[];
};
