---
name: commit
description: Valider et créer un commit Git atomique dans le dépôt courant. Utiliser quand l’utilisateur demande de committer, sauvegarder les changements dans Git ou créer un point d’étape, sans pousser le commit.
---

# Committer les changements

Créer un commit ciblé, vérifié et lisible. Ne jamais pousser dans ce workflow.

## Workflow

1. Lire les instructions du dépôt, puis inspecter `git status --short`, le diff de travail et le diff indexé.
2. Identifier précisément les changements demandés. Préserver les modifications non liées et ne jamais les restaurer, les masquer ou les inclure sans raison.
3. Vérifier qu’aucun secret, fichier privé, photo source non destinée au dépôt, cache ou artefact généré inutile n’est sur le point d’être indexé.
4. Exécuter les validations proportionnées. Pour MiniPaintDex, lancer `pnpm check` avant le commit.
5. Ajouter explicitement les chemins du périmètre avec `git add`; éviter `git add .` quand le dépôt contient des changements sans rapport.
6. Relire `git diff --cached --stat` et `git diff --cached`.
7. Créer un message impératif et concis qui décrit le résultat utilisateur.
8. Exécuter le commit, puis rapporter le hash court, le message et l’état restant du dépôt.

Si une validation échoue, corriger seulement ce qui appartient au périmètre ou expliquer le blocage. Ne jamais contourner les hooks avec `--no-verify` sauf demande explicite et justification claire.
