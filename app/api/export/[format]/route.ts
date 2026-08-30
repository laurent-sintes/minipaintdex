import { paintCatalog } from '@/lib/catalog';

function csvCell(value: string | number) {
  const text = String(value);
  return /[",\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
}

export async function GET(
  _request: Request,
  { params }: { params: Promise<{ format: string }> },
) {
  const { format } = await params;
  const paints = paintCatalog;

  if (format === 'csv') {
    const header = ['id', 'marque', 'gamme', 'reference', 'nom', 'couleur_hex', 'famille_couleur', 'fini', 'medium', 'volume_ml', 'quantite', 'tags', 'usages_conseilles', 'fiche_fabricant', 'image_fabricant', 'credit_image', 'rendu_image', 'source_rendu', 'credit_rendu', 'licence_rendu', 'exemple_rendu_url', 'verifie_le', 'notes'];
    const rows = paints.map((paint) => [
      paint.id, paint.brand, paint.range, paint.reference, paint.name, paint.colorHex, paint.colorFamily,
      paint.finish, paint.medium, paint.volumeMl, paint.quantity, paint.tags.join('|'), paint.recommendedUses.join('|'),
      paint.manufacturerUrl, paint.manufacturerImage, paint.manufacturerImageCredit, paint.resultImage,
      paint.resultImageSource, paint.resultImageCredit, paint.resultImageLicense, paint.resultReferenceUrl,
      paint.manufacturerVerifiedAt, paint.notes,
    ].map(csvCell).join(','));
    return new Response([header.join(','), ...rows].join('\n'), {
      headers: {
        'content-type': 'text/csv; charset=utf-8',
        'content-disposition': 'attachment; filename="peintures.csv"',
      },
    });
  }

  if (format === 'yaml') {
    const quote = (value: string) => JSON.stringify(value);
    const yaml = paints.map((paint) => [
      `- id: ${quote(paint.id)}`,
      `  marque: ${quote(paint.brand)}`,
      `  gamme: ${quote(paint.range)}`,
      `  reference: ${quote(paint.reference)}`,
      `  nom: ${quote(paint.name)}`,
      `  couleur_hex: ${quote(paint.colorHex)}`,
      `  famille_couleur: ${quote(paint.colorFamily)}`,
      `  fini: ${quote(paint.finish)}`,
      `  medium: ${quote(paint.medium)}`,
      `  volume_ml: ${paint.volumeMl}`,
      `  quantite: ${paint.quantity}`,
      `  tags: [${paint.tags.map(quote).join(', ')}]`,
      `  usages_conseilles: [${paint.recommendedUses.map(quote).join(', ')}]`,
      `  description_fabricant: ${quote(paint.manufacturerDescription)}`,
      `  fiche_fabricant: ${quote(paint.manufacturerUrl)}`,
      `  image_fabricant: ${quote(paint.manufacturerImage)}`,
      `  credit_image: ${quote(paint.manufacturerImageCredit)}`,
      `  rendu_image: ${quote(paint.resultImage)}`,
      `  source_rendu: ${quote(paint.resultImageSource)}`,
      `  credit_rendu: ${quote(paint.resultImageCredit)}`,
      `  licence_rendu: ${quote(paint.resultImageLicense)}`,
      `  exemple_rendu_url: ${quote(paint.resultReferenceUrl)}`,
      `  verifie_le: ${quote(paint.manufacturerVerifiedAt)}`,
      `  notes: ${quote(paint.notes)}`,
    ].join('\n')).join('\n');
    return new Response(`peintures:\n${yaml.split('\n').map((line) => `  ${line}`).join('\n')}\n`, {
      headers: {
        'content-type': 'application/yaml; charset=utf-8',
        'content-disposition': 'attachment; filename="peintures.yaml"',
      },
    });
  }

  return Response.json({ error: 'Format inconnu.' }, { status: 404 });
}
