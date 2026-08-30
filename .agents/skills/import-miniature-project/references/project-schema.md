# Schéma du référentiel projets

Chaque fichier `data/projects/<slug>.yaml` décrit un jeu, une boîte ou un périmètre cohérent.

```yaml
schema_version: 1
id: slug-stable
name: Nom affiché
game: Nom du jeu
scope: Boîte de base et extension X
expected_paintable_count: 198
edition:
  note: Précision sur l’édition
  url: https://source-officielle.example
sources:
  - kind: official_inventory
    label: Livret de règles
    url: https://source.example
items:
  - id: slug-figurine
    name: Nom affiché
    kind: Héros
    quantity: 1
    status: à préparer
    description: Description et intention de peinture
    reference_images:
      - url: https://image-autorisée.example/photo.jpg
        page_url: https://page-source.example
        credit: Auteur ou éditeur
        license: CC BY 4.0
    paints:
      - brand: Vallejo
        name: Dead White
        role: Éclairage final
        color_hex: "#f2f1e8"
        pending_import: true
    preparation:
      - title: Nettoyer
        detail: Instruction concise.
    painting:
      - title: Couche de base
        detail: Instruction concise et actionnable.
    sources:
      - kind: painting_reference
        label: Galerie de référence
        url: https://source.example
```

## Contraintes

- `schema_version`, `id`, `name`, `game`, `scope` et `items` sont requis.
- Les identifiants sont uniques dans leur portée, stables et écrits en minuscules ASCII avec des tirets.
- `quantity` est un entier strictement positif.
- `expected_paintable_count` est optionnel. Lorsqu’il est présent, il doit être égal à la somme des `quantity` de toutes les fiches du projet.
- `paints`, `preparation`, `painting`, `reference_images` et `sources` sont toujours des listes, même vides.
- Une image affichée exige `url`, `page_url`, `credit` et une `license` autorisant sa réutilisation. Sans droit clair, enregistrer uniquement la page comme `source` externe.
- Une peinture absente de `data/peintures.yaml` porte `pending_import: true`.
- Les faits, sources et propositions de peinture ne doivent jamais être présentés comme équivalents.
