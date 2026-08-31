# Livraison Git

## Commit

Un commit exige une demande explicite dans le message courant.

1. Inspecter le statut, le diff de travail et le diff indexé.
2. Écarter secrets, caches, artefacts, médias privés et changements hors périmètre.
3. Exécuter `scripts/test-data-tools.ps1`, puis `scripts/minipaintdex.ps1 build`.
4. Ajouter explicitement les chemins utiles ; éviter `git add .` dans un dépôt mêlant plusieurs travaux.
5. Relire le diff indexé et créer un commit atomique au message impératif.
6. Rapporter le hash, le message et les changements restant hors commit. Ne jamais pousser dans ce workflow.

## Push

Un push exige une demande explicite distincte. Il ne crée pas automatiquement de commit.

1. Inspecter branche, remote, upstream et état du dépôt.
2. Vérifier les validations attachées au commit à publier.
3. Récupérer l’état distant et arrêter en cas de divergence non résolue.
4. Pousser sans réécriture d’historique ; définir l’upstream seulement s’il manque.
5. Vérifier que les hashes local et distant correspondent.

Ne jamais utiliser `--force`, supprimer une branche ou pousser des tags sans demande explicite.
