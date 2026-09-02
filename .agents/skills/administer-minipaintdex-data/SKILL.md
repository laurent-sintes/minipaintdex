---
name: administer-minipaintdex-data
description: "Administrer les données Mini Paint Dex : importer des peintures par photo, importer un produit à peindre depuis Internet, créer et importer des datasets portables avec les scripts Python déterministes et le CLI Java. Pour scraper ou rafraîchir les peintures du marché, utiliser le skill scrape-market-paints."
---

# Administrer les données Mini Paint Dex

Choisir le workflow demandé dans [`references/workflows.md`](references/workflows.md), puis lire uniquement les références spécialisées indiquées.

## Répartition obligatoire

- Le raisonnement visuel et la recherche qualifient les identités, sources, licences et ambiguïtés.
- `tools/minipaintdex-data/mpdx_data.py` effectue les opérations déterministes : hash, normalisation, contrôles, génération de change sets et packaging de datasets.
- Le CLI Java simule puis applique les mutations. Les scripts Python et le frontend n’écrivent jamais directement dans `data/` ni dans le ledger.

Toujours conserver la distinction entre la connaissance du marché, l’inventaire du Workshop, les choix d’un PaintingProject et les WorkshopItems physiques. Ne jamais committer ni pousser dans ce skill sauf demande explicite distincte.
