# Taxonomie fonctionnelle

Conserver deux axes distincts :

- `range_observed` / `range_canonical` : vocabulaire commercial de la marque ;
- `functional_class` : rôle comparable entre marques.

Une classe fonctionnelle facilite les recherches et les propositions de palette. Elle ne garantit jamais une teinte ou un comportement identique.

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

## Champs utiles au raisonnement de palette

Ne pas remplacer les propriétés techniques par la seule classe fonctionnelle. Conserver si connu :

- `opacity`: opaque, semi-opaque, transparent ;
- `finish`: mat, satiné, brillant, métallique ;
- `application_method`: pinceau, aérographe, bombe, marqueur ;
- `medium`: acrylique à l’eau, huile, émail, etc. ;
- `color_family` et `color_hex` ;
- `recommended_uses` ;
- `requires_light_primer` pour les peintures monocouche à contraste.
