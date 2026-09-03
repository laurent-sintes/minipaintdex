import { readFile, readdir } from 'node:fs/promises';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parse } from 'yaml';

const projectRoot = fileURLToPath(new URL('../../', import.meta.url));
const errors = [];
const warnings = [];
const kebabId = /^[a-z0-9]+(?:-[a-z0-9]+)*$/u;
const workflowStages = new Set(['preparation', 'priming', 'pre_highlight', 'painting', 'finishing', 'basing']);
const eventTypes = new Set([
  'paint_pot.registered', 'paint_pot.observed', 'paint_pot.opened', 'paint_pot.possession_changed', 'paint_pot.note_added', 'paint_pot.photo_added',
  'workshop.created', 'painting_project.created', 'workshop_item.added', 'workshop_item.named',
  'workflow.stage.started', 'workflow.stage.completed', 'workflow.stage.skipped', 'workflow.stage.reopened',
  'workshop_recipe.created', 'workshop_recipe.validated', 'workshop_recipe.activated',
  'workshop_recipe.superseded', 'workshop_recipe.archived', 'recipe.assigned',
  'paint.used', 'photo.added', 'photo.caption.updated', 'photo.removed',
  'workshop_item.comment_added', 'workshop_item.photo_added', 'shopping_item.status_changed',
  'comment.added', 'milestone.reached',
]);
const technicalPaintRoles = new Set(['technical_effect', 'primer', 'wash', 'ink', 'varnish', 'medium', 'auxiliary', 'pigment']);
const paintRoles = new Set(['color_paint', ...technicalPaintRoles]);
const applicationMethods = new Set(['brush', 'airbrush', 'spray', 'marker']);
const applicationSystems = new Set(['conventional_layering', 'one_coat_shading', 'washing', 'priming', 'effect_application', 'unknown']);
const coverages = new Set(['opaque', 'semi_opaque', 'translucent', 'transparent', 'unknown']);
const finishes = new Set(['matte', 'satin', 'gloss', 'unknown']);
const effects = new Set(['metallic', 'fluorescent', 'pearlescent']);
const undercoats = new Set(['light', 'dark', 'any', 'unknown']);
const mediums = new Set(['water_based_acrylic', 'acrylic', 'alcohol_based', 'oil', 'enamel', 'unknown']);
const imageQualities = new Set(['official_photo', 'retailer_photo', 'owned_photo', 'generic_visual', 'color_swatch', 'none']);
const imageQualityLimitationCodes = new Set([
  'official-photo-not-published', 'official-source-unavailable', 'official-candidate-rejected',
  'official-reference-unmatched', 'better-source-not-found', 'manually-provided',
  'historical-reason-not-recorded',
]);

function requireValue(value, location, message) {
  if (value === undefined || value === null || value === '') errors.push(`${location}: ${message}`);
}

function requireArray(value, location) {
  if (!Array.isArray(value)) errors.push(`${location}: array required`);
  return Array.isArray(value) ? value : [];
}

async function readYaml(path) {
  return parse(await readFile(join(projectRoot, path), 'utf8'));
}

async function yamlFiles(directory) {
  return (await readdir(join(projectRoot, directory)))
    .filter((name) => name.endsWith('.yaml'))
    .map((name) => join(directory, name).replaceAll('\\', '/'));
}

const sitePath = 'data/site/fr.yaml';
const site = await readYaml(sitePath);
for (const section of ['metadata', 'brand', 'units', 'navigation', 'header', 'home', 'workflow', 'market', 'workshop', 'product_detail', 'collection', 'shopping', 'paint_detail', 'errors']) {
  if (!site?.[section] || typeof site[section] !== 'object') errors.push(`${sitePath}:${section}: required section`);
}
for (const service of ['paint_products', 'market_paintable_products', 'workshop_paints', 'workshop_admin', 'shopping']) {
  for (const field of ['title', 'description', 'action']) requireValue(site?.home?.[service]?.[field], `${sitePath}:home.${service}`, `${field} required`);
}
for (const [location, value] of [
  [`${sitePath}:market.kind_labels`, site?.market?.kind_labels],
  [`${sitePath}:workshop.event_labels`, site?.workshop?.event_labels],
  [`${sitePath}:shopping.priorities`, site?.shopping?.priorities],
  [`${sitePath}:collection.value_labels`, site?.collection?.value_labels],
]) {
  if (!value || typeof value !== 'object') errors.push(`${location}: required localized labels`);
}

const paintCatalogPaths = await yamlFiles('data/market/paints');
if (!paintCatalogPaths.length) errors.push('data/market/paints: at least one brand catalog is required');
const paintProducts = [];
const paintProductIds = new Set();
for (const paintCatalogPath of paintCatalogPaths) {
  const paintCatalog = await readYaml(paintCatalogPath);
  if (paintCatalog?.schema_version !== 1) errors.push(`${paintCatalogPath}: schema_version must be 1`);
  requireValue(paintCatalog?.brand, paintCatalogPath, 'brand required');
  const usageGuides = new Map((paintCatalog?.paint_usage_guides ?? []).map(guide => [guide.id, guide]));
  if (usageGuides.size !== (paintCatalog?.paint_usage_guides ?? []).length) errors.push(`${paintCatalogPath}: duplicate usage guide`);
  for (const guide of usageGuides.values()) {
    if (guide.schema_version !== 1 || !Number.isInteger(guide.revision) || guide.revision < 1 || !kebabId.test(guide.id)) errors.push(`${paintCatalogPath}: invalid guide identity/revision`);
    if (guide.brand !== paintCatalog.brand || !Array.isArray(guide.ranges) || !guide.ranges.length) errors.push(`${paintCatalogPath}: invalid guide scope`);
  }
  const brandPaints = requireArray(paintCatalog?.paints, `${paintCatalogPath}:paints`);
  paintProducts.push(...brandPaints);
  for (const [index, paint] of brandPaints.entries()) {
    const location = `${paintCatalogPath}:paints[${index}]`;
    if (paint?.schema_version !== 1) errors.push(`${location}: schema_version must be 1`);
    for (const field of ['id', 'brand', 'manufacturer', 'range', 'profile', 'name', 'data_status', 'lifecycle_status']) requireValue(paint?.[field], location, `${field} required`);
    if (paint?.brand !== paintCatalog?.brand) errors.push(`${location}: brand must match file brand ${paintCatalog?.brand}`);
    if (paint?.id && !kebabId.test(paint.id)) errors.push(`${location}: id must be lowercase kebab-case`);
    if (paint?.data_status && !['confirmed', 'review', 'unknown'].includes(paint.data_status)) errors.push(`${location}: invalid data_status ${paint.data_status}`);
    const profile = paint?.profile ?? {};
    const roles = requireArray(profile.roles, `${location}:profile.roles`);
    const methods = requireArray(profile.application_methods, `${location}:profile.application_methods`);
    const paintEffects = requireArray(profile.effects, `${location}:profile.effects`);
    for (const role of roles) if (!paintRoles.has(role)) errors.push(`${location}: unsupported profile role ${role}`);
    for (const method of methods) if (!applicationMethods.has(method)) errors.push(`${location}: unsupported application method ${method}`);
    if (!applicationSystems.has(profile.application_system)) errors.push(`${location}: unsupported application system ${profile.application_system}`);
    if (!coverages.has(profile.coverage)) errors.push(`${location}: unsupported coverage ${profile.coverage}`);
    if (!finishes.has(profile.finish)) errors.push(`${location}: unsupported finish ${profile.finish}`);
    for (const effect of paintEffects) if (!effects.has(effect)) errors.push(`${location}: unsupported effect ${effect}`);
    if (!undercoats.has(profile.undercoat?.tone)) errors.push(`${location}: unsupported undercoat ${profile.undercoat?.tone}`);
    if (typeof profile.undercoat?.pre_highlighted_surface_recommended !== 'boolean') errors.push(`${location}: profile.undercoat.pre_highlighted_surface_recommended must be boolean`);
    if (!mediums.has(profile.medium)) errors.push(`${location}: unsupported medium ${profile.medium}`);
    for (const guideId of paint.usage_guide_ids ?? []) {
      const guide = usageGuides.get(guideId);
      if (!guide || !guide.ranges.includes(paint.range)) errors.push(`${location}: unknown/out-of-scope usage guide ${guideId}`);
    }
    const hasGuideSteps = (paint.usage_guide_ids ?? []).some(id => usageGuides.get(id)?.original?.steps?.length > 0);
    if (roles.some((role) => technicalPaintRoles.has(role)) && !hasGuideSteps) {
      requireValue(paint?.usage_instructions?.summary, location, 'usage_instructions.summary required for technical paint');
      if (requireArray(paint?.usage_instructions?.steps, `${location}:usage_instructions.steps`).length === 0) errors.push(`${location}: usage_instructions.steps must explain how to use a technical paint`);
      requireArray(paint?.usage_instructions?.tips, `${location}:usage_instructions.tips`);
    }
    if (paint?.color?.hex && !/^#[0-9a-f]{6}$/i.test(paint.color.hex)) errors.push(`${location}: color.hex must be a six-digit hexadecimal color`);
    const image = paint?.manufacturer_image;
    if (!image || typeof image !== 'object') errors.push(`${location}: manufacturer_image must be an object`);
    else {
      const quality = image.image_quality ?? 'none';
      if (!imageQualities.has(quality)) errors.push(`${location}: unsupported manufacturer_image.image_quality ${quality}`);
      const limitation = image.quality_limitation;
      if (quality === 'official_photo' && limitation !== undefined && limitation !== null) {
        errors.push(`${location}: manufacturer_image.quality_limitation must be absent for official_photo`);
      }
      if (quality !== 'official_photo') {
        if (!limitation || typeof limitation !== 'object' || Array.isArray(limitation)) {
          errors.push(`${location}: manufacturer_image.quality_limitation required for ${quality}`);
        } else {
          if (!imageQualityLimitationCodes.has(limitation.code)) errors.push(`${location}: unsupported manufacturer_image.quality_limitation.code ${limitation.code}`);
          requireValue(limitation.detail, location, 'manufacturer_image.quality_limitation.detail required');
          requireValue(limitation.observed_at, location, 'manufacturer_image.quality_limitation.observed_at required');
        }
      }
      if (quality !== 'none') requireValue(image.quality_verified_at, location, `manufacturer_image.quality_verified_at required for ${quality}`);
      if (['official_photo', 'retailer_photo', 'owned_photo', 'generic_visual'].includes(quality) && !image.path && !image.source_url) errors.push(`${location}: manufacturer_image requires a path or source_url for ${quality}`);
      if (quality === 'retailer_photo') {
        requireValue(image.credit, location, 'manufacturer_image.credit required for retailer_photo');
        requireValue(image.reference_url, location, 'manufacturer_image.reference_url required for retailer_photo');
      }
      for (const field of ['source_url', 'reference_url']) if (image[field] && !String(image[field]).startsWith('https://')) errors.push(`${location}: manufacturer_image.${field} must use HTTPS`);
    }
    if (paint?.mapping_report && paint.mapping_report.mapping_version !== 1) errors.push(`${location}: mapping_report.mapping_version must be 1`);
    for (const [snapshotIndex, snapshot] of requireArray(paint?.source_snapshots, `${location}:source_snapshots`).entries()) {
      const snapshotLocation = `${location}:source_snapshots[${snapshotIndex}]`;
      requireValue(snapshot?.provider, snapshotLocation, 'provider required');
      if (!String(snapshot?.url ?? '').startsWith('https://')) errors.push(`${snapshotLocation}: url must use HTTPS`);
      if (!snapshot?.payload || typeof snapshot.payload !== 'object' || Array.isArray(snapshot.payload)) errors.push(`${snapshotLocation}: payload must be an object`);
    }
    if (paint?.id && paintProductIds.has(paint.id)) errors.push(`${location}: duplicate id ${paint.id}`);
    if (paint?.id) paintProductIds.add(paint.id);
  }
}

const productFiles = await yamlFiles('data/market/paintable-products');
if (!productFiles.length) errors.push('data/market/paintable-products: at least one YAML paintable product is required');
const productsById = new Map();
const catalogItemsById = new Map();
for (const path of productFiles) {
  const product = await readYaml(path);
  if (product?.schema_version !== 1) errors.push(`${path}: schema_version must be 1`);
  for (const field of ['id', 'name', 'line', 'product_type', 'scope']) requireValue(product?.[field], path, `${field} required`);
  if (product?.id && productsById.has(product.id)) errors.push(`${path}: duplicate paintable product id ${product.id}`);
  if (product?.id) productsById.set(product.id, product);
  let quantity = 0;
  for (const [index, item] of requireArray(product?.catalog_items, `${path}:catalog_items`).entries()) {
    const location = `${path}:catalog_items[${index}]`;
    for (const field of ['id', 'product_id', 'name', 'kind', 'quantity']) requireValue(item?.[field], location, `${field} required`);
    if (item?.id && !kebabId.test(item.id)) errors.push(`${location}: id must be lowercase kebab-case`);
    if (item?.product_id !== product?.id) errors.push(`${location}: product_id must reference ${product?.id}`);
    if (!Number.isInteger(item?.quantity) || item.quantity < 1) errors.push(`${location}: quantity must be a positive integer`);
    else quantity += item.quantity;
    if (!['hero', 'enemy', 'scenery', 'vehicle', 'creature', 'accessory'].includes(item?.kind)) errors.push(`${location}: invalid English kind ${item?.kind}`);
    if (item?.id && catalogItemsById.has(item.id)) errors.push(`${location}: duplicate catalog item id ${item.id}`);
    if (item?.id) catalogItemsById.set(item.id, item);
    for (const [imageIndex, image] of requireArray(item?.reference_images, `${location}:reference_images`).entries()) {
      const imageLocation = `${location}:reference_images[${imageIndex}]`;
      for (const field of ['url', 'page_url', 'credit', 'license']) requireValue(image?.[field], imageLocation, `${field} required`);
    }
  }
  if (!Number.isInteger(product?.expected_paintable_count) || product.expected_paintable_count < 1) errors.push(`${path}: expected_paintable_count must be a positive integer`);
  else if (quantity !== product.expected_paintable_count) errors.push(`${path}: catalog quantities total ${quantity}, expected ${product.expected_paintable_count}`);
}

const paintPots = new Map();

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
        if (!paintProductIds.has(slot.market_paint_id)) errors.push(`${location}: unknown market paint ${slot.market_paint_id}`);
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

const jsonlFiles = (await readdir(join(projectRoot, 'data/ledger/events')))
  .filter((name) => name.endsWith('.jsonl')).sort()
  .map((name) => join('data/ledger/events', name).replaceAll('\\', '/'));
const eventIds = new Set();
const workshopItemIds = new Set();
const workshopRecipeIds = new Set();
const workshopItemsByProduct = new Map();
const paintingProjectsById = new Map();
let eventCount = 0;
for (const path of jsonlFiles) {
  const lines = (await readFile(join(projectRoot, path), 'utf8')).split(/\r?\n/u).filter(Boolean);
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
    if (event?.event_type?.startsWith('paint_pot.')) {
      const id = event.aggregate_id;
      if (event.aggregate_type !== 'paint_pot' || !kebabId.test(id)) errors.push(`${location}: invalid paint pot identity`);
      if (event.event_type === 'paint_pot.registered') {
        if (paintPots.has(id)) errors.push(`${location}: duplicate paint pot`);
        if (!paintProductIds.has(event.payload.paint_product_id)) errors.push(`${location}: unknown paint product`);
        paintPots.set(id, { productId: event.payload.paint_product_id, possession: 'owned' });
      } else if (!paintPots.has(id)) errors.push(`${location}: unknown paint pot`);
      if (event.event_type === 'paint_pot.possession_changed') {
        if (!['owned', 'given-away', 'discarded'].includes(event.payload.possession)) errors.push(`${location}: invalid possession`);
        if (paintPots.has(id)) paintPots.get(id).possession = event.payload.possession;
      }
      if (event.event_type === 'paint_pot.observed') {
        if (!['unknown', 'usable', 'thickened', 'dried'].includes(event.payload.condition)) errors.push(`${location}: invalid condition`);
        if (!['unknown', 'full', 'half', 'low', 'empty'].includes(event.payload.remaining_level)) errors.push(`${location}: invalid remaining level`);
      }
    }
    if (event?.event_type === 'painting_project.created') {
      requireValue(event?.payload?.workshop_id, location, 'payload.workshop_id required');
      requireValue(event?.payload?.paintable_product_id, location, 'payload.paintable_product_id required');
      requireValue(event?.payload?.name, location, 'payload.name required');
      if (event?.aggregate_id) paintingProjectsById.set(event.aggregate_id, event.payload.paintable_product_id);
    }
    if (event?.event_type === 'workshop_item.added') {
      const paintingProjectId = event?.payload?.painting_project_id;
      requireValue(paintingProjectId, location, 'payload.painting_project_id required');
      const productId = paintingProjectsById.get(paintingProjectId);
      requireValue(productId, location, 'painting_project_id must reference an earlier painting_project.created event');
      requireValue(event?.payload?.catalog_item_id, location, 'payload.catalog_item_id required');
      if (event?.payload?.catalog_item_id && !catalogItemsById.has(event.payload.catalog_item_id)) errors.push(`${location}: unknown catalog item ${event.payload.catalog_item_id}`);
      if (workshopItemIds.has(event.aggregate_id)) errors.push(`${location}: duplicate workshop item ${event.aggregate_id}`);
      if (event.aggregate_id) workshopItemIds.add(event.aggregate_id);
      workshopItemsByProduct.set(productId, (workshopItemsByProduct.get(productId) ?? 0) + 1);
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

for (const [productId, product] of productsById) {
  const expected = product?.expected_paintable_count;
  const actual = workshopItemsByProduct.get(productId) ?? 0;
  if (actual > 0 && actual !== expected) errors.push(`data/ledger/events:${productId}: ${actual} physical workshop items, expected ${expected}`);
}

if (guideCatalogItemIds.size !== catalogItemsById.size) warnings.push(`${catalogItemsById.size - guideCatalogItemIds.size} market catalog item(s) have no painting guide`);

const ownedPaintIds = new Set([...paintPots.values()].filter(pot => pot.possession === 'owned').map(pot => pot.productId));
for (const warning of warnings) console.warn(`WARNING ${warning}`);
if (errors.length) {
  for (const error of errors) console.error(`ERROR ${error}`);
  process.exitCode = 1;
} else {
  console.log(`Valid repositories: ${paintProducts.length} market paints, ${ownedPaintIds.size} owned paints, ${productsById.size} paintable product(s), ${catalogItemsById.size} catalog items, ${guideCount} market painting guides, ${workshopRecipeIds.size} workshop recipes, ${eventCount} ledger events.`);
}
