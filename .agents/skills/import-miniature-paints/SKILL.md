---
name: import-miniature-paints
description: Importer des photos de pots de peinture, identifier et enrichir les produits, puis proposer et appliquer un change set traçable au marché et à l’inventaire MiniPaintDex. Utiliser pour les imports personnels Citadel/Warhammer Colour, Vallejo, The Army Painter, Prince August et autres marques.
---

# Importer des peintures pour figurines

Produire un inventaire exact, traçable et comparable entre marques à partir de photos. Séparer systématiquement les observations, les inférences et les données confirmées.

## Répartition des tâches

- Utiliser la vision et le raisonnement pour lire les étiquettes, lever les ambiguïtés, rechercher les fiches fabricant et qualifier les usages.
- Utiliser `tools/minipaintdex-data/mpdx_data.py` pour les opérations déterministes : empreinte des photos, normalisation, enrichissement vérifié, recadrage, construction et validation du change set.
- Ne pas confier au script Python l’identification visuelle, la sélection d’un résultat web ou l’invention d’une référence ou d’une URL.

## Workflow

1. Créer un répertoire de travail `imports/runs/<date>-<slug>/`. Inventorier les images et calculer leur SHA-256 avec `mpdx_data.py paint hash-photos`. Ignorer une photo déjà traitée selon son empreinte, pas seulement selon son nom.
2. Examiner chaque photo et produire un fichier candidat JSON conforme à [`references/import-schema.md`](references/import-schema.md). Conserver le texte tel qu’il apparaît sur le pot dans `brand_observed`, `range_observed`, `name_observed` et `reference_observed`.
3. Exécuter `mpdx_data.py paint normalize`. Lire les avertissements et soumettre à validation humaine toute identité incertaine, référence contradictoire, OCR incomplet ou correspondance Prince August/Vallejo ambiguë.
4. Après confirmation de l’identité, enrichir la fiche. Lire [`references/source-strategy.md`](references/source-strategy.md) avant toute recherche fabricant ou récupération d’image. Enregistrer les champs vérifiés dans un catalogue JSON indexé par référence, puis utiliser `mpdx_data.py paint enrich` afin que l’application soit reproductible.
5. Vérifier les dimensions et le cadrage des packshots. Pour les PNG où le pot est minuscule au centre d’une grande zone transparente, utiliser `mpdx_data.py paint trim-images` ; ne jamais détourer ou reconstruire un produit par génération d’image.
6. Classer la peinture par fonction commune en suivant [`references/taxonomy.md`](references/taxonomy.md). Garder aussi le nom de gamme propre à la marque : l’équivalence fonctionnelle ne signifie pas que les formulations ou couleurs sont interchangeables.
7. Construire un change set `market_paints` avec `mpdx_data.py changeset build-paints`. Celui-ci sépare les données du marché et les quantités à ajouter dans `data/workshop/paints.yaml`.
8. Valider le change set, puis l’appliquer d’abord en `--dry-run`. Si le serveur répond sur `127.0.0.1:8080`, appliquer par `POST /api/v1/market/paint-changesets`; sinon utiliser `minipaintdex market paints apply`. Ne jamais écrire directement dans `data/`.
9. Archiver uniquement la photo, les preuves structurées, le change set et le rapport final. Les fichiers de travail reproductibles restent dans le répertoire du run et peuvent être ignorés après validation.
10. Exécuter `scripts/minipaintdex.ps1 build` et les tests Python. Rapporter les ajouts, mises à jour, quantités possédées et fiches encore incertaines.

## Invariants

- La marque canonique actuelle est `Warhammer Colour`; `Citadel`, `Citadel Colour` et `Games Workshop` restent des alias historiques/recherchables. Ne jamais traiter leur changement de nom comme un changement de formulation sans preuve produit contraire.
- Écrire `Xpress Color`, pas `Cpress Color`. Un OCR proche doit produire un avertissement et une proposition, pas une correction silencieuse.
- Conserver séparément `brand_observed`, `brand_canonical` et `manufacturer`. Prince August commercialise ses propres gammes et distribue aussi des gammes Vallejo : ne jamais tout rabattre automatiquement sur Vallejo.
- Une équivalence de fonction relie notamment `Contrast`, `Xpress Color` et `Speedpaint`, mais ne prétend pas établir une équivalence exacte de teinte, de couvrance, de réactivation ou de comportement.
- Pour les peintures comportementales, renseigner si les sources le permettent un `application_profile` structuré (transparence, accumulation dans les creux, séparation de pigments, réactivation, sous-couche attendue, fini et type d’effet). Le rapprochement d’atelier utilise ce profil et doit toujours demander une validation manuelle.
- Ne jamais inventer une référence, un volume, une fiche produit ou une photo fabricant. Laisser le champ vide et passer le statut à `a_verifier` si la source n’est pas suffisante.
- Toujours enregistrer la provenance du packshot. Une image hébergée par un revendeur peut servir de copie locale si elle représente clairement le produit, mais elle ne doit pas être décrite comme téléchargée depuis le site fabricant.
- Une peinture de fonction `technical_effect`, `primer`, `wash_shade`, `ink` ou `auxiliary` doit inclure `usage_instructions.summary`, des `steps` actionnables et des `tips` de précaution.
- Ne jamais supprimer une peinture du marché pendant un import photo. Les retraits et suppressions appartiennent au skill de rafraîchissement des marques.

## Commandes usuelles

```powershell
python tools/minipaintdex-data/mpdx_data.py paint hash-photos imports/photos --output imports/runs/current/photo-manifest.json
python tools/minipaintdex-data/mpdx_data.py paint normalize imports/runs/current/candidates.json --output imports/runs/current/normalized.json
python tools/minipaintdex-data/mpdx_data.py paint enrich imports/runs/current/normalized.json --catalog imports/runs/current/enrichments.json --output imports/runs/current/enriched.json
python tools/minipaintdex-data/mpdx_data.py changeset build-paints imports/runs/current/enriched.json --output imports/runs/current/paint-changeset.json
python tools/minipaintdex-data/mpdx_data.py changeset validate imports/runs/current/paint-changeset.json --format json
.\scripts\minipaintdex.ps1 cli --root . --format json market paints apply --input imports/runs/current/paint-changeset.json --dry-run
```

Utiliser l’exécutable Python fourni par l’espace de travail si `python` n’est pas disponible dans le terminal.
