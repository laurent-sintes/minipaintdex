# Serveur local

Utiliser le lanceur déterministe depuis la racine, sans reconstituer manuellement le workflow :

- démarrer : `scripts/minipaintdex.ps1 start` ;
- relancer : `scripts/minipaintdex.ps1 restart` ;
- arrêter : `scripts/minipaintdex.ps1 stop` ;
- état sans mutation du serveur : `scripts/minipaintdex.ps1 status` ;
- diagnostic complet sans redémarrage : `scripts/minipaintdex.ps1 doctor`.

Le résultat est JSON. `start` conserve une instance saine à jour ; s’il trouve une instance
périmée, il demande `restart`. Les commandes de lancement attendent la readiness et testent
l’identité de l’instance/build, les API principales et la SPA. Rapporter séparément
`startupSeconds` (création du processus jusqu’à readiness) et `postStartTestSeconds`
(contrôles HTTP après readiness), puis l’URL. `checks[].elapsedMilliseconds` détaille les
endpoints. `status`/`doctor` exposent aussi le dernier lancement enregistré.

Le lanceur réutilise les classes et ressources dont les empreintes sont valides. Sinon il
prépare le serveur via Maven `process-classes`, sans tests complets ni packaging. Ne pas
précéder un simple lancement par `build`. `build` reste la validation complète `clean verify`,
à exécuter après les modifications du produit et avant livraison, serveur géré arrêté.

L’exécution est cachée, avec journaux persistants dans `.local-build/server/`. Il n’est pas
nécessaire de conserver une fenêtre ou une session PTY. Lire les chemins de logs retournés
en cas d’échec. `-TimeoutSeconds` borne l’attente de démarrage/arrêt ; `-Port` choisit le port
local. Le modèle photo reste une installation explicite, pas une étape automatique du lancement.

Un port occupé par une instance non gérée n’autorise aucun arrêt automatique. Identifier
son lancement ; utiliser sa session connue ou demander une décision si son propriétaire est
inconnu. Un échec d’arrêt gracieux ne déclenche jamais un kill forcé. Une compilation échouée
ne permet jamais de servir un ancien artefact. Une instance ayant échoué aux contrôles reste
identifiable pour `doctor`/`stop`. Si le sandbox bloque le contrôle de processus, demander
l’élévation ciblée pour la même commande, sans contournement.

Spring sert toujours lui-même la SPA en local. Le JAR autonome reste le livrable de production.
Utiliser Vite sur `5173` uniquement à la demande explicite de hot reload frontend.
