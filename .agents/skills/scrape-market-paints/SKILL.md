---
name: scrape-market-paints
description: "Scraper les peintures du marché Mini Paint Dex : reconstruire ou rafraîchir les catalogues de marques, normaliser les références, fiabiliser couleurs et images, auditer la couverture et produire des change sets déterministes. Utiliser aussi quand l'utilisateur dit « scraper les peintures du marché » ou demande si le référentiel peut être reconstruit depuis zéro."
---

# Scraper les peintures du marché

Lire [`references/paint-brand-refresh.md`](references/paint-brand-refresh.md) pour un refresh courant d'une ou plusieurs marques.

Pour une reconstruction depuis un catalogue vide, une reprise après incident, ou une demande de reproductibilité, lire aussi [`references/rebuild-from-empty.md`](references/rebuild-from-empty.md). Cette seconde référence enregistre le meilleur chemin appris pendant la constitution du catalogue actuel et indique honnêtement les étapes qui ne sont pas encore autonomes.

## Invariants

- La vérité canonique reste un fichier YAML par marque sous `data/market/paints/`.
- Les collecteurs, manifestes et corrections produisent des change sets ; ils n'écrivent jamais directement dans `data/`.
- Le CLI Java simule toute mutation avant une application explicite.
- Une collecte incomplète, une chute de volume, une ambiguïté d'identité ou une source non traçable bloque l'application.
- Une amélioration ne doit perdre ni attribut source, ni identité stable, ni provenance, ni image de meilleure qualité.
- Ne jamais supprimer le catalogue canonique comme première étape : le sauvegarder par Git ou dataset avant tout exercice de reconstruction.
- Ne jamais committer ni pousser sans demande explicite distincte.
