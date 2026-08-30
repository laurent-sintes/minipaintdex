import { env } from 'cloudflare:workers';

export async function POST(request: Request) {
  try {
    const formData = await request.formData();
    const file = formData.get('photo');
    if (!(file instanceof File)) {
      return Response.json({ error: 'Aucune photo reçue.' }, { status: 400 });
    }
    if (!file.type.startsWith('image/')) {
      return Response.json({ error: 'Le fichier doit être une image.' }, { status: 415 });
    }

    const id = crypto.randomUUID();
    const safeName = file.name.replace(/[^a-zA-Z0-9._-]/g, '-');
    const objectKey = `imports/${id}-${safeName}`;
    await env.FILES.put(objectKey, file.stream(), {
      httpMetadata: { contentType: file.type },
      customMetadata: { originalName: file.name },
    });

    await env.DB.prepare(`CREATE TABLE IF NOT EXISTS imports (
      id TEXT PRIMARY KEY,
      object_key TEXT NOT NULL,
      filename TEXT NOT NULL,
      content_type TEXT NOT NULL,
      status TEXT NOT NULL DEFAULT 'a_verifier',
      created_at TEXT NOT NULL
    )`).run();
    await env.DB.prepare(`INSERT INTO imports
      (id, object_key, filename, content_type, status, created_at)
      VALUES (?, ?, ?, ?, 'a_verifier', ?)`).bind(
      id, objectKey, file.name, file.type, new Date().toISOString(),
    ).run();

    return Response.json({ id, filename: file.name, status: 'a_verifier' }, { status: 201 });
  } catch (error) {
    console.error(error);
    return Response.json({ error: 'La photo n’a pas pu être enregistrée.' }, { status: 500 });
  }
}
