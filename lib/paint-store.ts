import { env } from 'cloudflare:workers';
import { enrichPaint, samplePaints, type ManufacturerInfo, type Paint } from './sample-data';

type PaintInput = Omit<Paint, 'id' | 'createdAt' | 'updatedAt' | keyof ManufacturerInfo> & { id?: string };

async function ensureSchema() {
  const d1 = env.DB;
  await d1.batch([
    d1.prepare(`CREATE TABLE IF NOT EXISTS paints (
      id TEXT PRIMARY KEY,
      brand TEXT NOT NULL,
      range TEXT,
      reference TEXT,
      name TEXT NOT NULL,
      color_hex TEXT NOT NULL,
      finish TEXT NOT NULL DEFAULT 'mat',
      medium TEXT NOT NULL DEFAULT 'acrylique',
      quantity INTEGER NOT NULL DEFAULT 1,
      tags TEXT NOT NULL DEFAULT '[]',
      notes TEXT NOT NULL DEFAULT '',
      created_at TEXT NOT NULL,
      updated_at TEXT NOT NULL
    )`),
    d1.prepare(`CREATE TABLE IF NOT EXISTS imports (
      id TEXT PRIMARY KEY,
      object_key TEXT NOT NULL,
      filename TEXT NOT NULL,
      content_type TEXT NOT NULL,
      status TEXT NOT NULL DEFAULT 'a_verifier',
      created_at TEXT NOT NULL
    )`),
    d1.prepare('CREATE INDEX IF NOT EXISTS idx_paints_brand_name ON paints (brand, name)'),
    d1.prepare('CREATE INDEX IF NOT EXISTS idx_imports_status ON imports (status)'),
  ]);

  await d1.batch(samplePaints.map((paint) => d1.prepare(`DELETE FROM paints
      WHERE id = ?
        AND EXISTS (
          SELECT 1 FROM paints AS existing
          WHERE lower(existing.brand) = lower(?)
            AND lower(existing.name) = lower(?)
            AND existing.id <> ?
        )`).bind(paint.id, paint.brand, paint.name, paint.id)));

  await d1.batch(samplePaints.map((paint) => d1.prepare(`INSERT INTO paints
      (id, brand, range, reference, name, color_hex, finish, medium, quantity, tags, notes, created_at, updated_at)
      SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
      WHERE NOT EXISTS (
        SELECT 1 FROM paints AS existing
        WHERE lower(existing.brand) = lower(?)
          AND lower(existing.name) = lower(?)
      )`).bind(
      paint.id, paint.brand, paint.range, paint.reference, paint.name, paint.colorHex, paint.finish,
      paint.medium, paint.quantity, JSON.stringify(paint.tags), paint.notes, paint.createdAt, paint.updatedAt,
      paint.brand, paint.name,
    )));
}

function rowString(value: unknown, fallback = '') {
  return typeof value === 'string' ? value : fallback;
}

function fromRow(row: Record<string, unknown>): Paint {
  return enrichPaint({
    id: rowString(row.id),
    brand: rowString(row.brand),
    range: rowString(row.range),
    reference: rowString(row.reference),
    name: rowString(row.name),
    colorHex: rowString(row.color_hex),
    finish: rowString(row.finish),
    medium: rowString(row.medium),
    quantity: Number(row.quantity),
    tags: JSON.parse(rowString(row.tags, '[]')) as string[],
    notes: rowString(row.notes),
    createdAt: rowString(row.created_at),
    updatedAt: rowString(row.updated_at),
  });
}

export async function listPaints() {
  await ensureSchema();
  const result = await env.DB.prepare('SELECT * FROM paints ORDER BY updated_at DESC, brand, name').all();
  return result.results.map((row) => fromRow(row as Record<string, unknown>));
}

export async function addPaint(input: PaintInput) {
  await ensureSchema();
  const id = input.id || crypto.randomUUID();
  const now = new Date().toISOString();
  await env.DB.prepare(`INSERT INTO paints
    (id, brand, range, reference, name, color_hex, finish, medium, quantity, tags, notes, created_at, updated_at)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`).bind(
    id, input.brand, input.range, input.reference, input.name, input.colorHex, input.finish,
    input.medium, input.quantity, JSON.stringify(input.tags), input.notes, now, now,
  ).run();
  return enrichPaint({ ...input, id, createdAt: now, updatedAt: now });
}
