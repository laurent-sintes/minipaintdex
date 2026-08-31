# Routage des workflows

## Import de peintures par photo

Lire `paint-import-schema.md`, puis `source-strategy.md` après identification visuelle, et `paint-taxonomy.md` pour la classification. Utiliser le pipeline `paint hash-photos`, `paint normalize`, `paint enrich`, `changeset build-paints`, validation, dry-run Java puis application explicite.

## Import d’un produit à peindre

Lire `paintable-product-schema.md`. Rechercher l’inventaire officiel, vérifier le total, documenter les images réutilisables et produire un change set `market_product`. Simuler puis appliquer. La création d’un PaintingProject est un cas d’usage séparé.

## Rafraîchissement des marques

Lire `paint-brand-refresh.md`. Accepter une marque canonique ou `all`, comparer l’intégralité du catalogue connu, enrichir les produits techniques et distinguer retrait et suppression.

## Datasets

Lire `datasets.md`. Créer un paquet nommé avec Python, vérifier son manifeste et son checksum, puis l’importer par le CLI Java. La simulation est le défaut ; l’écriture exige `--apply`.
