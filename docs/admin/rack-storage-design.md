# Rack storage

## Catalog and ownership

Every Market `PaintProduct.containerFormatId` references an internal `PaintContainerFormat`.
Brand refreshes collect packaging evidence by exact commercial reference, never by brand alone.
Paint and container upserts share one validated, atomic change set. Missing geometry uses an
explicit unidentified format with null dimensions; refresh preserves previously qualified links.
There is no container registry or selector in the web UI.

`RackProduct` owns its rows and sourced photo metadata (remote URL, source page, credit and rights
status). The `scrape-market-racks` skill qualifies manufacturer references and uses Java's
preview/save use case, shared by REST and CLI. Outside dimensions are not useful row dimensions.

`AddRacks(rackProductId, quantity, location)` creates one `WorkshopRack` per physical copy in one
idempotent atomic event batch (quantity 1–100). New custom configurations and row overrides are
rejected, including through REST/CLI. Existing history remains readable. An unidentified owner
rack must not be falsely published as a commercial model.

An empty proposal rack selection means every owned rack. Strategies are brand/range, color
family and usage. Product container links provide geometry automatically; an explicit personal
observation can override it internally. Proposals are read-only, preserve locks and optionally
other placements, and require explicit confirmation against the same inventory/catalog token.

## Initial data quality, 2026-09-04

All 2,031 existing paint references have a container link. The initial association uses five
provisional container profiles for 1,665 references (commercial brand and volume matching,
`container_assignment_status: estimated`); it is not a per-SKU verified measurement. The other
366 references have dedicated unidentified profiles and cannot be automatically positioned.
The five dimensions remain marked estimated. Future manufacturer evidence must refine these
links; volume or brand alone must not be treated as proof of an actual physical pot's geometry.

Two initial manufacturer rack candidates were inspected with their official photographs:

- [HobbyZone OM05s](https://www.hobbyzone.pl/en/modular-workshop-system/46-om05s-paints-module-26mm.html):
  36 openings of 26 mm; four rows of nine. The published compatibility explicitly includes
  Army Painter 18 ml and Vallejo 17 ml, not the current Vallejo 18 ml profile.
- [HobbyZone OM05b](https://www.hobbyzone.pl/en/modular-workshop-system/45-om05b-paints-module-36mm.html):
  26 openings of 36 mm; rear-to-front rows of 7, 6, 7 and 6. Published compatibility includes
  Games Workshop 12 ml and Citadel 24 ml. The 18 ml profile is not inferred from this list.

Both are top-of-stack modules, exterior 300 × 150 × 150 mm. Source photos remain remote and
credited to HobbyZone; no redistribution license is asserted. Slot geometry does not certify
the provisional dimensions of a paint container.

## Owner observations

Source: owner conversation, 2026-09-03, photograph `IMG_5337.jpeg`.
The unidentified wooden rack has four continuous rows. The owner reports a capacity
of 14 standard dropper bottles **or** 11 Citadel/Warhammer pots per row, and no height
problem for these formats. Do not infer a manufacturer, SKU, millimetre measurement,
or compatibility with every bottle sold by these brands from that observation.

For this rack, use per-row capacity calibrations linked to explicitly identified
container formats, with `heightVerified: true`. This is workshop knowledge, not a
public claim about a market rack. A taller Citadel format can share the capacity
when its footprint belongs to the observed family; height is not a constraint for
this owner's rack. Other racks keep their own height limits.

## Relative capacity

One standard pot consumes `1/14` of a row; one Citadel pot consumes `1/11`.
A proposed mixed row with `s` standard pots and `c` Citadel pots satisfies:

```text
s / 14 + c / 11 <= 1
```

Equivalent integer form: `11*s + 14*c <= 154`.
Examples: 14/0, 0/11, 7/5, 5/7, 2/9. The 7/6 combination does not fit.
The four rows therefore hold 56 all-standard or 44 all-Citadel pots. Mixed
capacity must be checked per row, not by pooling leftover fractions between rows.

This is an explicitly accepted **estimate** for mixed arrangements: two observed
integer capacities cannot establish exact bottle widths, free margin or an exact
physical width ratio. It is deliberately not converted to millimetres.
`offsetFraction` addresses relative placements; `offsetMm` addresses measured
ones, and `slotId` addresses fixed apertures. A row uses one coordinate system.
The observed capacity includes the observed spacing; do not add the policy's
millimetre gap to fractions. Known contradictory measurements remain a conflict.

The proposal is not proof that the owner moved the pots. Only a confirmed
arrangement changes the workshop placement history.
