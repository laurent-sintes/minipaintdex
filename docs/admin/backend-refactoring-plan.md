# Plan de refactorisation backend

Ce plan transforme progressivement Mini Paint Dex vers l’urbanisme défini dans `AGENTS.md`. Il assume que le projet est encore en construction : les API et le ledger peuvent être remplacés sans couche de compatibilité, mais les données utilisateur qui ne sont pas explicitement jetables doivent rester préservées.

## Résultat cible

```text
REST adapter ------------------\
                                 > application input ports -> AggregateRoot
Picocli adapter ---------------/                              |
                                                               v
                                                    typed DomainEvent records
                                                               |
                                                               v
                                                     EventBus application port
                                                               |
                                          durable publication store + Spring Events
                                                               |
                                                               v
                                              asynchronous single LedgerSubscriber
                                                               |
                                                               v
                                                  committed event notifications
                                                      |                  |
                                                      v                  v
                                                projections             SSE
```

Les catalogues `market` et la configuration `site` restent des référentiels versionnés sur fichiers. Le contexte `workshop` reste event-sourcé. REST et CLI invoquent les mêmes cas d’usage, sans logique métier dupliquée.

## État d’implémentation

Les fondations de ce plan sont désormais présentes : AggregateRoots exécutables, records d’événements typés, codec explicite, contrôle de version du ledger, publication durable, EventBus Spring mono-consommateur, reprise et drain au shutdown, snapshot effectif, ports d’entrée ségrégés, adaptateurs fichier cohésifs, contrôleurs REST découpés, `ProblemDetail`, pagination Spring traduite en `PageQuery`, HATEOAS, Actuator, OpenAPI, NDJSON, SSE par lot, CLI `--wait` et invalidation ciblée du cache React.

Les frontières applicatives utilisent maintenant des commandes et vues immuables typées. Les imports bulk extensibles emploient un arbre `StructuredDocument` indépendant de Jackson et YAML. Spring expose quatre services cohésifs (`Site`, `MarketCatalog`, `Workshop`, `Administration`) et REST comme Picocli ne dépendent plus de la classe de coordination interne. Une règle ArchUnit interdit le retour de `Map<String,Object>` dans les packages de contrats applicatifs.

L’urbanisme fonctionnel est également exécutable : le domaine Market réside sous
`domain.market`, Workshop sous `domain.workshop`, et les règles ArchUnit contrôlent notamment
l’absence de dépendance Market vers Workshop et l’interdiction pour `MarketCatalogApplicationService`
de repasser par le noyau de coordination transverse ou son snapshot global. Le port dédié
`MarketCatalogReader` ne publie que des données du shared kernel. Le catalogue Market ne calcule plus
`quantity` ni `inWorkshop`. Les quantités possédées sont une projection Workshop qui consomme
`MarketCatalogUseCases`; les badges de l’interface sont composés à partir de deux réponses distinctes.

La campagne de contrôle finale reste la source de vérité : une capacité n’est considérée livrée que si le build Maven racine, les tests d’architecture et les scénarios d’exécution associés passent.

## Constats de départ (baseline historique)

- La modularisation Maven sépare déjà `domain`, `application`, `adapter-file`, `bootstrap`, `server` et `cli`.
- `MiniPaintDexService` concentrait requêtes, commandes, projection, validation, export et mapping de présentation ; les ports et services publics sont maintenant séparés, et le noyau de coordination restant doit continuer à rétrécir au fil des capacités.
- Le contrôleur REST était monolithique et retournait principalement des `Map<String, Object>` ; les contrôleurs sont désormais séparés et leurs modèles métier sont typés.
- Le dépôt fichier implémente de nombreux ports et concentre cache, ledger, catalogue, média, verrouillage et cycle de vie.
- `PaintableProduct` porte de vrais invariants, mais plusieurs agrégats workshop restent des états projetés et les décisions sont encore prises par des handlers ou projecteurs.
- Le ledger manipule une enveloppe générique avec payload non typé.
- La pagination des peintures est facultative et réalisée après chargement et filtrage complets en mémoire.
- HATEOAS, OpenAPI, streaming NDJSON, SSE, Problem Details et Actuator ne sont pas encore intégrés.
- REST et CLI utilisent la même classe concrète, mais pas encore des input ports ségrégés.
- Les tests existants donnent une base utile, mais ne couvrent pas encore les frontières d’architecture, la concurrence des agrégats, la reprise du pipeline événementiel ni l’arrêt gracieux.

## Principes d’exécution

1. Avancer par capacités cohérentes et garder le projet compilable à chaque frontière de phase.
2. Ne pas entretenir l’ancien et le nouveau modèle en parallèle : migrer une tranche puis supprimer immédiatement l’ancien chemin.
3. Préserver les formats et données non jetables ; le ledger de développement peut être réinitialisé au moment explicitement prévu.
4. Utiliser des tests de caractérisation uniquement pour sécuriser une migration, pas pour pérenniser un mauvais contrat.
5. Lancer pendant les itérations les contrôles les moins coûteux capables de détecter l’erreur recherchée.
6. Reporter les contrôles transverses, les scénarios de charge et le build complet multi-technologie à la campagne finale.
7. Ne créer aucun commit et ne pousser aucune modification sans demande explicite distincte de l’utilisateur.

## Phase 0 — Baseline et garde-fous

### Travaux

- Inventorier les routes REST, commandes CLI, input/output réels et fichiers persistés.
- Capturer les résultats métier utiles qui doivent rester vrais après le changement de contrat.
- Ajouter des règles ArchUnit sur les dépendances :
  - `domain` ne dépend d’aucun framework ni module externe de l’application ;
  - `application` dépend uniquement du domaine et de bibliothèques Java neutres ;
  - les adaptateurs dépendent des ports, jamais l’inverse ;
  - REST ne dépend pas de `adapter-file` ;
  - CLI et REST n’accèdent pas aux repositories concrets.
- Décider et documenter les identifiants et versions des agrégats avant de réécrire le ledger.

### Contrôle rapide

- Tests ArchUnit seuls.
- Compilation de `domain` et `application`.
- Aucun build frontend ni test de charge.

### Sortie attendue

Une base de caractérisation minimale et des frontières automatiquement protégées.

## Phase 1 — Vocabulaire DDD et agrégats exécutables

### Travaux

- Introduire les value objects d’identité et de version nécessaires.
- Introduire une abstraction framework-independent `AggregateRoot` et le support des événements en attente.
- Transformer `Workshop`, `PaintingProject`, `WorkshopItem` et `WorkshopRecipe` en agrégats exécutables :
  - les méthodes métier valident les invariants ;
  - elles émettent les événements ;
  - elles appliquent ces événements à leur propre état ;
  - les projecteurs ne décident plus des transitions.
- Implémenter le cycle de vie complet de `PaintingProject` et les transitions de recette.
- Créer un record Java par événement métier dans le même package que son AggregateRoot.
- Utiliser une famille d’événements scellée par agrégat et une interface commune non Spring.
- Séparer l’événement métier de son enveloppe technique typée.
- Supprimer les payloads `Map<String, Object>` du domaine.
- Revoir les agrégats encore implicites, notamment l’intention d’achat, avant de conserver leurs événements dans le ledger.

### Contrôle rapide

- Tests unitaires d’agrégats en style given-events / when-command / then-events.
- Tests de transitions invalides et versions attendues.
- Aucun serveur Spring démarré.

### Sortie attendue

Toutes les décisions workshop sont prises par un AggregateRoot et produisent des événements métier typés.

## Phase 2 — Codec événementiel et nouveau ledger

### Travaux

- Créer un codec d’adaptateur qui associe explicitement `event_type` et `schema_version` à chaque record métier.
- Conserver les annotations Jackson et la représentation JSONL hors du domaine.
- Encoder et décoder les enveloppes, événements et value objects sans réflexion implicite fragile.
- Ajouter la validation de schéma, l’ordre, l’idempotence et la version attendue à l’append.
- Réinitialiser le ledger de développement jetable au nouveau format, sans fabriquer de faux événements historiques.
- Réécrire les projecteurs à l’aide de pattern matching exhaustif sur les familles d’événements.

### Contrôle rapide

- Test de round-trip pour chaque type d’événement.
- Tests de lots atomiques, collision d’idempotence et version concurrente.
- Test de reconstruction des quatre agrégats workshop.

### Sortie attendue

Un ledger fortement typé et reproductible, sans payload métier générique.

## Phase 3 — EventBus asynchrone, publication durable et arrêt gracieux

### Travaux

- Définir dans `application` les interfaces documentées `EventBus`, `EventSubscriber`, `EventPublicationStore` et leurs records de résultat.
- Publier des `EventBatch` atomiques et ordonnés, un batch par commande.
- Ajouter le module `adapter-spring-events` :
  - adaptation vers `ApplicationEventPublisher` ;
  - dispatcher dédié, borné et mono-consommateur ;
  - aucun passage du modèle Spring dans `application` ou `domain`.
- Implémenter dans `adapter-file` le publication store durable : `pending`, `processing`, `completed`, `failed`.
- Faire du ledger l’unique `EventSubscriber` critique de la topologie initiale.
- Faire participer les événements acceptés mais non committés à l’état/version effectifs des agrégats.
- Bloquer ou sérialiser deux commandes concurrentes ciblant le même agrégat afin qu’aucune ne valide un état périmé.
- Reprendre les publications inachevées au démarrage avant de déclarer la write-readiness.
- Après acquittement ledger seulement, publier un `CommittedEventBatch` destiné aux projections et notifications.
- Implémenter un composant `SmartLifecycle` avec la séquence : arrêt des entrées, fermeture de l’admission, drain, flush ledger, acquittement, fermeture persistance.
- Configurer par `@ConfigurationProperties` capacité, thread, délais, reprise, retry, back-pressure et timeout d’arrêt.
- Préparer l’adaptateur, sans imposer AMQP maintenant, pour une externalisation Spring Modulith ou Spring Integration ultérieure.

### Sémantique des commandes

- Une publication acceptée durablement peut être retournée avant son ingestion dans le ledger.
- REST renvoie alors `202 Accepted`, un identifiant de publication et un lien de suivi.
- CLI fournit le même résultat JSON et peut proposer une attente explicite.
- Les lectures restent éventuellement cohérentes jusqu’au `CommittedEventBatch`.

### Contrôle rapide

- Tests du publication store et du subscriber avec exécuteur déterministe.
- Test de reprise ciblé après publication non acquittée.
- Test de drain avec une petite file, sans lancer toute la SPA.

### Sortie attendue

Une ingestion asynchrone robuste aux arrêts normaux et récupérable après interruption brutale.

## Phase 4 — Application layer par cas d’usage

### Travaux

- Découper `MiniPaintDexService` en input ports cohésifs, par exemple :
  - recherches et consultation du marché ;
  - administration des référentiels ;
  - commandes et requêtes workshop ;
  - recettes et rapprochement ;
  - shopping ;
  - activité et maintenance.
- Créer un handler par commande/requête ou un petit service cohésif lorsque plusieurs opérations partagent réellement la même politique.
- Remplacer toutes les sorties `Map<String, Object>` par des records de résultat typés.
- Créer `PageQuery`, `SortQuery`, `PageResult` et les read models nécessaires sans dépendance Spring.
- Déplacer CSV, YAML, HAL, JSON et rendu CLI dans leurs adaptateurs.
- Retirer liveness/readiness du service métier.
- Documenter tous les input/output ports et contrats de ressources avec la Javadoc comportementale demandée.
- Laisser uniquement les commentaires de points chauds dans les implémentations.

### Contrôle rapide

- Tests directs de chaque handler avec ports en mémoire.
- Tests de règles de dépendances.
- Compilation de `application`, `domain` et de leurs dépendants immédiats.

### Sortie attendue

REST et CLI peuvent dépendre d’interfaces de cas d’usage stables, sans service universel.

## Phase 5 — Ports de persistance et adaptateurs fichiers

### Travaux

- Remplacer le snapshot universel et le repository fichier multifonction par des ports typés et des adaptateurs cohésifs : site, peintures marché, produits à peindre, guides, inventaire, workshop, ledger, publications et média.
- Mutualiser seulement les mécanismes techniques légitimes : verrou cross-process, remplacement atomique, empreintes, codec YAML/JSONL.
- Garder le cache versionné à l’intérieur de l’infrastructure tant qu’aucun cas d’usage n’exige une sémantique de cache.
- Conserver l’initialisation explicite, la publication atomique des générations et la sentinelle de désynchronisation.
- Faire échouer les writes sur persistance dégradée tout en conservant la dernière génération valide pour les reads.
- Ajouter Actuator et des health contributors pour persistance, publication store et progression du ledger.
- Préparer les ports pour une future implémentation base de données sans reproduire les détails du fichier.

### Contrôle rapide

- Tests de contrat réutilisables pour chaque repository.
- Tests ciblés de verrouillage, remplacement atomique et cache generationnel.
- Un test Spring de binding/configuration, pas encore le JAR complet.

### Sortie attendue

Des adaptateurs remplaçables et de taille maîtrisée, sans dépendance circulaire entre adapters.

## Phase 6 — REST typé, pagination et hypermédia

### Travaux

- Découper le contrôleur par familles de ressources.
- Utiliser des records REST dédiés et Jakarta Validation.
- Remplacer les erreurs maison par RFC 9457 `ProblemDetail`.
- Supprimer toute dépendance REST vers une exception de `adapter-file`.
- Utiliser `page`, `size`, `sort` et les limites standard configurées par Spring côté HTTP, puis traduire vers les types applicatifs.
- Rendre les catalogues volumineux paginés par défaut ; supprimer le retour intégral implicite.
- Ajouter Spring HATEOAS et des assemblers dédiés :
  - liens `self`, parents et ressources liées ;
  - liens de pagination ;
  - liens d’actions uniquement quand l’agrégat autorise la commande ;
  - liens de suivi pour les commandes asynchrones.
- Normaliser les routes de commandes en ressources REST cohérentes, sans conserver d’alias obsolète.
- Remplacer les endpoints maison de santé par les groupes Actuator appropriés.

### Contrôle rapide

- Tests MockMvc par contrôleur et assembler.
- Tests de bornes de pagination, liens permis/interdits et Problem Details.
- Pas encore de scénario navigateur complet.

### Sortie attendue

Une API typée, navigable et cohérente avec les frontières DDD.

## Phase 7 — OpenAPI, streaming et événements serveur

### Travaux

- Ajouter springdoc compatible Spring Boot 4 au module serveur.
- Décrire operation IDs, schémas, HAL, pagination, erreurs, NDJSON, SSE et réponses `202`.
- Exposer `/v3/api-docs`, `/v3/api-docs.yaml` et l’interface interactive locale.
- Ajouter sous `ABOUT` une page “API REST”, immédiatement à côté de la documentation administrateur.
- Ajouter des endpoints dédiés de streaming de référentiels en `application/x-ndjson` avec `StreamingResponseBody`.
- Ne jamais détourner l’endpoint paginé pour produire un flux complet.
- Ajouter un flux SSE de notifications committées : identifiant stable, `Last-Event-ID`, heartbeat, reprise bornée, topics et resynchronisation.
- Nettoyer les emitters déconnectés et configurer l’exécuteur/timeout asynchrone MVC.

### Contrôle rapide

- Test de génération OpenAPI et présence des schémas critiques.
- Test NDJSON sur quelques entrées et déconnexion simulée.
- Test SSE unitaire avec reprise d’un identifiant.

### Sortie attendue

Des contrats découvrables, un transfert bulk borné et un rafraîchissement frontend événementiel.

## Phase 8 — Alignement CLI et React

### Travaux CLI

- Faire dépendre chaque commande Picocli du même input port que REST.
- Conserver un JSON déterministe et des codes de sortie documentés.
- Aligner `202` sur un reçu de publication et fournir `--wait` lorsque l’opérateur a besoin du résultat committé.
- Continuer à déléguer les mutations au serveur local lorsqu’il est actif afin de conserver un seul writer.

### Travaux React

- Mettre à jour les modèles TypeScript depuis les contrats OpenAPI ou un mapping explicitement testé.
- Utiliser systématiquement les ressources paginées et libérer les pages quittées.
- Ne pas stocker le catalogue complet reçu en NDJSON dans un cache global.
- S’abonner aux SSE committés, invalider la ressource concernée puis la relire via REST.
- Gérer attente de publication, succès committé, erreur et reconnexion.
- Ajouter la page `ABOUT / API REST` sans popup.
- Conserver tous les libellés indépendants du domaine dans `data/site`.

### Contrôle rapide

- Tests ciblés des routes, hooks de chargement, invalidations SSE et pagination.
- Tests CLI des nouveaux reçus et de l’option d’attente.

### Sortie attendue

Deux adaptateurs alignés sur les mêmes cas d’usage et un frontend sans second cache métier.

## Phase 9 — Documentation, nettoyage et cohérence

### Travaux

- Mettre à jour README, documentation DDD, services REST, CLI, skills, données et datasets.
- Documenter les garanties EventBus, les états de publication, l’éventuelle cohérence et le shutdown.
- Générer les diagrammes de bounded contexts, agrégats, ports/adapters et pipeline événementiel.
- Supprimer les anciennes routes, DTO, maps, projecteurs décisionnels, facades et fichiers devenus inutiles.
- Vérifier qu’aucun type ou identifiant historique ambigu ne subsiste.
- Vérifier que le skill projet utilise toujours Maven comme point d’entrée et que les skills de données passent par CLI/REST pour écrire.

### Contrôle rapide

- Recherche statique des anciens types/routes et des `Map<String, Object>` aux frontières.
- Vérification des liens documentaires.

### Sortie attendue

Un seul urbanisme lisible dans le code, les contrats, les données, la documentation et les skills.

## Phase 10 — Contrôles profonds finaux

Cette phase est volontairement exécutée une seule fois lorsque toutes les phases précédentes sont intégrées et stables.

### Build produit

- Exécuter le Maven Wrapper depuis la racine avec le cycle `verify` complet.
- Vérifier Java, Python, lint, types TypeScript, tests React et build SPA.
- Construire le JAR Spring Boot self-contained et démarrer exactement cet artefact.

### Architecture et contrats

- Exécuter toutes les règles ArchUnit.
- Vérifier qu’aucun module domaine/application ne dépend de Spring, Jackson, Picocli ou du filesystem.
- Valider l’OpenAPI généré et l’absence de route non documentée.
- Exécuter les contrats sémantiques communs REST/CLI.

### DDD et concurrence

- Tester chaque AggregateRoot, toutes ses commandes et transitions interdites.
- Lancer des commandes concurrentes sur un même agrégat et vérifier versions, invariants et idempotence.
- Vérifier qu’une commande B voit l’événement accepté de A même si le ledger ne l’a pas encore committé.
- Reconstruire toutes les projections depuis un ledger vide puis complet.

### Pipeline événementiel

- Charger la queue au-delà d’un batch Reichbusters complet.
- Vérifier ordre, back-pressure, métriques, absence de doublon et progression de readiness.
- Arrêter Spring Boot avec des publications pending et in-flight ; vérifier que le tuyau se ferme, se vide et que le ledger est flushé avant la fin du processus.
- Forcer une interruption sans drain, redémarrer, reprendre les publications et comparer l’état final.
- Injecter une erreur d’écriture ledger, vérifier `failed`, reprise et absence de perte.

### API, streaming et SSE

- Tester bornes et abus de `page`/`size`/`sort`.
- Vérifier tous les liens HATEOAS selon les états des agrégats.
- Streamer le catalogue complet en NDJSON en mesurant mémoire, délai du premier élément et comportement sur déconnexion.
- Tester SSE avec reconnexion, `Last-Event-ID`, heartbeat, replay expiré et resynchronisation.
- Vérifier les réponses `202`, le suivi de publication et la convergence des projections.

### Exploitation locale et frontend

- Vérifier Actuator liveness/readiness en état sain et dégradé.
- Contrôler les paramètres JVM, exécuteurs et délais d’arrêt.
- Tester les parcours React principaux dans le navigateur intégré sans catalogue global en cache.
- Vérifier la page OpenAPI, la documentation administrateur et les libellés provenant de `data/site`.

### Données et livraison

- Valider tous les YAML, JSONL, datasets et références d’identifiants.
- Vérifier que les données actives, datasets et média respectent leurs répertoires.
- Examiner le diff Git, les fichiers générés et les changements sans rapport.
- Ne committer qu’après demande explicite ; ne pousser qu’après une seconde demande explicite.

## Définition de terminé

Le refacto est terminé lorsque :

- chaque commande métier passe par un input port et un AggregateRoot ;
- chaque événement métier est un record typé émis par son AggregateRoot ;
- le bus accepte durablement les batches et le ledger les ingère de manière asynchrone, ordonnée et récupérable ;
- Spring Boot attend réellement le drain du pipeline à l’arrêt ;
- REST et CLI exposent les mêmes cas d’usage ;
- l’API est typée, paginée, hypermédia et documentée par OpenAPI ;
- NDJSON et SSE respectent les bornes mémoire et les garanties de reprise ;
- le frontend reste un client REST/SSE sans cache métier parallèle ;
- les règles d’architecture et tous les contrôles profonds finaux passent ;
- la documentation et `AGENTS.md` décrivent exactement le code livré.
