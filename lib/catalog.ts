import { parse } from 'yaml';
import paintsYaml from '@/data/peintures.yaml?raw';
import type { Paint } from '@/lib/paint-model';
import type {
  PaintingProject,
  ProjectItem,
  ProjectPaint,
  ProjectSource,
  ProjectStep,
  ReferenceImage,
} from '@/lib/project-model';

type UnknownRecord = Record<string, unknown>;

function record(value: unknown): UnknownRecord {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as UnknownRecord : {};
}

function string(value: unknown): string {
  if (typeof value === 'string') return value;
  if (typeof value === 'number' || typeof value === 'boolean') return String(value);
  return '';
}

function number(value: unknown, fallback = 0): number {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function list(value: unknown): unknown[] {
  return Array.isArray(value) ? value : [];
}

function split(value: unknown): string[] {
  return string(value).split('|').map((item) => item.trim()).filter(Boolean);
}

const parsedPaints = record(parse(paintsYaml));

export const paintCatalog: Paint[] = list(parsedPaints.peintures).map((entry) => {
  const paint = record(entry);
  const verifiedAt = string(paint.verifie_le);
  return {
    id: string(paint.id),
    brand: string(paint.marque_canonique || paint.marque_observee),
    range: string(paint.gamme_canonique || paint.gamme_observee),
    reference: string(paint.reference),
    name: string(paint.nom),
    colorHex: string(paint.couleur_hex) || '#777777',
    finish: string(paint.fini),
    medium: string(paint.medium),
    quantity: number(paint.quantite, 1),
    tags: split(paint.tags),
    notes: string(paint.notes),
    createdAt: verifiedAt ? `${verifiedAt}T00:00:00.000Z` : '',
    updatedAt: verifiedAt ? `${verifiedAt}T00:00:00.000Z` : '',
    manufacturerUrl: string(paint.fiche_fabricant),
    manufacturerImage: string(paint.image_fabricant),
    manufacturerImageCredit: string(paint.credit_image),
    volumeMl: number(paint.volume_ml),
    colorFamily: string(paint.famille_couleur),
    manufacturerDescription: string(paint.notes),
    recommendedUses: split(paint.usages_conseilles),
    manufacturerVerifiedAt: verifiedAt,
  };
});

function source(value: unknown): ProjectSource {
  const item = record(value);
  return { kind: string(item.kind), label: string(item.label), url: string(item.url) };
}

function referenceImage(value: unknown): ReferenceImage {
  const item = record(value);
  return {
    url: string(item.url),
    pageUrl: string(item.page_url),
    credit: string(item.credit),
    license: string(item.license) || undefined,
  };
}

function projectPaint(value: unknown): ProjectPaint {
  const item = record(value);
  return {
    brand: string(item.brand),
    name: string(item.name),
    role: string(item.role),
    colorHex: string(item.color_hex) || '#777777',
    pendingImport: item.pending_import === true || undefined,
  };
}

function step(value: unknown): ProjectStep {
  const item = record(value);
  return { title: string(item.title), detail: string(item.detail) };
}

function projectItem(value: unknown): ProjectItem {
  const item = record(value);
  return {
    id: string(item.id),
    name: string(item.name),
    kind: string(item.kind),
    quantity: number(item.quantity, 1),
    status: string(item.status),
    description: string(item.description),
    referenceImages: list(item.reference_images).map(referenceImage),
    paints: list(item.paints).map(projectPaint),
    preparation: list(item.preparation).map(step),
    painting: list(item.painting).map(step),
    sources: list(item.sources).map(source),
  };
}

const projectFiles = import.meta.glob('../data/projects/*.yaml', {
  eager: true,
  query: '?raw',
  import: 'default',
}) as Record<string, string>;

export const projectCatalog: PaintingProject[] = Object.values(projectFiles)
  .map((contents) => {
    const project = record(parse(contents));
    const edition = record(project.edition);
    return {
      schemaVersion: number(project.schema_version, 1),
      id: string(project.id),
      name: string(project.name),
      game: string(project.game),
      scope: string(project.scope),
      edition: { note: string(edition.note), url: string(edition.url) },
      sources: list(project.sources).map(source),
      items: list(project.items).map(projectItem),
    };
  })
  .sort((left, right) => left.name.localeCompare(right.name, 'fr'));

export function findProject(id: string): PaintingProject | undefined {
  return projectCatalog.find((project) => project.id === id);
}

export function findPaint(id: string): Paint | undefined {
  return paintCatalog.find((paint) => paint.id === id);
}
