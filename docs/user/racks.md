# Ranger tes peintures dans des racks

Dans **Racks du marché**, choisis un modèle, indique combien d’exemplaires tu possèdes
et leur emplacement. Chaque exemplaire devient un rack distinct dans **Mes racks**.
La création de racks personnalisés n’est pas proposée à ce jour.

Les fiches du marché regroupent photos sourcées, rangées et caractéristiques de
stockage. Les types de contenants sont un référentiel interne : tu n’as rien à
choisir ni à configurer dans l’interface. Chaque peinture du marché référence son
conditionnement, collecté lors du scraping de la marque. Une dimension inconnue
reste inconnue ; une dimension estimée n’est pas une mesure certifiée.

## Proposer puis confirmer

Depuis **Mes racks**, répartis tes pots entre tous tes racks, ou ouvre un rack pour
ne remplir que celui-ci. Choisis un regroupement par **marque/gamme**, **couleur** ou
**usage**. Les dimensions et les emplacements disponibles limitent le rangement.
Autorise explicitement les estimations pour utiliser des dimensions provisoires
ou des capacités relatives. Les pots sans géométrie exploitable restent non placés.

**Conserver les placements existants** évite de déplacer les pots déjà rangés.
Un pot verrouillé reste toujours à sa place lors d’une réorganisation.

La proposition ne modifie rien. Elle indique les placements et les pots sans place.
Vérifie-la, range physiquement tes pots, puis clique **Confirmer après rangement
physique**. Si ton atelier ou le référentiel a changé, génère une nouvelle proposition.
Tu peux verrouiller, déverrouiller ou retirer un pot depuis sa rangée.
Un pot vide ou sec encore possédé occupe toujours de la place.

## Ton rack en photo

Tes observations sont conservées : quatre rangées, chacune contenant 14 pots
standards ou 11 Citadel, sans problème de hauteur pour les formats discutés.
Sa marque et sa référence restent à identifier pour le relier à un modèle commercial.
Nous ne créons pas une fausse fiche de marché pour contourner l’absence de racks custom.

## Administration

Le skill `scrape-market-racks` alimente les modèles du marché avec provenance et photos.
Le skill `scrape-market-paints` collecte aussi les conditionnements. Les commandes
REST et CLI partagent les mêmes validations ; aucune édition de référentiel n’est
exposée dans ces écrans.

Le CLI propose `market rack-products search|show|save`,
`workshop racks list|show|add|save` et
`workshop paint-storage pots|preview|confirm|identify-container|set-placement`.
Les mutations structurées prennent `--input fichier.json`, une clé d’idempotence
et un identifiant de corrélation. L’option globale `--wait` attend explicitement
la confirmation du journal après acceptation durable.
