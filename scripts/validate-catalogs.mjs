import { readFile, readdir } from 'node:fs/promises';
import { join } from 'node:path';
import { parse } from 'yaml';

const errors = [];
const warnings = [];

function requireValue(value, location, message) {
  if (value === undefined || value === null || value === '') errors.push(`${location}: ${message}`);
}

function requireArray(value, location) {
  if (!Array.isArray(value)) errors.push(`${location}: array required`);
  return Array.isArray(value) ? value : [];
}

async function readYaml(path) {
  return parse(await readFile(path, 'utf8'));
}

async function yamlFiles(directory) {
  return (await readdir(directory))
    .filter((name) => name.endsWith('.yaml'))
    .map((name) => join(directory, name).replaceAll('\\', '/'));
}

const sitePath = 'data/site/fr.yaml';
const site = await readYaml(sitePath);
for (const section of ['metadata', 'brand', 'units', 'navigation', 'header', 'workflow', 'market', 'collection', 'projects', 'shopping', 'imports', 'paint_detail', 'miniature_detail', 'project_detail', 'paint_page', 'errors']) {
  if (!site?.[section] || typeof site[section] !== 'object') errors.push(`${sitePath}:${section}: required section`);
}

const paintCatalogPath = 'data/market/paints/catalog.yaml';
const paintCatalog = await readYaml(paintCatalogPath);
const marketPaints = requireArray(paintCatalog?.paints, `${paintCatalogPath}:paints`);
const marketPaintIds = new Set();
for (const [index, paint] of marketPaints.entries()) {
  const location = `${paintCatalogPath}:paints[${index}]`;
  for (const field of ['id', 'brand', 'manufacturer', 'range', 'functional_type', 'name']) requireValue(paint?.[field], location, `${field} required`);
  if (paint?.color?.hex && !/^#[0-9a-f]{6}$/i.test(paint.color.hex)) errors.push(`${location}: color.hex must be a six-digit hexadecimal color`);
  if (paint?.id && marketPaintIds.has(paint.id)) errors.push(`${location}: duplicate id ${paint.id}`);
  if (paint?.id) marketPaintIds.add(paint.id);
}

const gameFiles = await yamlFiles('data/market/games');
if (!gameFiles.length) errors.push('data/market/games: at least one YAML game is required');
const gamesById = new Map();
const catalogItemsById = new Map();
for (const path of gameFiles) {
  const game = await readYaml(path);
  for (const field of ['id', 'name', 'game', 'scope']) requireValue(game?.[field], path, `${field} required`);
  if (game?.id && gamesById.has(game.id)) errors.push(`${path}: duplicate game id ${game.id}`);
  if (game?.id) gamesById.set(game.id, game);
  for (const [index, item] of requireArray(game?.catalog_items, `${path}:catalog_items`).entries()) {
    const location = `${path}:catalog_items[${index}]`;
    for (const field of ['id', 'game_id', 'name', 'kind']) requireValue(item?.[field], location, `${field} required`);
    if (item?.game_id !== game?.id) errors.push(`${location}: game_id must reference ${game?.id}`);
    if (item?.id && catalogItemsById.has(item.id)) errors.push(`${location}: duplicate catalog item id ${item.id}`);
    if (item?.id) catalogItemsById.set(item.id, item);
    for (const [imageIndex, image] of requireArray(item?.reference_images, `${location}:reference_images`).entries()) {
      const imageLocation = `${location}:reference_images[${imageIndex}]`;
      for (const field of ['url', 'page_url', 'credit', 'license']) requireValue(image?.[field], imageLocation, `${field} required`);
    }
  }
}

const workshopPaintsPath = 'data/workshop/paints.yaml';
const workshopPaints = requireArray((await readYaml(workshopPaintsPath))?.paints, `${workshopPaintsPath}:paints`);
const ownedPaintIds = new Set();
for (const [index, paint] of workshopPaints.entries()) {
  const location = `${workshopPaintsPath}:paints[${index}]`;
  requireValue(paint?.paint_id, location, 'paint_id required');
  if (!Number.isInteger(paint?.quantity) || paint.quantity < 1) errors.push(`${location}: quantity must be a positive integer`);
  if (paint?.paint_id && !marketPaintIds.has(paint.paint_id)) errors.push(`${location}: unknown market paint ${paint.paint_id}`);
  if (paint?.paint_id && ownedPaintIds.has(paint.paint_id)) errors.push(`${location}: duplicate owned paint ${paint.paint_id}`);
  if (paint?.paint_id) ownedPaintIds.add(paint.paint_id);
}

const recipeFiles = await yamlFiles('data/workshop/recipes');
const recipeIds = new Set();
const recipeCatalogItemIds = new Set();
let recipeCount = 0;
for (const path of recipeFiles) {
  const document = await readYaml(path);
  for (const [index, recipe] of requireArray(document?.recipes, `${path}:recipes`).entries()) {
    recipeCount += 1;
    const location = `${path}:recipes[${index}]`;
    requireValue(recipe?.id, location, 'id required');
    requireValue(recipe?.catalog_item_id, location, 'catalog_item_id required');
    if (recipe?.id && recipeIds.has(recipe.id)) errors.push(`${location}: duplicate recipe id ${recipe.id}`);
    if (recipe?.id) recipeIds.add(recipe.id);
    if (recipe?.catalog_item_id && !catalogItemsById.has(recipe.catalog_item_id)) errors.push(`${location}: unknown catalog item ${recipe.catalog_item_id}`);
    if (recipe?.catalog_item_id && recipeCatalogItemIds.has(recipe.catalog_item_id)) errors.push(`${location}: multiple recipes for ${recipe.catalog_item_id}`);
    if (recipe?.catalog_item_id) recipeCatalogItemIds.add(recipe.catalog_item_id);
    for (const paint of requireArray(recipe?.paints, `${location}:paints`)) {
      if (paint?.paint_id) {
        if (!marketPaintIds.has(paint.paint_id)) errors.push(`${location}: unknown recipe paint ${paint.paint_id}`);
      } else if (paint?.pending_import === true) {
        requireValue(paint?.requested_paint?.brand, location, 'requested_paint.brand required for pending import');
        requireValue(paint?.requested_paint?.name, location, 'requested_paint.name required for pending import');
      } else {
        errors.push(`${location}: paint_id required unless pending_import is true`);
      }
    }
    requireArray(recipe?.preparation, `${location}:preparation`);
    requireArray(recipe?.painting, `${location}:painting`);
  }
}

const jsonlFiles = (await readdir('data/ledger/events'))
  .filter((name) => name.endsWith('.jsonl'))
  .map((name) => join('data/ledger/events', name).replaceAll('\\', '/'));
if (!jsonlFiles.length) errors.push('data/ledger/events: at least one JSONL ledger is required');
const eventIds = new Set();
const workshopItemsByProject = new Map();
let eventCount = 0;
for (const path of jsonlFiles) {
  const lines = (await readFile(path, 'utf8')).split(/\r?\n/u).filter(Boolean);
  for (const [index, line] of lines.entries()) {
    const location = `${path}:${index + 1}`;
    let event;
    try {
      event = JSON.parse(line);
    } catch {
      errors.push(`${location}: invalid JSON event`);
      continue;
    }
    eventCount += 1;
    for (const field of ['event_id', 'event_type', 'occurred_at', 'recorded_at', 'aggregate_type', 'aggregate_id', 'actor', 'payload']) requireValue(event?.[field], location, `${field} required`);
    if (event?.event_id && eventIds.has(event.event_id)) errors.push(`${location}: duplicate event_id ${event.event_id}`);
    if (event?.event_id) eventIds.add(event.event_id);
    if (event?.event_type === 'workshop_item.added') {
      requireValue(event?.project_id, location, 'project_id required for workshop_item.added');
      requireValue(event?.payload?.catalog_item_id, location, 'payload.catalog_item_id required');
      if (event?.payload?.catalog_item_id && !catalogItemsById.has(event.payload.catalog_item_id)) errors.push(`${location}: unknown catalog item ${event.payload.catalog_item_id}`);
      workshopItemsByProject.set(event.project_id, (workshopItemsByProject.get(event.project_id) ?? 0) + 1);
    }
  }
}

for (const [gameId, game] of gamesById) {
  const expected = game?.expected_paintable_count;
  const actual = workshopItemsByProject.get(gameId) ?? 0;
  if (!Number.isInteger(expected) || expected < 1) errors.push(`data/market/games:${gameId}: expected_paintable_count must be a positive integer`);
  else if (actual !== expected) errors.push(`data/ledger/events:${gameId}: ${actual} physical workshop items, expected ${expected}`);
}

if (recipeCatalogItemIds.size !== catalogItemsById.size) warnings.push(`${catalogItemsById.size - recipeCatalogItemIds.size} market catalog item(s) have no painting recipe`);

for (const warning of warnings) console.warn(`WARNING ${warning}`);
if (errors.length) {
  for (const error of errors) console.error(`ERROR ${error}`);
  process.exitCode = 1;
} else {
  console.log(`Valid repositories: ${marketPaints.length} market paints, ${ownedPaintIds.size} owned paints, ${gamesById.size} game(s), ${catalogItemsById.size} catalog items, ${recipeCount} recipes, ${eventCount} ledger events.`);
}
