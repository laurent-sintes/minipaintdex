# MiniPaintDex

MiniPaintDex est une application locale de suivi de peinture de figurines. Elle suit un modèle de **self-contained system** : un seul JAR Spring Boot expose l’API REST et sert le SPA React compilé. Les données restent dans des fichiers versionnés ; aucune base de données ni installation Node globale n’est nécessaire.

## Démarrage rapide

Dans l’espace de travail actuel, le JDK portable, Maven, Node et pnpm sont déjà provisionnés localement sous `.tools` ou `target`.

```powershell
.\scripts\minipaintdex.ps1 build
.\scripts\minipaintdex.ps1 server
```

L’application complète est disponible sur [http://127.0.0.1:8080](http://127.0.0.1:8080). Arrêter le serveur avec `Ctrl+C`.

Sur une nouvelle machine, seul un JDK 25 doit être disponible via `JAVA_HOME` ou placé dans `.tools\jdk25`. Le Maven Wrapper télécharge Maven ; le build Maven télécharge ensuite les versions verrouillées de Node et pnpm.

## Build Maven

Le reactor Maven est l’unique point d’entrée du build : validation des référentiels, lint, contrôle TypeScript, build Vite, compilation Java, tests et assemblage des exécutables.

```powershell
.\scripts\minipaintdex.ps1 build
```

Équivalent Maven pour un environnement Java déjà configuré :

```powershell
.\mvnw.cmd verify
```

Artefacts produits :

- `backend/server/target/minipaintdex-server-0.2.0-SNAPSHOT.jar` : serveur REST et SPA auto-contenu ;
- `backend/cli/target/minipaintdex-cli-0.2.0-SNAPSHOT.jar` : adaptateur CLI des mêmes cas d’usage.

Les tests backend seuls se lancent avec :

```powershell
.\scripts\minipaintdex.ps1 test
```

## CLI

La CLI réutilise exactement les mêmes services applicatifs que l’API REST.

```powershell
.\scripts\minipaintdex.ps1 cli --root . --format json health
.\scripts\minipaintdex.ps1 cli --root . --format json market paints search --brand Citadel
.\scripts\minipaintdex.ps1 cli --root . --format json workshop items list --project reichbusters-reloaded
.\scripts\minipaintdex.ps1 cli --root . --format json activity list
```

Utiliser `... cli --help` pour l’arbre complet des commandes.

## Développement du SPA

Le développement conserve deux processus pour bénéficier du rechargement Vite, sans changer l’architecture d’exécution :

```powershell
.\scripts\minipaintdex.ps1 server
```

Puis, dans un second terminal après un premier build :

```powershell
.\target\frontend\node\pnpm.cmd dev
```

Vite sert alors le SPA sur `http://127.0.0.1:5173` et transmet `/api` et `/media` à Spring Boot sur le port `8080`.

## Architecture

Le backend est un monolithe modulaire Maven :

- `backend/domain` : modèle d’événements, workflow et projection des objets physiques ;
- `backend/application` : cas d’usage indépendants des transports et du stockage ;
- `backend/adapter-file` : lecture YAML et journal JSONL append-only ;
- `backend/server` : adaptateur REST Spring MVC et hébergement du SPA ;
- `backend/cli` : adaptateur Picocli des mêmes cas d’usage ;
- `spa` et `components` : interface React/Vite consommant uniquement l’API REST.

Le navigateur n’écrit jamais dans `data`. Toute mutation passe par un service applicatif, exposé en REST et en CLI, puis produit un événement dans le ledger global.

Les décisions détaillées et règles destinées aux agents sont consignées dans `AGENTS.md`.

## Référentiels

Le cœur des données et les noms de champs restent en anglais pour éviter les ambiguïtés d’encodage :

```text
data/
  site/                  libellés et configuration de présentation
  market/
    paints/              catalogue des peintures du marché
    games/               jeux et éléments de catalogue
  workshop/
    paints.yaml          identifiants et quantités possédés
    recipes/             fiches et recettes de peinture
    shopping.yaml        achats envisagés
  ledger/events/         journal métier global JSONL append-only
```

L’import actuel contient 47 peintures possédées, 59 types d’éléments Reichbusters Reloaded et 198 objets physiques suivis individuellement. Le workflow d’un objet couvre `preparation`, `priming`, `pre_highlight`, `painting`, `finishing` et `basing`.

Cette séparation permet de remplacer plus tard l’adaptateur fichier par une base de données sans modifier le domaine, les cas d’usage, l’API ou le SPA.

## API REST

La base de l’API est `/api/v1`. Les principaux services couvrent :

- santé et bootstrap du SPA ;
- recherche du marché par texte, type, couleur, marque, gamme, fini, volume et tags ;
- consultation des jeux, projets et objets physiques ;
- ajout d’un objet et transitions du workflow ;
- lecture du ledger et reconstruction des projections ;
- exports CSV et YAML.

## Skills du dépôt

Les skills dans `.agents/skills` automatisent les opérations persistantes :

- `import-miniature-paints` identifie et fusionne les pots photographiés ;
- `import-miniature-project` importe un jeu et ses références traçables ;
- `commit` crée un commit Git atomique ;
- `push` vérifie et pousse la branche courante.

Toute image enregistrée doit conserver sa source, son crédit et sa licence. Un aperçu numérique de couleur ne doit pas être présenté comme un rendu réel peint.
