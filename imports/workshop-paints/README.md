# Workshop paint imports

- `photos/`: drop photos of owned paint pots here; unprocessed or unresolved photos stay here.
- `archive/<date>/<import-id>/`: verified photos, with confirmed duplicates in `duplicates/`.
- `runs/<import-id>/`: observations, proposals, simulations and archive manifests for that batch.

These directories target `workshop.paints`, never market scraping or painting-project progress photos.
An archived duplicate does not add stock. Two distinct pots of the same paint do add two units.

Historical runs are evidence: their embedded source paths record the location at processing time.
Do not rerun an old stock import. The reorganization run `2026-09-03-catalog-editions` retains
the old-to-new path mapping, SHA-256 checks and the pre-change catalog/inventory backup.
