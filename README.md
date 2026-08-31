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
.\mvnw.cmd clean verify
```

Artefacts produits :

- `backend/server/target/minipaintdex-server-0.2.0-SNAPSHOT.jar` : serveur REST et SPA auto-contenu ;
- `backend/cli/target/minipaintdex-cli-0.2.0-SNAPSHOT.jar` : adaptateur CLI des mêmes cas d’usage.

Les tests backend seuls se lancent avec :

```powershell
.\scripts\minipaintdex.ps1 test
```

Le build complet exécute également les tests du frontend, les validations des référentiels et la suite des outils de données Python. Cette dernière reste lançable isolément :

```powershell
.\scripts\test-data-tools.ps1
```

## CLI

La CLI réutilise exactement les mêmes services applicatifs que l’API REST.

```powershell
.\scripts\minipaintdex.ps1 cli --root . --format json health
.\scripts\minipaintdex.ps1 cli --root . --format json market paints search --brand "Warhammer Colour"
.\scripts\minipaintdex.ps1 cli --root . --format json market paints apply --input imports/runs/paint-refresh/changeset.json --dry-run
.\scripts\minipaintdex.ps1 cli --root . --format json market paintable-products apply --input imports/runs/product-import/changeset.json --dry-run
.\scripts\minipaintdex.ps1 cli --root . --format json market paintable-products preview-import --product reichbusters-reloaded
.\scripts\minipaintdex.ps1 cli --root . --format json workshop painting-projects create --product reichbusters-reloaded --project-id paint-reichbusters
.\scripts\minipaintdex.ps1 cli --root . --format json market guides reconcile --guide reichbusters-reloaded-red-hawk-guide
.\scripts\minipaintdex.ps1 cli --root . --format json workshop items list --project paint-reichbusters
.\scripts\minipaintdex.ps1 cli --root . --format json datasets import --input datasets/workshop/painting-projects/reichbusters
.\scripts\minipaintdex.ps1 cli --root . --format json workshop recipes list --catalog-item reichbusters-reloaded-red-hawk
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
Push-Location .\frontend
..\target\toolchain\node\pnpm.cmd dev
```

Vite sert alors le SPA sur `http://127.0.0.1:5173` et transmet `/api` et `/media` à Spring Boot sur le port `8080`.

## Architecture

Le backend est un monolithe modulaire Maven :

- `backend/domain` : agrégats `PaintableProduct`, `Workshop`, `PaintingProject`, objets physiques, recettes, workflow et événements ;
- `backend/application` : cas d’usage indépendants des transports et du stockage ;
- `backend/adapter-file` : lecture YAML et journal JSONL append-only ;
- `backend/bootstrap` : configuration Spring typée et assemblage commun des dépendances ;
- `backend/server` : adaptateur REST Spring MVC et hébergement du SPA ;
- `backend/cli` : adaptateur Picocli des mêmes cas d’usage ;
- `frontend` : package React/Vite autonome, avec ses sources, ses assets et sa configuration ;
- `tools/minipaintdex-data` : traitements déterministes partagés par les skills d’import et de rafraîchissement.
- `datasets` : paquets portables nommés, distincts du stockage actif ;
- `docs` : documentation utilisateur et administrateur embarquée dans le JAR.

Le navigateur n’écrit jamais dans `data`. Toute mutation passe par un service applicatif exposé en REST et en CLI. Les activités de l’atelier et de la liste d’achats produisent des événements dans le ledger global ; les référentiels de marché restent des fichiers versionnés appliqués par change set.

Le modèle DDD canonique, ses invariants, les décisions détaillées et les règles destinées aux agents sont consignés dans `AGENTS.md`.

## Configuration Spring Boot

Les valeurs techniques par défaut sont centralisées dans `config/application.yaml`. Spring Boot charge ce fichier dans le serveur REST comme dans la CLI, puis applique ses mécanismes standards de surcharge : fichier externe, variable d’environnement, propriété système ou argument de ligne de commande.

La configuration typée `minipaintdex` couvre notamment :

- la racine du dépôt et chaque emplacement du stockage fichier ;
- le répertoire des médias et les origines autorisées en développement ;
- les types comportementaux, limites, scores, seuils et poids du moteur de rapprochement.

Les propriétés sont validées au démarrage. Chaque jeu de poids du matcher doit être positif ou nul et totaliser exactement `1.0`. Par exemple, une surcharge locale peut être passée sans modifier le code :

```powershell
.\scripts\minipaintdex.ps1 server --minipaintdex.paint-matching.candidate-limit=8
```

Spring résout ces valeurs et construit des objets Java typés. Le domaine et les services applicatifs restent indépendants de Spring ; l’adaptateur fichier ne contient plus de chemins relatifs codés en dur.

## Référentiels

Le cœur des données et les noms de champs restent en anglais pour éviter les ambiguïtés d’encodage :

```text
data/
  site/                  libellés et configuration de présentation
  market/
    paints/              catalogue des peintures du marché
    paintable-products/  boîtes, gammes et éléments de catalogue à peindre
    painting-guides/     guides publics sourcés et versionnés
  workshop/
    paints.yaml          identifiants et quantités possédés
    shopping.yaml        intentions d’achat personnelles par identifiant de peinture
  ledger/events/         journal métier global JSONL append-only
```

Un `PaintableProduct` est l’agrégat du marché pour une boîte, une extension ou une gamme contenant des éléments à peindre. Ses quantités décrivent le contenu théorique. `Workshop` est le contexte personnel durable. Un `PaintingProject` porte l’intention de peindre un produit et crée un `WorkshopItem` par exemplaire physique. Un guide de peinture du marché contient la palette et la méthode publiées ou inférées à partir d’une référence traçable. Une recette d’atelier est un autre agrégat : elle versionne les substitutions, mélanges, couches et techniques réellement choisies par le propriétaire, puis peut être affectée à un objet physique précis. Son cycle `draft → validated → active → superseded/archived` est conservé dans le ledger.

Le rapprochement d’un guide avec l’atelier ne compare que les peintures possédées. Les peintures opaques sont classées principalement par distance CIEDE2000. Les gammes comportementales (Contrast, Speedpaint, lavis, encres et effets techniques) combinent type et profil d’application et exigent toujours une validation manuelle. Tous les paramètres de classement sont injectés depuis la configuration Spring Boot.

Le référentiel Reichbusters Reloaded contient 59 types d’éléments et 198 objets potentiels. Le ledger initial est volontairement vide : créer un `PaintingProject` instancie les objets physiques. Le workflow couvre `preparation`, `priming`, `pre_highlight`, `painting`, `finishing` et `basing`.

Cette séparation contient l’impact d’un futur passage en base de données derrière les ports. L’adaptateur de persistance, les migrations et certains mappings devront évoluer, tandis que le domaine, les intentions des cas d’usage et les contrats publics stables resteront protégés.

## API REST

La base de l’API est `/api/v1`. Les principaux services couvrent :

- santé et bootstrap léger du SPA ;
- recherche du marché par texte, type, couleur, marque, gamme, fini, volume et tags ;
- recherche complémentaire paginée, facettes séparées, fabricant, médium, opacité, cycle de vie et référence ;
- simulation et application de change sets de peintures et de produits à peindre ;
- consultation d’un `PaintableProduct`, prévisualisation de ses peintures manquantes et création idempotente d’un `PaintingProject` ;
- consultation des guides de peinture du marché et rapprochement avec le stock possédé ;
- administration de l’atelier, progression des projets et consultation des objets physiques ;
- création, transition et consultation des recettes d’atelier, puis affectation à un objet physique ;
- ajout d’un objet, transitions ordonnées du workflow, commentaires et photos d’avancement ;
- liste d’achats séparant besoins calculés et achats planifiés, avec état persistant dans le ledger ;
- lecture du ledger et reconstruction des projections ;
- exports CSV et YAML.
- métadonnées de version, auteur et documentation embarquée dans « À propos ».

## Outils de données et skills

Les traitements répétables sont regroupés dans le package Python `tools/minipaintdex-data`. Pour développer les imports sur une nouvelle machine :

```powershell
python -m pip install -e ".\tools\minipaintdex-data[images]"
python .\tools\minipaintdex-data\mpdx_data.py --help
```

Le rafraîchissement accepte une marque canonique ou `all`. Dans ce dernier cas, les providers officiels enregistrés sont exécutés sans liste dupliquée dans le skill ; une marque locale sans provider est signalée. Il compare les références existantes, propose les ajouts et mises à jour, et transforme une disparition vérifiée en retrait par défaut. Une suppression doit être explicitement demandée et reste refusée si la peinture est possédée, citée par un guide du marché ou utilisée par une recette d’atelier. Toute peinture technique doit fournir un résumé et des étapes d’utilisation ; une trame générique porte un statut explicite de révision.

Les skills dans `.agents/skills` sont rationalisés autour de deux points d’entrée :

- `mini-paint-dex-project` pilote le développement, le build, le serveur local et les opérations Git explicitement demandées ;
- `administer-minipaintdex-data` orchestre l’import photo, les produits à peindre, le rafraîchissement des marques et les datasets.

Les imports et rafraîchissements produisent un change set, puis utilisent le service REST local ou son adaptateur CLI. Ils n’écrivent jamais directement dans `data`. Les skills Git ne s’exécutent que sur une demande explicite de commit ou de push ; le mot « Go » n’accorde pas cette autorisation.

Les datasets sont documentés dans `datasets/README.md`. Leur création et leur validation sont déterministes côté Python ; leur import passe par `minipaintdex datasets import`, en simulation par défaut et avec `--apply` pour écrire.

Toute image enregistrée doit conserver sa source, son crédit et sa licence. Un aperçu numérique de couleur ne doit pas être présenté comme un rendu réel peint.
