---
name: push
description: Vérifier puis pousser en sécurité la branche Git courante vers son dépôt distant. Utiliser quand l’utilisateur demande de pousser, publier ou envoyer des commits existants; ne crée pas automatiquement de commit.
---

# Pousser la branche courante

Publier les commits existants sans réécrire l’historique distant.

## Workflow

1. Inspecter la branche, `git status --short`, `git remote -v` et la configuration upstream.
2. Si des changements non commités existent, les signaler. Ne les committer que si l’utilisateur a aussi demandé un commit.
3. Exécuter les validations du dépôt si elles ne sont pas déjà attestées pour le commit à pousser. Pour MiniPaintDex, utiliser `scripts/test-data-tools.ps1`, puis `scripts/minipaintdex.ps1 build`.
4. Récupérer l’état du distant et comparer la branche locale à son upstream. En cas de divergence, arrêter et expliquer les options sûres.
5. Pousser la branche avec `git push`; ajouter l’upstream uniquement si la branche n’en possède pas.
6. Vérifier que le hash local correspond au hash distant, puis rapporter la branche, le remote et le commit publiés.

Ne jamais utiliser `--force`, `--force-with-lease`, supprimer une branche distante ni pousser des tags sans demande explicite.

Ce skill ne s’exécute que sur une demande explicite de push dans le message courant. Ne jamais déduire cette autorisation d’un ancien message ou d’une approbation « Go ».
