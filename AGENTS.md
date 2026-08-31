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

```text
MARKET (reference knowledge, file-versioned)
  MarketPaint                         aggregate root for one commercial paint reference
  PaintableProduct                    aggregate root for a box, range, expansion, or set
    └── CatalogItem                   entity describing one kind of paintable component
         └── quantity                 number supplied by the market product
  MarketPaintingGuide                versioned aggregate root targeting one CatalogItem

WORKSHOP (owner state, event-sourced)
  Workshop                            aggregate root for the owner's whole workshop
    └── paintingProjectIds            references PaintingProject aggregate IDs
  PaintingProject                     aggregate root for the intent to paint one PaintableProduct
  WorkshopItem                        aggregate root for one physical miniature/scenery copy
  WorkshopRecipe                     versioned aggregate root for one personal painting plan

SITE (supporting configuration, file-versioned)
  SiteConfiguration                   localized labels and application presentation settings
```

`Product` alone is not part of the ubiquitous language because it is ambiguous with a paint sold on the market. Always use `PaintableProduct` in Java, API contracts, CLI semantics, file schemas, and technical documentation. User-facing French may use “produit à peindre”. `MiniProduct` is not used because a `PaintableProduct` can also contain scenery, vehicles, creatures, or accessories.

### Market bounded context

- `MarketPaint` and `PaintableProduct` have different identities, metadata, search behavior, and lifecycles.
- A `PaintableProduct` owns its `CatalogItem` entities. Each catalog item has a stable ID, `product_id`, English `kind`, and positive market quantity.
- The sum of catalog-item quantities must equal `expected_paintable_count`; invalid products must fail loading or change-set validation.
- A `PaintableProduct` contains public facts and sourced knowledge only. It never contains ownership, personal progress, physical workshop state, or personal recipes.
- A `MarketPaintingGuide` targets a catalog item by ID and owns its public paint slots and source provenance. It is not the owner's recipe.

### Workshop bounded context

- `Workshop` is an aggregate root with stable ID `my-workshop`. It is the durable owner context and references its `PaintingProject` aggregate IDs.
- `PaintingProject` is the aggregate root for the owner's intent to paint one `PaintableProduct`. It has its own ID, name and lifecycle `planned -> active -> completed -> archived`. It references market facts by `paintable_product_id` and never copies them.
- Creating a painting project is an idempotent application command. It uses catalog quantities to append one `painting_project.created` event and one `workshop_item.added` event per physical copy in one atomic ledger batch.
- Every physical copy is a separate `WorkshopItem` aggregate root with its own workflow, recipe assignment, photos, notes, and history. It references both a `catalog_item_id` and a `painting_project_id`.
- `WorkshopRecipe` has an independent lifecycle. It may be inspired by a market guide, but the owner's substitutions, mixtures, layers, and techniques belong only to the workshop.
- Use `PaintingProject` in the core and technical contracts, and the French label “Projet” in the UI. Never use the ambiguous bare Java type `Project`.

### Relationships and read models

| Source | Relationship | Target | Rule |
| --- | --- | --- | --- |
| `CatalogItem` | belongs to | `PaintableProduct` | same `product_id`, no cross-product child |
| `MarketPaintingGuide` | documents | `CatalogItem` | versioned, sourced market knowledge |
| `PaintingProject` | references | `PaintableProduct` | `paintable_product_id`; market facts stay in market |
| `WorkshopItem` | instance of | `CatalogItem` | one aggregate per physical copy |
| `WorkshopItem` | grouped by | `PaintingProject` | `painting_project_id` |
| `WorkshopRecipe` | plans | `CatalogItem` | owner lifecycle independent of market guide |
| recipe assignment | attaches | `WorkshopItem` | two copies may use different recipes |

Market product views and workshop views are distinct projections. A market view may overlay an `inWorkshop` badge, but it must not expose workshop progress as market truth. A workshop view joins IDs at read time to calculate physical counts, workflow progress, missing owned paints, activity, and guide coverage.

## Target repository layout

The target architecture is a modular Spring Boot application with a React/Vite frontend. Java 25 is the target language level. Maven is the root build and dependency-management tool; use the checked-in Maven Wrapper. pnpm remains the frontend package manager pinned in `frontend/package.json`, but the root Maven lifecycle must orchestrate the frontend validation and build as well as the deterministic Python data-tool tests so `mvnw verify` validates the whole product.

Maven is the single build and test entry point for every technology in the repository. The root `verify` lifecycle must compile, validate and test the Java/Spring backend, the TypeScript/React frontend and the Python data tools. Technology-specific commands may accelerate a local iteration, but they never replace the final Maven verification and no project technology may maintain an independent release build outside that lifecycle.

MiniPaintDex follows the self-contained system pattern. The production deliverable is one executable Spring Boot JAR containing the REST API and the compiled SPA. Spring Boot serves `index.html`, frontend assets, media, and the client-side route fallback. The separate Vite server exists only for development and proxies API requests to Spring Boot.

```text
backend/
  domain/                 # Pure Java entities, value objects, events, workflow rules
  application/            # Commands, queries, handlers, ports, DTO contracts
  adapter-file/           # YAML/JSONL outbound repositories
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
  admin/                  # DDD, REST and skill documentation

config/
  application.yaml       # Canonical Spring Boot technical defaults

data/
  site/
  market/
    paints/
    paintable-products/
    painting-guides/
  workshop/
  ledger/
    events/

media/
  workshop/
```

Do not add compatibility aliases or duplicate API vocabularies during this early construction phase unless the user explicitly asks for backward compatibility. Prefer one clear current model and reset disposable local seed data when authorized.

## Browser and server boundary

The HTML/JavaScript frontend must never read from or write to `data/` or `media/` directly. It must not know filesystem paths.

All domain reads and writes go through the local REST server. The server is the only online writer and is responsible for:

- validating commands and workflow transitions;
- invoking application services;
- appending domain events;
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
| Search market paints | `GET /api/v1/market/paints` | `minipaintdex market paints search` |
| Refresh a paint brand | `POST /api/v1/market/paint-refreshes` | `minipaintdex market paints refresh` |
| Create a painting project | `POST /api/v1/workshop/painting-projects` | `minipaintdex workshop painting-projects create` |
| Add a workshop item | `POST /api/v1/workshop/items` | `minipaintdex workshop items add` |
| Import a dataset | category-specific REST command | `minipaintdex datasets import` |
| Transition a workflow stage | `POST /api/v1/workshop/items/{id}/stage-transitions` | `minipaintdex workshop stage transition` |
| Attach a photo | `POST /api/v1/workshop/items/{id}/photos` | `minipaintdex workshop photos add` |
| Read activity | `GET /api/v1/activity` | `minipaintdex activity list` |
| Rebuild projections | maintenance REST resource | `minipaintdex projections rebuild` |

The frontend and CLI send business intent, not arbitrary events. For example, a stage-transition command is validated by the application service, which then emits the corresponding domain event.

When the REST server is running, mutation commands from the CLI should use it so there is one writer. An explicitly offline maintenance mode may invoke application handlers in process only after acquiring an exclusive storage lock.

## Market catalogs

Market catalogs contain reference data, not ownership state. They remain versioned file repositories rather than event-sourced aggregates.

### Paints

Maintain complete paint ranges by brand and range where sources permit. A paint catalog record should have a stable ID and may include:

- manufacturer and brand;
- manufacturer reference;
- range and functional type;
- color family and color metadata;
- finish, medium, opacity, volume, and other searchable properties;
- lifecycle status such as current or discontinued;
- manufacturer page and traceable sources;
- image provenance, credit, and usage status;
- last verification date.

Do not use a display name alone as identity. Prefer a manufacturer reference when available and otherwise derive a stable canonical ID from brand, range, and product. Never silently delete products during refresh; mark missing products as discontinued or unavailable until verified.

The market paint browser must support normal filters including brand, range, type, color, finish, medium, opacity, volume, manufacturer reference, lifecycle status, and other catalog metadata. It may overlay workshop ownership as a computed badge or filter without copying market fields into workshop data.

The paint-brand refresh skill must accept a brand name or `all`, where `all` is resolved dynamically from the brands already present in the market catalog. It may accept range, current/all scope, removal, and dry-run options. It must resolve aliases, prefer official sources, normalize identifiers, retain provenance and verification dates, compare every refreshed product with the local record, report additions and field-level updates, and handle missing products explicitly. Missing products are retired by default. Deletion requires verified complete source coverage, an explicit removal option, and application validation; owned paints and paints referenced by market guides or workshop recipes cannot be deleted. A dry run must not mutate canonical catalogs. Skills must use REST or CLI application interfaces for writes once those interfaces exist.

Paints with functional types `technical_effect`, `primer`, `wash_shade`, `ink`, or `auxiliary` must include structured `usage_instructions` with an explanatory summary, actionable steps, and useful tips or precautions. These instructions are market product knowledge and are displayed dynamically by the paint sheet.

Never fabricate a representative grey for an unknown color. An absent or invalid `color.hex` remains missing metadata in APIs, rendering, and reconciliation. The matcher uses its configured missing-metadata score, emits an explicit reason, and never reports a CIEDE2000 distance or `close_color` reason for an unknown color.

Technical instructions must distinguish sourced product/range guidance from reusable generic templates through `instruction_status` and `review_required`. A generic template can help an operator but must remain visibly marked for review and must not be presented as manufacturer-specific instructions.

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

For ordinary opaque paints, rank candidates primarily with CIE Lab and CIEDE2000 color distance, then use functional type, finish, opacity, and medium. For behavioral products such as Contrast, Speedpaint, washes, inks, primers, auxiliaries, and technical effects, compare functional type plus structured application behavior such as transparency, pooling, pigment separation, reactivation, undercoat, finish, and effect type. RGB is only a minor signal for those products and every result requires manual review.

The matcher may propose a single paint candidate, but the workshop solution may instead be a mixture, ordered layer stack, or technique. Persist only the user's explicit workshop choice through the recipe command.

## Workshop inventory

Workshop paint inventory references market paint IDs rather than duplicating product metadata.

```yaml
schema_version: 1
paints:
  - paint_id: citadel-contrast-apothecary-white
    quantity: 1
```

Allow workshop-specific fields only when they describe the owned stock, such as quantity, acquisition details, bottle condition, or personal notes.

## Physical workshop items

Every physical miniature, vehicle, scenery piece, or other paintable component owned by the user is a first-class `workshop_item`. Do not model ownership only as a catalog ID plus a quantity or a painted boolean.

Each physical item has its own stable ID and references one market catalog item and one painting project. It can independently hold a display name, recipe assignment, progress, notes, photos, and history. Multiple copies of one market item therefore produce multiple workshop items.

```yaml
id: ws-reichbusters-soldier-001
catalog_item_id: reichbusters-soldier
painting_project_id: paint-reichbusters
display_name: Soldier 1
```

This per-item identity is mandatory because each physical piece may later receive its own progress photos and journal entries. Paintable-product quantities and completion counts in the workshop are projections calculated from workshop items, not primary ownership fields.

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

Events are immutable. Never edit or delete an existing event to correct history. Append a compensating or corrective domain event instead. Media files are not embedded in the ledger; photo events reference a stable media ID and server-managed location plus metadata such as stage, timestamp, caption, provenance, and hash when useful.

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

The left navigation must visually separate market references from personal data:

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
  Version and author
```

The actual user-facing French labels and other project-independent UI strings must come from `data/site` localization/configuration files rather than being hard-coded in components. Domain-dependent names continue to come from market or workshop data.

The shopping read model separates derived missing paints (`required`) from explicit personal purchase intentions (`planned`). `data/workshop/shopping.yaml` stores only stable market paint IDs when a reference exists plus personal intent such as reason and priority. Required rows are projected from active painting projects, market guides, and workshop ownership. Checked state is activity in the ledger, not ephemeral React state.

Large market catalogs must be queried through paginated REST resources with separate facets. The SPA bootstrap carries summaries, owned-paint overlays, products, and workshop views; it must not embed the full market paint catalog or render thousands of cards at once.

## Media and provenance

Store user-owned progress photos under `media/workshop` through the server. Attach them to individual workshop items and, when relevant, a workflow stage.

For internet images and manufacturer assets, retain source URLs, attribution, and usage status. Do not publish copied images without clear usage rights. Distinguish manufacturer packshots, sourced photos of actual painted results, user progress photos, and approximate generated or color-swatch previews.

## Storage and future database migration

All persistence must be accessed through outbound repository ports. The initial adapters use files. Future database adapters must be replaceable without changing domain entities, application handlers, REST contracts, CLI semantics, or the frontend.

Stable IDs act as foreign keys across market catalogs, workshop membership, physical items, recipes, events, projections, and media metadata. Avoid denormalized copies of market facts in workshop records. YAML is the canonical format for reference/configuration files; CSV may exist only as generated import/export material, not as a second manually maintained source of truth.

## Portable datasets and deterministic administration

`data/` is active application storage. `datasets/` is a separate portable exchange area with the canonical categories `market.paint-brand`, `market.paintable-product`, `workshop.paints`, and `workshop.painting-project`.

Each named dataset contains a versioned `dataset.yaml` manifest and a checksummed `payload/change-set.json`. Python may read application references and create or validate datasets, but only Java application use cases may import them. `minipaintdex datasets import` performs a dry run by default and requires `--apply` to mutate storage. Importing `workshop.paints` replaces the inventory; market and painting-project imports merge through their domain commands.

Keep deterministic transformations in `tools/minipaintdex-data`. Human or agent reasoning identifies images, products, sources and ambiguity; Python performs hashing, normalization, comparison, packaging and validation. Do not encode visual identification or unverified web judgments in deterministic scripts.

## Validation and delivery

- Never bypass or weaken the execution sandbox. Keep generated files and test fixtures inside the repository `target/` directories rather than the system temporary directory when the environment restricts it.
- When an in-scope operation legitimately needs access outside the writable workspace, such as updating `.git`, downloading dependencies, or controlling an external process, request the narrowest explicit elevation and explain the exact action. Do not invent shell workarounds to evade approval.
- A sandbox-related failure must be retried with a workspace-local path or an explicit approved elevation. Record only portable repository behavior here; never encode machine-specific unrestricted paths or permissions.
- Treat a user message containing only `Go` (or equivalent approval) as authorization to implement the refactor or change currently under discussion. It does not authorize a Git commit or push.
- Keep Git publication under the user's control. Create a commit only when the user explicitly asks for a commit in the current request, and push only when the user explicitly asks for a push in the current request. Never carry commit or push authorization forward from an older request after additional work has been requested.
- Keep schemas versioned and validate all persisted files.
- Validate event payloads by event type before appending.
- Make event writes idempotent and safe against partial writes.
- Enforce idempotency inside the same cross-process critical section as the append. Related YAML replacements must be staged and rolled back as one repository operation; all file-backed mutations share the repository write lock.
- Preserve existing user data and unrelated worktree changes.
- Add domain-handler tests plus REST and CLI adapter contract tests.
- Keep REST and CLI behaviors aligned for every application use case.
- Run `mvnw verify` before committing. It must include backend tests and the pnpm frontend checks.
- Use the repository `mini-paint-dex-project` skill for build, server and explicitly requested Git delivery.
- Use `administer-minipaintdex-data` for photo imports, paintable-product imports, brand refreshes and dataset workflows.
