# Schéma candidat JSON

La sortie de l’analyse visuelle est un fichier JSON UTF-8. Utiliser une liste sous la clé `paints`.

```json
{
  "source_photo": "imports/workshop-paints/photos/IMG_0001.jpeg",
  "source_hash": "sha256 calculé par le script",
  "paints": [
    {
      "brand_observed": "CITADEL COLOUR",
      "range_observed": "CONTRAST",
      "name_observed": "Leviadon Blue",
      "reference_observed": "",
      "quantity": 1,
      "confidence": 0.97,
      "evidence": ["nom et gamme lisibles sur l’étiquette"],
      "warnings": []
    }
  ]
}
```

## Règles

- `source_photo`, `source_hash` et `paints` sont requis.
- Les champs `*_observed` reflètent l’étiquette sans moderniser la marque ni traduire la gamme.
- `confidence` est compris entre 0 et 1.
- `evidence` décrit seulement ce qui est visible.
- `warnings` contient les reflets, occultations, lectures partielles et conflits possibles.
- Les champs fabricant, URL, couleur et usages sont ajoutés après normalisation et vérification, pas pendant l’OCR.

Le script ajoute notamment : `brand_canonical`, `brand_aliases`, `manufacturer`, `range_canonical`, `functional_class`, `dedupe_key`, `needs_review` et `status`.

## Destination et archivage

La file d’attente des pots possédés est `imports/workshop-paints/photos/`. Conserver
les analyses dans `imports/workshop-paints/runs/<import-id>/` et les photos traitées
dans `imports/workshop-paints/archive/<date>/<import-id>/`.

Après simulation, application Java et vérification des quantités par l’API, préparer
un manifeste `schema_version: 1`, `target: workshop.paints`, `import_id`, `archive_date`,
`verified_inventory_sha256` (empreinte du fichier d’inventaire vérifié) et `photos`.
Chaque photo porte son nom de fichier `path`, son `sha256` et un `outcome` :
`imported`, `duplicate` ou `pending`. Un doublon doit désigner une photo archivée
via `duplicate_of` ; des pixels identiques peuvent confirmer une copie dont les
métadonnées JPEG ont changé. Ne pas confondre deux pots distincts d’une même référence
avec deux photographies du même pot.

Simuler `paint archive-batch --root . --manifest <manifest.json>`, puis appliquer avec
`--apply`. Le helper vérifie les empreintes, refuse les écrasements, laisse les photos
`pending` en place et conserve un manifeste de localisation. Réparer les chemins de
provenance devenus obsolètes par un change set marché simulé puis appliqué en Java,
sans modifier les quantités. Conserver le chemin original comme preuve historique.
