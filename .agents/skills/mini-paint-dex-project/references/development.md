# Développement, build et tests

1. Travailler depuis la racine Git et respecter le modèle DDD décrit dans `AGENTS.md`.
2. Préserver les modifications utilisateur sans rapport avec la demande.
3. Faire porter la configuration, le binding et la validation par Spring. Le front n’accède jamais directement à `data/`.
4. Garder les cas d’usage indépendants du transport et les exposer de manière cohérente en REST et CLI.
5. Exécuter au besoin les tests Python seuls avec `scripts/test-data-tools.ps1` pendant une itération sur `tools/minipaintdex-data`.
6. Exécuter le build complet avec `scripts/minipaintdex.ps1 build`. Il vérifie les tests Python et Java, le lint, les tests et le build du frontend.
7. Diagnostiquer la première cause réelle d’un échec. Ne pas masquer un problème avec un build partiel ou un artefact obsolète.

Utiliser `scripts/minipaintdex.ps1 cli -- <arguments>` pour le CLI. Le script fournit la toolchain locale et la racine par configuration Spring.
