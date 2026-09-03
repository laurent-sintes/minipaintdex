# Guide utilisateur

Mini Paint Dex est une application locale pour préparer et suivre la peinture de figurines et de décors.

## Marché

Le marché contient les références partagées : peintures disponibles, produits à peindre et guides documentés. Il décrit ce qui existe indépendamment de ce que tu possèdes.

Le bandeau supérieur donne directement accès à Accueil, Peintures, Produits à peindre, Mes peintures, Mon atelier et Achats. Des séparations distinguent les références du marché de ton atelier. « À propos » rassemble les pages de documentation, sans seconde rangée permanente. Sur petit écran, les liens se répartissent sur plusieurs lignes.

### Trouver une peinture

Les filtres sont à gauche sur ordinateur, ou dans le panneau « Filtres » sur mobile. Dans « Marque et gamme », coche une marque entière ou déplie ses gammes pour choisir seulement celles qui t’intéressent.

Plusieurs choix dans un même groupe élargissent la recherche : bleu ou rouge, par exemple. Les groupes se cumulent : (bleu ou rouge) et application à l’aérographe. Tu peux aussi combiner une marque entière avec une gamme d’une autre marque.

Les compteurs indiquent les références compatibles avec les autres groupes de filtres. Les filtres actifs s’affichent au-dessus des résultats : retire-les un par un, ou réinitialise la recherche. « Plus de filtres » donne accès aux caractéristiques techniques. L’adresse conserve recherche, sélections, tri et page ; tu peux la partager ou revenir en arrière.

Chaque carte affiche marque, gamme, nom et référence sans masquer l’image. « Peinture de couleur » désigne le rôle générique `color_paint`, distinct des sous-couches, vernis, textures ou auxiliaires : ce badge peu discriminant n’apparaît plus sur les cartes. La gamme et les indications d’application aident à distinguer les variantes pinceau, aérographe ou à ombrage en une couche.

### Catalogues et anciennes collections

Une peinture garde la même identité lorsqu’elle est reconduite dans plusieurs catalogues. Sa fiche peut indiquer plusieurs « Catalogues documentés », chacun avec une source et un repère dans cette source. Une édition peut être annuelle ou porter un autre nom : aucune année n’est déduite de la date de collecte.

L’absence de catalogue documenté signifie que ce rattachement reste inconnu. Elle ne signifie ni que le pot est invalide, ni que la peinture n’est plus vendue. Les anciennes références restent utilisables dans ton inventaire.

## Mon atelier

L’atelier contient ton inventaire de peintures et tes projets de peinture. Créer un projet depuis un produit du marché instancie chaque figurine ou décor à suivre. Chaque élément avance séparément dans les étapes préparation, sous-couche, pré-éclairage, peinture, finitions puis socle.

Les commentaires, transitions et photos d’avancement alimentent le journal global de l’atelier.

### Importer les photos de mes peintures

Dépose les photos de tes pots dans `imports/workshop-paints/photos/`, puis demande leur import. Ce dossier est exclusivement destiné à l’inventaire de peintures de ton atelier, pas aux catalogues du marché ni aux photos de figurines.

Après confirmation de l’import, les photos traitées rejoignent `imports/workshop-paints/archive/<date>/<import-id>/`. Les analyses, simulations et manifestes sont conservés dans `imports/workshop-paints/runs/<import-id>/`. Une photo encore ambiguë reste dans le dépôt ; un doublon confirmé est archivé sous `duplicates/` sans augmenter les quantités.

## Peintures manquantes

Avant de créer un projet, la prévisualisation compare les peintures demandées par les guides du marché avec ton inventaire. Les substitutions personnelles appartiennent à l’atelier et ne modifient jamais la connaissance du marché.

## Jeux de données

Un dataset est un paquet portable créé depuis les références locales. Son import est d’abord simulé, puis appliqué explicitement avec le CLI d’administration.
