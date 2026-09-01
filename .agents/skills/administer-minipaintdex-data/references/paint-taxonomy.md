# Taxonomie fonctionnelle

Conserver le vocabulaire commercial observé séparément du profil standard :

- `range_observed` / `range_canonical` : vocabulaire commercial de la marque ;
- `profile` : caractéristiques comparables entre marques.

Les anciennes classes fonctionnelles sont uniquement des entrées d’import traduites vers plusieurs axes : `roles`, `application_methods`, `application_system`, `coverage`, `finish`, `effects`, `undercoat` et `medium`. La table ci-dessous est portée dans les fichiers versionnés `tools/minipaintdex-data/mappings/<brand>.yaml`.

| Classe fonctionnelle | Warhammer Colour | Vallejo | The Army Painter | Prince August |
|---|---|---|---|---|
| `one_coat_contrast` | Contrast | Xpress Color | Speedpaint / Speedpaint 2.0 | Xpress Color distribué par Prince August ; vérifier fabricant et référence |
| `opaque_standard` | Base, Layer | Model Color, Game Color | Warpaints Fanatic | Classic, Games |
| `wash_shade` | Shade | Wash / Game Color Wash | Warpaints Washes | Wash / Lavis |
| `ink` | — ou Technical selon produit | Game Color Ink | Warpaints Inks selon gamme | Encres |
| `metallic` | Base/Layer metallic ou gamme métallique | Metal Color, Game Color Metallic, True Metallic Metal | Warpaints Fanatic Metallics, Speedpaint Metallics | Metal Color et gammes métalliques propres |
| `airbrush` | Air | Model Air, Game Air, Mecha Color | Warpaints Air | PA-Air, Air, Games Air |
| `primer` | Spray / Undercoat | Primers, Hobby Paint | Colour Primer | XpressBase, apprêts polyuréthane |
| `technical_effect` | Technical | Special FX, Diorama FX, Weathering FX | Effects | FX, Diorama FX, pigments et patines |
| `fluorescent` | gamme ou teinte Fluo | Game Color Fluo, Premium Fluorescent | Warpaints Fanatic Effects/Fluo selon produit | Fluo |
| `auxiliary` | Medium, Varnish | Auxiliary Products | Mediums, Varnishes | Médiums, diluants, vernis |

## Marques et alias

- `Warhammer Colour` est la marque canonique actuelle.
- Alias de recherche : `Citadel`, `Citadel Colour`, `Games Workshop paint`, `GW paint`.
- `Vallejo` et `Acrylicos Vallejo` désignent la même marque canonique `Vallejo`.
- `Army Painter` et `The Army Painter` deviennent `The Army Painter`.
- `Prince Auguste` est une graphie utilisateur fréquente ; la marque canonique est `Prince August`.

## Cas Prince August / Vallejo

Ne pas décider uniquement à partir du site marchand ou du lieu d’achat.

- Les références Vallejo fréquemment rencontrées utilisent des familles numériques comme `70.xxx`, `71.xxx`, `72.xxx` ou `77.xxx`.
- Les gammes propres Prince August utilisent notamment des références `Pxxx`, `PGxxx`, `Gxxx`, `GAxxx` ou `PA-Air` selon les générations.
- Une référence ressemblant à Vallejo sur un produit déclaré Prince August produit `manufacturer_candidate: Vallejo` et `needs_review: true`. Confirmer avec le logo du flacon et une source officielle.

## Profil standard utile au raisonnement de palette

Ne pas remplacer les propriétés techniques par la seule classe fonctionnelle. Conserver si connu :

- `profile.coverage`: `opaque`, `semi_opaque`, `translucent`, `transparent`, `unknown` ;
- `profile.finish`: `matte`, `satin`, `gloss`, `unknown` ;
- `profile.application_methods`: `brush`, `airbrush`, `spray`, `marker` ;
- `profile.medium`: `water_based_acrylic`, `acrylic`, `alcohol_based`, `oil`, `enamel`, `unknown` ;
- `profile.application_system`, `profile.effects` et `profile.undercoat` ;
- `color_family` et `color_hex` ;
- `recommended_uses` ;
- `profile.undercoat.pre_highlighted_surface_recommended` pour les peintures monocouche à contraste.

Pour les produits dont le rendu dépend davantage du comportement que du RGB, utiliser aussi `application_profile` avec les propriétés connues et sourcées :

- `transparency_behavior` ;
- `pooling` ;
- `pigment_separation` ;
- `reactivation` ;
- `recommended_undercoat` ;
- `effect_type`.

Les valeurs inconnues du profil standard utilisent `unknown` quand le vocabulaire le prévoit. Toute propriété source non mappée reste dans `source_observation` et apparaît dans `mapping_report.unmapped_fields`. Ce profil aide à classer des candidats, jamais à déclarer automatiquement deux produits interchangeables.
