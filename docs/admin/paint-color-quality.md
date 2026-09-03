# Paint colour data qualification

## Scope and invariants

This workflow qualifies Market `PaintProduct` reference data before designing a
swatch filter. It changes neither Workshop state nor the frontend. Paint IDs,
commercial references, images, shared guides and catalog memberships stay intact.
Market corrections use the existing Java change-set application command; they do
not create Workshop events. No contract or schema version changes.

HEX is an approximate digital representation, not a measurement of dry paint.
Coverage, finish, medium, application system and effects remain independent
properties. In particular, a metallic paint can also be a one-coat paint; its
flat HEX does not describe its reflective appearance. Unknown fields stay unknown.

## Repeatable audit

```powershell
python tools/minipaintdex-data/mpdx_data.py catalog audit-color-quality --output tools/minipaintdex-data/target/paint-color-quality/audit.json
```

The read-only report includes every paint and brand, missing/invalid colours,
noncanonical family labels, unknown technical fields, explicit colour-source
evidence and source disagreements. A product-page snapshot without a HEX is not
counted as evidence for the current HEX. Lack of such evidence does not prove the
value wrong. Empty effects do not establish that a paint has no special effect.

Continuous `digital_lab` coordinates use the same sRGB/D65 calculation as Java
`PaintMatchEngine`. The report also derives `digital_hsl`, a candidate swatch and
boundary-review reasons using the versioned `resources/paint-hsl-policy.json`.
Neither coordinate system is persisted as another canonical colour. Existing
family labels remain separate; neither is a perceptually uniform taxonomy.

## Reviewed corrections

```powershell
python tools/minipaintdex-data/mpdx_data.py catalog reviewed-color-corrections --manifest tools/minipaintdex-data/resources/paint-color-quality-review.json --output tools/minipaintdex-data/target/paint-color-quality/reviewed-changeset.json --audit-log tools/minipaintdex-data/target/paint-color-quality/reviewed-audit.json
.\scripts\minipaintdex.ps1 cli --root . --format json market paint-products apply --input tools/minipaintdex-data/target/paint-color-quality/reviewed-changeset.json
# After reviewing the simulation:
.\scripts\minipaintdex.ps1 cli --root . --format json market paint-products apply --input tools/minipaintdex-data/target/paint-color-quality/reviewed-changeset.json --apply
```

The reviewed manifest targets exact ID/brand/reference tuples. Free-label family
normalization requires the exact previous label. Metallic corrections require
the exact name and reference in a retained official Vallejo catalogue snapshot.
Unexpected existing effects block the correction instead of being overwritten.
Each correction retains before/after values, rationale, review date, evidence and
manifest SHA-256 in `source_snapshots`. Existing snapshots are preserved. Unrelated
fields, including original product verification dates, are not upgraded.

After application, regenerate the change set: it must have zero operations.
Collectors now preserve existing HEX, explicitly reviewed fields and all prior
source snapshots. They still contain range-level defaults and name-based family
heuristics, so unreviewed or new records are not automatically qualified. A stale
identity or conflicting reviewed field blocks replay and requires another review.

## Missing HEX enrichment

Use the existing `catalog enrich-paint-colors` command with `color-sources/paintdex.json`.
For the September 2026 follow-up, select `The Army Painter` and `Warhammer Colour`.
Download `army-painter.json` and `citadel.json` from `data/paints/` at the manifest's
exact Git revision, into a local source directory. Pass it as `--source-root`.
Generate both `--output` and `--audit-log`, simulate/apply with Java as above and
check zero operations on regeneration. Existing HEX values are never replaced.

The pinned source is [Paintdex at revision f77970b](https://github.com/s10-steve/paintdex/tree/f77970bad84e8b23337af24d204b741d51c67104/data/paints).
The MIT license is retained in `color-sources/PAINTDEX-LICENSE.txt`.
For those two files, `sha256` pins the original Windows checkout bytes and the
additional `sha256_text_lf` pins verified upstream content with CRLF converted to
LF. This accepts only the explicitly pinned line-ending equivalence, not JSON
reformatting or changed values. All other files retain their existing byte checks.

`Hydra Turquoise` has one reviewed spelling alias, `Hydra Turqoise`, within the
same historical `Warpaints` range; the source also carries reference `WP1141`.
No historical colour is borrowed from Fanatic or Air just because names match.

## Review on 2026-09-03

The first applied lot preserves all 2,031 products:

| Check | Before | After |
| --- | ---: | ---: |
| Colour-bearing products with HEX | 1,961 / 1,973 | 1,972 / 1,973 |
| Auxiliary products, no artificial HEX | 58 | 58 |
| Free-form family labels | 5 | 0 |
| Missing family | 398 | 398 |
| Explicit metallic name without metallic effect | 15 | 0 |
| HEX without explicit matching colour evidence in snapshots | 47 | 47 |
| Unknown coverage | 62 | 62 |
| Unknown finish | 526 | 526 |

Five existing descriptions were normalized: grey, green, purple, silver and brown.
Fifteen explicit metallic designations were reconciled with the retained official
catalogue. Eleven approximate HEX values were added from pinned community data
(ten original Warpaints and Armageddon Dunes). Existing HEX values were untouched.

### Remaining review queue

- `tap-wp1470` / Light Tone: resolved by the historical chart follow-up below;
  do not substitute the Fanatic formula or infer physical wash colour from the HEX.
- 398 missing families: define and review a stable perceptual taxonomy before
  assigning them. The Lab report is an input, not an approved classification.
- 47 existing HEX values lack explicit matching colour evidence in snapshots:
  preserve them pending source review, not automatically overwrite them.
- Comparing the two pinned external brand files also found 26 disagreements
  with existing HEX values (17 Warhammer, 9 Army Painter). These are in the
  enrichment audit, not necessarily in the retained-snapshot disagreement count;
  no conflicting source was imported and no existing value was replaced.
- Unknown coverage/finish require product- or range-specific sources. Empty
  effects and metal-like names are not enough to infer metallic behaviour.
- Existing lexical families can be technically valid labels but visually poor
  assignments (e.g. a name containing Rust becomes red). Audit swatch distances
  and review boundary cases before using those labels as a training reference.

This is a traceable first correction lot, not certification of the entire catalogue
or of colour fidelity. The future swatch UI is deliberately unchanged.

## Light Tone follow-up on 2026-09-03

The owner's photo shows The Army Painter **Quickshade Washes Light Tone**; its
front does not show a manufacturer reference. It is identification context, not
a colour measurement or a request to register another pot. No personal photo is
copied into Market storage.

The [official legacy product page](https://thearmypainter.com/products/warpaints-warpaints-washes-light-tone-wp1470p)
names Warpaints Washes Light Tone, SKU WP1470P, and explicitly describes Quickshade
Washes. Independently, page 2 of the
[2019 catalogue distributed by Freesia Enterprise](https://freesia-enterprise.com/wp-content/uploads/2020/07/APcatalogue2019WEB.pdf)
labels the chart cell **Light Tone WP1470**. This provides exact reference evidence
without renaming the existing `tap-wp1470` identity or aliasing different ranges.

The chart itself shows a red-brown digital swatch. The accepted approximate HEX is
`#b03622` (RGB 176, 54, 34), not a sample of the owner's photo and not a measured
dry-paint colour. The old community candidates `#AB3B2C` and `#B03812` are therefore
not inherently implausible just because they look reddish; the pinned historical
chart provides stronger provenance. The Fanatic candidate remains separate.

Extraction is recorded in `color-sources/army-painter-light-tone-2019-colors.json`:
PDF SHA-256, page, renderer/version, rendered dimensions, pixel rectangle and modal
pixel count. The complete PDF is not redistributed. Reproduce the extraction with
Poppler 26.05.0 and Pillow:

```powershell
pdftoppm -f 2 -l 2 -scale-to 2200 -png -singlefile APcatalogue2019WEB.pdf chart
```

```python
from collections import Counter
from PIL import Image

with Image.open("chart.png") as source:
    assert source.size == (2200, 1569)
    pixels = source.convert("RGB").crop((2006, 972, 2087, 1043))
    assert Counter(pixels.get_flattened_data()).most_common(1) == [((176, 54, 34), 3634)]
```

Apply only through the existing enrichment workflow:

```powershell
python tools/minipaintdex-data/mpdx_data.py catalog enrich-paint-colors --manifest tools/minipaintdex-data/color-sources/army-painter-light-tone-2019.json --source-root tools/minipaintdex-data/color-sources --as-of 2026-09-03 --output tools/minipaintdex-data/target/light-tone-review/change-set.json --audit-log tools/minipaintdex-data/target/light-tone-review/audit.json
```

Then simulate and apply that change set with the Java command documented above.
Regeneration must produce zero operations. This closes the last missing HEX:
1,973 of 1,973 colour-bearing references have a digital colour; the 58 exclusively
auxiliary products still have no artificial HEX. The 398 missing families and
other quality issues are unchanged. Complete HEX coverage is not a guarantee of
physical colour fidelity.

## Four-topic follow-up on 2026-09-03

`resources/paint-quality-followup-review.json` contains 339 explicit field
decisions affecting 252 existing products. It is a reviewed decision file, not
a rule that automatically endorses every future product in a named range.
The existing Java application command simulated and applied these records with
zero additions, deletions, rekeys or Workshop mutations. Existing images,
memberships, guides, verification dates and source snapshots were preserved.
One sourced usage guide was added for the 20 TMM Shade references.

| Check | Before this follow-up | After |
| --- | ---: | ---: |
| HEX without explicit matching colour evidence | 47 | 2 |
| Unknown finish | 526 | 363 |
| Unknown coverage | 62 | 62 |
| HSL-classifiable colour-bearing references | 1,973 | 1,973 |
| Legacy family missing | 398 | 398 |

### Sources and decisions

- 45 existing HEX values were replaced by reproducibly extracted manufacturer
  illustrations: 22 Warhammer gradients, 12 Vallejo 2026 chart cells, nine
  Speedpaint 2.0 poster badges, and two historical Model Color equivalents for
  Prince August. The latter are explicitly equivalents, not measured PA colours.
- All nine Speedpaint SKU pages explicitly identify the current 2.0 product.
  Sampling uses the upper-left face of the named poster badge, excluding its
  lettering and darker right face, not the painted example miniature.
- Warhammer and Xpress gradients use a recorded median rectangle. Metallic
  Vallejo illustrations use their upper-left region to avoid the drawn highlight.
  These choices are digital filtering conventions, not physical colour science.
- The 26 previously reported community conflicts were reviewed: 24 now use
  manufacturer illustrations; **Ork Flesh and Creed Camo remain unresolved**.
  Their current HEX was preserved because the official illustrations themselves
  show suspiciously large visual shifts. Candidate URLs, hashes and the decision
  rationale are in `resources/paint-quality-pending-review.json`.
- 163 finishes were documented: 59 Xpress paints (excluding medium), 42 standard
  Mecha colours, eight explicitly labelled Mecha varnishes/effects, 34 Hobby
  Paint products with varnishes handled separately, and 20 TMM Shade washes.
- Eight previously unmarked Mecha metallics were corrected; three already
  metallic references received corroborating evidence.
- Vallejo's **2026 catalogue, pp. 75-76**, explicitly distinguishes TMM Shade:
  its 20 references are satin, non-metallic washes. Their roles, application
  system, finish and effects were corrected together. `transparent` here is the
  canonical wash coverage category, not a measured opacity. The 20 TMM Airbrush
  references now carry the airbrush application method. The 20 Base references'
  existing opaque coverage was corroborated, not imposed on Shade.

Primary sources: [Vallejo 2026 catalogue](https://acrylicosvallejo.com/wp-content/uploads/2025/10/Catalogo_2026-R02.pdf),
[Xpress range](https://acrylicosvallejo.com/en/category/hobby/xpress-color-en/),
[Mecha standard colour description](https://acrylicosvallejo.com/producto/modelismo/mecha-color/light-blue-69016/),
[Army Painter downloads](https://thearmypainter.com/pages/downloads).
Exact Warhammer asset URLs and each extraction locator are retained in the review.

### Reproduction and application

Keep downloaded assets outside canonical data. The reviewed decisions retain the
source file SHA-256, render size, page and pixel rectangle. Reproducing the 45
HEX values requires Pillow and Poppler (`pdftoppm`, reviewed with 26.05.0), but not
network access or the exploratory extraction scripts:

```powershell
python tools/minipaintdex-data/mpdx_data.py catalog verify-reviewed-swatches --manifest tools/minipaintdex-data/resources/paint-quality-followup-review.json --source-root tmp/pdfs --source-root tools/minipaintdex-data/target/paint-color-quality --output tools/minipaintdex-data/target/paint-color-quality/swatch-verification.json
python tools/minipaintdex-data/mpdx_data.py catalog reviewed-color-corrections --manifest tools/minipaintdex-data/resources/paint-quality-followup-review.json --output tools/minipaintdex-data/target/paint-color-quality/followup-changeset.json --audit-log tools/minipaintdex-data/target/paint-color-quality/followup-audit.json
```

Simulate and apply non-empty changesets with the Java command above. Regeneration
after this lot returns zero operations. Semantically identical decision files
remain idempotent after formatting or LF/CRLF changes; the original evidence hash
is retained. Changing the decision's meaning still requires a fresh review.

### HSL preparation, not UI activation

The proposal partitions the hue circle into eight explicit intervals, with a
separate low-saturation neutral group, three saturation bands and five lightness
bands. Bounds are lower-inclusive and upper-exclusive; hue wraps at 360 degrees.
Each item receives continuous H/S/L, a stable swatch ID and an actual swatch HEX.
Achromatic hue is null, not a misleading red hue. Auxiliary-only products receive
no synthetic HEX or HSL. Metallic, fluorescent, wash and one-coat behaviours remain
independent profile dimensions; HSL never infers them.

All 1,973 coloured references are classified. **779 are near at least one proposed
boundary** and form a visual-review queue. These thresholds are a review proposal,
not an approved perceptual taxonomy. The 398 empty legacy family labels remain
untouched: the derived HSL grouping does not depend on names or pretend to repair
the old vocabulary. No frontend or production filter contract changed.

### Import protection and remaining work

The shared Java administrative handler preserves the union of historical source
snapshots and blocks replacement of existing HEX or reviewed fields without a new
matching before/after review. This covers upsert/rekey and all adapters using the
handler, including dataset imports. Validation precedes writes. Automatic Python
collectors retain the protected values and source evidence; the official-refresh
audit now includes colour-quality summaries before and after proposed upserts.

The audit distinguishes completeness from explicit review: a non-unknown default
is not certified merely because it is populated. Outstanding work includes the
two ambiguous HEX sources, 363 unknown finishes, 62 unknown coverage values,
unreviewed range defaults, and visual validation of HSL boundaries before UI work.
Pigment, texture and auxiliary coverage must not be filled with arbitrary opaque
values merely to reduce the unknown count. This lot is not certification of the
entire physical paint catalogue.
