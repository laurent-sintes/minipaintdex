---
name: import-miniature-project
description: Importer un jeu de figurines à partir de son nom, rechercher son inventaire et des références peintes traçables, puis produire et appliquer un change set MiniPaintDex. Utiliser pour ajouter un jeu, une boîte, une extension ou une gamme au marché et à l’atelier.
---

# Importer un projet de figurines

Prendre le nom du jeu fourni par l’utilisateur comme paramètre principal et produire un change set `miniature_project`. Lire [`references/project-schema.md`](references/project-schema.md) avant de structurer le résultat.

## Workflow

1. Identifier l’édition, la langue, la boîte et les extensions visées. Examiner d’abord le dépôt. Ne demander une précision que si plusieurs éditions changent matériellement l’inventaire et qu’aucun choix raisonnable n’est déductible.
2. Rechercher l’inventaire auprès de l’éditeur, du livret de règles ou d’une liste de composants officielle. Utiliser des sources secondaires uniquement pour résoudre les écarts. Enregistrer toutes les sources.
3. Distinguer les sculptures uniques des exemplaires identiques et conserver la quantité de chaque entrée. Signaler les éléments dont l’existence ou la quantité reste incertaine.
4. Rechercher des images de figurines peintes pour chaque sculpture. Préférer les galeries officielles, les contenus sous licence explicite et les images fournies par l’utilisateur. Enregistrer l’URL de l’image, la page source, le crédit et la licence connue.
5. Ne pas télécharger, republier ni hotlinker une image dont le droit d’usage est inconnu. Une telle image peut rester une source de recherche textuelle, mais pas une `reference_image` affichée par le site.
6. Produire un `painting_guide` de marché, jamais une recette personnelle. Distinguer `documented`, `observed` et `inferred`, conserver une provenance directe ou des `source_refs`, et versionner le guide. Réutiliser les identifiants du catalogue de peintures; marquer toute peinture absente avec `pending_import: true`.
7. Décomposer la palette publique en `slots` stables. Écrire une préparation et un mode opératoire adaptés à la sculpture. Le guide décrit la connaissance publiée; il ne présume ni des peintures possédées ni de la technique que l’utilisateur choisira.
8. Générer un objet `workshop_items` par élément physique, même pour plusieurs exemplaires de la même sculpture. Le nombre doit correspondre à `expected_paintable_count`.
9. Valider le change set avec `python tools/minipaintdex-data/mpdx_data.py changeset validate <fichier> --format json`, puis simuler son application. Si le serveur local répond, utiliser `POST /api/v1/market/project-changesets?dryRun=true`; sinon utiliser la CLI `market games apply --dry-run`.
10. Après validation des ambiguïtés, appliquer le même change set sans `dryRun`. Ne jamais écrire directement dans `data/market`, `data/workshop` ou le ledger.
11. Exécuter `scripts/minipaintdex.ps1 build`. Rapporter les images manquantes, incertitudes, peintures à importer et le décompte des objets physiques.

Les recettes personnelles sont créées séparément par `workshop recipes create`, puis validées, activées et affectées à un objet physique. Ne pas les fabriquer implicitement pendant l’import du marché.

Ne pas committer ni pousser sauf si l’utilisateur le demande aussi.
