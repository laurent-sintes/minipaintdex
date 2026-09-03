# MiniPaintDex agent instructions

This file defines the mandatory architecture and contribution rules for MiniPaintDex. Agents must preserve these decisions unless the user explicitly changes them.

## User communication

Communicate with the user in French and use informal second-person address (`tu`, `te`, `ton`) unless the user explicitly asks for another language or form of address.

## Product scope

MiniPaintDex is currently a local-first miniature-painting workshop application. Do not add authentication, remote hosting requirements, or a database unless explicitly requested. The architecture must nevertheless keep storage behind interfaces so a database can replace file storage later without changing the web client or the application use cases.

The application distinguishes three bounded contexts:

- `site`: application configuration and localized UI labels;
- `market`: public reference catalogs for paints and paintable products;
- `workshop`: the owner's inventory, painting projects, physical items, recipes, progress, photos, and activity.

Use English for directory names, schema keys, identifiers, event names, source code, API contracts, and other core data. French belongs in localized site configuration and user-facing text only. Use lowercase ASCII kebab-case for stable domain identifiers.

## Canonical domain model (DDD)

The domain model is the primary architectural driver. Before changing storage, REST, CLI, projections, or React, identify the affected bounded context, aggregate, identity, invariant, command, and event here. If a product decision changes the ubiquitous language or an aggregate boundary, update this section in the same change as the code. Adapters and screens must conform to the domain model; they must not define a competing model for convenience.

### Ubiquitous language and aggregate boundaries

The domain core owns the ubiquitous language. REST resources, request/response fields,
OpenAPI schemas, CLI command groups/options/JSON results, application contracts and frontend
models must use the same business concepts and relationship names. Do not introduce competing
synonyms such as `productId` for `paintableProductId` or `item` for a paint stock. A business
rename is one coordinated change across these surfaces, documentation and contract tests.
Technical collection/schema keywords and locally scoped value objects may remain generic when
their meaning is unambiguous. Existing persisted file/dataset/event encodings may be retained
without changing identities or history; document their single mapping in the adapters and never
expose alternate API or CLI vocabularies as compatibility aliases.

```text
MARKET (reference knowledge, file-versioned)
  PaintProduct                        aggregate root for one commercial paint reference
  PaintCatalogEdition                 sourced commercial publication, independent of scrape runs
  PaintUsageGuide                     shared, sourced usage document with revision-bound translations
  PaintableProduct                    aggregate root for a box, range, expansion, or set
    └── PaintableComponent             entity describing one kind of paintable component
         └── quantity                 number supplied by the market product
  MarketPaintingGuide                versioned aggregate root targeting one PaintableComponent

WORKSHOP (owner state; aggregates event-sourced, stock projected, shopping plan file-backed)
  Workshop                            aggregate root for the owner's whole workshop
    └── paintingProjectIds            references PaintingProject aggregate IDs
  PaintingProject                     aggregate root for the intent to paint one PaintableProduct
  WorkshopPaintable                   aggregate root for one physical miniature/scenery copy
  WorkshopRecipe                     versioned aggregate root for one personal painting plan
  PaintPot                            event-sourced aggregate root for one owned physical paint container
    └── paintProductId                 stable reference to the Market PaintProduct
  WorkshopPaintInventory              read-only projection of PaintPot histories
    └── WorkshopPaintStock             owned and usable pot counts grouped by PaintProduct
  WorkshopShoppingPlan               explicit personal purchase intentions
    └── PaintPurchaseIntent            intention, distinct from a calculated paint requirement
  ShoppingListEntry                  aggregate owning a list entry's checked marker

SITE (supporting configuration, file-versioned)
  SiteConfiguration                   localized labels and application presentation settings
```

`Product` alone is not part of the ubiquitous language because it is ambiguous with a paint sold on the market. Always use `PaintableProduct` in Java, API contracts, CLI semantics, file schemas, and technical documentation. User-facing French may use “produit à peindre”. `MiniProduct` is not used because a `PaintableProduct` can also contain scenery, vehicles, creatures, or accessories.

### Market bounded context

- The Java package boundary mirrors the bounded context: market domain types live below
  `com.minipaintdex.domain.market`; workshop types live below `com.minipaintdex.domain.workshop`.
  Do not create generic `domain.product` or `domain.paint` packages that hide context ownership.
- `MARKET` is the shared kernel of reference knowledge. Its published, stable contracts are the
  only market types that `WORKSHOP` may consume. The dependency is deliberately asymmetric:
  `WORKSHOP -> MARKET contracts` is allowed; `MARKET -> WORKSHOP` is forbidden in domain,
  application, adapters and tests. A cross-context read model must be composed outside the market
  package and must never make workshop state part of market truth.
- Keep market input ports pure. Import previews, owned-paint reconciliation and any other result
  involving inventory, projects or progress are workshop use cases, even when their URL links back
  to a market identifier. Market services must not receive a workshop repository, port, aggregate,
  event or view. They read through the dedicated `MarketCatalogReader` and `MarketCatalogSnapshot`;
  injecting the cross-context `SnapshotRepository` or `DataSnapshot` into a Market service is forbidden.
- Enforce the direction with ArchUnit. Any new market package or class must pass a rule prohibiting
  dependencies on `..workshop..`; do not bypass the rule through a universal service or an
  untyped `Map` facade.
- `PaintProduct` and `PaintableProduct` have different identities, metadata, search behavior, and lifecycles.
- A `PaintableProduct` owns its `PaintableComponent` entities. Each component has a stable ID, `paintableProductId`, English `kind`, and positive market quantity.
- The sum of paintable-component quantities must equal `expected_paintable_count`; invalid products must fail loading or change-set validation.
- A `PaintableProduct` contains public facts and sourced knowledge only. It never contains ownership, personal progress, physical workshop state, or personal recipes.
- A `MarketPaintingGuide` targets a paintable component by ID and owns its public paint slots and source provenance. It is not the owner's recipe.

### Workshop bounded context

- `Workshop` is an aggregate root with stable ID `my-workshop`. It is the durable owner context and references its `PaintingProject` aggregate IDs.
- `PaintingProject` is the aggregate root for the owner's intent to paint one `PaintableProduct`. It has its own ID, name and lifecycle `planned -> active -> completed -> archived`. It references market facts by `paintable_product_id` and never copies them.
- Creating a painting project is an idempotent application command. It uses catalog quantities to append one `painting_project.created` event and one `workshop_item.added` event per physical copy in one atomic ledger batch.
- Every physical copy is a separate `WorkshopPaintable` aggregate root with its own workflow, recipe assignment, photos, notes, and history. It references both a `paintableComponentId` and a `paintingProjectId`; persisted encodings are documented below.
- `WorkshopRecipe` has an independent lifecycle. It may be inspired by a market guide, but the owner's substitutions, mixtures, layers, and techniques belong only to the workshop.
- Use `PaintingProject` in the core and technical contracts, and the French label “Projet” in the UI. Never use the ambiguous bare Java type `Project`.

### Relationships and read models

| Source | Relationship | Target | Rule |
| --- | --- | --- | --- |
| `PaintableComponent` | belongs to | `PaintableProduct` | same `product_id`, no cross-product child |
| `MarketPaintingGuide` | documents | `PaintableComponent` | versioned, sourced market knowledge |
| `PaintingProject` | references | `PaintableProduct` | `paintable_product_id`; market facts stay in market |
| `WorkshopPaintable` | instance of | `PaintableComponent` | one aggregate per physical copy |
| `WorkshopPaintable` | grouped by | `PaintingProject` | `painting_project_id` |
| `WorkshopRecipe` | plans | `PaintableComponent` | owner lifecycle independent of market guide |
| recipe assignment | attaches | `WorkshopPaintable` | two copies may use different recipes |

Market product views and workshop views are distinct projections. Market responses never contain
`inWorkshop`, owned quantity, personal progress, or any other workshop-derived field. When the UI
needs a badge or a joined presentation, it composes separate Market and Workshop responses, or calls
a Workshop use case that consumes a published Market interface. A workshop view joins IDs at read
time to calculate physical counts, workflow progress, missing owned paints, activity, and guide coverage.

## Target repository layout

The target architecture is a modular Spring Boot application with a React/Vite frontend. Java 25 is the target language level. Maven is the root build and dependency-management tool; use the checked-in Maven Wrapper. pnpm remains the frontend package manager pinned in `frontend/package.json`, but the root Maven lifecycle must orchestrate the frontend validation and build as well as the deterministic Python data-tool tests so `mvnw verify` validates the whole product.

Maven is the single build and test entry point for every technology in the repository. The root `verify` lifecycle must compile, validate and test the Java/Spring backend, the TypeScript/React frontend and the Python data tools. Technology-specific commands may accelerate a local iteration, but they never replace the final Maven verification and no project technology may maintain an independent release build outside that lifecycle.

MiniPaintDex follows the self-contained system pattern. The production deliverable is one executable Spring Boot JAR containing the REST API and the compiled SPA. Spring Boot serves `index.html`, frontend assets, media, and the client-side route fallback. The separate Vite server exists only for development and proxies API requests to Spring Boot.

```text
backend/
  domain/                 # Pure Java entities, value objects, events, workflow rules
  application/            # Commands, queries, handlers, ports, DTO contracts
  adapter-file/           # YAML/JSONL outbound repositories
  adapter-lucene/         # Rebuildable embedded Market search index
  adapter-onnx/           # Local CPU segmentation of personal paint-pot photos
  adapter-spring-events/  # Spring Events bus, asynchronous dispatch and lifecycle adapter
  bootstrap/              # Shared validated Spring configuration and bean wiring
  server/                 # Spring Boot and Spring MVC REST adapter
  cli/                    # Picocli adapter using the same application services

frontend/
  package.json            # Frontend package and scripts
  vite.config.ts          # Vite development and production build
  public/                 # Static web assets copied by Vite
  src/
    components/           # React UI
    models/               # Frontend DTOs and view models
    styles/               # Shared Tailwind and application CSS

tools/
  minipaintdex-data/      # Deterministic Python import and validation tools

datasets/                 # Portable named datasets, never active application storage
  market/
    paint-brands/
    paintable-products/
  workshop/
    paints/
    painting-projects/

docs/
  user/                   # User documentation embedded in the application
  admin/                  # DDD, REST/OpenAPI, refactoring and skill documentation

config/
  application.yaml       # Canonical Spring Boot technical defaults

data/
  site/
  market/
    paints/
      <brand>.yaml
    paintable-products/
    painting-guides/
  workshop/
  ledger/
    events/
    publications/         # Durable pending/failed event-bus publications

media/
  market/
    paints/                 # Generated validated manufacturer-image cache
  workshop/
```

Do not add compatibility aliases or duplicate API vocabularies during this early construction phase unless the user explicitly asks for backward compatibility. Prefer one clear current model and reset disposable local seed data when authorized.

Until the first release, every MiniPaintDex contract owned by the application uses version `1`:
file schemas, change sets, datasets, event envelopes, REST routes and published model metadata. Evolve
that version in place instead of incrementing it. Do not add version negotiation, compatibility readers,
legacy aliases or migration commands unless the user explicitly authorizes a new version. Business
versions such as aggregate versions, painting-guide revisions and recipe revisions are not schema
versions and continue to evolve according to their domain rules.

## Browser and server boundary

The HTML/JavaScript frontend must never read from or write to `data/` or `media/` directly. It must not know filesystem paths.

All domain reads and writes go through the local REST server. The server is the only online writer and is responsible for:

- validating commands and workflow transitions;
- invoking application services;
- publishing aggregate-emitted domain event batches through the application event bus;
- building and serving projections;
- reading market and site repositories;
- storing and serving media;
- translating domain errors to transport errors.

Bind the local server to `127.0.0.1` by default. During development, the web dev server may proxy `/api` to it. A local production build may serve both the compiled frontend and the API.

Do not put business rules in React components, REST controllers, CLI commands, filesystem repositories, or serialization code.

Keep Spring annotations out of `backend/domain` and `backend/application`. Wire plain Java application services from the Spring Boot modules. Use Spring MVC rather than WebFlux for the local file-backed server. Use Picocli for non-interactive CLI commands, JSON output, and deterministic exit codes.

## Spring Boot configuration

Use Spring Boot's standard configuration facilities for every runtime or infrastructure setting. Canonical technical defaults belong in the versioned `config/application.yaml`; Spring may override them through the usual external `application.yaml`, environment variables, system properties, or command-line properties.

- Bind related settings once with validated, typed `@ConfigurationProperties` classes in the Spring bootstrap module.
- Do not scatter `@Value` expressions, direct environment reads, or repository-relative path literals across controllers, CLI commands, services, or adapters.
- Resolve storage locations, media locations, matching policies, limits, weights, scores, and other tunable behavior at startup. Inject typed layout or policy objects afterward.
- The REST server and CLI must start the same shared Spring bootstrap and therefore use the same configuration, repositories, application services, and domain policies.
- Keep Spring out of `backend/domain` and `backend/application`. Configuration binding maps Spring properties to plain Java policy/value objects before injecting them into those modules.
- File-backed market, workshop, ledger, and localized-site contents remain domain data loaded through repository ports. Spring owns their configured locations and object wiring; it does not turn those domain files into ad-hoc `Resource` reads in controllers.
- Fail startup on missing, malformed, out-of-range, or internally inconsistent mandatory configuration. In particular, each paint-matching weight set must be non-negative and sum to `1.0`.

Frontend-specific package management, TypeScript, lint, Vite configuration, and static assets belong under `frontend/`. The Maven root remains the single product build entry point and invokes pnpm with `frontend/` as its working directory.

## Hexagonal application architecture

Implement business capabilities once as application commands or queries. Expose each user-facing application use case through both a REST adapter and a CLI adapter. Both adapters must invoke the same application handler; never duplicate domain logic.

```text
REST adapter ----\
                  > application command/query handler -> domain -> outbound ports
CLI adapter -----/
```

Pure internal domain services, such as progress calculation, are shared implementation details and must not be exposed artificially as endpoints or CLI commands.

Every application use case must define:

- a typed command or query;
- a typed result;
- framework-independent domain/application errors;
- input validation at the boundary and invariant validation in the domain;
- a correlation identifier;
- an idempotency mechanism for mutations;
- direct handler tests independent of REST and CLI.

REST uses versioned routes under `/api/v1`. CLI commands use the `minipaintdex` executable and must support `--format json` so skills and automation never need to parse human-oriented output. Keep CLI and REST result semantics equivalent.

Examples:

| Use case | REST adapter | CLI adapter |
| --- | --- | --- |
| Search market paint-products | `POST /api/v1/market/paint-products/search` | `minipaintdex market paint-products search` |
| Refresh a paint brand | `POST /api/v1/market/paint-refreshes` | `minipaintdex market paint-products refresh` |
| Create a painting project | `POST /api/v1/workshop/painting-projects` | `minipaintdex workshop painting-projects create` |
| Add a workshop item | `POST /api/v1/workshop/paintables` | `minipaintdex workshop paintables add` |
| Import a dataset | category-specific REST command | `minipaintdex datasets import` |
| Transition a workflow stage | `POST /api/v1/workshop/paintables/{id}/stage-transitions` | `minipaintdex workshop paintables stages transition` |
| Attach a photo | `POST /api/v1/workshop/paintables/{id}/photos` | `minipaintdex workshop photos add` |
| Read activity | `GET /api/v1/activity` | `minipaintdex activity list` |
| Rebuild projections | maintenance REST resource | `minipaintdex projections rebuild` |

The frontend and CLI send business intent, not arbitrary events. For example, the application handler validates a stage-transition command at its boundary, loads the target aggregate and invokes its business method; the aggregate alone emits the corresponding domain event.

When the REST server is running, mutation commands from the CLI should use it so there is one writer. An explicitly offline maintenance mode may invoke application handlers in process only after acquiring an exclusive storage lock.

### Application services and ports

The application layer is the single transport-independent pivot for REST and CLI, but it must not become one universal service class. Define cohesive input-port interfaces by bounded context and capability, with one typed command or query and one typed result per use case. REST controllers and Picocli commands depend on those input ports, never on a concrete all-purpose facade. An optional facade may compose use cases for bootstrap purposes, but it must not own business rules or untyped presentation mapping.

The concrete Spring topology exposes four cohesive application services: `SiteApplicationService`, `MarketCatalogApplicationService`, `WorkshopApplicationService` and `AdministrationApplicationService`. REST controllers inject only the port they serve; Picocli subcommands select the same capability-specific ports. `WorkshopCommandService` and `WorkshopQueryService` are separate internal collaborators, never input ports and never injected into REST or CLI. The query side builds read models and cannot depend on `EventBus` or mutation writers; the command side loads aggregates and publishes their decisions. Site and administration logic must not flow through either collaborator.

Use interfaces where they express an architectural boundary, substitutable policy, input port or output port. Do not create one-to-one interfaces for every concrete class. Every public port interface must have Javadoc that specifies observable behavior, validation, ordering, idempotency, concurrency, consistency, failure and resource-lifetime guarantees. Implementation Javadoc and comments are reserved for non-obvious hotspots such as locking, atomic replacement, asynchronous shutdown, cache publication, retries, back-pressure or recovery; do not paraphrase straightforward code.

Each architectural Java package has a concise `package-info.java` describing its responsibility and dependency direction. Keep those files conceptual and stable; put behavior contracts on public interfaces, and reserve implementation comments for hotspots rather than duplicating code line by line.

Application contracts use domain types or dedicated immutable Java records. Do not expose `Map<String, Object>`, filesystem documents, Spring types, Jackson nodes or transport response objects across application boundaries. Export formatting, HAL links, HTTP errors, CLI rendering and YAML/JSON serialization belong to adapters.

Versioned bulk imports that must retain an extensible file schema use the immutable, serialization-neutral `StructuredDocument` algebra at the application boundary. Only input adapters translate JSON/YAML trees into it, and application validation must translate it to the relevant typed model before mutation. Do not use it as a shortcut for ordinary query results or aggregate state.

## Domain aggregates and events

Only an aggregate root may emit a domain event. Application handlers load or rehydrate an aggregate, invoke a business method, collect the events raised by that aggregate and publish them. Handlers, controllers, projectors, repositories and serialization code must never manufacture a domain event to bypass aggregate behavior.

Domain events are immutable, strongly typed Java records, equivalent in intent to Kotlin data classes. Each business event is colocated in the same package as the aggregate root that emits it and is named in English past tense. A common `domain.event` package may contain only framework-independent base abstractions such as `DomainEvent`, `EventEnvelope`, identifiers and metadata. It must not become a registry of unrelated business events.

Use one aggregate-local sealed event family where useful. The common cross-aggregate `DomainEvent` abstraction remains framework independent and does not carry Jackson, Spring Data, Spring Modulith or messaging annotations. Event payloads and aggregate state must not use `Map<String, Object>`. Keep delivery metadata such as event ID, recorded time, correlation, causation, actor and publication identity in a typed envelope so aggregate methods only create domain facts they genuinely know.

Event-sourced aggregates decide and apply:

```text
command -> AggregateRoot business method -> typed domain event -> aggregate apply(event)
ledger history -> aggregate apply(event) -> rehydrated state
```

Projectors only fold committed events into read models. They do not validate commands or decide whether a transition is allowed. Aggregate methods enforce invariants and expose allowed business actions; REST assemblers may translate those capabilities into hypermedia links without putting HTTP concepts in the domain.

Every event-sourced aggregate carries a version. Appending a batch uses expected versions or an equivalent application unit-of-work guard so invariant checks and persistence cannot race. Pending accepted publications must participate in the effective write state until they reach the ledger; a second command must never validate against state that ignores an earlier accepted event.

Domain events, application events and integration notifications are distinct:

- a domain event is a business fact emitted by an aggregate root;
- an application event reports an internal application or infrastructure occurrence and is not persisted as domain history unless the domain explicitly requires it;
- a committed-event notification is emitted only after durable ledger acknowledgement and may drive projections, SSE or future integrations;
- an external event is translated by an adapter into an application command before it reaches the domain.

## Event bus, asynchronous ledger and shutdown

The application defines a framework-independent `EventBus` publish/subscribe port and typed `EventSubscriber` contract. Aggregate roots never inject or call the bus themselves. The initial topology has exactly one critical domain-event subscriber, the ledger, but subscriber cardinality is bootstrap configuration rather than an interface limitation.

```text
REST or CLI
    -> application handler
    -> AggregateRoot
    -> EventBus.publish(EventBatch)
    -> durable publication store
    -> asynchronous LedgerSubscriber
    -> committed-event notification
    -> projections and SSE invalidation
```

`EventBatch` is the atomic unit produced by one command. It preserves event order and carries typed correlation and idempotency metadata. The local Spring adapter uses Spring Framework application events as its in-process pub/sub foundation behind the application port. Spring stays out of `domain` and `application`. Do not make the global Spring event multicaster asynchronous; use a dedicated, bounded, single-consumer executor or dispatcher for domain batches so unrelated Spring lifecycle events are unaffected and global ledger order remains deterministic.

Asynchronous ledger ingestion must not acknowledge a command after placing its only copy in volatile memory. Before `EventBus.publish` reports acceptance, store the batch in a durable file-backed publication store with explicit `pending`, `processing`, `completed` and `failed` states. Publication and consumption are idempotent. On startup, recover unfinished publications before write readiness becomes healthy. The ledger consumes and appends a complete batch atomically, acknowledges it, then triggers committed-event notifications. In a future database adapter this store becomes an outbox; in a future distributed topology the event-bus adapter may map to AMQP with publisher confirmation without changing application or domain code. Spring Modulith event externalization or Spring Integration AMQP adapters are infrastructure options, not domain dependencies.

Asynchronous command endpoints return an accepted publication receipt rather than pretending that a lagging projection has already changed. REST uses `202 Accepted` plus a status resource and hypermedia link where ingestion is not complete. CLI exposes equivalent JSON semantics and may offer an explicit wait option. The frontend observes committed notifications or publication status, then refetches the authoritative REST resource.

The delivered local topology uses `adapter-spring-events`, `ApplicationEventPublisher`, a durable file publication store under `data/ledger/publications`, and one ordered ledger worker. Keep `eventing.worker-count` equal to `1` while the authoritative ledger is one global JSONL sequence. CLI mutations expose the global `--wait` and ISO-8601 `--wait-timeout` options; waiting is explicit and never changes the durable-accept semantics of the command itself.

The Spring event-bus adapter participates in coordinated shutdown through `SmartLifecycle` or an equivalent Spring lifecycle component. Shutdown order is mandatory:

1. the web server stops accepting new requests and in-flight commands finish publishing;
2. the event bus rejects new publications;
3. the bounded dispatcher drains queued and in-flight publications;
4. the ledger flushes and acknowledges completed batches;
5. any unfinished durable publication remains recoverable;
6. persistence and caches close only after the event-bus stop callback completes.

Configure queue capacity, worker count, publication retry policy, recovery, back-pressure and shutdown timeout with validated Spring Boot properties. Keep the ledger worker count at one while a global ordered file ledger is used. Configure Spring Boot graceful shutdown and `spring.lifecycle.timeout-per-shutdown-phase`; never rely only on executor destruction defaults. Readiness is degraded while publication recovery is incomplete or the ledger consumer is unable to make progress.

## REST API architecture

REST is an inbound adapter over application input ports. Split controllers and representation assemblers by bounded context or cohesive resource family. Use typed request and response records, Jakarta validation at the boundary, framework-independent validation in application/domain, and RFC 9457 `ProblemDetail` responses. A REST adapter must not import an outbound adapter exception or concrete repository type.

Use Spring HATEOAS in the REST adapter for aggregate and resource links. Links are derived from identifiers, relationships and allowed actions returned by the domain/application model; domain objects never contain URLs or Spring HATEOAS types. Provide `self`, parent/aggregate, related-resource and available-transition links, plus `first`, `previous`, `next` and `last` for pageable resources. Do not add links that advertise a command forbidden by the current aggregate state.

All large collection GET endpoints are pageable by default. The HTTP adapter accepts conventional `page`, `size` and `sort` parameters, applies configurable Spring Boot default and maximum sizes, and translates them to framework-independent application `PageQuery` and `PageResult` records. Never return an entire large catalog merely because paging parameters were omitted. Facets remain separate resources and must apply the same filters consistently.

Bulk reference transfer is a separate streaming representation, not a paging bypass on the ordinary collection resource. Use Spring MVC HTTP streaming with `application/x-ndjson` for reference scans and exports. Stream incrementally with bounded memory, explicit ordering, cancellation on client disconnect and configured async timeouts. Do not introduce WebFlux solely for these endpoints, and do not make the React UI retain the full streamed catalog.

Expose one-way server-to-browser committed notifications with Server-Sent Events and Spring MVC `SseEmitter`. SSE carries sanitized integration/read-model invalidation records, not raw uncommitted domain payloads. Provide stable event IDs, `Last-Event-ID` resume, heartbeat, bounded replay or a clear resynchronization signal, topic filtering where useful, and cleanup on timeout/disconnect. The React client invalidates and refetches REST resources instead of building a second event-sourced domain cache.

Emit one SSE invalidation per committed `EventBatch`, not one browser invalidation per contained domain event. A bulk project import may contain hundreds of aggregate events and must trigger one coherent refetch rather than a request storm. The notification may list sanitized event and aggregate identifiers but never raw payloads.

Generate an OpenAPI 3 contract from the typed Spring MVC API with a Spring Boot 4 compatible springdoc integration. Expose JSON, YAML and an interactive local UI. Add a dedicated “REST API” page in the `ABOUT` navigation next to administrator documentation, with links to the interactive documentation and raw specifications; do not use a popup. Validate the generated contract in Maven and keep operation IDs, schemas, pagination, HAL links, problem details, streaming media types and SSE endpoints documented.

Prefer standard Spring facilities where they express infrastructure concerns: validated `@ConfigurationProperties`, dependency injection, Spring MVC, Actuator liveness/readiness with custom persistence and event-pipeline health contributors, graceful shutdown, managed executors and application events. Keep those facilities at adapter/bootstrap boundaries. Framework convenience must not leak into the domain model or replace aggregate invariants.

The file module may share one internal storage engine to preserve a single cross-process lock and atomic cache generation, but Spring and application code receive cohesive `SnapshotRepository`, catalog-writer, ledger, media and lifecycle adapters from `FilePersistenceAdapters`. Never inject the internal multipurpose engine outside its adapter package.

## Market catalogs

Market catalogs contain reference data, not ownership state. They remain versioned file repositories rather than event-sourced aggregates.

`PaintCatalogEdition` is a Market aggregate with a stable ID, brand, title, edition label,
optional publication year, explicit range scope and source URLs. It is not a scrape run or a
technical schema version. Brand YAML files store editions separately under `catalog_editions`;
paint records reference them through sourced `catalog_memberships` (edition ID, source URL,
locator). Membership is many-to-many, must reference an existing same-brand edition and never
changes paint identity or workshop quantity. A collection date must never manufacture an edition.
Unknown membership remains absent. Edition years do not date physical pots or establish retirement.
Refresh coverage identifies current versus historical scope and explicit ranges; incomplete,
historical or unspecified scope cannot retire a missing paint. Preserve existing memberships
and editions on refresh. A documented manufacturer retirement may still be applied explicitly.

### Paints

Maintain complete paint ranges by brand and range where sources permit. Store one schema-versioned YAML catalog per canonical brand under `data/market/paints/<brand>.yaml`; readers expose their union as one market catalog. A paint catalog record should have a stable ID and may include:

- manufacturer and brand;
- manufacturer reference;
- range and canonical `PaintProductProfile`;
- color family and color metadata;
- roles, application methods and system, coverage, finish, effects, undercoat, medium, volume, and other searchable properties;
- lifecycle status such as current or discontinued;
- manufacturer page and traceable sources;
- image provenance, credit, and usage status;
- last verification date.

Do not use a display name or range as paint identity. Every brand mapping declares one immutable,
globally unique lowercase ASCII `brand_code`. At first import, a paint ID is generated as
`<brand-code>-<normalized-manufacturer-reference>` and is never recomputed afterward. Preserve the
manufacturer's original spelling in `reference`; normalization only affects the derived ID. A refresh
must match an existing brand/reference identity and retain its ID. A manufacturer reference change
requires explicit reconciliation. When no manufacturer reference exists, use an explicitly sourced
stable product identifier or a reviewed `id_override`, never a mutable display name. Never silently
delete products during refresh; mark missing products as discontinued or unavailable until verified.

Brand adapters map their source vocabulary to the canonical profile through one versioned YAML mapping per brand. A record retains source observations and a mapping report; migration or refresh must never silently discard an unmapped source field. Canonical profile fields use controlled English kebab-case identifiers, while source labels remain traceable metadata.

Products whose roles are exclusively `varnish`, `medium`, or `auxiliary` use the canonical color family `auxiliary`. This is a selectable functional tone in the color facet, not missing or non-applicable color data. Its `color.hex` remains empty because inventing a representative chromatic value would be misleading.

Each refreshed record also retains a semantically lossless `source_snapshots` envelope containing the provider, source URL and collected source payload. Provider-generated request-time metadata with no source meaning may be removed explicitly so identical refreshes stay idempotent; commercial facts, provenance and real source update dates remain preserved. This envelope is import and audit evidence, not a competing domain model: it is excluded from market search, facets and generic React filters. A source-specific field is promoted to `PaintProductProfile` only when it expresses stable, cross-brand paint behavior useful to application use cases; volatile commerce and provider fields remain in the snapshot.

Official collectors are independent brand adapters under `tools/minipaintdex-data/src/minipaintdex_data/official_sources`; the orchestrator owns only provider registration, cross-provider validation and audit assembly. Each adapter has a minimum absolute volume and a ratio guard against the existing catalog. A suspicious source drop must fail collection before a change set can be built.

Manufacturer image URLs remain sourced catalog facts. Every `manufacturer_image` records one canonical `image_quality` and its `quality_verified_at` date. The ordered cross-brand quality vocabulary is: `official_photo` (1), `retailer_photo` (2), `owned_photo` (3), `generic_visual` (4), `color_swatch` (5), and `none` (6). The rank is derived from the vocabulary and is never persisted separately. Every quality other than `official_photo` also owns a required `quality_limitation` with a controlled kebab-case code, a human-readable detail and the observation date; an official photo must not carry one. Refresh and merge operations may improve an image or retain the current one, but must never downgrade it. A successful upgrade clears the limitation. A rejected official candidate retains the best previous image and records the rejection as its current limitation while the complete attempt remains in the refresh audit. An official photo may be challenged again once its quality verification is at least 365 days old. A deterministic dry-run planner reports every candidate and reason without mutating the catalog.

Validated local copies are generated under `media/market/paints/<brand>/<paint-id>.(webp|svg)` and are not versioned. Image caching validates source provenance, redirects, byte size, raster dimensions, readable content, flat-colour dominance, checkerboard backgrounds and minimum visual detail; it retries transient failures with bounded backoff and sanitizes SVGs before publication. Accepted raster images are converted to a fixed square presentation canvas while preserving their aspect ratio and centering their content; the remote source URL and source snapshot remain the evidence of the original. Official images use configured manufacturer HTTPS hosts. Retailer images require an HTTPS product page, explicit credit and traceable source provenance. A colour swatch or rejected artwork remains preserved in `source_snapshots` but is never presented as a product photo. Python may write this media cache and emit a market-paint change set; only the Java application use case may persist the resulting catalog paths. The web client uses the local path first and the qualified source URL as a resilient fallback.

When an official catalog page is protected from direct HTTP collection but remains normally accessible in an interactive browser, the operator may export an exact manifest of observed product reference, product page and largest rendered image URL. The deterministic image-source importer validates the brand hosts and exact catalog references. Official references absent from the current catalog are reported as unmatched and never silently discarded or invented.

The market paint browser searches only canonical fields and supports API-published filters including brand, range, role, application method, application system, color, finish, medium, coverage, effect, undercoat, and lifecycle. `GET /api/v1/market/paint-product-model` publishes the JSON Schema, filter definitions, and controlled vocabularies used by the generic React filter UI. Ownership badges and owned-only filters belong to Workshop endpoints or to a UI composition of separate responses; Market services must never read the Workshop inventory to produce them.

The paint-brand refresh skill must accept a brand name or `all`, where `all` is resolved dynamically from the brands already present in the market catalog. It may accept range, current/all scope, removal, and dry-run options. It must resolve aliases, prefer official sources, normalize identifiers, retain provenance and verification dates, compare every refreshed product with the local record, report additions and field-level updates, and handle missing products explicitly. Missing products are retired by default. Deletion requires verified complete source coverage, an explicit removal option, and application validation; owned paints and paints referenced by market guides or workshop recipes cannot be deleted. A dry run must not mutate canonical catalogs. Skills must use REST or CLI application interfaces for writes once those interfaces exist.

Paints with roles `technical_effect`, `primer`, `wash`, `ink`, `varnish`, `medium`, `auxiliary`, or `pigment` must resolve actionable instructions through explicit `usage_guide_ids` or product-specific `usage_instructions`. Shared guides own their summaries, steps, tips and translations; product-specific supplements must not duplicate them. These instructions are market product knowledge and are displayed dynamically by the paint sheet.

Never fabricate a representative grey for an unknown color. An absent or invalid `color.hex` remains missing metadata in APIs, rendering, and reconciliation. The matcher uses its configured missing-metadata score, emits an explicit reason, and never reports a CIEDE2000 distance or `close_color` reason for an unknown color.

Technical instructions must distinguish sourced product/range guidance from reusable generic templates through `instruction_status` and `review_required`. A generic template can help an operator but must remain visibly marked for review and must not be presented as manufacturer-specific instructions.

### Shared paint usage documentation

`PaintUsageGuide` belongs to Market, independently of `PaintProduct`, `PaintPot`, catalog editions
and miniature-specific `MarketPaintingGuide`. It has a stable ID, brand, explicit range scope,
business revision, original language/content, source URLs, knowledge status and translations.
Brand YAML files store the shared registry under `paint_usage_guides`; products explicitly reference
`usage_guide_ids`. A matching range never implicitly assigns a guide. References must resolve to
same-brand guides within the declared range scope. Product-local `usage_instructions` are optional
specific supplements; do not copy shared instructions into them. Technical paints must have actionable
local instructions or a resolved shared guide with steps. Extraction preserves original provenance,
does not change product identities and never touches Workshop history.

Translations are content owned by a guide, not Site UI labels. Store their language, method, review
status and source revision. Original content may be en, fr or explicitly mixed (mul); queries accept fr, en or original. Prefer a current French translation; otherwise expose the original and
an explicit fallback/stale status. Translation never upgrades the original knowledge status. A guide
source/content/scope change requires the next business revision; translations from older revisions
remain traceable but are not served as current. Schema versions stay 1. REST and CLI expose the same
typed guide queries and market change-set import semantics. Source HTML is evidence, never executable
markup in the UI. Generic/imported advice must remain visibly distinct from verified manufacturer advice.

### Paint search and suggestions

Paint search uses embedded Lucene behind the application `PaintProductSearchIndex` output port.
Lucene classes are confined to `adapter-lucene`; domain, application, REST and CLI contracts
must not expose them. The initial index lives in JVM memory, warms before readiness, and rebuilds
from the complete validated Market generation before the next search after a catalog change.
A replacement is published only when complete. Index data is disposable, not an aggregate,
an event history or a source of truth. No data migration is needed to rebuild it.

Index canonical names, references, brands/aliases, ranges and profile metadata, never source
snapshots or owner state. Search and suggest share text normalization, word-prefix matching,
bounded name typo tolerance, relevance ordering and facet rules. References are never fuzzily
corrected. Exact reference/name matches are prioritized; an empty search uses stable name/ID order.
Settings belong to validated `minipaintdex.paint-search` configuration.

Search uses dedicated read-only POST resources: `/api/v1/market/paint-products/search` and
`/api/v1/workshop/paint-stocks/search`. Both accept a typed MiniPaintDex JSON contract:
`query`, `filters`, `include` (results, suggestions, or both; default results) and optional
`suggestionLimit`. This contract is inspired by search-engine APIs but is not Elasticsearch
DSL or wire compatibility. Reject unsupported fields; never silently ignore an unknown query.
REST and CLI share `PaintSearchQuery` / `PaintSearchResult`; CLI exposes `search --include`.
No legacy collection-search or separate suggest aliases are maintained.
Requested results are pageable via `page`, `size`, `sort`; HAL page links require POST with
the same body. Facets remain separate GET resources using identical filter semantics.
Unrequested response parts are null, not empty successes. Both parts share one ranked selection
and one immutable generation per source. Result sorting never changes suggestion relevance.
Compact suggestions use `paintProductId`, name, brand, range, reference and catalog visual.
Suggestions-only requests return no result page or exhaustive total; blank text returns no suggestions.
Workshop applies ownership before limiting candidates and does not multiply references by pot count.
The browser debounces and aborts stale requests, supports keyboard selection, and never indexes
the full catalog locally. Explicit field sorting remains available alongside `relevance,desc`.

### Paintable products and catalog items

The market `PaintableProduct` catalog describes boxes, games, editions, expansions, ranges, and sets containing things to paint. Use a generic `catalog_item` concept so miniatures, vehicles, scenery, creatures, and other paintable components share the same model. Market records contain facts such as name, kind, product line, market quantity, source, reference image, and whether assembly is normally required. They do not contain ownership, personal progress, or personal recipes.

### Market painting guides

A `market_painting_guide` records knowledge published or inferred from professional, official, community, or otherwise traceable painted references. It belongs to `data/market/painting-guides`, targets one catalog item, has a stable ID and version, and carries a `knowledge_status` of `documented`, `observed`, or `inferred`.

Each guide contains stable `slots`. A slot describes the public guide's visual or technical intent and may reference a `market_paint_id`, role, application notes, and a pending market import. Preparation and painting steps are guide knowledge. Every guide must retain direct sources or source references and must not be presented as the owner's chosen recipe.

A new guide version must retain provenance and must not silently rewrite a workshop recipe based on an earlier version.

## Workshop recipes and assignments

A `workshop_recipe` is the owner's painting plan. It has an independent identity, version, lifecycle, and history in the global ledger. It may reference the market guide version that inspired it, but it owns the substitutions, mixtures, ordered layers, and techniques the owner actually plans to use.

The canonical lifecycle is `draft -> validated -> active -> superseded`; any non-archived state may transition to `archived`. A new revision is a new version that references the recipe it supersedes. Never overwrite an existing recipe event to revise it.

Recipe solutions map one guide slot to one of:

- `single_paint`: one owned market paint ID;
- `mixture`: several owned paint components and optional ratios;
- `layer_stack`: ordered owned paint components and application notes;
- `technique`: explicit instructions, with optional owned paint components.

All paint IDs used by a workshop recipe must exist in the owner's paint inventory at creation time. A `recipe.assigned` event attaches one active recipe version to one physical `workshop_item`. The assignment is not stored on the market catalog item, because two copies may use different plans.

## Paint reconciliation

Reconciliation is a read-only proposal from a market guide slot to paints owned in the workshop. It never mutates or auto-accepts a recipe. Matching weights, thresholds, behavioral paint types, candidate limits, and other tuning parameters must come from validated Spring Boot configuration at startup rather than numeric constants in the matcher.

For ordinary opaque paints, rank candidates primarily with CIE Lab and CIEDE2000 color distance, then use canonical role, finish, coverage, and medium. For behavioral products such as Contrast, Speedpaint, washes, inks, primers, auxiliaries, and technical effects, compare the canonical role and application system plus structured behavior such as coverage, undercoat, finish, and effects. RGB is only a minor signal for those products and every result requires manual review.

The matcher may propose a single paint candidate, but the workshop solution may instead be a mixture, ordered layer stack, or technique. Persist only the user's explicit workshop choice through the recipe command.

## Workshop inventory

Workshop paint inventory references market paint IDs rather than duplicating product metadata.

`PaintProduct` owns commercial reference facts; `PaintPot` owns one physical container's personal
state. They are separate aggregate roots, not subtypes. A pot has its own stable ID and references
`paintProductId`; two identical purchases are two pots. Commercial retirement never removes a pot.
Pot registration, opening, condition/remaining-level observations, possession changes, notes and
personal photos emit typed `paint_pot.*` events in the workshop ledger. Unknown opening, acquisition,
condition and remaining level must remain unknown; registration time is not acquisition time.
Possession (`owned`, `given-away`, `discarded`), condition (`unknown`, `usable`, `thickened`, `dried`)
and remaining level (`unknown`, `full`, `half`, `low`, `empty`) are independent dimensions.
Stock is a projection, never a separately editable count. Owned count includes owned empty/dried pots;
available count excludes given-away/discarded, empty and dried pots. Unknown condition/level does not
assert a full pot and remains available until an explicit contrary observation. Recipes reference
products, not particular pots; availability uses that projection.
Pot imports merge registrations by explicit stable pot ID, validate product identity, are atomic and
idempotent, and never reset existing observations, photos or history. Rephotographing a pot does not
register a new pot. Catalog changes cannot add inventory through quantity deltas or rekey product IDs
referenced by immutable pot histories. Personal pot photos stay in Workshop; a catalog refresh must
never replace them. Stock representative visuals prefer official and retailer catalog photos; otherwise
the latest dated photo of an owned pot is selected (stable media ID then pot ID break timestamp ties).
The Market provenance ordering and `PaintPotPhotoSelection` domain policy are composed in the application
and shared by stock search and detail reads; the Workshop selector does not import Market implementations. Personal photos
remain visible in each physical pot's journal regardless of catalog quality. The stock's `personalPhoto`
includes pot/media identity, derivative and original URLs, processing method, caption and recorded date.
`canReplacePhoto` requires an owned pot and catalog quality no better than `owned_photo`. Replacing the
representative photo uses `AddPaintPotPhotoCommand` / `PaintPotPhotoAdded`: history is retained, never
overwritten. The policy does not restrict independent journal-photo attachments. No schema migration.
REST searches use `POST /api/v1/workshop/paint-stocks/search` (`results.content`, `paintProduct`, `quantity`, `availableQuantity`, `personalPhoto`, `canReplacePhoto`) and the CLI uses
`workshop paint-stocks search` / `facets`. Product relations use `paintableProductId`, component
relations `paintableComponentId`, project relations `paintingProjectId` and physical-copy command
targets `workshopPaintableId`. The corresponding physical-copy collection is `workshop/paintables`.

`GET /api/v1/workshop/paint-stocks/{paintProductId}` and `workshop paint-stocks show --paint-product-id`
return the same correlated stock result, including zero stock for a known Market reference. The paint
dialog composes this Workshop read with Market facts and refetches on committed invalidation or upload
completion. Market responses and catalog cards never acquire owner-derived image fields.

Shopping list routes and CLI commands are scoped under `workshop/shopping-list/entries` and
`workshop shopping-list entries`. `set-checked` changes only the checked marker; it neither records
a purchase nor increments stock. Calculated requirements and explicit purchase intentions remain
distinct, even when composed into one `ShoppingListEntryView`.

Market and pre-existing workshop event encodings remain v1. Pot registrations and lifecycle use new `paint_pot.*` facts, also v1. `catalog_items` / `catalog_item_id`
encode Paintable components, `product_id` encodes a Paintable product relationship, and ledger `workshop_item.*` / `shopping_item.status_changed` encode
the renamed typed events. Dataset import adapters accept that same single persisted representation.
Ordinary REST/CLI commands and results use the canonical names, not these serialized keys.
The event envelope's project scope is distinct from the project referenced by a Workshop membership
event: `scopePaintingProjectId` is null for Workshop events, even when their payload references a project.

Paint stock has no editable YAML source. The former quantity file is retired after explicit pot
registration with exact per-product counts. Every physical pot is identified independently; missing
acquisition/opening dates, condition and remaining level stay unknown. Personal notes and images belong
to that pot and survive any catalog refresh.

## Physical workshop paintables

Every physical miniature, vehicle, scenery piece, or other paintable component owned by the user is a first-class `workshop_item`. Do not model ownership only as a catalog ID plus a quantity or a painted boolean.

Each physical item has its own stable ID and references one market catalog item and one painting project. It can independently hold a display name, recipe assignment, progress, notes, photos, and history. Multiple copies of one market item therefore produce multiple workshop paintables.

```yaml
id: ws-reichbusters-soldier-001
catalog_item_id: reichbusters-soldier
painting_project_id: paint-reichbusters
display_name: Soldier 1
```

This per-item identity is mandatory because each physical piece may later receive its own progress photos and journal entries. Paintable-product quantities and completion counts in the workshop are projections calculated from workshop paintables, not primary ownership fields.

## Painting workflow

The canonical workflow uses English stage identifiers:

1. `preparation`: assembly when needed, mold-line removal, cleanup;
2. `priming`: undercoat;
3. `pre_highlight`: progressively lighter dry-brushing or another preparation for rapid painting;
4. `painting`: main color application;
5. `finishing`: highlights and final painted details;
6. `basing`: base treatment.

Keep `finishing` and `basing` distinct in the domain even if the French UI groups them under “Finitions / Socle”. A stage state is one of `pending`, `in_progress`, `completed`, or `skipped`. Optional stages may be skipped with an explicit event and reason.

The workflow is ordered. Starting, completing, or skipping a stage requires every preceding stage to be `completed` or `skipped`. Reopening a completed or skipped stage remains possible and is recorded as a corrective event; bulk historical imports that need to bypass ordering must use an explicit, audited backfill use case rather than weakening normal transition rules.

Do not persist a single painted/unpainted boolean as the source of truth. Current stage, completion, project percentages, and aggregate counts are projections derived from the item's event history.

French workflow labels belong in site localization data, for example:

```yaml
workflow:
  preparation: Préparation
  priming: Sous-couche
  pre_highlight: Pré-éclairage
  painting: Peinture
  finishing: Finitions et éclaircissements
  basing: Socle
```

## Global event ledger

Workshop activity is event-driven. Maintain a global, append-only application ledger as the source of truth for workshop history and the activity-board journal. Store events in English, preferably as JSON Lines split into manageable time partitions such as `data/ledger/events/2026-08.jsonl`.

Only batches acknowledged by the ledger are committed history. The durable publication store is a recoverable asynchronous inbox, not a second editable ledger. Projectors and public committed-event streams consume acknowledged ledger events. Aggregate command handling must nevertheless include earlier accepted pending events in its effective version and state so asynchronous ingestion cannot weaken invariants.

Market catalogs and site configuration are not event-sourced. Only workshop operations and other genuine application activity belong in the ledger.

Every event envelope must include:

- sortable unique `event_id` (prefer ULID);
- `schema_version`;
- past-tense `event_type`;
- `occurred_at` and `recorded_at` UTC timestamps;
- `aggregate_type` and `aggregate_id`;
- optional `project_id`, containing the genuine `PaintingProject` aggregate ID for project-scoped activity;
- actor information;
- correlation and causation identifiers when applicable;
- a typed payload.

Representative event types include:

```text
workshop.created
painting_project.created
workshop_item.added
workshop_item.named
workshop_item.comment_added
workshop_item.photo_added
workflow.stage.started
workflow.stage.completed
workflow.stage.skipped
workflow.stage.reopened
recipe.assigned
workshop_recipe.created
workshop_recipe.validated
workshop_recipe.activated
workshop_recipe.superseded
workshop_recipe.archived
paint.used
shopping_item.status_changed
milestone.reached
```

Within a released data contract, events are immutable: never edit or delete an event to correct history; append a compensating or corrective domain event instead. Before the first release, local ledger history and publication files are disposable development data. A refactor may reset them completely instead of adding migrations or compatibility code, provided the reset is explicit and the resulting seed data passes the current model. Media files are not embedded in the ledger; photo events reference a stable media ID and server-managed location plus metadata such as stage, timestamp, caption, provenance, and hash when useful.

## Projections and activity board

Build read models from the ledger for:

- each workshop item's current workflow state;
- painting-project progress and counts;
- chronological workshop activity;
- Kanban views by workflow stage;
- recent photos and per-item galleries;
- milestones, statistics, and paint usage.

Generated projections are caches/read models, never manually maintained sources of truth. They must be reproducible from the ledger. Provide a rebuild use case through both CLI and a maintenance REST resource.

## Site navigation and configuration

The horizontal top navigation exposes Home and the Market and Workshop destinations directly, with subtle separators between contexts. Do not add a parent Market tab or a permanent contextual second row. About contains a compact disclosure of documentation pages. On small screens, the same links may wrap within the header:

```text
MARKET
  Paints
  Paintable products

MY WORKSHOP
  My paints
  Workshop administration
  Shopping list

ABOUT
  User documentation
  Administrator documentation
  REST API
  Version and author
```

The actual user-facing French labels and other project-independent UI strings must come from `data/site` localization/configuration files rather than being hard-coded in components. Domain-dependent names continue to come from market or workshop data.

The shopping read model separates derived missing paints (`required`) from explicit personal purchase intentions (`planned`). `data/workshop/shopping.yaml` stores only stable market paint IDs when a reference exists plus personal intent such as reason and priority. Required rows are projected from active painting projects, market guides, and workshop ownership. Checked state is activity in the ledger, not ephemeral React state.

Large market catalogs must be queried through paginated REST resources with separate facets. The SPA startup payload contains only site configuration and small dashboard counters. Product details, workshop views, shopping rows, documentation and paint pages are loaded from REST when their route is displayed. Never embed full catalogs or workshop collections in a bootstrap payload.

## React user experience

Keep the interaction model uniform across the SPA:

- React owns transient presentation state such as the active route, filters, dialog selection and form input. Spring REST resources remain the source of truth for domain state.
- Retain only data needed by the current view. A paginated catalog keeps one page, not every page already visited; leaving a heavy view releases its detail/list state. Do not create a second domain cache in React, browser storage or global module variables.
- Abort obsolete requests when a route, filter or search changes. Reload the affected REST resource after a successful command; optimistic updates are acceptable only for a small reversible control and must still converge on the service.
- Give every durable page a stable deep link and support browser back/forward navigation. Page-scale content such as documentation and application information belongs on pages, not in popups. Reserve dialogs for focused, temporary tasks or details.
- Use consistent loading, empty, error and disabled states. Never label a resource “not found” merely because its request is still pending.
- Desktop navigation uses one horizontal row of direct destinations. The active destination remains visually distinct and keyboard accessible, including its nested detail pages; About documentation remains reachable through an accessible disclosure.
- Paint filters occupy a left sidebar on desktop and a modal drawer on small screens. Brand and range form one hierarchical group: whole brands and brand-qualified ranges are combined with OR. Other facets use OR within each facet and AND across facets. Facet counts ignore their own selection (brand and range together), retain other criteria, and do not count workshop quantities as separate references.
- Paint search, selections, sorting and pagination have URL state and support back/forward. Result cards show brand, range and manufacturer reference outside the image; omit the generic color-paint badge while retaining useful technical roles and application methods.
- The Market paint result toolbar shows both the filtered match count and the total number of
  PaintProduct references. The total comes from the lightweight server dashboard and refreshes
  after committed invalidations; the browser never derives it by loading the complete catalog.
- Every desktop destination must remain reachable on small screens, either directly in mobile navigation or through a visible local sub-navigation.
- User-facing labels independent of market/workshop data come from `data/site`; components must not duplicate them.
- Paint dialogs place image provenance directly below the displayed image. Their six-position,
  read-only gauge fills toward the best provenance without presenting the domain's rank as a score.
  Source details always describe the displayed image, including fallback. Catalog memberships are
  visible among paint characteristics; unknown membership is explicitly unrecorded, never invented.
  Image provenance is always visible rather than hidden in a disclosure. The sticky dialog header
  owns contextual actions such as replacing the workshop visual and opening the manufacturer sheet.

## Media and provenance

Store personal photos under `media/workshop` through the server. Attach each to a `PaintPot` or a `WorkshopPaintable`; only a paintable photo may refer to a workflow stage.

Paint-pot photo background removal is local Java/ONNX CPU inference behind the application
`PaintPotPhotoProcessor` port; it never sends photos to an external service. Model installation is
an explicit administration step, with a pinned SHA-256, separate from application startup. Keep
ONNX classes in `adapter-onnx`, and bind limits/model settings in Spring bootstrap.
`PreviewPaintPotPhotoQuery` produces transient PNG bytes without writes or domain events.
`AddPaintPotPhotoCommand.removeBackground` optionally stores both the untouched original and a
transparent derivative in one `PaintPotPhotoAdded` decision. Its optional `PaintPotPhotoCutout`
value carries derivative identity, URL, size, hash and processing method; schema stays 1.
Workshop views prefer the derivative and retain `originalUrl`. Market imagery is never changed
by a personal upload. Do not infer paint condition or remaining quantity from segmentation.
The photo preview and attachment remain aligned across REST, CLI and the workshop UI, including
an explicit choice to retain the original without background removal.

For internet images and manufacturer assets, retain source URLs, attribution, and usage status. Do not publish copied images without clear usage rights. Distinguish manufacturer packshots, sourced photos of actual painted results, user progress photos, and approximate generated or color-swatch previews.

## Storage and future database migration

All persistence must be accessed through outbound repository ports. The initial adapters use files. Future database adapters must be replaceable without changing domain entities, application handlers, REST contracts, CLI semantics, or the frontend.

Each persistence adapter has an explicit initialization lifecycle. File persistence validates and loads its configured repositories before serving application reads; a future database adapter uses the same phase for connectivity, schema/migration checks and optional reference warm-up. Publish validated immutable, versioned caches atomically only after a complete load succeeds.

The file-backed event publication store initializes before command handling, recovers unfinished batches and feeds the single asynchronous ledger subscriber. The application does not become write-ready until pending publications are either committed or safely scheduled for ordered recovery. A planned shutdown drains the pipeline; an unplanned restart resumes from durable publication state without duplicating committed events.

Persistence remains the source of truth. All writes acquire the repository transaction/critical section, reconcile external changes, persist atomically, then publish the new cache generation. A scheduled sentinel compares cheap metadata first and reloads only when storage changed. An invalid external change keeps the last valid generation available for reads, marks readiness as degraded and rejects writes until storage is valid again. Expose constant-time liveness and synchronization/event-pipeline readiness through Spring Boot Actuator health groups and typed health contributors rather than application-service maps; liveness must not load persistence.

Size JVM memory explicitly in versioned launchers: Maven uses `.mvn/jvm.config`; `scripts/minipaintdex.ps1` applies separate server and CLI profiles. Keep task-specific environment overrides documented and never hide heap sizing in unrelated Spring properties.

Stable IDs act as foreign keys across market catalogs, workshop membership, physical items, recipes, events, projections, and media metadata. Avoid denormalized copies of market facts in workshop records. YAML is the canonical format for reference/configuration files; CSV may exist only as generated import/export material, not as a second manually maintained source of truth.

## Portable datasets and deterministic administration

`data/` is active application storage. `datasets/` is a separate portable exchange area with the canonical categories `market.paint-brand`, `market.paintable-product`, `workshop.paint-pots`, and `workshop.painting-project`.

Each named dataset contains a versioned `dataset.yaml` manifest and a checksummed `payload/change-set.json`. Python may read application references and create or validate datasets, but only Java application use cases may import them. `minipaintdex datasets import` performs a dry run by default and requires `--apply` to mutate storage. All datasets merge through domain commands. `workshop.paint-pots` registers stable pot identities, preserving any existing lifecycle and media; it never replaces stock. This registration dataset exports currently owned pots, not their journals or photo binaries: use a full data/media backup to preserve history.

Keep deterministic transformations in `tools/minipaintdex-data`. Human or agent reasoning identifies images, products, sources and ambiguity; Python performs hashing, normalization, comparison, packaging and validation. Do not encode visual identification or unverified web judgments in deterministic scripts.

Owned-paint photo intake is `imports/workshop-paints/photos/`, with batch archives under
`imports/workshop-paints/archive/<date>/<import-id>/` and processing evidence under
`imports/workshop-paints/runs/<import-id>/`. Other import targets must use separate roots.
Photo manifests declare `target: workshop.paint-pots`, a stable import ID, verified ledger SHA-256, explicit paint-pot IDs for imported photos, source SHA-256 and a
per-photo outcome. Archive only verified imports and confirmed duplicates; unresolved photos
remain in intake. Never overwrite an archive or count a repeated photo twice. Retain original
paths as historical evidence and maintain a relocation manifest with current locations and hashes.
Historical run artifacts are evidence, not scripts to rerun against current stock.

## Validation and delivery

- Never bypass or weaken the execution sandbox. Keep generated files and test fixtures inside the repository `target/` directories rather than the system temporary directory when the environment restricts it.
- When an in-scope operation legitimately needs access outside the writable workspace, such as updating `.git`, downloading dependencies, or controlling an external process, request the narrowest explicit elevation and explain the exact action. Do not invent shell workarounds to evade approval.
- A sandbox-related failure must be retried with a workspace-local path or an explicit approved elevation. Record only portable repository behavior here; never encode machine-specific unrestricted paths or permissions.
- Treat a user message containing only `Go` (or equivalent approval) as authorization to implement the refactor or change currently under discussion. It does not authorize a Git commit or push.
- Keep Git publication under the user's control. Create a commit only when the user explicitly asks for a commit in the current request, and push only when the user explicitly asks for a push in the current request. Never carry commit or push authorization forward from an older request after additional work has been requested.
- Keep schemas versioned and validate all persisted files.
- Validate each typed event record and envelope before publication, and verify codec round trips for every aggregate-local event family.
- Make event writes idempotent and safe against partial writes.
- Enforce idempotency inside the same cross-process critical section as the append. Related YAML replacements must be staged and rolled back as one repository operation; all file-backed mutations share the repository write lock.
- Preserve existing user data and unrelated worktree changes.
- Add domain-handler tests plus REST and CLI adapter contract tests.
- Keep REST and CLI behaviors aligned for every application use case.
- Optimize verification from cheap to expensive. During a refactor iteration, run the smallest affected module compile/test and focused architecture or contract tests first; do not repeatedly run the full multi-technology build after every mechanical edit.
- At stable phase boundaries, run affected-module tests with required upstream modules and only the integration tests relevant to that phase.
- Defer deep, high-cost controls until the complete refactor is integrated: the root `mvnw verify`, full Java/Python/frontend suites, executable-JAR smoke tests, OpenAPI validation, REST/CLI parity, concurrent aggregate commands, event publication recovery, queue back-pressure, SSE reconnection, streaming disconnect/memory behavior, and graceful shutdown with a non-empty event pipeline.
- Run the complete root `mvnw verify` and the deep final controls before committing. The root lifecycle must include backend tests and pnpm frontend checks.
- Use the repository `mini-paint-dex-project` skill for build, server and explicitly requested Git delivery.
- Use `administer-minipaintdex-data` for photo imports, paintable-product imports, brand refreshes and dataset workflows.
