import { addPaint, listPaints } from '@/lib/paint-store';
import { env } from 'cloudflare:workers';

function stringField(value: unknown, fallback = '') {
  return typeof value === 'string' ? value : fallback;
}

export async function GET() {
  try {
    return Response.json({ paints: await listPaints() });
  } catch (error) {
    console.error(error);
    return Response.json({ error: 'Impossible de charger le référentiel.' }, { status: 500 });
  }
}

export async function POST(request: Request) {
  try {
    const body = await request.json() as Record<string, unknown>;
    const name = stringField(body.name).trim();
    const brand = stringField(body.brand).trim();
    if (!name || !brand) {
      return Response.json({ error: 'La marque et le nom sont requis.' }, { status: 400 });
    }

    const paint = await addPaint({
      brand,
      range: stringField(body.range).trim(),
      reference: stringField(body.reference).trim(),
      name,
      colorHex: /^#[0-9a-f]{6}$/i.test(stringField(body.colorHex)) ? stringField(body.colorHex) : '#7a746b',
      finish: stringField(body.finish, 'mat'),
      medium: stringField(body.medium, 'acrylique'),
      quantity: Math.max(1, Number(body.quantity) || 1),
      tags: Array.isArray(body.tags) ? body.tags.filter((tag): tag is string => typeof tag === 'string') : [],
      notes: stringField(body.notes),
    });
    return Response.json({ paint }, { status: 201 });
  } catch (error) {
    console.error(error);
    return Response.json({ error: 'Impossible d’ajouter cette peinture.' }, { status: 500 });
  }
}

export async function DELETE(request: Request) {
  try {
    const body = await request.json() as { ids?: unknown };
    const ids = Array.isArray(body.ids) ? body.ids.map(String).filter(Boolean) : [];
    if (!ids.length) {
      return Response.json({ error: 'Aucun identifiant fourni.' }, { status: 400 });
    }
    await env.DB.batch(ids.map((id) => env.DB.prepare('DELETE FROM paints WHERE id = ?').bind(id)));
    return Response.json({ deleted: ids.length });
  } catch (error) {
    console.error(error);
    return Response.json({ error: 'Impossible de supprimer ces peintures.' }, { status: 500 });
  }
}
