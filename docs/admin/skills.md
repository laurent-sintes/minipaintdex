# Skills Codex

## mini-paint-dex-project

Skill chapeau pour diagnostiquer, construire, tester et lancer l’application. Il route aussi les demandes Git explicites. `Go` autorise le travail demandé mais jamais un commit ou un push. `commit` ne signifie pas `push`.

## administer-minipaintdex-data

Skill d’administration des référentiels et datasets. Il orchestre les scripts Python déterministes pour collecter, normaliser, contrôler et produire des change sets. Toute mutation de l’application passe ensuite par le CLI Java.

Les workflows couverts sont l’import de peintures par photo, l’import d’un produit à peindre, le rafraîchissement d’une ou de toutes les marques, la création d’un dataset et son import en mode simulation ou application.

Les mutations de l’atelier sont acceptées de manière asynchrone. Un skill qui a besoin de données effectivement committées utilise l’option CLI globale `--wait` et, si nécessaire, `--wait-timeout=PT30S`; il ne lit pas directement le ledger pour deviner la fin d’une commande.

## Commandes principales

```text
python tools/minipaintdex-data/mpdx_data.py dataset create ...
python tools/minipaintdex-data/mpdx_data.py dataset validate <dataset>
scripts/minipaintdex.ps1 cli -- datasets import --input <dataset>
scripts/minipaintdex.ps1 cli -- datasets import --input <dataset> --apply
```
