# Datasets portables

Les catégories canoniques sont :

- `market.paint-brand` avec `--brand` ;
- `market.paintable-product` avec `--product` ;
- `workshop.paint-pots` ;
- `workshop.painting-project` avec `--product`, et éventuellement `--project-id` et `--project-name`.

Créer et valider :

```powershell
python tools/minipaintdex-data/mpdx_data.py dataset create --root . --datasets-root datasets --category <catégorie> --name <nom> [...]
python tools/minipaintdex-data/mpdx_data.py dataset validate <répertoire> --format json
```

Importer par l’application :

```powershell
.\scripts\minipaintdex.ps1 cli --root . --format json datasets import --input <répertoire>
.\scripts\minipaintdex.ps1 cli --root . --format json datasets import --input <répertoire> --apply
```

Le premier appel est une simulation. Contrôler `dataset.yaml`, `payload/change-set.json`, la catégorie, le mode et le SHA-256 avant `--apply`. Toutes les catégories fusionnent par leurs cas d’usage. `workshop.paint-pots` inscrit les identités stables des pots possédés ; un réimport conserve l’état et les photos existants. Ce dataset ne transporte pas l’historique ni les fichiers média : une sauvegarde complète conserve `data/` et `media/`.
