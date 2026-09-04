# Skills Codex

## mini-paint-dex-project

Skill chapeau pour diagnostiquer, construire, tester et lancer l’application. Il route aussi les demandes Git explicites. `Go` autorise le travail demandé mais jamais un commit ou un push. `commit` ne signifie pas `push`.

Le démarrage utilise les commandes déterministes `start`, `restart`, `stop`, `status` et `doctor`
du lanceur PowerShell. Le skill interprète leurs résultats et leurs erreurs ; il ne reconstruit pas
le workflow à chaque demande et n’impose pas un `clean verify` pour un simple redémarrage.
Voir [le cycle du serveur local](local-server.md), notamment les contrôles de fraîcheur et les
temps distincts de démarrage et de tests post-démarrage. Aucun skill supplémentaire n’est nécessaire.

La livraison utilise aussi [les preuves de validation réutilisables](verification.md) : `verify`
pour le dossier de travail, `prepare-commit` pour l’index exact, puis `commit -Message` uniquement
sur demande explicite. Une preuve valide évite de reconstruire et de redémarrer le serveur.

## scrape-market-paints

Skill dédié à ce que le produit appelle « scraper les peintures du marché ». L'appel explicite est `$scrape-market-paints`. Il couvre le refresh courant, la reconstruction depuis un catalogue vide, la normalisation, les couleurs, les images, les audits et l'idempotence.

Le meilleur chemin appris pendant la constitution du référentiel est enregistré dans sa procédure `rebuild-from-empty.md`. Elle distingue les étapes déjà reproductibles des entrées externes qui doivent encore être automatisées ; une restauration Git n'y est pas confondue avec un nouveau scraping.

## administer-minipaintdex-data

Skill d’administration des imports ponctuels et datasets. Il orchestre les scripts Python déterministes pour normaliser, contrôler et produire des change sets. Toute mutation de l’application passe ensuite par le CLI Java.

Les workflows couverts sont l’import de peintures par photo, l’import d’un produit à peindre, la création d’un dataset et son import en mode simulation ou application. Le référentiel marché relève de `$scrape-market-paints`.

Les mutations de l’atelier sont acceptées de manière asynchrone. Un skill qui a besoin de données effectivement committées utilise l’option CLI globale `--wait` et, si nécessaire, `--wait-timeout=PT30S`; il ne lit pas directement le ledger pour deviner la fin d’une commande.

## Commandes principales

```text
python tools/minipaintdex-data/mpdx_data.py dataset create ...
python tools/minipaintdex-data/mpdx_data.py dataset validate <dataset>
scripts/minipaintdex.ps1 cli -- datasets import --input <dataset>
scripts/minipaintdex.ps1 cli -- datasets import --input <dataset> --apply
```
