# MiniPaintDex

MiniPaintDex est un atelier local pour inventorier des peintures de figurines et préparer des recettes de peinture jeu par jeu. Le site est généré à partir de fichiers YAML versionnés : aucune base de données ni service distant n’est nécessaire pour le développement courant.

## Architecture des données

Les fichiers sont les sources de vérité :

- `data/peintures.yaml` contient les pots, leurs références, couleurs, usages, sources et quantités ;
- `data/projects/*.yaml` contient les jeux, les figurines, les références peintes, les peintures requises et les modes opératoires ;
- `lib/catalog.ts` adapte ces fichiers au modèle TypeScript utilisé par le site ;
- `/paints/<id>`, `/projects/<id>` et `/projects/<id>/<figurine>` sont des pages dynamiques issues de ces référentiels.

Cette séparation permettra de remplacer plus tard l’adaptateur fichier par un adaptateur de base de données sans réécrire l’interface.

## Prérequis

- Node.js 24 recommandé, Node.js 22.13 minimum ;
- pnpm 11.19.0.

Le gestionnaire et sa version sont verrouillés par le champ `packageManager` de `package.json`.

## Installation

```powershell
corepack enable
pnpm install --frozen-lockfile
```

## Serveur local

```powershell
pnpm dev
```

Le site est ensuite disponible sur [http://127.0.0.1:5173](http://127.0.0.1:5173).

## Validation et build

```powershell
pnpm check
```

Cette commande valide les deux référentiels YAML, exécute le lint, contrôle les types TypeScript et produit le build Vite. Le même cycle est lancé par GitHub Actions à chaque push et pull request.

Commandes unitaires :

```powershell
pnpm validate:catalogs
pnpm lint
pnpm typecheck
pnpm build
```

## Ajouter des données

Les imports persistants sont réalisés avec les skills du dépôt dans `.agents/skills` :

- `import-miniature-paints` analyse des photos de pots, normalise et fusionne `data/peintures.yaml` ;
- `import-miniature-project` prend le nom d’un jeu, recherche son inventaire et des images peintes traçables, puis crée les fiches dans `data/projects` ;
- `commit` prépare et crée un commit Git atomique ;
- `push` vérifie et pousse les commits existants sans réécrire l’historique.

L’ajout manuel depuis l’interface est volontairement une prévisualisation de session. Il ne modifie pas les fichiers : cela évite qu’un navigateur local devienne une seconde source de vérité.

## Règles de contribution

- conserver des identifiants stables en minuscules ASCII avec des tirets ;
- enregistrer la provenance et le crédit de chaque image ;
- ne pas publier d’image sans droit d’usage clair ;
- marquer `pending_import: true` une peinture de projet absente du référentiel ;
- exécuter `pnpm check` avant tout commit.
