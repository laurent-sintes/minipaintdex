# Application data

`data/` est le stockage actif de l’instance locale Mini Paint Dex. Le serveur Spring Boot est le seul composant autorisé à l’écrire.

## Organisation

- `market/paints/` : catalogue global des peintures.
- `market/paintable-products/` : produits contenant des éléments à peindre.
- `market/painting-guides/` : connaissances et recettes sourcées du marché.
- `workshop/paints.yaml` : identifiants et quantités des peintures possédées.
- `workshop/shopping.yaml` : achats planifiés.
- `ledger/events/` : journal JSONL append-only des événements de l’atelier.
- `site/` : libellés et contenu de présentation localisés.

Les identifiants, clés et événements du cœur sont en anglais. Les scripts Python produisent des change sets mais ne modifient jamais directement ce répertoire. Les mutations passent par les cas d’usage Java, exposés en REST et en CLI.

`data/` ne doit pas être utilisé comme format d’échange. Pour copier, sauvegarder ou partager un sous-ensemble cohérent, utilise un paquet dans `datasets/`.
