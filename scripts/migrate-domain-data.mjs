import { mkdir, readFile, writeFile, access } from 'node:fs/promises';
import { constants } from 'node:fs';
import { dirname, join } from 'node:path';
import { randomBytes } from 'node:crypto';
import { parse, stringify } from 'yaml';

const root = process.cwd();
const force = process.argv.includes('--force');

function split(value) {
  if (Array.isArray(value)) return value.map(String).filter(Boolean);
  return String(value ?? '').split('|').map((entry) => entry.trim()).filter(Boolean);
}

function numeric(value, fallback = 0) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function normalize(value) {
  return String(value ?? '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
}

const alphabet = '0123456789ABCDEFGHJKMNPQRSTVWXYZ';
function encodeTime(time, length) {
  let value = time;
  let output = '';
  for (let index = 0; index < length; index += 1) {
    output = alphabet[value % 32] + output;
    value = Math.floor(value / 32);
  }
  return output;
}

function ulid(time = Date.now()) {
  const bytes = randomBytes(16);
  let random = '';
  for (let index = 0; index < 16; index += 1) random += alphabet[bytes[index] % 32];
  return encodeTime(time, 10) + random;
}

async function writeNew(relativePath, contents) {
  const path = join(root, relativePath);
  await mkdir(dirname(path), { recursive: true });
  if (!force) {
    try {
      await access(path, constants.F_OK);
      throw new Error(`${relativePath} already exists; rerun with --force to replace generated migration output.`);
    } catch (error) {
      if (error?.code !== 'ENOENT') throw error;
    }
  }
  await writeFile(path, contents, 'utf8');
}

const paintsSource = parse(await readFile(join(root, 'data', 'peintures.yaml'), 'utf8'));
const projectSource = parse(await readFile(join(root, 'data', 'projects', 'reichbusters-reloaded.yaml'), 'utf8'));
const siteSource = await readFile(join(root, 'data', 'site.yaml'), 'utf8');

const marketPaints = (paintsSource.peintures ?? []).map((paint) => ({
  id: String(paint.id),
  observed_brand: String(paint.marque_observee ?? ''),
  brand: String(paint.marque_canonique || paint.marque_observee || ''),
  brand_aliases: split(paint.alias_marque),
  manufacturer: String(paint.fabricant ?? ''),
  observed_range: String(paint.gamme_observee ?? ''),
  range: String(paint.gamme_canonique || paint.gamme_observee || ''),
  functional_type: String(paint.classe_fonctionnelle ?? ''),
  reference: String(paint.reference ?? ''),
  name: String(paint.nom ?? ''),
  confidence: numeric(paint.confiance),
  data_status: String(paint.statut ?? ''),
  lifecycle_status: 'unknown',
  warnings: split(paint.avertissements),
  color: {
    hex: String(paint.couleur_hex || '#777777'),
    family: String(paint.famille_couleur ?? ''),
  },
  finish: String(paint.fini ?? ''),
  medium: String(paint.medium ?? ''),
  volume_ml: numeric(paint.volume_ml),
  tags: split(paint.tags),
  recommended_uses: split(paint.usages_conseilles),
  manufacturer_page: String(paint.fiche_fabricant ?? ''),
  manufacturer_image: {
    path: String(paint.image_fabricant ?? ''),
    source_url: String(paint.source_image ?? ''),
    credit: String(paint.credit_image ?? ''),
  },
  result_image: {
    path: String(paint.rendu_image ?? ''),
    source_url: String(paint.source_rendu ?? ''),
    credit: String(paint.credit_rendu ?? ''),
    license: String(paint.licence_rendu ?? ''),
    reference_url: String(paint.exemple_rendu_url ?? ''),
  },
  provenance: {
    photo: String(paint.source_photo ?? ''),
    hashes: split(paint.source_hashes),
  },
  verified_at: String(paint.verifie_le ?? ''),
  notes: String(paint.notes ?? ''),
  deduplication_key: String(paint.cle_dedoublonnage ?? ''),
}));

const inventory = (paintsSource.peintures ?? []).map((paint) => ({
  paint_id: String(paint.id),
  quantity: numeric(paint.quantite, 1),
}));

const paintLookup = new Map(marketPaints.map((paint) => [
  `${normalize(paint.brand)}|${normalize(paint.name)}`,
  paint.id,
]));

const catalogItems = (projectSource.items ?? []).map((item) => ({
  id: `${projectSource.id}-${item.id}`,
  game_id: String(projectSource.id),
  name: String(item.name),
  kind: String(item.kind),
  description: String(item.description ?? ''),
  assembly_required: false,
  reference_images: item.reference_images ?? [],
  sources: item.sources ?? [],
}));

const recipes = (projectSource.items ?? []).map((item) => ({
  id: `${projectSource.id}-${item.id}-recipe`,
  catalog_item_id: `${projectSource.id}-${item.id}`,
  paints: (item.paints ?? []).map((paint) => {
    const paintId = paintLookup.get(`${normalize(paint.brand)}|${normalize(paint.name)}`);
    return {
      paint_id: paintId ?? null,
      role: String(paint.role ?? ''),
      ...(paintId ? {} : {
        requested_paint: {
          brand: String(paint.brand ?? ''),
          name: String(paint.name ?? ''),
          color_hex: String(paint.color_hex || '#777777'),
        },
        pending_import: true,
      }),
    };
  }),
  preparation: item.preparation ?? [],
  painting: item.painting ?? [],
}));

const game = {
  schema_version: 1,
  id: String(projectSource.id),
  name: String(projectSource.name),
  game: String(projectSource.game),
  scope: String(projectSource.scope),
  expected_paintable_count: numeric(projectSource.expected_paintable_count),
  edition: projectSource.edition ?? {},
  sources: projectSource.sources ?? [],
  catalog_items: catalogItems,
};

const initialTime = Date.parse('2026-08-30T00:00:00.000Z');
const events = [];
events.push({
  event_id: ulid(initialTime),
  schema_version: 1,
  event_type: 'project.created',
  occurred_at: new Date(initialTime).toISOString(),
  recorded_at: new Date(initialTime).toISOString(),
  aggregate_type: 'project',
  aggregate_id: String(projectSource.id),
  project_id: String(projectSource.id),
  actor: { type: 'migration', id: 'legacy-yaml-import' },
  correlation_id: 'legacy-reichbusters-migration',
  payload: {
    market_game_id: String(projectSource.id),
    name: String(projectSource.name),
  },
});

let physicalCount = 0;
for (const item of projectSource.items ?? []) {
  const quantity = numeric(item.quantity, 1);
  for (let ordinal = 1; ordinal <= quantity; ordinal += 1) {
    physicalCount += 1;
    const itemId = `ws-${projectSource.id}-${item.id}-${String(ordinal).padStart(3, '0')}`;
    const timestamp = new Date(initialTime + physicalCount).toISOString();
    events.push({
      event_id: ulid(initialTime + physicalCount),
      schema_version: 1,
      event_type: 'workshop_item.added',
      occurred_at: timestamp,
      recorded_at: timestamp,
      aggregate_type: 'workshop_item',
      aggregate_id: itemId,
      project_id: String(projectSource.id),
      actor: { type: 'migration', id: 'legacy-yaml-import' },
      correlation_id: 'legacy-reichbusters-migration',
      payload: {
        catalog_item_id: `${projectSource.id}-${item.id}`,
        display_name: quantity === 1 ? String(item.name) : `${item.name} ${ordinal}`,
        ordinal,
      },
    });
  }
}

if (projectSource.expected_paintable_count && physicalCount !== numeric(projectSource.expected_paintable_count)) {
  throw new Error(`Expected ${projectSource.expected_paintable_count} physical items but generated ${physicalCount}.`);
}

const shopping = {
  schema_version: 1,
  items: [
    { id: 'buy-1', brand: 'The Army Painter', name: 'Matt Black Colour Primer', reference: '', color_hex: '#242526', reason: 'Sous-couche noire du projet Reichbusters', priority: 'high' },
    { id: 'buy-2', brand: 'Warhammer Colour', name: 'Nuln Oil', reference: '', color_hex: '#2d2e2e', reason: 'Ombres des armes, armures et uniformes', priority: 'high' },
    { id: 'buy-3', brand: 'Vallejo', name: 'Dead White', reference: '72.001', color_hex: '#f2f1e8', reason: 'Zénithal au pinceau et cœur des lueurs Vril', priority: 'medium' },
  ],
};

await writeNew('data/site/fr.yaml', siteSource);
await writeNew('data/market/paints/catalog.yaml', stringify({ schema_version: 1, paints: marketPaints }, { lineWidth: 0 }));
await writeNew(`data/market/games/${projectSource.id}.yaml`, stringify(game, { lineWidth: 0 }));
await writeNew('data/workshop/paints.yaml', stringify({ schema_version: 1, paints: inventory }, { lineWidth: 0 }));
await writeNew(`data/workshop/recipes/${projectSource.id}.yaml`, stringify({ schema_version: 1, recipes }, { lineWidth: 0 }));
await writeNew('data/workshop/shopping.yaml', stringify(shopping, { lineWidth: 0 }));
await writeNew('data/ledger/events/2026-08.jsonl', `${events.map((event) => JSON.stringify(event)).join('\n')}\n`);

console.log(JSON.stringify({ paints: marketPaints.length, workshopPaints: inventory.length, catalogItems: catalogItems.length, workshopItems: physicalCount, events: events.length }, null, 2));
