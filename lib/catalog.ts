import { parse } from 'yaml';
import marketPaintsYaml from '@/data/market/paints/catalog.yaml?raw';
import paintInventoryYaml from '@/data/workshop/paints.yaml?raw';
import shoppingYaml from '@/data/workshop/shopping.yaml?raw';
import siteYaml from '@/data/site/fr.yaml?raw';
import { projectSnapshot, type DataSnapshot } from '@/packages/application/src/index';
import type { DomainEvent } from '@/packages/contracts/src/index';
import type { Paint } from '@/lib/paint-model';
import type { PaintingProject } from '@/lib/project-model';
import type { SiteConfig } from '@/lib/site-config-model';

type UnknownRecord = Record<string, unknown>;

const gameFiles = import.meta.glob('../data/market/games/*.yaml', { eager: true, query: '?raw', import: 'default' }) as Record<string, string>;
const recipeFiles = import.meta.glob('../data/workshop/recipes/*.yaml', { eager: true, query: '?raw', import: 'default' }) as Record<string, string>;
const eventFiles = import.meta.glob('../data/ledger/events/*.jsonl', { eager: true, query: '?raw', import: 'default' }) as Record<string, string>;

function document(contents: string) {
  return parse(contents) as UnknownRecord;
}

const paints = document(marketPaintsYaml);
const inventory = document(paintInventoryYaml);
const shopping = document(shoppingYaml);
const snapshot: DataSnapshot = {
  site: document(siteYaml),
  marketPaints: Array.isArray(paints.paints) ? paints.paints as UnknownRecord[] : [],
  paintInventory: Array.isArray(inventory.paints) ? inventory.paints as UnknownRecord[] : [],
  games: Object.values(gameFiles).map(document),
  recipes: Object.values(recipeFiles).flatMap((contents) => {
    const parsed = document(contents);
    return Array.isArray(parsed.recipes) ? parsed.recipes as UnknownRecord[] : [];
  }),
  shopping: Array.isArray(shopping.items) ? shopping.items as UnknownRecord[] : [],
  events: Object.values(eventFiles).flatMap((contents) => contents.split(/\r?\n/).filter(Boolean).map((line) => JSON.parse(line) as DomainEvent)),
};

const view = projectSnapshot(snapshot);

export const siteConfig = view.config as SiteConfig;
export const paintCatalog = view.paints as Paint[];
export const projectCatalog = view.projects as PaintingProject[];

export function findProject(id: string): PaintingProject | undefined {
  return projectCatalog.find((project) => project.id === id);
}

export function findPaint(id: string): Paint | undefined {
  return paintCatalog.find((paint) => paint.id === id);
}
