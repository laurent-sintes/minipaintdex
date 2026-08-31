# Rafraîchissement des marques

1. Résoudre la marque dans le catalogue local. `all` signifie toutes les marques connues disposant d’un provider officiel ; signaler les autres.
2. Utiliser catalogues, pages de gamme et pages produit officiels. Déclarer `coverage.complete: true` uniquement avec une preuve d’exhaustivité.
3. Comparer identifiants, références, métadonnées, images, provenance et dates. Ignorer les seules différences de présentation.
4. Pour `technical_effect`, `primer`, `wash_shade`, `ink` et `auxiliary`, fournir un résumé, des étapes et des précautions sourcées. Une trame générique reste `review_required`.
5. Générer avec `changeset refresh-paints`. Une référence absente d’une couverture complète est conservée. Avec une couverture complète elle est retirée, jamais supprimée silencieusement.
6. `--remove-missing` n’est utilisé que sur demande explicite et preuve de disparition. L’application refuse encore la suppression d’une peinture possédée ou référencée.
7. Valider, simuler avec le CLI Java, appliquer explicitement, puis exécuter les tests Python et le build.

Ne jamais interpréter une erreur HTTP ou un catalogue incomplet comme une preuve de suppression.
