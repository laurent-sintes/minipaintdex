# Livraison Git

## Commit

Un commit exige une demande explicite dans le message courant.

1. Inspecter le statut, le diff de travail et le diff indexé.
2. Écarter secrets, caches, artefacts, médias privés et changements hors périmètre.
3. Ajouter explicitement les chemins utiles ; éviter `git add .` dans un dépôt mêlant plusieurs travaux. Relire le diff indexé.
4. Exécuter `scripts/minipaintdex.ps1 prepare-commit`. Cette commande réutilise une preuve `clean verify` uniquement pour le contenu exact de l’index et le contexte de validation. Sinon elle valide l’index dans une copie isolée, sans stash, sans altérer les changements exclus, ni arrêter le serveur. Ne pas lancer d’abord les tests Python : Maven les inclut déjà.
5. Créer le commit atomique avec `scripts/minipaintdex.ps1 commit -Message "Message impératif"`. Cette commande revérifie la preuve, refuse un index modifié/non validé et mesure Git ; elle ne stage rien, ne reconstruit rien et ne pousse pas. Les hooks restent actifs ; une différence entre le commit créé et l’index validé est signalée, sans rollback automatique.
6. Rapporter le hash, le message, la réutilisation ou non de la validation, les temps retournés et les changements restant hors commit. Ne jamais pousser dans ce workflow.

Un `Go` autorisant l’implémentation ne permet pas d’exécuter la commande `commit` dans le vrai dépôt. Les tests Git utilisent uniquement des dépôts temporaires de test. Les journaux et preuves sont sous `.local-build/verification/` ; voir `docs/admin/verification.md` pour les limites et le diagnostic. Ne pas confondre cette preuve avec `.local-build/server/build.json`, réservé au lancement.

## Push

Un push exige une demande explicite distincte. Il ne crée pas automatiquement de commit.

1. Inspecter branche, remote, upstream et état du dépôt.
2. Vérifier les validations attachées au commit à publier.
3. Récupérer l’état distant et arrêter en cas de divergence non résolue.
4. Pousser sans réécriture d’historique ; définir l’upstream seulement s’il manque.
5. Vérifier que les hashes local et distant correspondent.

Ne jamais utiliser `--force`, supprimer une branche ou pousser des tags sans demande explicite.
