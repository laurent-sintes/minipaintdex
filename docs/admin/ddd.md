# Modèle DDD

## Règle de langage ubiquitaire

Le cœur du domaine définit le vocabulaire. Les contrats applicatifs, ressources et champs REST,
schémas OpenAPI, commandes/options/résultats CLI et modèles frontend reprennent les mêmes concepts.
Un renommage métier se propage dans toutes ces couches et dans leurs tests de contrat : pas de
synonymes concurrents ni d’alias d’API. Cette règle de design est obligatoire dans `AGENTS.md`.

| Concept | Domaine | REST | CLI |
| --- | --- | --- | --- |
| Référence commerciale | `PaintProduct` | `market/paint-products` | `market paint-products` |
| Pot physique | `PaintPot` | `workshop/paint-pots` | `workshop paint-pots` |
| Stock calculé | `WorkshopPaintStock` | `workshop/paint-stocks` | `workshop paint-stocks` |
| Exemplaire à peindre | `WorkshopPaintable` | `workshop/paintables` | `workshop paintables` |
| Élément de catalogue | `PaintableComponent` | `paintableComponents` | `--paintable-component-id` |
| Projet de peinture | `PaintingProject` | `workshop/painting-projects` | `workshop painting-projects` |
| Ligne d'achat | `ShoppingListEntry` | `workshop/shopping-list/entries` | `workshop shopping-list entries` |

## Bounded context Market

- `PaintProduct` représente une peinture commercialisée et son `PaintProductProfile` standard (rôles, méthode et système d’application, couvrance, fini, effets, sous-couche et liant). Les termes propres aux marques sont convertis par des mappings YAML versionnés sans supprimer les observations source.
- `PaintableProduct` est l’aggregate root d’une boîte, gamme ou autre produit contenant des figurines ou décors à peindre.
- `MarketPaintingGuide` conserve la connaissance sourcée d’un peintre ou d’une publication. Il ne décrit pas les choix personnels de l’atelier.

`MARKET` porte le shared kernel de connaissances de référence. `WORKSHOP` peut dépendre de ses
contrats stables pour référencer peintures, produits, éléments de catalogue et guides. La dépendance
inverse est interdite : le marché ne connaît ni état, ni service, ni événement, ni projection de
l’atelier. Les vues qui rapprochent les deux contextes, telles que la prévisualisation d’import ou
le rapprochement avec les peintures possédées, sont des cas d’usage de l’atelier.

Concrètement, les réponses Market ne portent ni `inWorkshop`, ni quantité possédée. L’endpoint
Workshop des peintures compose une référence Market avec la quantité personnelle en dépendant du
port `MarketCatalogUseCases`. Le navigateur compose de la même manière le badge « dans l’atelier »
à partir des collections Market et Workshop séparées.

Les packages Java rendent cette direction visible avec `com.minipaintdex.domain.market..` et
`com.minipaintdex.domain.workshop..`. Une règle ArchUnit autorise `WORKSHOP -> MARKET` par contrat
et refuse toute dépendance `MARKET -> WORKSHOP`.

Le port `MarketCatalogReader` publie un `MarketCatalogSnapshot` limité aux peintures, produits à
peindre et guides du contexte. Les services Market ne reçoivent ni le `SnapshotRepository` global,
ni son `DataSnapshot` transverse. Cette restriction est contrôlée automatiquement par ArchUnit.

Le catalogue physique est découpé par marque dans `data/market/paints/<brand>.yaml`. L’adaptateur
fichier fusionne ces documents dans un seul snapshot logique et les remplace sous le même verrou.
Les correspondances de vocabulaire sont isolées dans `tools/minipaintdex-data/mappings/<brand>.yaml`.
Chaque mapping déclare aussi un `brand_code` technique immuable. L'identité initiale d'une peinture
est `<brand-code>-<référence-fabricant-normalisée>` : par exemple `pau-p951`, `tap-wp2007p`,
`val-72-483` ou `cit-prod4190213-99189958145`. La référence source reste conservée sans perte dans
`reference`; la gamme et le nom, susceptibles d'évoluer, ne participent pas à l'identité. Une fois
créé, l'ID est conservé par les refreshs et tout changement de référence exige une réconciliation explicite.
Le profil canonique alimente seul la recherche et les filtres. Chaque peinture rafraîchie conserve
en parallèle un `source_snapshots` horodaté avec le fournisseur, l’URL et la charge source collectée :
les attributs propres à une marque restent donc auditables sans polluer le modèle standard. Un attribut
ne rejoint `PaintProductProfile` que s’il décrit un comportement stable et comparable entre marques.
Les horodatages fabriqués à l'heure de lecture par un fournisseur sont exclus explicitement ; les faits
commerciaux, la provenance et les vraies dates de mise à jour restent conservés.

Les images suivent une politique commune à toutes les marques. `manufacturer_image` conserve sa
qualité de provenance et sa date de vérification : `official_photo` (1), `retailer_photo` (2),
`owned_photo` (3), `generic_visual` (4), `color_swatch` (5) ou `none` (6). Toute qualité autre que
`official_photo` doit expliquer sa limite dans `quality_limitation` avec un code contrôlé, un détail
lisible et la date du constat. Un rafraîchissement conserve toujours la meilleure qualité connue ; si
un candidat officiel échoue au contrôle, la meilleure image précédente reste en place et son motif
est actualisé. Une photo officielle peut être remise en concurrence après 365 jours. Le score visuel
technique et ses motifs complets restent dans l’audit d’import : ils aident à détecter aplats, damiers
et images pauvres sans remplacer la provenance métier. Les rasters acceptés sont publiés dans un
canevas carré centré, tandis que l’URL et l’instantané de source préservent la preuve originale.

## Bounded context Workshop

- `Workshop` est l’aggregate root durable du contexte personnel et référence les projets en cours.
- `PaintingProject` est l’aggregate root de l’intention de peindre un `PaintableProduct`. Son cycle de vie est `planned`, `active`, `completed`, `archived`.
- `WorkshopPaintable` est l’aggregate root d’une figurine ou d’un décor physique. Il porte son workflow, ses commentaires, ses photos et l’affectation d’une recette.
- `WorkshopRecipe` est l’aggregate root d’un plan de peinture personnel, distinct du guide du marché.


### Pots et stock

Un `PaintPot` est un agrégat event-sourced indépendant, avec un `paintProductId` stable.
Deux pots du même produit ont deux identités et des historiques distincts.

`WorkshopPaintStock` est une projection par produit : `quantity` compte les pots possédés,
`availableQuantity` exclut les pots secs ou vides. Une observation inconnue ne signifie pas plein.
Les recettes désignent toujours des produits, et calculent la disponibilité depuis les pots.

L'état, le niveau estimé, la possession, l'ouverture, les notes et les photos appartiennent au pot.
L'image catalogue n'est utilisée qu'en repli clairement identifié. L'import fusionne les identités
sans modifier un pot existant ; les quantités ne sont plus une source stockée éditable.

`WorkshopShoppingPlan.PaintPurchaseIntent` décrit une intention explicite. Les besoins de peinture
sont calculés à la lecture ; `ShoppingListEntryView` les rapproche des intentions. L’agrégat
`ShoppingListEntry` conserve uniquement l’état coché : cocher ne signifie ni acheter ni ajouter un pot.

## Journal et projections du Workshop

Le ledger JSONL est le journal global append-only du board de l’atelier. Les projections reconstruisent les vues du Workshop, des PaintingProjects, des WorkshopPaintables, des recettes et des achats.

Seuls les AggregateRoots émettent les événements métier, sous forme de records Java typés rangés dans leur package. Une enveloppe technique porte l’identité, la version d’agrégat, la corrélation, l’acteur et l’idempotence. Un `EventBatch` regroupe atomiquement tous les événements d’une commande.

L’application publie ce lot dans un `EventBus` indépendant de Spring. L’adaptateur Spring Events l’enregistre d’abord dans une outbox fichier, puis un consommateur unique l’ajoute au ledger. L’acquittement produit une notification de lot committé destinée aux projections et au SSE. Au shutdown, Spring ferme l’admission, vide le dispatcher puis laisse toute publication non terminée récupérable au redémarrage.

La version attendue de chaque agrégat est contrôlée dans la même section critique que l’append. Les événements acceptés mais pas encore ingérés participent au snapshot effectif des décisions suivantes.

L’ordre de rejeu est celui du ledger : partitions mensuelles ordonnées, puis ordre des lignes.
Les événements d’un lot conservent leur séquence, y compris dans le snapshot effectif des publications
en attente. Ni leur date ni leur identifiant ne doivent les retrier : une création doit toujours
précéder les événements qui en dépendent, même lorsque leurs horodatages sont identiques.

Le cœur des identifiants, événements et données reste en anglais. Les libellés français appartiennent à `data/site/fr.yaml`.

## Encodages persistés conservés

Les adaptateurs conservent les formats v1 Market et les événements existants du Workshop. Les quantités
historiques ont été converties en inscriptions `paint_pot.registered` à identité stable, sans changer les
références ni le nombre de pots. Les anciens artefacts d’import restent des preuves, pas des imports à rejouer.

| Encodage existant | Sens canonique |
| --- | --- |
| `catalog_items`, `catalog_item_id` | composants à peindre, `paintableComponents`, `paintableComponentId` |
| `product_id` | `paintableProductId` |
| `paint_pot.registered`, `paint_product_id` | `PaintPot`, référence `paintProductId` |
| `workshop_item` et `workshop_item.*` | agrégat et événements typés `WorkshopPaintable` |
| `shopping_item.status_changed` | `ShoppingListEntryCheckedChanged` |
| enveloppe `project_id` | portée projet ; différente d’une référence de projet dans un événement Workshop |

Ce sont des mappings d’encodage, pas des lecteurs de versions alternatives. Les imports de datasets
transportent ce format ; les commandes REST/CLI ordinaires utilisent les noms canoniques en camelCase.


## Embedded paint search

`PaintProductSearchIndex` is an outbound read port, implemented by `adapter-lucene`.
It indexes commercial `PaintProduct` facts only. The domain and application never import Lucene.
No aggregate, paint identity, pot history or file schema changes for indexing.

The in-memory index warms at startup and is reconstructed on the first read of a changed,
validated catalog generation. Whole-generation replacement and reader closure are serialized
with searches; callers never observe a partly rebuilt index. An unchanged generation is reused.
Failed reconstruction fails the request and retains the previous generation for recovery.
Shutdown closes the reader, directory and analyzer after in-flight requests.
The bootstrap caches typed Market snapshots independently of Workshop changes.

Name matching folds accents/case, requires all analyzed terms, supports word prefixes, and
allows at most one edit in alphabetic name terms of at least five characters (configurable).
Numeric references are normalized without approximate correction. Exact reference/name weights,
field weights, expansion and input limits are validated Spring settings.
This is text search, not semantic paint substitution or color-distance matching.

Suggest is a distinct read use case and compact contract, backed by the same matching and
filtering rules as the pageable search. Workshop applies owner scope before limiting results;
the Market index never contains quantities, remaining level, notes or personal photos.

## Shared paint usage guides

Market owns the `PaintUsageGuide` aggregate, distinct from commercial catalog editions,
miniature painting guides, and physical pots. Brand catalogs contain a `paint_usage_guides`
registry. A product stores explicit `usage_guide_ids`; same-range products do not inherit a
document implicitly. The full prospective generation validates unique IDs, same-brand and
declared-range scope, and actionable instructions for technical products.

A guide owns its original structured content, sources, business revision, knowledge status
and translations. Source/content/scope changes advance exactly one revision. A translation
records its source revision, language, method and review status; stale translations remain
traceable but queries fall back to the original. Machine translations always require review
and never upgrade generic or unverified advice to manufacturer authority.

Product-local `usage_instructions` remain optional specific supplements, not copies of common
guidance. Exact extraction groups identical content and status within a brand; different
descriptions or precautions remain distinct. Original import evidence is retained separately.
Guide updates and product links use the existing atomic Market change-set boundary; no Workshop
event, identity, ownership, or observation changes.

The deterministic Python extraction command prepares a change set, never active data writes.
Java validates and imports it through REST/CLI. Brand dataset exports include guides, references
and translations. Refresh preserves shared links, avoids reinserting identical source text and
requires explicit document review when linked instructions change.
