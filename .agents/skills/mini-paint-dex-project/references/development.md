# Développement, build et tests

1. Travailler depuis la racine Git et respecter le modèle DDD décrit dans `AGENTS.md`.
2. Préserver les modifications utilisateur sans rapport avec la demande.
3. Faire porter la configuration, le binding et la validation par Spring. Le front n’accède jamais directement à `data/`.
4. Garder les cas d’usage indépendants du transport et les exposer de manière cohérente en REST et CLI.
5. Exécuter au besoin les tests Python seuls avec `scripts/test-data-tools.ps1` pendant une itération sur `tools/minipaintdex-data`.
6. À la fin des modifications, exécuter `scripts/minipaintdex.ps1 verify` : il réutilise une preuve complète exacte ou lance `clean verify` (Python, Java, lanceurs, lint, tests et build frontend). Sur un cache valide, le serveur reste intact ; sinon le lanceur arrête l’instance gérée et la restaure après succès. Un échec laisse le serveur arrêté et conserve les logs. `verification-status` inspecte la preuve sans build ni contrôle du serveur. `build` reste un rebuild forcé, serveur préalablement arrêté. Pour un commit partiel, suivre `git-delivery.md` plutôt que valider le mauvais contenu. Un simple lancement/redémarrage suit `local-server.md`.
7. Diagnostiquer la première cause réelle d’un échec. Ne pas masquer un problème avec un build partiel ou un artefact obsolète.

Utiliser `scripts/minipaintdex.ps1 cli -- <arguments>` pour le CLI. Le script fournit la toolchain locale et la racine par configuration Spring.
