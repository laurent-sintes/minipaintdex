import { readFile, readdir } from 'node:fs/promises';
import { join } from 'node:path';
import { parse } from 'yaml';

const errors = [];
const warnings = [];
const kebabId = /^[a-z0-9]+(?:-[a-z0-9]+)*$/u;
const workflowStages = new Set(['preparation', 'priming', 'pre_highlight', 'painting', 'finishing', 'basing']);
const eventTypes = new Set([
  'project.created', 'workshop_item.added', 'workshop_item.named',
  'workflow.stage.started', 'workflow.stage.completed', 'workflow.stage.skipped', 'workflow.stage.reopened',
  'workshop_recipe.created', 'workshop_recipe.validated', 'workshop_recipe.activated',
  'workshop_recipe.superseded', 'workshop_recipe.archived', 'recipe.assigned',
  'paint.used', 'photo.added', 'photo.caption.updated', 'photo.removed',
  'comment.added', 'milestone.reached',
]);
const technicalPaintTypes = new Set(['technical_effect', 'primer', 'wash_shade', 'ink', 'auxiliary']);

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
for (const section of ['metadata', 'brand', 'units', 'navigation', 'header', 'home', 'workflow', 'market', 'collection', 'projects', 'shopping', 'imports', 'paint_detail', 'miniature_detail', 'project_detail', 'paint_page', 'errors']) {
  if (!site?.[section] || typeof site[section] !== 'object') errors.push(`${sitePath}:${section}: required section`);
}
for (const service of ['market_paints', 'market_games', 'workshop_paints', 'workshop_projects', 'shopping', 'imports']) {
  for (const field of ['title', 'description', 'action']) requireValue(site?.home?.[service]?.[field], `${sitePath}:home.${service}`, `${field} required`);
}
for (const [location, value] of [
  [`${sitePath}:projects.statuses`, site?.projects?.statuses],
  [`${sitePath}:shopping.priorities`, site?.shopping?.priorities],
]) {
  if (!value || typeof value !== 'object') errors.push(`${location}: required localized labels`);
}

const paintCatalogPath = 'data/market/paints/catalog.yaml';
const paintCatalog = await readYaml(paintCatalogPath);
if (paintCatalog?.schema_version !== 1) errors.push(`${paintCatalogPath}: schema_version must be 1`);
const marketPaints = requireArray(paintCatalog?.paints, `${paintCatalogPath}:paints`);
const marketPaintIds = new Set();
for (const [index, paint] of marketPaints.entries()) {
  const location = `${paintCatalogPath}:paints[${index}]`;
  for (const field of ['id', 'brand', 'manufacturer', 'range', 'functional_type', 'name', 'data_status', 'lifecycle_status']) requireValue(paint?.[field], location, `${field} required`);
  if (paint?.id && !kebabId.test(paint.id)) errors.push(`${location}: id must be lowercase kebab-case`);
  if (paint?.data_status && !['confirmed', 'review', 'unknown'].includes(paint.data_status)) errors.push(`${location}: invalid data_status ${paint.data_status}`);
  if (technicalPaintTypes.has(paint?.functional_type)) {
    requireValue(paint?.usage_instructions?.summary, location, 'usage_instructions.summary required for technical paint');
    if (requireArray(paint?.usage_instructions?.steps, `${location}:usage_instructions.steps`).length === 0) errors.push(`${location}: usage_instructions.steps must explain how to use a technical paint`);
    requireArray(paint?.usage_instructions?.tips, `${location}:usage_instructions.tips`);
  }
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
  if (game?.schema_version !== 1) errors.push(`${path}: schema_version must be 1`);
  for (const field of ['id', 'name', 'game', 'scope']) requireValue(game?.[field], path, `${field} required`);
  if (game?.id && gamesById.has(game.id)) errors.push(`${path}: duplicate game id ${game.id}`);
  if (game?.id) gamesById.set(game.id, game);
  for (const [index, item] of requireArray(game?.catalog_items, `${path}:catalog_items`).entries()) {
    const location = `${path}:catalog_items[${index}]`;
    for (const field of ['id', 'game_id', 'name', 'kind']) requireValue(item?.[field], location, `${field} required`);
    if (item?.id && !kebabId.test(item.id)) errors.push(`${location}: id must be lowercase kebab-case`);
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
const workshopPaintDocument = await readYaml(workshopPaintsPath);
if (workshopPaintDocument?.schema_version !== 1) errors.push(`${workshopPaintsPath}: schema_version must be 1`);
const workshopPaints = requireArray(workshopPaintDocument?.paints, `${workshopPaintsPath}:paints`);
const ownedPaintIds = new Set();
for (const [index, paint] of workshopPaints.entries()) {
  const location = `${workshopPaintsPath}:paints[${index}]`;
  requireValue(paint?.paint_id, location, 'paint_id required');
  if (!Number.isInteger(paint?.quantity) || paint.quantity < 1) errors.push(`${location}: quantity must be a positive integer`);
  if (paint?.paint_id && !marketPaintIds.has(paint.paint_id)) errors.push(`${location}: unknown market paint ${paint.paint_id}`);
  if (paint?.paint_id && ownedPaintIds.has(paint.paint_id)) errors.push(`${location}: duplicate owned paint ${paint.paint_id}`);
  if (paint?.paint_id) ownedPaintIds.add(paint.paint_id);
}

const guideFiles = await yamlFiles('data/market/painting-guides');
const guideIds = new Set();
const guideCatalogItemIds = new Set();
let guideCount = 0;
for (const path of guideFiles) {
  const document = await readYaml(path);
  if (document?.schema_version !== 1) errors.push(`${path}: schema_version must be 1`);
  for (const [index, guide] of requireArray(document?.painting_guides, `${path}:painting_guides`).entries()) {
    guideCount += 1;
    const location = `${path}:painting_guides[${index}]`;
    requireValue(guide?.id, location, 'id required');
    requireValue(guide?.catalog_item_id, location, 'catalog_item_id required');
    requireValue(guide?.knowledge_status, location, 'knowledge_status required');
    if (!Number.isInteger(guide?.version) || guide.version < 1) errors.push(`${location}: version must be a positive integer`);
    if (!['documented', 'observed', 'inferred'].includes(guide?.knowledge_status)) errors.push(`${location}: invalid knowledge_status ${guide?.knowledge_status}`);
    if (guide?.id && guideIds.has(guide.id)) errors.push(`${location}: duplicate painting guide id ${guide.id}`);
    if (guide?.id) guideIds.add(guide.id);
    if (guide?.catalog_item_id && !catalogItemsById.has(guide.catalog_item_id)) errors.push(`${location}: unknown catalog item ${guide.catalog_item_id}`);
    if (guide?.catalog_item_id && guideCatalogItemIds.has(guide.catalog_item_id)) errors.push(`${location}: multiple default guides for ${guide.catalog_item_id}`);
    if (guide?.catalog_item_id) guideCatalogItemIds.add(guide.catalog_item_id);
    const slotIds = new Set();
    for (const slot of requireArray(guide?.slots, `${location}:slots`)) {
      requireValue(slot?.id, location, 'slot.id required');
      if (slot?.id && slotIds.has(slot.id)) errors.push(`${location}: duplicate slot id ${slot.id}`);
      if (slot?.id) slotIds.add(slot.id);
      if (slot?.market_paint_id) {
        if (!marketPaintIds.has(slot.market_paint_id)) errors.push(`${location}: unknown market paint ${slot.market_paint_id}`);
      } else if (slot?.pending_import === true) {
        requireValue(slot?.requested_paint?.brand, location, 'requested_paint.brand required for pending import');
        requireValue(slot?.requested_paint?.name, location, 'requested_paint.name required for pending import');
      } else {
        errors.push(`${location}: market_paint_id required unless pending_import is true`);
      }
    }
    requireArray(guide?.source_refs, `${location}:source_refs`);
    requireValue(guide?.provenance?.method, location, 'provenance.method required');
    requireArray(guide?.preparation, `${location}:preparation`);
    requireArray(guide?.painting, `${location}:painting`);
  }
}

const jsonlFiles = (await readdir('data/ledger/events'))
  .filter((name) => name.endsWith('.jsonl'))
  .map((name) => join('data/ledger/events', name).replaceAll('\\', '/'));
if (!jsonlFiles.length) errors.push('data/ledger/events: at least one JSONL ledger is required');
const eventIds = new Set();
const workshopItemIds = new Set();
const workshopRecipeIds = new Set();
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
    for (const field of ['event_id', 'schema_version', 'event_type', 'occurred_at', 'recorded_at', 'aggregate_type', 'aggregate_id', 'actor', 'correlation_id', 'payload']) requireValue(event?.[field], location, `${field} required`);
    if (event?.schema_version !== 1) errors.push(`${location}: schema_version must be 1`);
    if (event?.event_type && !eventTypes.has(event.event_type)) errors.push(`${location}: unsupported event_type ${event.event_type}`);
    if (event?.occurred_at && Number.isNaN(Date.parse(event.occurred_at))) errors.push(`${location}: occurred_at must be an ISO timestamp`);
    if (event?.recorded_at && Number.isNaN(Date.parse(event.recorded_at))) errors.push(`${location}: recorded_at must be an ISO timestamp`);
    requireValue(event?.actor?.type, location, 'actor.type required');
    requireValue(event?.actor?.id, location, 'actor.id required');
    if (event?.event_id && eventIds.has(event.event_id)) errors.push(`${location}: duplicate event_id ${event.event_id}`);
    if (event?.event_id) eventIds.add(event.event_id);
    if (event?.event_type === 'workshop_item.added') {
      requireValue(event?.project_id, location, 'project_id required for workshop_item.added');
      requireValue(event?.payload?.catalog_item_id, location, 'payload.catalog_item_id required');
      if (event?.payload?.catalog_item_id && !catalogItemsById.has(event.payload.catalog_item_id)) errors.push(`${location}: unknown catalog item ${event.payload.catalog_item_id}`);
      if (workshopItemIds.has(event.aggregate_id)) errors.push(`${location}: duplicate workshop item ${event.aggregate_id}`);
      if (event.aggregate_id) workshopItemIds.add(event.aggregate_id);
      workshopItemsByProject.set(event.project_id, (workshopItemsByProject.get(event.project_id) ?? 0) + 1);
    }
    if (event?.event_type?.startsWith('workflow.stage.')) {
      if (!workshopItemIds.has(event.aggregate_id)) errors.push(`${location}: workflow event references an unknown workshop item ${event.aggregate_id}`);
      if (!workflowStages.has(event?.payload?.stage)) errors.push(`${location}: invalid payload.stage ${event?.payload?.stage}`);
      if (event.event_type === 'workflow.stage.skipped') requireValue(event?.payload?.reason, location, 'payload.reason required when skipping a stage');
    }
    if (event?.event_type === 'workshop_recipe.created') {
      requireValue(event?.payload?.catalog_item_id, location, 'payload.catalog_item_id required');
      requireValue(event?.payload?.display_name, location, 'payload.display_name required');
      if (!Number.isInteger(event?.payload?.version) || event.payload.version < 1) errors.push(`${location}: payload.version must be positive`);
      if (workshopRecipeIds.has(event.aggregate_id)) errors.push(`${location}: duplicate workshop recipe ${event.aggregate_id}`);
      if (event.aggregate_id) workshopRecipeIds.add(event.aggregate_id);
    }
    if (event?.event_type?.startsWith('workshop_recipe.') && event.event_type !== 'workshop_recipe.created' && !workshopRecipeIds.has(event.aggregate_id)) {
      errors.push(`${location}: recipe lifecycle event references unknown recipe ${event.aggregate_id}`);
    }
    if (event?.event_type === 'recipe.assigned') {
      if (!workshopItemIds.has(event.aggregate_id)) errors.push(`${location}: assignment references unknown workshop item ${event.aggregate_id}`);
      if (!workshopRecipeIds.has(event?.payload?.recipe_id)) errors.push(`${location}: assignment references unknown recipe ${event?.payload?.recipe_id}`);
    }
  }
}

for (const [gameId, game] of gamesById) {
  const expected = game?.expected_paintable_count;
  const actual = workshopItemsByProject.get(gameId) ?? 0;
  if (!Number.isInteger(expected) || expected < 1) errors.push(`data/market/games:${gameId}: expected_paintable_count must be a positive integer`);
  else if (actual !== expected) errors.push(`data/ledger/events:${gameId}: ${actual} physical workshop items, expected ${expected}`);
}

if (guideCatalogItemIds.size !== catalogItemsById.size) warnings.push(`${catalogItemsById.size - guideCatalogItemIds.size} market catalog item(s) have no painting guide`);

for (const warning of warnings) console.warn(`WARNING ${warning}`);
if (errors.length) {
  for (const error of errors) console.error(`ERROR ${error}`);
  process.exitCode = 1;
} else {
  console.log(`Valid repositories: ${marketPaints.length} market paints, ${ownedPaintIds.size} owned paints, ${gamesById.size} game(s), ${catalogItemsById.size} catalog items, ${guideCount} market painting guides, ${workshopRecipeIds.size} workshop recipes, ${eventCount} ledger events.`);
}
