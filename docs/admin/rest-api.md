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
- `GET /market/paint-catalog-editions?brand=...&page=0&size=60&sort=id,asc` et `/{id}` : éditions commerciales sourcées, distinctes des collectes ;
- `GET /market/paint-model` : JSON Schema v1 complet du modèle standard, vocabulaires contrôlés, filtres et tris génériques ;
- `GET /market/paints/quality` : indicateurs de complétude des champs standard, des fiches techniques et des visuels ;
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

## Recherche de peintures : filtres multiples

Les paramètres de facettes sont répétables, par exemple `color=blue&color=red`. Les valeurs d’une même facette sont combinées en OU ; les facettes distinctes sont combinées en ET. `brand` et `range` constituent un seul groupe en OU. Une sélection `range` est qualifiée par sa marque, par exemple `range=Vallejo%3A%3AModel+Air`. La forme non encodée est `marque::gamme` ; les deux-points et antislashs littéraux sont échappés par un antislash.

Les facettes renvoient `value`, `label`, `count` et `parentValue` (marque pour une gamme, sinon null). Le compteur ignore la sélection de sa propre facette, ou tout le groupe marque/gamme, mais conserve les autres critères, la recherche textuelle et les contraintes de fiche fabricant/résultat réel. Les valeurs incompatibles restent présentes avec un compteur nul. `total` désigne le nombre de références correspondant à tous les critères. Les facettes de l’Atelier restent limitées aux références possédées.

`x-filters` publie les contrôles `checkbox`/`toggle` et les groupes `catalog`/`primary`/`advanced`. REST, export filtré en streaming et CLI utilisent la même requête applicative. Exemple CLI : `minipaintdex --format json market paints search --brand Vallejo --range "Warhammer Colour::Contrast" --color blue --color red`.

## Commandes

### Éditions commerciales de peintures

Les fichiers de marque possèdent une liste `catalog_editions`. Chaque édition décrit `schema_version: 1`, `id`, `brand`, `title`, `edition_label`, `publication_year` facultatif, `ranges` et `source_urls`. Les gammes sont explicites et les sources sont des URL HTTPS. Chaque peinture peut porter plusieurs `catalog_memberships` : `catalog_edition_id`, `source_url`, `locator` (page ou autre repère vérifiable). Le serveur vérifie l’existence de l’édition, la même marque, la gamme et l’appartenance de la source à l’édition.

Le change set de peintures accepte une liste `catalog_editions` avec ses `operations` habituelles, ou seule avec `operations: []`. Une mise à jour d’édition conserve son identité et sa marque. L’application valide puis remplace atomiquement la génération du catalogue. Ces changements sont idempotents ; ils sont séparés des changements de quantité ou d’identité des peintures. L’absence de la liste préserve les éditions existantes. Les datasets de marque les transportent aussi.

CLI équivalent : `minipaintdex --format json market paint-catalog-editions list --brand "The Army Painter"`, puis `market paint-catalog-editions show --id tap-product-catalogue-2019`. Les commandes de lecture acceptent `--correlation-id` et la liste est paginée (`--page`, `--size`). La mutation utilise `market paints apply --input <changeset.json>`, d’abord sans `--apply`, puis avec cette option après vérification.

Une collecte n’est pas une édition commerciale. Elle ne crée aucune année fictive et ne retire les références absentes que pour une couverture explicitement complète, actuelle et limitée à leurs gammes ; les références historiques ou de statut inconnu sont conservées.

### Autres commandes

- `POST /market/paint-changesets` : simuler par défaut un change set de peintures ; l'application exige `dryRun=false` explicitement ;
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
