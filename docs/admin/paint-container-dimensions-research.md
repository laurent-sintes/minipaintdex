# Paint container dimensions: research and qualification

Research date: 2026-09-03. Status: provisional evidence, not an active Market catalog.

## Scope and outcome

This investigation prepares rack compatibility for Citadel / Warhammer Colour,
Vallejo and The Army Painter. It does not implement racks, change `PaintProduct`
or `PaintPot`, assign formats to existing pots, or import any Market data.

Useful dimensions were found in container CAD models, first-person measurements
and rack designers' compatibility documentation. No manufacturer-certified
external dimensional drawing was established for the targeted paint containers
during this search. Published bottle volume is not dimensional evidence.

The observations below support approximate planning. They do not establish
manufacturing tolerances, identify the user's exact packaging generation, or
guarantee a tight mechanical fit. A search date is not a measurement date.

## Provisional format candidates

All dimensions are millimetres. These identifiers label research candidates,
not published domain identities. Unknown dimensions remain unknown.

| Candidate | Horizontal observation | Height observation | Evidence and qualification |
| --- | --- | --- | --- |
| `citadel-flip-top-12ml` | CAD bounds 34.94 x 32.28 | 42 | S1; model dimensions, not a physical measurement |
| `citadel-flip-top-18ml` | CAD bounds 34.94 x 32.24 | 50 | S1; model dimensions, not a physical measurement |
| `citadel-flip-top-24ml` | CAD bounds 34.94 x 32.30 | 59 | S1; model dimensions, not a physical measurement |
| `vallejo-dropper-17ml-historical` | Diameter 24 | 80 | S4; historical community report, exact product and method unspecified |
| `vallejo-dropper-18ml` | Diameter 24.75 | 78.5 | S2; reported digital-caliper measurement, range/generation unspecified |
| `army-painter-dropper-18ml` | Diameter 24.75 | 76.5 | S2; reported digital-caliper measurement, range/generation unspecified |
| `vallejo-metal-color-32ml` | Diameter 35.25 | 74.25 | S2; reported digital-caliper measurement, exact product unspecified |
| `vallejo-bottle-35ml-unresolved` | Unknown | Unknown | Separate identification needed; do not inherit 32 ml geometry |
| `vallejo-bottle-60ml-unresolved` | Unknown | Unknown | S7 documents an adapter opening, not a bottle dimension |
| `vallejo-bottle-200ml-unresolved` | Diameter 49 | 147 | S3; rack designer's example, exact product unspecified |

The CAD horizontal bounds are rounded here for readability. They must not be
relabelled as the diameter of a circular base. Neither CAD precision nor the
number of decimals in a caliper report establishes physical accuracy. S2 does
not explicitly document the cap state, measurement points or instrument error.

For the illustrated rack, a first approximation is therefore a wider/shorter
Citadel family and a narrower/taller dropper family, with distinct heights within
each family. The photograph alone does not establish rack capacity or identify
the exact container generations.

## Evidence register

### S1 — Citadel reference geometry

[soswow: Citadel Paint Pods Models, Cults](https://cults3d.com/en/3d-model/tool/citadel-paint-pods-models-for-modeling-holders-and-such)

Published 2021-05-23; inspected through indexed page content on 2026-09-03.
Direct page retrieval was unsuccessful during this research. The author supplies
stand-in pot models for designing holders, with STEP, F3D and STL files.

The page reports these STL axis-aligned bounds:

| File | X | Y | Z |
| --- | ---: | ---: | ---: |
| `Citadel 12ml pot.stl` | 34.936695 | 32.2826 | 42.0 |
| `Citadel 18ml pot.stl` | 34.937901 | 32.242634 | 50.0 |
| `Citadel 24ml pot.stl` | 34.938463 | 32.2984 | 59.0 |

These are model metadata; the binaries were not downloaded or measured locally.
The author does not establish a metrology protocol or manufacturing tolerance.
The similar horizontal bounds support a shared-envelope hypothesis, not proof
that every historical or current Citadel container has identical dimensions.

### S2 — First-person digital-caliper observations

[NacidoCoqui, comments on Prodicer's Paint Pro Tower, Cults](https://cults3d.com/it/modello-3d/gioco/paint-pro-tower-modular-rotatable-paint-pot-holder/commenti)

Comment dated 2026-01-13 18:37 UTC; inspected through indexed comment content on
2026-09-03. Direct retrieval failed. The contributor states that they have the
bottles and digital calipers. The retained observations are:

- Vallejo dropper: diameter 24.75; height 78.5.
- Army Painter dropper: diameter 24.75; height 76.5.
- Vallejo Metal Color: diameter 35.25; height 74.25.

The comment labels capacities as `18-mm` and `32-mm`. Reading these as 18 ml and
32 ml is a contextual interpretation, supported by the surrounding discussion,
not a silently corrected quotation. No exact paint reference, lot, packaging
generation, cap state, uncertainty or measurement photograph is supplied.

Retain as community measurements requiring scope confirmation. Do not apply the
Army Painter observation automatically to every Warpaints, Fanatic, Air or
Speedpaint generation. Likewise, the Vallejo observation does not identify which
18 ml range or cap was measured.

### S3 — Rack designer's practical dimensions

[3DOtakuPrints: Master Painter, Gamefound](https://gamefound.com/zh/projects/3dotakuprints/master-painter-paint-rack)

Inspected through indexed campaign content on 2026-09-03; direct retrieval did
not expose the text. Exact measurement and publication dates were not established.
The designer lists bottle examples for their own storage system:

- Citadel: diameter 32.5, heights 42 and 50; one repeated section uses 33 for the
  taller example.
- Vallejo: diameter 24.5, height 78.
- Army Painter: diameter 24.5, height 75.
- Vallejo 200 ml: diameter 49, height 147.

Except for the 200 ml example, capacities and exact product generations are not
specified. These examples corroborate general size families but cannot identify
the format of an existing `PaintPot`. The small differences from S2 remain
separate observations, not values to average into a supposedly exact standard.

### S4 — Historical Vallejo observation

[Lt. Hazel: Dimensions of a Vallejo Paint Bottle, Lead Adventure Forum](https://leadadventureforum.com/index.php?topic=1897.0)

Reply dated 2007-06-14; retrieved in the preceding research exchange. The reply
reports diameter 24 and height 80. It does not explicitly provide capacity,
measurement equipment or the product reference. Association with the historical
17 ml format is a research hypothesis, not a confirmed mapping. It provides no
evidence about the newer r-PET packaging.

### S5 — Current packaging differs from historical packaging

[Vallejo: Model Color introduction and chart](https://acrylicosvallejo.com/wp-content/uploads/2024/03/CC329-R00-Model-Color-NewIC.pdf)

Official manufacturer content, inspected through indexed text on 2026-09-03.
It identifies the new transparent 18 ml r-PET packaging. It is evidence of
packaging characteristics, not of external width or height. Thus a generic
third-party statement that Model Color is always 17 ml must not drive assignment.

### S6 — Rack openings, not bottle dimensions

[Back 2 Base-ix: Paint Rack, Large Straight, MDF](https://www.back2base-ix.com/products/paint-large)

The rack maker offers a 25.5 mm opening option for dropper families including
Vallejo and Army Painter, and a 33 mm option for the current Citadel range.
Inspected through indexed product text on 2026-09-03; direct retrieval failed.
These are the manufacturer's compatibility claims for this rack, not metrology
for every listed paint product.

[LITKO: Paint Rack Guide](https://litko.net/pages/paint-racks)

The guide, dated 2026-06-10, associates common Vallejo and Army Painter droppers
with 26 mm rack holes and Citadel pots with 34 mm holes. It also calls Army
Painter bottles 26 mm wide and Vallejo bottles about 25 mm wide. The former
conflicts with S2's 24.75 mm sample and S3's 24.5 mm example. Retain the rack
compatibility claim separately; do not select 26 mm as a verified pot diameter.

Different rack openings may reflect clearance, support depth and the part of the
pot passing through the opening. They must not be averaged or substituted for
the maximum horizontal envelope needed on a shelf.

### S7 — Vallejo adapter dimensions

[Chaco: Vallejo Paint Shaker/Mixer for Drill, Cults](https://cults3d.com/en/3d-model/art/vallejo-paint-shaker-mixer-for-drill)

Published 2020-07-24, with original Thingiverse publication shown as 2020-07-23.
The author identifies 26 mm and 36 mm internal adapter diameters for 17 ml and
60 ml Vallejo bottles. These are adapter dimensions only. They do not establish
a 26 mm or 36 mm bottle diameter or any bottle height. No usage instructions from
the tool are adopted here.

## Conflicts and excluded shortcuts

- **Army Painter 26 mm:** the initial conversation reported LITKO's value. S2
  and S3 supply smaller figures. The 26 mm claim is not a canonical dimension;
  the 24.75 mm sample is a more directly described measurement, still provisional.
- **Citadel 32–33 mm versus approximately 35 mm:** a body diameter and a model's
  complete horizontal bounds are not necessarily the same measurement. The
  hinge/lid-envelope explanation is a hypothesis to check on a physical pot.
- **CAD axes:** a bottle stored at an angle produces misleading bounding-box
  heights. For example, [fusefactory's Army Painter model](https://cults3d.com/en/3d-model/tool/army-painter-bottle-rack)
  accompanies an inclined holder and has bounds around 72.29 x 23.99 x 53.04.
  These are not accepted as upright width/depth/height.
- **Inconsistent tables:** [PROSCALE's English Citadel guide](https://proscalehobbies.com/articles/citadel-paint-storage-guide/)
  and its [Spanish version](https://proscalehobbies.com/es/articulos/guia-almacenamiento-pinturas-citadel/)
  report incompatible heights for the same nominal formats. They are excluded
  as dimensional authorities rather than selecting the convenient language.
- **Capacity is not geometry:** 18 ml in two brands need not imply the same
  shape; 32 ml and 35 ml Vallejo bottles are not merged.
- **Repeated source text is not independent corroboration:** mirrors, translated
  pages and copies of the same sizing table count as one originating claim.

## Local catalog scope, not pot-format assignment

Read-only inspection of canonical two-space `volume_ml` fields on 2026-09-03
found the following reference counts. Nested source-snapshot values were excluded.
These are commercial records, not owned pots or verified packaging identities.

| Catalog | Recorded volumes and reference counts |
| --- | --- |
| `warhammer-colour.yaml` | 12 ml: 173; 18 ml: 81; 24 ml: 46; 400 ml: 12; zero/unknown: 20 |
| `vallejo.yaml` | 18 ml: 857; 32 ml: 18; 35 ml: 57; 400 ml: 34 |
| `the-army-painter.yaml` | 18 ml: 508; 400 ml: 29 |

The absence of a currently recorded 17 ml Vallejo reference does not prove the
owner has no historical 17 ml containers. A catalog refresh can describe current
packaging while an owned pot remains an older physical object. No automatic
join based solely on brand, range or volume is authorized by this evidence.
The 400 ml products require a separate container investigation; they are outside
this first small-pot rack scope. Zero volume never identifies a format.

## Minimal physical verification

Prioritize one actual example of each owned format: Citadel 12/18/24 ml,
historical and newer Vallejo droppers, Army Painter dropper, and Metal Color
if present. Add examples when the cap, body or packaging generation differs.
One sample describes that sample; several establish an observed variation, not
a certified production tolerance.

For each sample record:

1. Paint reference, printed capacity, packaging appearance and photo; exact
   physical pot identity when available. Do not date the pot from a scrape date.
2. Total height with the cap fully closed, from the resting surface to the top.
3. Body/base diameter and maximum left-to-right and front-to-back envelope,
   including lid, hinge and label; retain the orientation used for both axes.
4. Diameter at the rack's support/insertion height if a hole is involved.
5. Instrument, stated precision, observation date and measurement method.
6. Any direct fit test, naming the physical rack, row and tested configuration.

For an exact millimetre model of the wooden rack, usable width, depth and insertion
path would still be useful. The owner subsequently confirmed **14 standard pots or
11 Citadel/Warhammer pots per row, with no height problem** (2026-09-03).
Height measurement is therefore not a prerequisite for arranging these formats
on this particular rack. See [relative rack capacity](rack-storage-design.md).
Do not translate that fit observation into an infinite clearance or a measured
height; other formats and other racks need their own compatibility evidence.
Handling clearance is a separate policy allowance for measured geometry, not an
invented addition to the measured pot dimensions or observed relative capacity.

## Conditions for later integration

The proposed `PaintContainerFormat` remains a design candidate until its domain
contract is implemented. This document is not a versioned import dataset or a
second editable source of active application data.

A later implementation should retain independent, sourced observations with
measurement kind, units, cap state, sample scope and uncertainty. A reviewed
decision may select nominal values and bounds; conflicts and unknown values
must survive. Corrections must not change paint identities or rewrite pot history.

Public sourced packaging facts belong to Market. Personal measurements and
identification of an owned `PaintPot` belong to Workshop. Rack opening dimensions
belong to the rack definition, and workshop fit observations remain personal.
Any proposed dimension-dependent placement must show its uncertainty and must
not become a guaranteed fit merely because a container shares a brand or volume.
