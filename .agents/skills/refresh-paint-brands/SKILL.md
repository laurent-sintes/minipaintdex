---
name: refresh-paint-brands
description: Rechercher et rafraîchir les gammes de peintures d’une marque ou de toutes les marques connues, comparer le marché local, enrichir les peintures techniques et proposer ajouts, mises à jour, retraits ou suppressions traçables. Utiliser pour synchroniser les catalogues fabricants MiniPaintDex.
---

# Rafraîchir les marques de peinture

Accepter une marque canonique ou la valeur `all`. `all` signifie toutes les marques distinctes déjà présentes dans `data/market/paints/catalog.yaml`; ne pas maintenir une liste codée dans le skill.

## Workflow

1. Lire le catalogue local et résoudre la marque demandée. Pour `all`, traiter chaque marque comme une unité de recherche indépendante, puis réunir les résultats dans un seul fichier vérifié.
2. Rechercher d’abord les catalogues, pages de gamme et pages produit officiels. Enregistrer pour chaque marque une entrée `coverage` avec `complete: true` uniquement si la source permet réellement d’affirmer que la gamme visée est exhaustive.
3. Comparer chaque produit distant aux identifiants, références, métadonnées, images, provenance et dates déjà enregistrés. Produire des fiches canoniques complètes pour les ajouts et mises à jour ; ne pas créer une mise à jour à partir d’une simple différence de présentation du site fabricant.
4. Pour `technical_effect`, `primer`, `wash_shade`, `ink` et `auxiliary`, ajouter `usage_instructions` avec un résumé, des étapes explicites et des conseils ou précautions issus d’une source traçable. Une fiche technique sans mode d’emploi reste en révision et ne doit pas être appliquée.
5. Construire le change set avec `mpdx_data.py changeset refresh-paints`. Une référence absente d’une couverture complète devient par défaut `retire`, jamais une suppression silencieuse. Utiliser `--remove-missing` seulement si l’utilisateur a demandé la suppression et si la disparition est confirmée ; l’application refusera encore de supprimer une peinture possédée, citée par un guide de marché ou utilisée par une recette d’atelier.
6. Examiner le résumé `add/update/retire/delete`, les avertissements de couverture et les différences. Valider le change set puis exécuter une simulation par REST si le serveur local fonctionne, sinon par CLI.
7. Appliquer le même fichier sans `dryRun`, puis exécuter `scripts/minipaintdex.ps1 build`. Rapporter les changements par marque, les références conservées faute de preuve, les retraits et les suppressions refusées.

Ne jamais écrire directement dans `data/`. Ne jamais considérer une erreur HTTP, une page temporairement absente ou un catalogue incomplet comme une preuve de suppression.

## Commandes

```powershell
python tools/minipaintdex-data/mpdx_data.py changeset refresh-paints imports/runs/brand-refresh/verified.json --catalog data/market/paints/catalog.yaml --brand all --output imports/runs/brand-refresh/changeset.json
python tools/minipaintdex-data/mpdx_data.py changeset validate imports/runs/brand-refresh/changeset.json --format json
.\scripts\minipaintdex.ps1 cli --root . --format json market paints apply --input imports/runs/brand-refresh/changeset.json --dry-run
```

Pour une suppression demandée explicitement, ajouter `--remove-missing` lors de la génération du change set. Ne jamais transformer cette option en comportement par défaut.
