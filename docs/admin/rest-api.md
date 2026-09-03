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
- `POST /market/paint-products/search?page=0&size=60&sort=name,asc` : recherche paginée ; taille maximale 200 ;
- `GET /market/paint-products/facets` : facettes du catalogue ;
- `GET /market/paint-catalog-editions?brand=...&page=0&size=60&sort=id,asc` et `/{id}` : éditions commerciales sourcées, distinctes des collectes ;
- `GET /market/paint-product-model` : JSON Schema v1 complet du modèle standard, vocabulaires contrôlés, filtres et tris génériques ;
- `GET /market/paint-products/quality` : indicateurs de complétude des champs standard, des fiches techniques et des visuels ;
- `GET /market/paint-products/stream` : transfert incrémental `application/x-ndjson`, distinct de la pagination ;
- `GET /market/paintable-products` et `/{id}` : catalogue et détail d’un produit à peindre ;
- `POST /workshop/paint-stocks/search?page=0&size=60` et `GET /workshop/paint-stocks/facets` : références Market possédées,
  composées avec les quantités de l’Atelier ;
- `GET /workshop/painting-project-import-previews/{paintableProductId}` : impact et peintures manquantes avant import ;
- `GET /workshop/painting-guide-reconciliations/{guideId}` : rapprochement avec les peintures possédées ;
- `GET /workshop`, `/workshop/painting-projects` et `/{id}` : atelier et projets ;
- `GET /workshop/paintables` et `/{id}` : éléments physiques et progression ;
- `GET /workshop/recipes` : recettes personnelles ;
- `GET /workshop/shopping-list/entries` : besoins calculés et achats planifiés ;
- `GET /activity?paintingProjectId=...` : ledger global, éventuellement filtré par projet ;
- `GET /events` : notifications SSE de lots committés avec `Last-Event-ID`, heartbeat et replay borné ;
- `GET /publications/{id}` : état durable d’une commande asynchrone ;
- `GET /about` et `/documentation?audience=user|administrator` : version, auteur et documentation embarquée.

Les représentations des agrégats exposent des liens HATEOAS `self`, parent, collections liées et actions permises. Les liens de transition dépendent de l’état courant de l’agrégat.

## Recherche de peintures : filtres multiples

Le corps JSON de recherche porte des tableaux dans `filters`, par exemple `"color":["blue","red"]`. Les GET de facettes et de streaming gardent les paramètres répétables `color=blue&color=red`. Les valeurs d’une même facette sont combinées en OU ; les facettes distinctes sont combinées en ET. `brand` et `range` constituent un seul groupe en OU. Une sélection `range` est qualifiée par sa marque, par exemple `range=Vallejo%3A%3AModel+Air`. La forme non encodée est `marque::gamme` ; les deux-points et antislashs littéraux sont échappés par un antislash.

Les facettes renvoient `value`, `label`, `count` et `parentValue` (marque pour une gamme, sinon null). Le compteur ignore la sélection de sa propre facette, ou tout le groupe marque/gamme, mais conserve les autres critères, la recherche textuelle et les contraintes de fiche fabricant/résultat réel. Les valeurs incompatibles restent présentes avec un compteur nul. `total` désigne le nombre de références correspondant à tous les critères. Les facettes de l’Atelier restent limitées aux références possédées.

`x-filters` publie les contrôles `checkbox`/`toggle` et les groupes `catalog`/`primary`/`advanced`. REST, export filtré en streaming et CLI utilisent la même requête applicative. Exemple CLI : `minipaintdex --format json market paint-products search --brand Vallejo --range "Warhammer Colour::Contrast" --color blue --color red`.

## Commandes

### Éditions commerciales de peintures

Les fichiers de marque possèdent une liste `catalog_editions`. Chaque édition décrit `schema_version: 1`, `id`, `brand`, `title`, `edition_label`, `publication_year` facultatif, `ranges` et `source_urls`. Les gammes sont explicites et les sources sont des URL HTTPS. Chaque peinture peut porter plusieurs `catalog_memberships` : `catalog_edition_id`, `source_url`, `locator` (page ou autre repère vérifiable). Le serveur vérifie l’existence de l’édition, la même marque, la gamme et l’appartenance de la source à l’édition.

Le change set de peintures accepte une liste `catalog_editions` avec ses `operations` habituelles, ou seule avec `operations: []`. Une mise à jour d’édition conserve son identité et sa marque. L’application valide puis remplace atomiquement la génération du catalogue. Ces changements sont idempotents ; ils sont séparés des changements de quantité ou d’identité des peintures. L’absence de la liste préserve les éditions existantes. Les datasets de marque les transportent aussi.

CLI équivalent : `minipaintdex --format json market paint-catalog-editions list --brand "The Army Painter"`, puis `market paint-catalog-editions show --id tap-product-catalogue-2019`. Les commandes de lecture acceptent `--correlation-id` et la liste est paginée (`--page`, `--size`). La mutation utilise `market paint-products apply --input <changeset.json>`, d’abord sans `--apply`, puis avec cette option après vérification.

Une collecte n’est pas une édition commerciale. Elle ne crée aucune année fictive et ne retire les références absentes que pour une couverture explicitement complète, actuelle et limitée à leurs gammes ; les références historiques ou de statut inconnu sont conservées.

### Autres commandes

- `POST /market/paint-changesets` : simuler par défaut un change set de peintures ; l'application exige `dryRun=false` explicitement ;
- `POST /market/paintable-product-changesets` : simuler ou appliquer un produit et ses guides ;
- `POST /workshop/paint-pot-imports` : simuler ou fusionner les inscriptions de pots ;
- `POST /workshop/painting-projects` : créer un projet et ses éléments physiques ;
- `POST /workshop/painting-projects/{id}/transitions` : changer le cycle de vie du projet ;
- `POST /workshop/paintables` : ajouter un exemplaire physique ;
- `POST /workshop/paintables/{id}/stage-transitions` : faire avancer son workflow ;
- `POST /workshop/paintables/{id}/comments` et `/photos` : enrichir son journal ;
- `POST /workshop/recipes`, `/{id}/transitions` et `/workshop/paintables/{id}/recipe-assignments` : gérer les recettes ;
- `POST /workshop/shopping-list/entries/{shoppingListEntryId}/checked` : changer uniquement le marqueur coché, sans achat ni ajout de stock ;
- `POST /projections/rebuild` : contrôler la reconstruction des vues depuis le ledger.

Les commandes d’agrégat répondent `202 Accepted` une fois le lot enregistré dans le publication store durable. L’en-tête `Location` désigne `/api/v1/publications/{id}`. Le ledger consomme ensuite le lot de manière asynchrone ; seule sa confirmation produit une notification SSE.

Le navigateur recharge les ressources REST actives à réception d’un lot committé ou d’une demande
de resynchronisation. Une coupure SSE est affichée en rouge ; le retour de connexion déclenche aussi
une relecture. Le statut HTTP d’une erreur EventSource n’étant pas exposé par le navigateur, son
infobulle décrit cette limite au lieu d’inventer une cause précise.

À l’arrêt, le flux ferme ses connexions sur `ContextClosedEvent`, avant la phase d’arrêt gracieux
HTTP. La fermeture est idempotente, refuse les nouveaux abonnements et annule les notifications
de présentation en attente ; elle ne touche ni au ledger ni aux publications durables.

Le CLI Java appelle les mêmes ports applicatifs. Lorsqu’un serveur local est disponible, il lui délègue les écritures pour conserver un seul writer. L’option globale `--wait` attend explicitement le commit du lot (`--wait-timeout=PT30S` par défaut).

## Contrats et CLI alignés sur le domaine

Les identifiants de relation sont `paintProductId`, `paintableProductId`, `paintableComponentId`,
`paintingProjectId` et `workshopPaintableId`. Un projet renvoie les mêmes noms que sa commande de
création. Un produit contient `paintableComponents`, la collection physique renvoie `paintables`
et la liste d’achats renvoie `entries`. Le détail d’un exemplaire expose ses champs directement,
avec `activity` et les liens HAL, sans enveloppe `item`.

La recherche de stocks renvoie `results.content: [{paintProduct, quantity, availableQuantity, personalPhoto, canReplacePhoto}]`,
avec `results.totalElements`, `results.page`, `results.size`, les suggestions demandées et la corrélation. Le CLI restitue ces mêmes champs et accepte les filtres répétables, les indicateurs
`--manufacturer-sheet-only` / `--real-result-only`, la pagination et le tri :

```text
minipaintdex --format json workshop paint-stocks search --brand Vallejo --page 0 --size 60 --sort "name,asc"
minipaintdex --format json workshop paint-stocks facets --color blue --color red
minipaintdex --format json workshop paint-stocks show --paint-product-id cit-27-29 --correlation-id photo-read
minipaintdex --format json workshop paintables list --painting-project-id paint-game
minipaintdex --format json workshop paintables show --workshop-paintable-id ws-copy-1
minipaintdex --format json workshop shopping-list entries list
minipaintdex --format json workshop shopping-list entries set-checked --shopping-list-entry-id buy-one --checked true
```

`GET /api/v1/workshop/paint-stocks/{paintProductId}` renvoie `{stock, correlationId}` et les liens
HAL vers le produit, les pots et l’atelier. Une référence connue sans pot renvoie un stock nul,
pas une erreur ; une référence inconnue renvoie 404. `personalPhoto` est le visuel personnel
retenu (identité du pot et du média, URL affichée et originale, traitement, légende et date), ou
null. Les photos officielles et de revendeur restent prioritaires. `canReplacePhoto` n’est vrai
que pour un pot possédé et un visuel catalogue de qualité personnelle ou inférieure. Cette règle
n’empêche pas de photographier un pot dans son journal. Le remplacement ajoute une photo via
la commande existante, conserve l’historique et ne modifie jamais le catalogue Market.

Les commandes de recette ordinaires utilisent `recipeId`, `paintableComponentId`, `basedOnGuideId`,
`supersedesRecipeId`, `displayName`, `actorId`, `occurredAt` ; les solutions utilisent `guideSlotId`
et `paintProductId`. Le JSON fourni à `workshop recipes create --input` est le même que celui du REST.
Les imports versionnés restent distincts : leurs fichiers/datasets conservent leur encodage v1
(`paint_id`, `catalog_item_id`, etc.), traduit par les adaptateurs. Aucun alias d’ancienne API n’est ajouté.
## Produits de peinture et pots physiques

Le catalogue publie `PaintProductView` via `POST /market/paint-products/search` (page `results.content`) et `GET /market/paint-products/{paintProductId}`.
L'atelier publie `PaintPotView` sous `/workshop/paint-pots` (collection paginée `pots`).
Filtrer par `paintProductId` et ajouter `includeRemoved=true` pour inclure les pots donnés ou jetés.

- `POST /workshop/paint-pots` : `paintPotId`, `paintProductId`, `acquiredAt` facultatif.
- `POST /workshop/paint-pot-imports?dryRun=true` : `schemaVersion: 1`, `kind: workshop_paint_pots`, liste `pots` des mêmes inscriptions.
- `POST /workshop/paint-pots/{id}/observations` : `condition` et `remainingLevel`.
- `POST /workshop/paint-pots/{id}/openings` : date `occurredAt` facultative, sinon maintenant.
- `POST /workshop/paint-pots/{id}/possession-changes` : `possession`.
- `POST /workshop/paint-pots/{id}/notes` : `note`.
- `POST /workshop/paint-pots/{id}/photos` : multipart `file`, `caption` facultative.

CLI : `workshop paint-pots search|show|add|import|observe|open|set-possession|note|photo`.
`import --input <json>` simule ; `--apply` applique. Toutes les mutations acceptent les métadonnées
d'idempotence/corrélation et le `--wait` global. Les réponses 202 désignent une publication durable :
attendre son engagement avant de relire la projection.

Les imports déjà connus ne changent ni état ni photos. Une référence déjà utilisée dans l'histoire
d'un pot ne peut être supprimée ni réidentifiée ; son retrait commercial reste possible.


## Paint search and suggestions

Search uses embedded Lucene behind an application port, not an Elasticsearch server.
The JSON contract is MiniPaintDex-specific, inspired by search APIs but **not Elasticsearch-compatible**.

- `POST /api/v1/market/paint-products/search?page=0&size=60&sort=relevance,desc`
- `POST /api/v1/workshop/paint-stocks/search?page=0&size=60`

Example request body:

```json
{
  "query": "kar",
  "filters": { "range": ["Warhammer Colour::Layer"], "manufacturerSheetOnly": true },
  "include": ["results", "suggestions"],
  "suggestionLimit": 8
}
```

`include` defaults to `["results"]`. Select `["suggestions"]` for autocomplete without a result page
or exhaustive count. The response contains `results` (content, page, size, totalElements),
`suggestions` and `correlationId`; unrequested parts are null. Empty requested parts mean no match.
Both parts use the same filters and ranked selection. Result sorting does not change suggestion relevance.
Defaults/maximums: page size 60/200, suggestions 8/20, text length 200, analyzed terms 16.
Search limits are configured under `minipaintdex.paint-search`; Spring pagination uses `spring.data.web.pageable`.

OR applies within facets, AND across facets, and brand/range form one OR group. Default order is
relevance (name/ID for empty text); existing name/brand/range/reference/date sorts remain available.
Each suggestion contains `paintProductId`, `name`, `brand`, `range`, `reference`,
`manufacturerImage`, `colorHex` and HAL relationships. Workshop suggestions include only owned
references, deduplicated by product, with ownership applied before the limit. Blank text gives no suggestions.

HAL `self`, `first`, `previous`, `next`, `last` links target POST searches: resend the **same JSON body**
at the supplied URL. Facets remain separate GET resources; pass the same text and filters as query
parameters. These read-only POSTs create no domain events and need no idempotency key.
Malformed JSON and unsupported fields return 400 ProblemDetail; invalid values return 422;
an unavailable index returns 503, never an empty successful response.
`X-Correlation-Id` is echoed, or generated when omitted.

CLI equivalents use the same application handler and business result, without HTTP links:

```text
minipaintdex --format json market paint-products search --query kar --include results,suggestions --size 60
minipaintdex --format json workshop paint-stocks search --query kar --include suggestions --suggestion-limit 8 --range "Warhammer Colour::Layer"
```

`--correlation-id` supplies the equivalent correlation value. The old GET collection-search routes
and separate `suggest` commands/resources are removed, with no compatibility aliases.

## Shared paint usage guides

- `GET /api/v1/market/paint-usage-guides`: pageable registry, with optional `brand`, `range`,
  and `paintProductId` filters. Product filtering follows explicit links, never implicit range inheritance.
- `GET /api/v1/market/paint-usage-guides/{paintUsageGuideId}`: one document.
- Both accept `language=fr|en|original` (default `fr`) and `X-Correlation-Id`.
- Lists support `page`, `size` and `sort=id|title,asc|desc`; HAL exposes paging and document links.
- Returned content includes its effective language, source revision, knowledge status,
  translation status/review flag and source URLs. Missing or stale translations return the
  original with an explicit status. Unknown products/documents return 404; invalid parameters return 422.

CLI equivalents:
```text
minipaintdex --format json market paint-usage-guides list --paint-product-id <id> --language fr
minipaintdex --format json market paint-usage-guides show --paint-usage-guide-id <id> --language original
```

The existing `POST /api/v1/market/paint-changesets?dryRun=true|false` and CLI
`market paint-products apply` also accept `paint_usage_guides`. Guide-only upserts are allowed.
A new guide starts at revision 1; a source change requires the next revision. Reapplying an
identical change set is idempotent. Products reference `usage_guide_ids`. Imports validate the
whole prospective catalog before committing documents and references together.

Extraction preparation:
```text
python tools/minipaintdex-data/mpdx_data.py paint-usage-guides extract --catalog data/market/paints --translations tools/minipaintdex-data/resources/paint-usage-translations-fr.json --output <change-set.json>
```
This tool uses exact operator-supplied translations/templates, fails on untranslated text,
and never writes active storage. Review, dry-run, then apply through Java. Technical instructions
retain their unverified/generic status and their original source evidence.
