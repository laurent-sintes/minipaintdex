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
10. Estimer d'abord les visuels à rechallenger avec `assets plan-paint-image-refresh`; ce dry run sélectionne toutes les qualités 2 à 6 ainsi que les photos officielles dont `quality_verified_at` date d'au moins 365 jours. Auditer ensuite les visuels avec `assets audit --min-width 300 --min-height 300` et distinguer `local`, `remote_only`, `too_small`, les classifications de visuels rejetés et `missing`. Pour toute qualité autre que `official_photo`, renseigner `quality_limitation` avec un code contrôlé, un détail opérateur et la date d'observation ; une tentative officielle rejetée doit conserver son motif technique exact.
11. Générer les nouvelles identités avec le `brand_code` déclaré dans le mapping et la référence fabricant normalisée. Ne jamais inclure la gamme ou le nom dans un nouvel ID et ne jamais recalculer l'ID d'une peinture déjà reconnue.

## Cache des images fabricant

1. Les collecteurs de marque restent séparés sous `official_sources/`; ne pas remettre de logique fournisseur dans l'orchestrateur.
2. Lancer `assets cache-paint-images` avec un journal et un change set de sortie. Le programme qualifie la provenance, contrôle les redirections, le poids, les dimensions, les aplats, les damiers et le niveau de détail, puis produit du WebP sur un canevas carré homogène ou conserve un SVG sanitisé. Utiliser `--normalize-local` pour remettre également les rasters déjà en cache au format de présentation courant sans retéléchargement. Les manifestes revendeur doivent déclarer `image_quality: retailer_photo`, un crédit, des URL HTTPS traçables et la raison structurée qui explique l'absence d'une photo officielle.
3. Valider et simuler le change set avec `minipaintdex market paints apply --input <change-set>`, puis l'appliquer explicitement avec `--apply` par le même cas d'usage Java.
4. Pour un site protégé comme Vallejo, observer les cartes produits dans le navigateur normal et exporter un manifeste exact `reference`, `name`, `page_url`, `image_url`. Ne pas contourner le challenge et ne pas déduire les URL.
5. Transformer ce manifeste avec `assets import-paint-image-sources`. `--allow-unmatched` conserve dans le change set la liste des nouvelles références officielles absentes du catalogue au lieu de bloquer les correspondances exactes.
6. Relancer le cache puis `assets audit`. Une image locale hors cache n'est conservée que lorsqu'aucune source officielle validée ne permet de la remplacer.
7. Appliquer l'ordre canonique sans régression : `official_photo`, `retailer_photo`, `owned_photo`, `generic_visual`, `color_swatch`, `none`. Un nuancier, un aplat ou un damier reste une preuve dans `source_snapshots`, mais ne doit pas être présenté comme une photo produit.
8. Un audit visuel revu peut déclarer dans le manifeste `quality_overrides` avec la référence fabricant comme clé et `generic_visual` ou `color_swatch` comme valeur. Cet override corrige la qualification du visuel existant sans perdre son fichier, son URL, son crédit ni sa provenance. Lorsqu'un meilleur visuel de même rang remplace explicitement une source erronée, marquer uniquement cet item `reviewed_replacement: true`; cette option ne peut jamais dégrader le rang de qualité.

Ne jamais interpréter une erreur HTTP ou un catalogue incomplet comme une preuve de suppression.
Pour Warhammer Colour, contrôler l'exhaustivité de l'index officiel de la boutique : le nombre de résultats annoncé doit être égal au nombre de fiches effectivement collectées et toute chute brutale de volume doit bloquer l'application.

Le chemin opérateur recommandé est `catalog refresh-official-paints --brand <marque|all> --output <change-set> --audit-log <audit>`. Il orchestre la collecte, la comparaison, la validation et l'estimation des images à rechallenger sans écrire dans `data/`. Le change set obtenu est ensuite simulé par `minipaintdex market paints apply --input <change-set>` et appliqué uniquement avec `--apply` après lecture de l'audit.
