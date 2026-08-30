---
name: import-miniature-paints
description: Importer des photos de pots de peinture pour figurines, identifier les produits, normaliser les marques et gammes par fonction, enrichir depuis des sources traçables, dédupliquer et fusionner le référentiel local. Utiliser pour les imports Citadel/Warhammer Colour, Vallejo, The Army Painter, Prince August et autres marques de peinture de figurines.
---

# Importer des peintures pour figurines

Produire un inventaire exact, traçable et comparable entre marques à partir de photos. Séparer systématiquement les observations, les inférences et les données confirmées.

## Répartition des tâches

- Utiliser la vision et le raisonnement pour lire les étiquettes, lever les ambiguïtés, rechercher les fiches fabricant et qualifier les usages.
- Utiliser [`scripts/paint_inventory.py`](scripts/paint_inventory.py) pour les opérations déterministes : empreinte des photos, normalisation, application d’un catalogue d’enrichissement vérifié, recadrage des marges transparentes des packshots, validation, classification fonctionnelle, détection des doublons, fusion et génération CSV/YAML.
- Ne pas confier au script Python l’identification visuelle, la sélection d’un résultat web ou l’invention d’une référence ou d’une URL.

## Workflow

1. Inventorier les nouvelles images et calculer leur SHA-256 avec `paint_inventory.py hash-photos`. Ignorer une photo déjà traitée selon son empreinte, pas seulement selon son nom.
2. Examiner chaque photo et produire un fichier candidat JSON conforme à [`references/import-schema.md`](references/import-schema.md). Conserver le texte tel qu’il apparaît sur le pot dans `brand_observed`, `range_observed`, `name_observed` et `reference_observed`.
3. Exécuter `paint_inventory.py normalize`. Lire les avertissements et soumettre à validation humaine toute identité incertaine, référence contradictoire, OCR incomplet ou correspondance Prince August/Vallejo ambiguë.
4. Après confirmation de l’identité, enrichir la fiche. Lire [`references/source-strategy.md`](references/source-strategy.md) avant toute recherche fabricant ou récupération d’image. Enregistrer les champs vérifiés dans un catalogue JSON indexé par référence, puis utiliser `paint_inventory.py enrich` afin que l’application soit reproductible.
5. Vérifier les dimensions et le cadrage des packshots. Pour les PNG où le pot est minuscule au centre d’une grande zone transparente, utiliser `paint_inventory.py trim-images` ; ne jamais détourer ou reconstruire un produit par génération d’image.
6. Classer la peinture par fonction commune en suivant [`references/taxonomy.md`](references/taxonomy.md). Garder aussi le nom de gamme propre à la marque : l’équivalence fonctionnelle ne signifie pas que les formulations ou couleurs sont interchangeables.
7. Exécuter `paint_inventory.py merge` vers de nouveaux fichiers de sortie, examiner le rapport, puis seulement remplacer les fichiers du référentiel si la fusion est correcte.
8. Archiver la photo et son analyse en conservant l’empreinte, la date, les sources consultées et le statut de validation. Après déplacement, utiliser `paint_inventory.py relocate-source` pour mettre à jour la provenance sans compter une seconde fois les pots.

## Invariants

- La marque canonique actuelle est `Warhammer Colour`; `Citadel`, `Citadel Colour` et `Games Workshop` restent des alias historiques/recherchables. Ne jamais traiter leur changement de nom comme un changement de formulation sans preuve produit contraire.
- Écrire `Xpress Color`, pas `Cpress Color`. Un OCR proche doit produire un avertissement et une proposition, pas une correction silencieuse.
- Conserver séparément `brand_observed`, `brand_canonical` et `manufacturer`. Prince August commercialise ses propres gammes et distribue aussi des gammes Vallejo : ne jamais tout rabattre automatiquement sur Vallejo.
- Une équivalence de fonction relie notamment `Contrast`, `Xpress Color` et `Speedpaint`, mais ne prétend pas établir une équivalence exacte de teinte, de couvrance, de réactivation ou de comportement.
- Ne jamais inventer une référence, un volume, une fiche produit ou une photo fabricant. Laisser le champ vide et passer le statut à `a_verifier` si la source n’est pas suffisante.
- Toujours enregistrer la provenance du packshot. Une image hébergée par un revendeur peut servir de copie locale si elle représente clairement le produit, mais elle ne doit pas être décrite comme téléchargée depuis le site fabricant.

## Commandes usuelles

```powershell
python .agents/skills/import-miniature-paints/scripts/paint_inventory.py hash-photos imports/photos --output imports/photo-manifest.json
python .agents/skills/import-miniature-paints/scripts/paint_inventory.py normalize imports/analyses/candidates.json --output imports/analyses/normalized.json
python .agents/skills/import-miniature-paints/scripts/paint_inventory.py enrich imports/analyses/normalized.json --catalog imports/analyses/enrichments.json --output imports/analyses/enriched.json
python .agents/skills/import-miniature-paints/scripts/paint_inventory.py trim-images public/manufacturer/paint-a.png public/manufacturer/paint-b.png --padding 24
python .agents/skills/import-miniature-paints/scripts/paint_inventory.py merge imports/analyses/enriched.json --inventory data/peintures.csv --output-csv imports/analyses/peintures.merged.csv --output-yaml imports/analyses/peintures.merged.yaml --report imports/analyses/merge-report.json
python .agents/skills/import-miniature-paints/scripts/paint_inventory.py relocate-source --inventory data/peintures.csv --old imports/photos/IMG_0001.jpeg --new imports/archive/2026-08-30/IMG_0001.jpeg --output-csv imports/analyses/peintures.archived.csv --output-yaml imports/analyses/peintures.archived.yaml
python .agents/skills/import-miniature-paints/scripts/paint_inventory.py validate --inventory data/peintures.csv
```

Utiliser l’exécutable Python fourni par l’espace de travail si `python` n’est pas disponible dans le terminal.
