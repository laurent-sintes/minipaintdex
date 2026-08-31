# Modèle DDD

## Bounded context Market

- `MarketPaint` représente une peinture commercialisée et ses métadonnées fabricant.
- `PaintableProduct` est l’aggregate root d’une boîte, gamme ou autre produit contenant des figurines ou décors à peindre.
- `MarketPaintingGuide` conserve la connaissance sourcée d’un peintre ou d’une publication. Il ne décrit pas les choix personnels de l’atelier.

## Bounded context Workshop

- `Workshop` est l’aggregate root durable du contexte personnel et référence les projets en cours.
- `PaintingProject` est l’aggregate root de l’intention de peindre un `PaintableProduct`. Son cycle de vie est `planned`, `active`, `completed`, `archived`.
- `WorkshopItem` est l’aggregate root d’une figurine ou d’un décor physique. Il porte son workflow, ses commentaires, ses photos et l’affectation d’une recette.
- `WorkshopRecipe` est l’aggregate root d’un plan de peinture personnel, distinct du guide du marché.

## Bounded context Activity

Le ledger JSONL est le journal global append-only du board de l’atelier. Les projections reconstruisent les vues du Workshop, des PaintingProjects, des WorkshopItems, des recettes et des achats.

Le cœur des identifiants, événements et données reste en anglais. Les libellés français appartiennent à `data/site/fr.yaml`.
