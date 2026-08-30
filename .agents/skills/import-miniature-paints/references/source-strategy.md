# Stratégie de recherche et de provenance

Lire ce document uniquement après avoir extrait une identité candidate depuis la photo.

## Ordre des sources

1. Page produit officielle correspondant exactement au nom et, si possible, à la référence.
2. Catalogue ou nuancier officiel récent de la marque.
3. Article technique officiel du fabricant pour les propriétés de gamme et les usages.
4. Revendeur identifiable uniquement pour compléter un packshot indisponible sur le site officiel ou confirmer une donnée non critique. Enregistrer son URL et son nom dans le crédit.

Ne pas utiliser une fiche revendeur comme preuve d’une propriété qui contredit le fabricant. Ne jamais dériver plusieurs URL à partir d’un motif supposé : rechercher et vérifier chaque résultat.

## Sources de départ par marque

- Warhammer Colour : `warhammer.com`, `paint.warhammer.com`, `warhammer-community.com`.
- Vallejo : `acrylicosvallejo.com`, ses pages de gamme et catalogues PDF.
- The Army Painter : `thearmypainter.com`, pages de gamme et pages produit.
- Prince August : `prince-august.net`. Vérifier si la page présente une gamme propre Prince August ou une gamme Prince August–Vallejo.

## Chemin éprouvé pour un catalogue difficile

1. Ouvrir une fiche produit officielle dans un navigateur interactif et relever le titre, le volume, les propriétés de gamme et l’URL canonique.
2. Inspecter le visuel réellement rendu. Certains catalogues renvoient un nuancier à la place d’un packshot ; ne pas l’étiqueter comme photo du pot.
3. Si le site déclenche une vérification humaine, s’arrêter. Ne pas la contourner et ne pas automatiser une série de variantes d’URL.
4. Rechercher le nom exact, la gamme et la référence dans les résultats d’images. Choisir un packshot net et frontal provenant d’un revendeur identifiable.
5. Télécharger une copie locale, vérifier visuellement le nom sur l’étiquette et enregistrer : URL du visuel, page source, hôte, date de vérification et mention `packshot distribué par revendeur`.
6. Garder séparément `manufacturer_url` et `image_source_url`.

## Changement Citadel → Warhammer Colour

Le changement de nom annoncé en 2026 conserve les peintures et les catégories existantes, dont Contrast. Stocker :

- `brand_observed`: ce qui est imprimé sur le pot photographié ;
- `brand_canonical`: `Warhammer Colour` ;
- `brand_aliases`: inclure `Citadel Colour` et `Citadel` pour la recherche ;
- `manufacturer`: `Games Workshop`.

## Contrôle final

Une fiche enrichie doit permettre de répondre oui à ces questions :

- Le produit montré correspond-il exactement au nom et à la gamme observés ?
- La référence est-elle lue ou sourcée, jamais devinée ?
- La fonction commune est-elle distincte de la gamme commerciale ?
- L’URL officielle et la provenance du visuel sont-elles séparées ?
- Une incertitude visible est-elle conservée dans `warnings` et `status` ?
