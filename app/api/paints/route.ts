import { paintCatalog } from '@/lib/catalog';

export function GET() {
  return Response.json({ paints: paintCatalog, source: 'data/peintures.yaml' });
}

export function POST() {
  return Response.json({ error: 'Référentiel en lecture seule. Modifiez data/peintures.yaml via le skill import-miniature-paints.' }, { status: 405 });
}

export function DELETE() {
  return Response.json({ error: 'Référentiel en lecture seule.' }, { status: 405 });
}
