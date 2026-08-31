# Portable datasets

`datasets/` contient des paquets nommés, contrôlables et importables dans Mini Paint Dex. Il ne s’agit pas du stockage actif de l’application.

## Catégories

- `market/paint-brands/` : gamme d’une marque du marché.
- `market/paintable-products/` : produit à peindre et guides associés.
- `workshop/paints/` : inventaire personnel de peintures.
- `workshop/painting-projects/` : création d’un projet de peinture à partir d’un produit du marché.

## Format

Chaque dataset est un répertoire contenant :

- `dataset.yaml` : manifeste versionné, catégorie, mode d’import, sources et SHA-256 ;
- `payload/change-set.json` : commande transport-neutre destinée à l’application.

Les chemins et checksums sont vérifiés avant chaque import. Un dataset de peintures d’atelier remplace l’inventaire ; les datasets de marché et de projet sont fusionnés par les cas d’usage du domaine.

## Commandes

```text
python tools/minipaintdex-data/mpdx_data.py dataset create --category workshop.paints --name "atelier-principal"
python tools/minipaintdex-data/mpdx_data.py dataset validate datasets/workshop/paints/atelier-principal
scripts/minipaintdex.ps1 cli -- datasets import --input datasets/workshop/paints/atelier-principal
scripts/minipaintdex.ps1 cli -- datasets import --input datasets/workshop/paints/atelier-principal --apply
```

Sans `--apply`, le CLI Java effectue une simulation. La création refuse d’écraser un paquet existant sans `--replace`.
