# Modèle DDD

## Bounded context Market

- `MarketPaint` représente une peinture commercialisée et son `MarketPaintProfile` standard (rôles, méthode et système d’application, couvrance, fini, effets, sous-couche et liant). Les termes propres aux marques sont convertis par des mappings YAML versionnés sans supprimer les observations source.
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
Le profil canonique alimente seul la recherche et les filtres. Chaque peinture rafraîchie conserve
en parallèle un `source_snapshots` horodaté avec le fournisseur, l’URL et la charge source collectée :
les attributs propres à une marque restent donc auditables sans polluer le modèle standard. Un attribut
ne rejoint `MarketPaintProfile` que s’il décrit un comportement stable et comparable entre marques.
Les horodatages fabriqués à l'heure de lecture par un fournisseur sont exclus explicitement ; les faits
commerciaux, la provenance et les vraies dates de mise à jour restent conservés.

## Bounded context Workshop

- `Workshop` est l’aggregate root durable du contexte personnel et référence les projets en cours.
- `PaintingProject` est l’aggregate root de l’intention de peindre un `PaintableProduct`. Son cycle de vie est `planned`, `active`, `completed`, `archived`.
- `WorkshopItem` est l’aggregate root d’une figurine ou d’un décor physique. Il porte son workflow, ses commentaires, ses photos et l’affectation d’une recette.
- `WorkshopRecipe` est l’aggregate root d’un plan de peinture personnel, distinct du guide du marché.

## Bounded context Activity

Le ledger JSONL est le journal global append-only du board de l’atelier. Les projections reconstruisent les vues du Workshop, des PaintingProjects, des WorkshopItems, des recettes et des achats.

Seuls les AggregateRoots émettent les événements métier, sous forme de records Java typés rangés dans leur package. Une enveloppe technique porte l’identité, la version d’agrégat, la corrélation, l’acteur et l’idempotence. Un `EventBatch` regroupe atomiquement tous les événements d’une commande.

L’application publie ce lot dans un `EventBus` indépendant de Spring. L’adaptateur Spring Events l’enregistre d’abord dans une outbox fichier, puis un consommateur unique l’ajoute au ledger. L’acquittement produit une notification de lot committé destinée aux projections et au SSE. Au shutdown, Spring ferme l’admission, vide le dispatcher puis laisse toute publication non terminée récupérable au redémarrage.

La version attendue de chaque agrégat est contrôlée dans la même section critique que l’append. Les événements acceptés mais pas encore ingérés participent au snapshot effectif des décisions suivantes.

Le cœur des identifiants, événements et données reste en anglais. Les libellés français appartiennent à `data/site/fr.yaml`.
