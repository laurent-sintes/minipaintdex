# Reconstruction du référentiel des peintures du marché

Cette procédure est le chemin canonique appris lors de la constitution et de la fiabilisation du catalogue. Elle s'applique quand le catalogue doit être reconstruit depuis zéro ou quand il faut démontrer qu'un refresh est reproductible.

## Définition du résultat

Une reconstruction est réussie seulement si elle fournit :

- un fichier version 1 par marque dans `data/market/paints/` ;
- les mêmes identités `<brand-code>-<référence-fabricant-normalisée>` pour les références déjà connues ;
- toutes les observations source conservées dans `source_snapshots` ;
- les profils normalisés sans perte des attributs propres aux marques ;
- les meilleures images disponibles, leur niveau de qualité et la raison structurée d'une qualité non optimale ;
- une couleur numérique approximative pour le filtrage lorsque le produit porte une teinte, ou la famille spéciale `auxiliary` lorsqu'il s'agit d'un auxiliaire ;
- un audit final et un second passage sans opération.

Restaurer les YAML avec Git n'est pas une reconstruction par scraping. Le test recherché consiste à repartir d'un catalogue vide contrôlé et à recréer le résultat à partir des collecteurs et des entrées versionnées.

## 1. Protéger la référence connue

Avant tout essai, relever le commit, exporter un dataset portable et conserver les quatre fichiers canoniques. Ne jamais effacer directement `data/market/paints/` dans le répertoire de travail principal. Faire l'exercice dans un emplacement isolé configuré par Spring Boot.

La référence constatée le 2 septembre 2026 est un garde-fou, pas un quota éternel :

| Marque | Peintures |
| --- | ---: |
| Prince August | 196 |
| The Army Painter | 526 |
| Vallejo | 966 |
| Warhammer Colour | 331 |
| Total | 2 019 |

À cette date, les images sont qualifiées ainsi : 1 668 photos officielles, 300 photos revendeur, 41 visuels génériques et 10 nuanciers. Toute différence doit être expliquée par l'audit avant application ; elle ne doit jamais être acceptée parce qu'un endpoint distant a simplement répondu sans erreur.

## 2. Figer les entrées reproductibles

Utiliser les mappings version 1 sous `tools/minipaintdex-data/mappings/`. Chaque mapping déclare le `brand_code`, les correspondances vers le profil canonique et les attributs source à conserver.

Épingler chaque entrée externe : URL, date d'observation, checksum du document ou révision Git complète, licence lorsque nécessaire. Conserver dans le dépôt les manifestes revus, mais pas les téléchargements temporaires ni le cache d'images.

Entrées actuellement nécessaires :

- les endpoints et pages officiels utilisés par les collecteurs Prince August, Army Painter et Warhammer ;
- le catalogue PDF officiel Vallejo fourni par `--vallejo-pdf` ;
- le manifeste d'images revendeur `tools/minipaintdex-data/image-sources/warhammer-colour.json` ;
- le manifeste couleur `tools/minipaintdex-data/color-sources/paintdex.json` et la révision Paintdex qu'il épingle ;
- les cinq manifestes de compléments couleur officiels listés à l'étape 6, avec leurs fichiers de teintes extraites versionnés dans le même répertoire ;
- tout manifeste revendeur ou correction revue requis pour reproduire une donnée qui ne vient pas d'une source officielle accessible.

Si une entrée n'est pas versionnée ou téléchargeable de manière déterministe, la reconstruction exacte n'est pas autonome. Le signaler dans l'audit au lieu de réutiliser silencieusement une valeur du catalogue final.

## 3. Collecter marque par marque

Lancer d'abord chaque marque séparément. Cela localise les régressions et empêche qu'une source défaillante contamine un refresh global.

```powershell
python tools/minipaintdex-data/mpdx_data.py catalog refresh-official-paints `
  --brand "<marque>" `
  --catalog "<catalogue-isolé>" `
  --output "<run>/<marque>-changeset.json" `
  --audit-log "<run>/<marque>-audit.json"
```

Ajouter `--vallejo-pdf <catalogue.pdf>` pour Vallejo. Ne jamais utiliser `--remove-missing` pendant une reconstruction ordinaire.

Règles apprises par fournisseur :

- **Warhammer Colour** : comparer le nombre annoncé par l'index officiel au nombre de fiches réellement collectées. Les visuels de couleur officiels ne sont pas des photos de pots ; utiliser ensuite les photos revendeur revues. Une chute vers quelques dizaines de références bloque le run.
- **The Army Painter** : supprimer des snapshots les horodatages Shopify fabriqués à la requête. Distinguer les formules Speedpaint courantes des formules historiques ; une collision de noms ne doit pas choisir arbitrairement une teinte.
- **Vallejo** : utiliser le PDF officiel pour l'exhaustivité et les références. Quand Cloudflare protège les cartes produit, les observer dans le navigateur normal et enregistrer les URL exactes dans un manifeste ; ne jamais contourner le challenge ni inventer les URL.
- **Prince August** : conserver la référence Prince August comme identité. La correspondance vers Vallejo sert à l'enrichissement, jamais à remplacer l'identité. Sélectionner la photo du pot et rejeter le simple aplat de couleur.

## 4. Auditer puis appliquer les données structurées

Pour chaque marque, vérifier au minimum : volume, doublons d'identité, références absentes, attributs source non mappés, changements de gamme, produits techniques, images et provenance. Un attribut non représenté par le modèle standard reste dans l'observation source et doit apparaître dans le rapport de mapping.

Simuler avec le cas d'usage Java, lire le résultat JSON, puis seulement appliquer :

```powershell
.\scripts\minipaintdex.ps1 cli --root . --format json market paints apply --input "<changeset>"
.\scripts\minipaintdex.ps1 cli --root . --format json market paints apply --input "<changeset>" --apply
```

Appliquer les marques une par une. Ne lancer `--brand all` qu'après leur réussite individuelle.

## 5. Reconstituer les images sans régression

Commencer par estimer les candidates, puis télécharger et qualifier les sources. Le cache `media/market/paints/` est généré et ignoré par Git ; sa reproductibilité dépend donc des URL et décisions stockées dans les catalogues ou manifestes.

Ordre de préférence obligatoire : `official_photo`, `retailer_photo`, `owned_photo`, `generic_visual`, `color_swatch`, `none`.

Pour chaque source non officielle, conserver crédit, page de référence, URL HTTPS et `quality_limitation`. Rejeter comme photo de produit les aplats, silhouettes synthétiques, damiers et images trop petites. Un remplacement à qualité égale exige `reviewed_replacement: true` dans un manifeste exact.

Après chaque lot : générer le change set d'images, simuler, appliquer, puis exécuter `assets audit`. Une image rejetée reste une preuve de tentative dans la provenance avec la raison technique du rejet.

## 6. Compléter les couleurs destinées au filtre

Requalifier d'abord les produits fonctionnels : médiums, vernis, retardateurs, mastics et masques liquides prennent la famille `auxiliary` sans faux code hexadécimal.

Exécuter ensuite `catalog enrich-paint-colors` avec une source épinglée et vérifiée par checksum. Les correspondances automatiques admises sont exactes : référence fabricant, référence Prince August transposée de façon revue, ou couple nom/gamme avec alias explicite. Préférer la formule courante lorsqu'une gamme possède plusieurs générations documentées.

Ne jamais écraser une couleur existante en cas de désaccord. Journaliser le conflit. Les couleurs extraites d'un nuancier numérique sont approximatives et servent au regroupement par teinte, pas à une mesure de peinture sèche.

Les nuanciers officiels sont prioritaires pour les références encore absentes. Toute extraction doit conserver le document, son checksum, la page, la méthode d'échantillonnage et un niveau de confiance. Les pigments, métalliques et effets nécessitent une estimation représentative plutôt qu'un pixel de reflet ou de fond blanc.

Le chemin validé le 2 septembre 2026 complète les couleurs dans cet ordre, après la collecte structurée. Tous les manifestes sont sous `tools/minipaintdex-data/color-sources/` :

| Ordre | Manifeste | Source root | Complément appris |
| --- | --- | --- | --- |
| 1 | `paintdex.json` | `data/paints/` du checkout Paintdex épinglé | Correspondances exactes et alias revus, dont les générations Speedpaint |
| 2 | `vallejo-catalog-2026.json` | `tools/minipaintdex-data/color-sources/` | 174 teintes du catalogue officiel Vallejo 2026 ; 70.540 Matt Base est un médium, pas une teinte blanche |
| 3 | `army-painter-historical-2026.json` | idem | 54 teintes du tableau officiel Historical/Fanatic |
| 4 | `army-painter-official-products.json` | idem | 5 GameMaster et 2 Speedpaint John Blanche, échantillons d'étiquettes officielles |
| 5 | `prince-august-vallejo-model-color-rev18.json` | idem | P733, P737 et P951 via les références Model Color historiques revues |
| 6 | `warhammer-official-gradients.json` | idem | 26 cartes couleur officielles Warhammer, sans utiliser les photos revendeur |

Pour chaque ligne, appeler `catalog enrich-paint-colors --manifest <manifeste> --source-root <source-root> --catalog <catalogue> --output <change-set> --audit-log <audit>`, puis simuler et appliquer par le cas d'usage Java. Les valeurs déjà présentes restent prioritaires ; un désaccord est audité, jamais écrasé.

Les fichiers `*-colors.json` sont des observations revues, épinglées par SHA-256 dans leur manifeste : ils permettent de rejouer les teintes sans ré-extraire les PDF ni dépendre du cache média. Les cartes Warhammer sont des nuanciers acceptables pour extraire une teinte, mais restent interdites comme photos de pots. Pour les gradients, conserver la méthode de médiane d'une zone interne excluant bord blanc et silhouette ; pour le spray White Scar, le remplissage SVG officiel est blanc.

Résultat constaté après cette séquence : 1 961 références avec un hexadécimal et 58 auxiliaires sélectionnables, soit 2 019/2 019 références couvertes. Le second passage de chaque manifeste doit rester sans opération. Cette couverture ne prouve pas une précision colorimétrique uniforme : les nuanciers numériques, peintures transparentes, effets et métalliques restent approximatifs.

## 7. Contrôler le résultat

Le run final doit vérifier :

1. le nombre total et le détail par marque, gamme et rôle ;
2. l'absence de doublon d'ID et de référence au sein d'une marque ;
3. les références sans couleur et la séparation explicite des auxiliaires ;
4. la répartition des qualités d'image et chaque raison de qualité non optimale ;
5. les fichiers média manquants, trop petits, en damier ou assimilables à un aplat ;
6. les attributs source non mappés ou perdus ;
7. la validité de chaque fichier version 1 ;
8. le build complet avec `.\scripts\minipaintdex.ps1 build`.

Rejouer ensuite collecte, enrichissement couleur et traitement d'images contre le catalogue reconstruit. Tous les change sets doivent contenir zéro opération, hors variation distante explicitement auditée. C'est le critère d'idempotence.

## État de reproductibilité connu

Le chemin est désormais enregistré, mais l'automatisation complète reste à construire. Les collecteurs, mappings, contrôles, exceptions Warhammer, manifeste Paintdex et compléments couleur officiels sont versionnés. Les compléments couleur ne dépendent plus de fichiers temporaires. Le téléchargement du PDF Vallejo, la récupération de la révision Paintdex et certains manifestes revendeur ne sont pas encore orchestrés par une commande unique. Tant que ces points subsistent, ne jamais affirmer qu'une suppression complète peut être reconstruite exactement en un seul appel.
