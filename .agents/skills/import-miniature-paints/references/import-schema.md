# Schéma candidat JSON

La sortie de l’analyse visuelle est un fichier JSON UTF-8. Utiliser une liste sous la clé `paints`.

```json
{
  "source_photo": "imports/photos/IMG_0001.jpeg",
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
