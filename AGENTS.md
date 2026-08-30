# MiniPaintDex agent instructions

This file defines the mandatory architecture and contribution rules for MiniPaintDex. Agents must preserve these decisions unless the user explicitly changes them.

## Product scope

MiniPaintDex is currently a local-first miniature-painting workshop application. Do not add authentication, remote hosting requirements, or a database unless explicitly requested. The architecture must nevertheless keep storage behind interfaces so a database can replace file storage later without changing the web client or the application use cases.

The application distinguishes three domains:

- `site`: application configuration and localized UI labels;
- `market`: public reference catalogs for paints, games, miniatures, scenery, and other paintable products;
- `workshop`: the owner's inventory, projects, physical paintable items, recipes, progress, photos, and activity.

Use English for directory names, schema keys, identifiers, event names, source code, API contracts, and other core data. French belongs in localized site configuration and user-facing text only. Use lowercase ASCII kebab-case for stable domain identifiers.

## Target repository layout

The target architecture is a modular Spring Boot application with a React/Vite frontend. Java 25 is the target language level. Maven is the root build and dependency-management tool; use the checked-in Maven Wrapper. pnpm remains the frontend package manager pinned in `package.json`, but the root Maven lifecycle must orchestrate the frontend validation and build so `mvnw verify` validates the whole product.

MiniPaintDex follows the self-contained system pattern. The production deliverable is one executable Spring Boot JAR containing the REST API and the compiled SPA. Spring Boot serves `index.html`, frontend assets, media, and the client-side route fallback. The separate Vite server exists only for development and proxies API requests to Spring Boot.

```text
backend/
  domain/                 # Pure Java entities, value objects, events, workflow rules
  application/            # Commands, queries, handlers, ports, DTO contracts
  adapter-file/           # YAML/JSONL outbound repositories
  server/                 # Spring Boot and Spring MVC REST adapter
  cli/                    # Picocli adapter using the same application services

app/                      # Existing Vinext/React application routes
components/               # React UI
lib/                      # Frontend-only adapters and types

data/
  site/
  market/
    paints/
    games/
  workshop/
  ledger/
    events/

media/
  workshop/
```

Migrate toward this layout incrementally. Do not break working routes or discard existing data merely to reach the target structure. Add adapters or compatibility readers during migration and remove legacy paths only after validation.

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
| Add a workshop item | `POST /api/v1/workshop/items` | `minipaintdex workshop items add` |
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

The paint-brand refresh skill must accept a brand name and may accept range, current/all scope, and dry-run options. It must resolve aliases, prefer official sources, normalize identifiers, retain provenance and verification dates, report diffs, validate data, and run the project checks. A dry run must not mutate canonical catalogs. Skills must use REST or CLI application interfaces for writes once those interfaces exist.

### Games and paintable catalog items

The market game catalog describes games, editions, products, and generic paintable catalog items. Use a generic `catalog_item` concept so miniatures, vehicles, scenery, and other paintable components share the same model. Market records contain facts such as name, kind, game, source, reference image, and whether assembly is normally required. They do not contain ownership, personal progress, or personal recipes.

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

Each physical item has its own stable ID and references one market catalog item. It can independently hold a display name, project membership, recipe assignment, progress, notes, photos, and history. Multiple copies of one market item therefore produce multiple workshop items.

```yaml
id: ws-reichbusters-soldier-001
catalog_item_id: reichbusters-soldier
project_id: reichbusters-reloaded
display_name: Soldier 1
```

This per-item identity is mandatory because each physical piece may later receive its own progress photos and journal entries. Project quantities and completion counts are projections calculated from workshop items, not primary ownership fields.

## Painting workflow

The canonical workflow uses English stage identifiers:

1. `preparation`: assembly when needed, mold-line removal, cleanup;
2. `priming`: undercoat;
3. `pre_highlight`: progressively lighter dry-brushing or another preparation for rapid painting;
4. `painting`: main color application;
5. `finishing`: highlights and final painted details;
6. `basing`: base treatment.

Keep `finishing` and `basing` distinct in the domain even if the French UI groups them under “Finitions / Socle”. A stage state is one of `pending`, `in_progress`, `completed`, or `skipped`. Optional stages may be skipped with an explicit event and reason.

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
- optional `project_id`;
- actor information;
- correlation and causation identifiers when applicable;
- a typed payload.

Representative event types include:

```text
project.created
workshop_item.added
workshop_item.named
workflow.stage.started
workflow.stage.completed
workflow.stage.skipped
workflow.stage.reopened
recipe.assigned
paint.used
photo.added
photo.caption.updated
photo.removed
comment.added
milestone.reached
```

Events are immutable. Never edit or delete an existing event to correct history. Append a compensating or corrective domain event instead. Media files are not embedded in the ledger; photo events reference a stable media ID and server-managed location plus metadata such as stage, timestamp, caption, provenance, and hash when useful.

## Projections and activity board

Build read models from the ledger for:

- each workshop item's current workflow state;
- project progress and counts;
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
  Games and miniatures

MY WORKSHOP
  My paints
  My projects
  Shopping list
  Imports
```

The actual user-facing French labels and other project-independent UI strings must come from `data/site` localization/configuration files rather than being hard-coded in components. Domain-dependent names continue to come from market or workshop data.

## Media and provenance

Store user-owned progress photos under `media/workshop` through the server. Attach them to individual workshop items and, when relevant, a workflow stage.

For internet images and manufacturer assets, retain source URLs, attribution, and usage status. Do not publish copied images without clear usage rights. Distinguish manufacturer packshots, sourced photos of actual painted results, user progress photos, and approximate generated or color-swatch previews.

## Storage and future database migration

All persistence must be accessed through outbound repository ports. The initial adapters use files. Future database adapters must be replaceable without changing domain entities, application handlers, REST contracts, CLI semantics, or the frontend.

Stable IDs act as foreign keys across market catalogs, workshop inventory, projects, recipes, events, projections, and media metadata. Avoid denormalized copies of market facts in workshop records. YAML is the canonical format for reference/configuration files; CSV may exist only as generated import/export material, not as a second manually maintained source of truth.

## Validation and delivery

- Keep schemas versioned and validate all persisted files.
- Validate event payloads by event type before appending.
- Make event writes idempotent and safe against partial writes.
- Preserve existing user data and unrelated worktree changes.
- Add domain-handler tests plus REST and CLI adapter contract tests.
- Keep REST and CLI behaviors aligned for every application use case.
- Run `mvnw verify` before committing. It must include backend tests and the pnpm frontend checks.
- Use the repository `commit` and `push` skills when the user asks for those operations.
