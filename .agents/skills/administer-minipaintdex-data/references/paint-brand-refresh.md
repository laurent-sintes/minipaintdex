# Rafraîchissement des marques

1. Résoudre la marque dans le catalogue local. `all` signifie toutes les marques connues disposant d’un provider officiel ; signaler les autres.
2. Utiliser catalogues, pages de gamme et pages produit officiels. Déclarer `coverage.complete: true` uniquement avec une preuve d’exhaustivité.
3. Comparer identifiants, références, métadonnées, images, provenance et dates. Ignorer les seules différences de présentation. Conserver la charge source sémantique complète dans `source_snapshots` ; exclure explicitement les horodatages fabriqués à l'heure de la requête, et faire porter la recherche uniquement sur le profil canonique.
4. Pour `technical_effect`, `primer`, `wash_shade`, `ink` et `auxiliary`, fournir un résumé, des étapes et des précautions sourcées. Une trame générique reste `review_required`.
5. Générer avec `changeset refresh-paints`. Une référence absente d’une couverture complète est conservée. Avec une couverture complète elle est retirée, jamais supprimée silencieusement.
6. `--remove-missing` n’est utilisé que sur demande explicite et preuve de disparition. L’application refuse encore la suppression d’une peinture possédée ou référencée.
7. Écrire un journal structuré avec `--audit-log` pendant la collecte et la génération du change set. Vérifier les volumes par marque, la couverture, les champs modifiés et la provenance avant application.
8. Valider, simuler avec le CLI Java, appliquer explicitement, puis exécuter les tests Python et le build.
9. Rejouer la même collecte contre le catalogue appliqué : le change set doit contenir zéro opération.
10. Auditer les visuels avec `assets audit --min-width 300 --min-height 300` et distinguer `local`, `remote_only`, `too_small` et `missing`.

## Cache des images fabricant

1. Les collecteurs de marque restent séparés sous `official_sources/`; ne pas remettre de logique fournisseur dans l'orchestrateur.
2. Lancer `assets cache-paint-images` avec un journal et un change set de sortie. Le programme n'accepte que les hôtes HTTPS officiels configurés, contrôle les redirections, le poids, les dimensions et le contenu, puis produit du WebP ou conserve un SVG sanitisé.
3. Valider et simuler le change set avec `minipaintdex market paints apply --dry-run`, puis l'appliquer par le même cas d'usage Java.
4. Pour un site protégé comme Vallejo, observer les cartes produits dans le navigateur normal et exporter un manifeste exact `reference`, `name`, `page_url`, `image_url`. Ne pas contourner le challenge et ne pas déduire les URL.
5. Transformer ce manifeste avec `assets import-paint-image-sources`. `--allow-unmatched` conserve dans le change set la liste des nouvelles références officielles absentes du catalogue au lieu de bloquer les correspondances exactes.
6. Relancer le cache puis `assets audit`. Une image locale hors cache n'est conservée que lorsqu'aucune source officielle validée ne permet de la remplacer.

Ne jamais interpréter une erreur HTTP ou un catalogue incomplet comme une preuve de suppression.
Pour Warhammer Colour, contrôler l'exhaustivité de l'index officiel de la boutique : le nombre de résultats annoncé doit être égal au nombre de fiches effectivement collectées et toute chute brutale de volume doit bloquer l'application.
