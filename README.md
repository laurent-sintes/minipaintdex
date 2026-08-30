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

Le build complet exécute également les tests du frontend et les validations des référentiels. Les outils de données Python ont leur propre suite :

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
.\scripts\minipaintdex.ps1 cli --root . --format json workshop paintable-products import --product reichbusters-reloaded
.\scripts\minipaintdex.ps1 cli --root . --format json market guides reconcile --guide reichbusters-reloaded-red-hawk-guide
.\scripts\minipaintdex.ps1 cli --root . --format json workshop items list --product reichbusters-reloaded
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

- `backend/domain` : agrégats `PaintableProduct`, `Workshop`, objets physiques, recettes, workflow et événements ;
- `backend/application` : cas d’usage indépendants des transports et du stockage ;
- `backend/adapter-file` : lecture YAML et journal JSONL append-only ;
- `backend/bootstrap` : configuration Spring typée et assemblage commun des dépendances ;
- `backend/server` : adaptateur REST Spring MVC et hébergement du SPA ;
- `backend/cli` : adaptateur Picocli des mêmes cas d’usage ;
- `frontend` : package React/Vite autonome, avec ses sources, ses assets et sa configuration ;
- `tools/minipaintdex-data` : traitements déterministes partagés par les skills d’import et de rafraîchissement.

Le navigateur n’écrit jamais dans `data`. Toute mutation passe par un service applicatif, exposé en REST et en CLI, puis produit un événement dans le ledger global.

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
    shopping.yaml        achats envisagés
  ledger/events/         journal métier global JSONL append-only
```

Un `PaintableProduct` est l’agrégat du marché pour une boîte, une extension ou une gamme contenant des éléments à peindre. Ses quantités décrivent le contenu théorique. `Workshop` est un agrégat distinct : son import référence le produit puis crée un `WorkshopItem` par exemplaire physique. Un guide de peinture du marché contient la palette et la méthode publiées ou inférées à partir d’une référence traçable. Une recette d’atelier est un autre agrégat : elle versionne les substitutions, mélanges, couches et techniques réellement choisies par le propriétaire, puis peut être affectée à un objet physique précis. Son cycle `draft → validated → active → superseded/archived` est conservé dans le ledger.

Le rapprochement d’un guide avec l’atelier ne compare que les peintures possédées. Les peintures opaques sont classées principalement par distance CIEDE2000. Les gammes comportementales (Contrast, Speedpaint, lavis, encres et effets techniques) combinent type et profil d’application et exigent toujours une validation manuelle. Tous les paramètres de classement sont injectés depuis la configuration Spring Boot.

L’import actuel contient 47 peintures possédées, 59 types d’éléments Reichbusters Reloaded et 198 objets physiques suivis individuellement. Le workflow d’un objet couvre `preparation`, `priming`, `pre_highlight`, `painting`, `finishing` et `basing`.

Cette séparation permet de remplacer plus tard l’adaptateur fichier par une base de données sans modifier le domaine, les cas d’usage, l’API ou le SPA.

## API REST

La base de l’API est `/api/v1`. Les principaux services couvrent :

- santé et bootstrap du SPA ;
- recherche du marché par texte, type, couleur, marque, gamme, fini, volume et tags ;
- recherche complémentaire par fabricant, médium, opacité, cycle de vie et référence ;
- simulation et application de change sets de peintures et de produits à peindre ;
- consultation d’un `PaintableProduct`, prévisualisation de ses peintures manquantes et import idempotent dans `Workshop` ;
- consultation des guides de peinture du marché et rapprochement avec le stock possédé ;
- administration de l’atelier, progression des produits possédés et consultation des objets physiques ;
- création, transition et consultation des recettes d’atelier, puis affectation à un objet physique ;
- ajout d’un objet et transitions du workflow ;
- lecture du ledger et reconstruction des projections ;
- exports CSV et YAML.

## Outils de données et skills

Les traitements répétables sont regroupés dans le package Python `tools/minipaintdex-data`. Pour développer les imports sur une nouvelle machine :

```powershell
python -m pip install -e ".\tools\minipaintdex-data[images]"
python .\tools\minipaintdex-data\mpdx_data.py --help
```

Le rafraîchissement accepte une marque canonique ou `all`. Dans ce dernier cas, la liste est déduite du catalogue local. Il compare les références existantes, propose les ajouts et mises à jour, et transforme une disparition vérifiée en retrait par défaut. Une suppression doit être explicitement demandée et reste refusée si la peinture est possédée, citée par un guide du marché ou utilisée par une recette d’atelier. Toute peinture technique doit fournir un résumé et des étapes d’utilisation.

Les skills dans `.agents/skills` automatisent les opérations persistantes :

- `import-miniature-paints` identifie et fusionne les pots photographiés ;
- `import-paintable-product` importe une boîte, une extension ou une gamme et ses références traçables, puis la rattache séparément à l’atelier si demandé ;
- `refresh-paint-brands` rafraîchit une marque ou toutes les marques connues, avec comparaison complète et simulation ;
- `run-local-server` construit, démarre et vérifie l’application locale self-contained ;
- `commit` crée un commit Git atomique ;
- `push` vérifie et pousse la branche courante.

Les imports et rafraîchissements produisent un change set, puis utilisent le service REST local ou son adaptateur CLI. Ils n’écrivent jamais directement dans `data`. Les skills Git ne s’exécutent que sur une demande explicite de commit ou de push ; le mot « Go » n’accorde pas cette autorisation.

Toute image enregistrée doit conserver sa source, son crédit et sa licence. Un aperçu numérique de couleur ne doit pas être présenté comme un rendu réel peint.
