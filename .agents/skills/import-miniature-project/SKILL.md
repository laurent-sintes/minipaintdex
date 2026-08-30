---
name: import-miniature-project
description: Importer un jeu de figurines à partir de son nom, rechercher son inventaire et des références peintes traçables, produire des fiches de peinture et écrire le projet dans le référentiel YAML dynamique de MiniPaintDex. Utiliser pour ajouter un jeu, une boîte, une extension ou une gamme de figurines au site.
---

# Importer un projet de figurines

Prendre le nom du jeu fourni par l’utilisateur comme paramètre principal et produire `data/projects/<slug>.yaml`. Lire [`references/project-schema.md`](references/project-schema.md) avant toute écriture.

## Workflow

1. Identifier l’édition, la langue, la boîte et les extensions visées. Examiner d’abord le dépôt. Ne demander une précision que si plusieurs éditions changent matériellement l’inventaire et qu’aucun choix raisonnable n’est déductible.
2. Rechercher l’inventaire auprès de l’éditeur, du livret de règles ou d’une liste de composants officielle. Utiliser des sources secondaires uniquement pour résoudre les écarts. Enregistrer toutes les sources.
3. Distinguer les sculptures uniques des exemplaires identiques et conserver la quantité de chaque entrée. Signaler les éléments dont l’existence ou la quantité reste incertaine.
4. Rechercher des images de figurines peintes pour chaque sculpture. Préférer les galeries officielles, les contenus sous licence explicite et les images fournies par l’utilisateur. Enregistrer l’URL de l’image, la page source, le crédit et la licence connue.
5. Ne pas télécharger, republier ni hotlinker une image dont le droit d’usage est inconnu. Une telle image peut rester une source de recherche textuelle, mais pas une `reference_image` affichée par le site.
6. Déduire une recette de peinture en séparant clairement ce qui est observé sur les références de ce qui est proposé par l’analyse. Réutiliser en priorité les produits présents dans `data/peintures.yaml`; marquer toute peinture absente avec `pending_import: true`.
7. Écrire pour chaque figurine une préparation courte et un mode opératoire ordonné, adapté à la sculpture et à la recette.
8. Fusionner sans écraser les informations validées existantes. Conserver les identifiants stables et utiliser des slugs ASCII.
9. Exécuter `pnpm validate:catalogs`, puis `pnpm check`. Corriger les erreurs et rapporter les images manquantes, les incertitudes et les peintures à importer.

Ne pas committer ni pousser sauf si l’utilisateur le demande aussi.
