# Routage des workflows

## Import de peintures par photo

Lire `paint-import-schema.md`, puis `source-strategy.md` après identification visuelle, et `paint-taxonomy.md` pour la classification. Utiliser `paint hash-photos`, `paint normalize` et `paint enrich` pour qualifier les produits. `changeset build-paints` enrichit exclusivement le marché, sans quantités. Inscrire ensuite chaque pot explicitement identifié via `paint-pots build-import`, puis `workshop paint-pots import` Java (simulation puis `--apply`). Réutiliser l’ID d’un pot rephotographié ; ne jamais en créer un deuxième. Les photos personnelles s’attachent via `workshop paint-pots photo`, jamais via le catalogue.

## Import d’un produit à peindre

Lire `paintable-product-schema.md`. Rechercher l’inventaire officiel, vérifier le total, documenter les images réutilisables et produire un change set `market_product`. Simuler puis appliquer. La création d’un PaintingProject est un cas d’usage séparé.

## Datasets

Lire `datasets.md`. Créer un paquet nommé avec Python, vérifier son manifeste et son checksum, puis l’importer par le CLI Java. La simulation est le défaut ; l’écriture exige `--apply`.
