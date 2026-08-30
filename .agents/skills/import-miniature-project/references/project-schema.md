# Schéma du change set projet

Le fichier JSON produit est un transport vers les services Java. Il ne duplique ni statut personnel ni quantité dans le marché.

```json
{
  "schema_version": 1,
  "kind": "miniature_project",
  "project": {
    "schema_version": 1,
    "id": "slug-stable",
    "name": "Nom affiché",
    "game": "Nom du jeu",
    "scope": "Boîte de base et extension X",
    "expected_paintable_count": 198,
    "edition": {"note": "Édition", "url": "https://source-officielle.example"},
    "sources": [{"kind": "official_inventory", "label": "Livret", "url": "https://source.example"}],
    "catalog_items": [
      {
        "id": "slug-stable-figurine",
        "game_id": "slug-stable",
        "name": "Nom affiché",
        "kind": "miniature",
        "assembly_required": false,
        "description": "Description factuelle",
        "reference_images": [],
        "sources": []
      }
    ]
  },
  "painting_guides": [
    {
      "id": "slug-stable-figurine-guide",
      "version": 1,
      "knowledge_status": "documented",
      "catalog_item_id": "slug-stable-figurine",
      "sources": [{"kind": "professional_guide", "label": "Guide de l’auteur", "url": "https://source.example"}],
      "slots": [{"id": "slug-stable-figurine-guide-slot-01", "market_paint_id": "market-paint-id", "role": "highlight"}],
      "preparation": [{"title": "Clean", "detail": "Instruction concise."}],
      "painting": [{"title": "Base coat", "detail": "Instruction actionnable."}]
    }
  ],
  "workshop_items": [
    {
      "id": "ws-slug-stable-figurine-001",
      "catalog_item_id": "slug-stable-figurine",
      "project_id": "slug-stable",
      "display_name": "Nom affiché 1"
    }
  ]
}
```

## Contraintes

- `schema_version`, `kind`, `project`, `painting_guides` et `workshop_items` sont requis.
- Le cœur du modèle, les identifiants, les genres, les rôles et les clés restent en anglais.
- Les identifiants sont uniques dans leur portée, stables et écrits en minuscules ASCII avec des tirets.
- Chaque exemplaire physique possède une entrée dans `workshop_items`. `expected_paintable_count` doit être égal à la taille de cette liste.
- `slots`, `preparation`, `painting`, `reference_images`, `sources`, `painting_guides` et `workshop_items` sont toujours des listes, même vides.
- Une image affichée exige `url`, `page_url`, `credit` et une `license` autorisant sa réutilisation. Sans droit clair, enregistrer uniquement la page comme `source` externe.
- Une peinture absente de `data/market/paints/catalog.yaml` porte `pending_import: true` avec un objet `requested_paint`.
- Chaque guide porte une `version`, un `knowledge_status` parmi `documented`, `observed`, `inferred`, et une provenance directe (`sources`) ou indirecte (`source_refs`).
- Chaque `slot` a un identifiant stable. Il représente une intention visuelle ou technique du guide public, pas encore une peinture choisie dans l’atelier.
- Les faits, sources et propositions de peinture ne doivent jamais être présentés comme équivalents. Une recette d’atelier n’appartient pas à ce change set.
- Le change set est appliqué par REST ou CLI. Le skill ne modifie jamais directement les YAML ni le ledger.
