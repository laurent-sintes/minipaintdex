# Services REST

Toutes les routes sont préfixées par `/api/v1`.

## Lecture

- `GET /health` : santé et compteurs du stockage local.
- `GET /bootstrap` : vue initiale de la SPA.
- `GET /market/paints` et `/market/paints/facets` : recherche paginée et facettes.
- `GET /market/paintable-products` : catalogue des produits à peindre.
- `GET /market/painting-guides/{id}/reconciliation` : rapprochement avec les peintures possédées.
- `GET /workshop` et `/workshop/painting-projects` : atelier et projets.
- `GET /workshop/items` : éléments physiques et progression.
- `GET /about` et `/documentation` : métadonnées de build et documentation embarquée.

## Commandes

- `POST /market/paint-changesets` : appliquer ou simuler un change set de peintures.
- `POST /market/paintable-product-changesets` : appliquer ou simuler un produit et ses guides.
- `POST /workshop/paint-inventory-imports` : remplacer ou simuler l’inventaire personnel.
- `POST /workshop/painting-projects` : créer un projet et ses éléments physiques.
- `POST /workshop/items/{id}/stage-transitions` : faire avancer le workflow.
- `POST /workshop/items/{id}/comments` et `/photos` : enrichir le journal.

Le CLI Java appelle les mêmes cas d’usage. Lorsqu’un serveur local est disponible, il lui délègue les écritures pour conserver un seul writer.
