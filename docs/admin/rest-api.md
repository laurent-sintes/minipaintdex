# Services REST

L’API métier est préfixée par `/api/v1`. Elle est générée et testable localement via OpenAPI :

- interface interactive : `/swagger-ui.html` ;
- contrat JSON : `/v3/api-docs` ;
- contrat YAML : `/v3/api-docs.yaml`.

Les erreurs applicatives utilisent `application/problem+json` et le modèle RFC 9457 `ProblemDetail`.

Les contrôleurs REST dépendent de quatre ports applicatifs cohésifs (`Site`, `MarketCatalog`, `Workshop`, `Administration`). Les résultats métier sont des records Java immuables ; les enveloppes HAL, la forme JSON des événements et les arbres d’import restent des responsabilités d’adaptateur.

## Santé et exploitation

Les contrôles de santé ne sont pas des cas d’usage métier :

- `GET /actuator/health/liveness` vérifie le processus Spring Boot ;
- `GET /actuator/health/readiness` agrège la persistance, le cache et le pipeline événementiel ;
- `GET /actuator/info` expose les informations de build autorisées.

## Lecture

- `GET /site/config` et `/dashboard` : configuration localisée et compteurs légers du SPA ;
- `GET /market/paints?page=0&size=60&sort=name,asc` : recherche paginée ; taille maximale 200 ;
- `GET /market/paints/facets` : facettes du catalogue ;
- `GET /market/paints/stream` : transfert incrémental `application/x-ndjson`, distinct de la pagination ;
- `GET /market/paintable-products` et `/{id}` : catalogue et détail d’un produit à peindre ;
- `GET /workshop/paints?page=0&size=60` et `/workshop/paints/facets` : références Market possédées,
  composées avec les quantités de l’Atelier ;
- `GET /workshop/painting-project-import-previews/{productId}` : impact et peintures manquantes avant import ;
- `GET /workshop/painting-guide-reconciliations/{guideId}` : rapprochement avec les peintures possédées ;
- `GET /workshop`, `/workshop/painting-projects` et `/{id}` : atelier et projets ;
- `GET /workshop/items` et `/{id}` : éléments physiques et progression ;
- `GET /workshop/recipes` : recettes personnelles ;
- `GET /shopping/items` : besoins calculés et achats planifiés ;
- `GET /activity?paintingProjectId=...` : ledger global, éventuellement filtré par projet ;
- `GET /events` : notifications SSE de lots committés avec `Last-Event-ID`, heartbeat et replay borné ;
- `GET /publications/{id}` : état durable d’une commande asynchrone ;
- `GET /about` et `/documentation?audience=user|administrator` : version, auteur et documentation embarquée.

Les représentations des agrégats exposent des liens HATEOAS `self`, parent, collections liées et actions permises. Les liens de transition dépendent de l’état courant de l’agrégat.

## Commandes

- `POST /market/paint-changesets` : simuler ou appliquer un change set de peintures ;
- `POST /market/paintable-product-changesets` : simuler ou appliquer un produit et ses guides ;
- `POST /workshop/paint-inventory-imports` : simuler ou remplacer l’inventaire personnel ;
- `POST /workshop/painting-projects` : créer un projet et ses éléments physiques ;
- `POST /workshop/painting-projects/{id}/transitions` : changer le cycle de vie du projet ;
- `POST /workshop/items` : ajouter un exemplaire physique ;
- `POST /workshop/items/{id}/stage-transitions` : faire avancer son workflow ;
- `POST /workshop/items/{id}/comments` et `/photos` : enrichir son journal ;
- `POST /workshop/recipes`, `/{id}/transitions` et `/workshop/items/{id}/recipe-assignments` : gérer les recettes ;
- `POST /shopping/items/{id}/status` : changer l’état d’une intention d’achat ;
- `POST /projections/rebuild` : contrôler la reconstruction des vues depuis le ledger.

Les commandes d’agrégat répondent `202 Accepted` une fois le lot enregistré dans le publication store durable. L’en-tête `Location` désigne `/api/v1/publications/{id}`. Le ledger consomme ensuite le lot de manière asynchrone ; seule sa confirmation produit une notification SSE.

Le CLI Java appelle les mêmes ports applicatifs. Lorsqu’un serveur local est disponible, il lui délègue les écritures pour conserver un seul writer. L’option globale `--wait` attend explicitement le commit du lot (`--wait-timeout=PT30S` par défaut).
