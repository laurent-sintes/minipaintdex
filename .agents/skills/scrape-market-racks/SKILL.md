---
name: scrape-market-racks
description: "Scraper les racks du marché Mini Paint Dex avec photos, provenance et caractéristiques de rangement, puis préparer et appliquer les fiches RackProduct. Ne pas créer de racks personnels ni modifier les placements."
---

# Scraper les racks du marché

Utiliser les pages fabricant des modèles ou marques demandés. La collecte est une recherche de
références commerciales, pas un achat ni une création d'exemplaires dans l'atelier.

1. Lire le contrat courant avec le CLI `market rack-products show` et le modèle
   `backend/domain/src/main/java/com/minipaintdex/domain/market/storage/RackProduct.java`.
   Lire les formats internes par `market container-formats search`, par pages.
2. Relever la référence fabricant, les URL produit et photos réellement observées, le crédit et
   le statut d'utilisation. Une photo d'illustration n'est pas une liste d'accessoires inclus.
   Conserver les URL distantes ; ne pas republier de copie sans droits.
3. Distinguer encombrement extérieur et stockage utile. Décrire chaque rangée réelle par
   `RackRowDefinition` : tablette continue ou emplacements fixes, dimensions utiles en mm ou
   capacités explicitement documentées pour des formats identifiés. Une capacité totale ne
   suffit pas pour inventer sa répartition entre rangées. Inspecter photo/notice si nécessaire.
4. Une alvéole de diamètre donné ne certifie pas à elle seule la stabilité de tous les petits
   flacons. Renseigner `acceptedFormatIds` seulement pour les conditionnements documentés.
   Ne pas fabriquer la profondeur ou le dégagement en hauteur à partir des dimensions extérieures.
   Les capacités homogènes de tablettes peuvent utiliser `capacityCalibrations` ; le mélange
   reste estimé. Les valeurs inconnues sont null, jamais zéro ni infinies.
5. Préparer un JSON camelCase pour `SaveRackReferenceCommand` sous `target/rack-scrape/`
   avec `rackProduct`, `containerFormat: null`, `expectedRevision` et `dryRun: true`.
   La révision vient de `market rack-products search --format json` (option globale).
6. Valider par `scripts/minipaintdex.ps1 cli --format json market rack-products save --input <fiche>`.
   Après lecture du résultat, appliquer avec le même document portant `dryRun: false`.
   En cas de conflit, relire la fiche et comparer avant de réessayer. Ne pas écraser un changement.
   Vérifier la lecture finale et le rejeu idempotent. Aucun accès direct en écriture à `data/`.

Les racks custom ne sont pas proposés. Un modèle non identifié sur une photo reste hors du marché.
Une nouvelle marque ne nécessite pas un collecteur universel fragile : la qualification web
est faite par l'agent et l'import déterministe par le même cas d'usage Java REST/CLI.
