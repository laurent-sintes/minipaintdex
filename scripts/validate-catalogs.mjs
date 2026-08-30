import { readFile, readdir } from 'node:fs/promises';
import { join } from 'node:path';
import { parse } from 'yaml';

const errors = [];
const warnings = [];
const ids = new Set();

function requireValue(value, location, message) {
  if (value === undefined || value === null || value === '') errors.push(`${location}: ${message}`);
}

function requireArray(value, location) {
  if (!Array.isArray(value)) errors.push(`${location}: tableau requis`);
  return Array.isArray(value) ? value : [];
}

const paintsDocument = parse(await readFile('data/peintures.yaml', 'utf8'));
const paints = requireArray(paintsDocument?.peintures, 'data/peintures.yaml:peintures');
for (const [index, paint] of paints.entries()) {
  const location = `data/peintures.yaml:peintures[${index}]`;
  requireValue(paint?.id, location, 'id requis');
  requireValue(paint?.nom, location, 'nom requis');
  requireValue(paint?.marque_canonique ?? paint?.marque_observee, location, 'marque requise');
  if (paint?.couleur_hex && !/^#[0-9a-f]{6}$/i.test(paint.couleur_hex)) errors.push(`${location}: couleur_hex invalide`);
  if (paint?.id && ids.has(`paint:${paint.id}`)) errors.push(`${location}: id dupliqué ${paint.id}`);
  ids.add(`paint:${paint?.id}`);
}

const projectNames = (await readdir('data/projects')).filter((name) => name.endsWith('.yaml'));
if (!projectNames.length) errors.push('data/projects: au moins un projet YAML est requis');

let itemCount = 0;
for (const filename of projectNames) {
  const location = join('data/projects', filename).replaceAll('\\', '/');
  const project = parse(await readFile(location, 'utf8'));
  requireValue(project?.id, location, 'id requis');
  requireValue(project?.name, location, 'name requis');
  requireValue(project?.game, location, 'game requis');
  requireValue(project?.scope, location, 'scope requis');
  if (project?.id && ids.has(`project:${project.id}`)) errors.push(`${location}: id de projet dupliqué ${project.id}`);
  ids.add(`project:${project?.id}`);
  const items = requireArray(project?.items, `${location}:items`);
  itemCount += items.length;
  for (const [index, item] of items.entries()) {
    const itemLocation = `${location}:items[${index}]`;
    requireValue(item?.id, itemLocation, 'id requis');
    requireValue(item?.name, itemLocation, 'name requis');
    requireValue(item?.kind, itemLocation, 'kind requis');
    if (!Number.isInteger(item?.quantity) || item.quantity < 1) errors.push(`${itemLocation}: quantity doit être un entier positif`);
    requireArray(item?.paints, `${itemLocation}:paints`);
    requireArray(item?.preparation, `${itemLocation}:preparation`);
    requireArray(item?.painting, `${itemLocation}:painting`);
    for (const [imageIndex, image] of requireArray(item?.reference_images, `${itemLocation}:reference_images`).entries()) {
      const imageLocation = `${itemLocation}:reference_images[${imageIndex}]`;
      requireValue(image?.url, imageLocation, 'url requise');
      requireValue(image?.page_url, imageLocation, 'page_url requise pour la traçabilité');
      requireValue(image?.credit, imageLocation, 'credit requis');
    }
    const itemKey = `item:${project?.id}:${item?.id}`;
    if (item?.id && ids.has(itemKey)) errors.push(`${itemLocation}: id de figurine dupliqué ${item.id}`);
    ids.add(itemKey);
    for (const paint of requireArray(item?.paints, `${itemLocation}:paints`)) {
      requireValue(paint?.brand, itemLocation, 'paint.brand requis');
      requireValue(paint?.name, itemLocation, 'paint.name requis');
      if (paint?.color_hex && !/^#[0-9a-f]{6}$/i.test(paint.color_hex)) errors.push(`${itemLocation}: paint.color_hex invalide`);
      const exists = paints.some((candidate) => candidate?.nom?.toLocaleLowerCase('fr') === paint?.name?.toLocaleLowerCase('fr'));
      if (!exists && !paint?.pending_import) warnings.push(`${itemLocation}: peinture absente du référentiel et non marquée pending_import: ${paint?.name}`);
    }
  }
}

for (const warning of warnings) console.warn(`AVERTISSEMENT ${warning}`);
if (errors.length) {
  for (const error of errors) console.error(`ERREUR ${error}`);
  process.exitCode = 1;
} else {
  console.log(`Référentiels valides : ${paints.length} peintures, ${projectNames.length} projet(s), ${itemCount} fiche(s).`);
}
