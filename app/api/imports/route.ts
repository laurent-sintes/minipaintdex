export function POST() {
  return Response.json({ error: 'Import serveur désactivé en mode local. Utilisez le skill import-miniature-paints.' }, { status: 501 });
}
