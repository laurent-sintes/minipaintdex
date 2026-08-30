---
name: import-paintable-product
description: Importer par son nom une boîte, une extension, une gamme ou un autre produit contenant des éléments à peindre, rechercher son inventaire et des références peintes traçables, puis l’ajouter au marché et éventuellement à l’atelier MiniPaintDex.
---

# Importer un produit à peindre

Prendre le nom fourni par l’utilisateur comme paramètre principal et produire un change set `market_product`. Lire [`references/paintable-product-schema.md`](references/paintable-product-schema.md) avant de structurer le résultat.

## Workflow

1. Identifier le type de `PaintableProduct`, la ligne, l’édition, la boîte et les extensions visées. Examiner d’abord le dépôt. Ne demander une précision que si plusieurs éditions changent matériellement l’inventaire et qu’aucun choix raisonnable n’est déductible.
2. Rechercher l’inventaire auprès de l’éditeur, du livret de règles ou d’une liste de composants officielle. Utiliser des sources secondaires uniquement pour résoudre les écarts. Enregistrer toutes les sources.
3. Distinguer les références uniques des exemplaires identiques. Enregistrer la quantité positive sur chaque `catalog_item` et vérifier que leur somme égale `expected_paintable_count`. Signaler les éléments incertains.
4. Rechercher des images de figurines peintes pour chaque sculpture. Préférer les galeries officielles, les contenus sous licence explicite et les images fournies par l’utilisateur. Enregistrer l’URL de l’image, la page source, le crédit et la licence connue.
5. Ne pas télécharger, republier ni hotlinker une image dont le droit d’usage est inconnu. Une telle image peut rester une source de recherche textuelle, mais pas une `reference_image` affichée par le site.
6. Produire un `painting_guide` de marché, jamais une recette personnelle. Distinguer `documented`, `observed` et `inferred`, conserver une provenance directe ou des `source_refs`, et versionner le guide. Réutiliser les identifiants du catalogue de peintures; marquer toute peinture absente avec `pending_import: true`.
7. Décomposer la palette publique en `slots` stables. Écrire une préparation et un mode opératoire adaptés à la sculpture. Le guide décrit la connaissance publiée; il ne présume ni des peintures possédées ni de la technique que l’utilisateur choisira.
8. Ne pas générer d’état d’atelier dans le change set marché. `Workshop`, `WorkshopProduct` et les `WorkshopItem` ont un cycle de vie distinct.
9. Valider avec `python tools/minipaintdex-data/mpdx_data.py changeset validate <fichier> --format json`, puis simuler l’application. Si le serveur répond, utiliser `POST /api/v1/market/paintable-product-changesets?dryRun=true`; sinon utiliser la CLI `market paintable-products apply --dry-run`.
10. Après validation des ambiguïtés, appliquer le même change set sans `dryRun`. Ne jamais écrire directement dans `data/market`, `data/workshop` ou le ledger.
11. Si le produit doit aussi rejoindre l’atelier, appeler d’abord `GET /api/v1/market/paintable-products/{id}/workshop-import-preview` ou la CLI `market paintable-products preview-import --product <id>`. Rapporter les peintures manquantes et les slots à qualifier, puis appeler `POST /api/v1/workshop/paintable-products` ou `workshop paintable-products import --product <id>`. L’application crée les objets physiques atomiquement et de façon idempotente.
12. Exécuter `scripts/minipaintdex.ps1 build`. Rapporter les images manquantes, incertitudes, peintures à importer et le décompte des objets physiques.

Les recettes personnelles sont créées séparément par `workshop recipes create`, puis validées, activées et affectées à un objet physique. Ne pas les fabriquer implicitement pendant l’import du marché.

Ne pas committer ni pousser sauf si l’utilisateur le demande aussi.
