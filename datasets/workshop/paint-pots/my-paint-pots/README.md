# Pots de ma collection

Ce dataset conserve les 172 identités physiques issues des 161 références de l'ancien inventaire.
La conversion du 3 septembre 2026 préserve exactement chaque quantité. État, niveau, acquisition
et ouverture n'ont pas été inventés.

L'import fusionne les identités : il n'efface pas les observations, les notes ni les photos déjà
enregistrées et ne remet pas dans le stock un pot donné ou jeté.

```powershell
.\scripts\minipaintdex.ps1 cli --format json datasets import --input datasets/workshop/paint-pots/my-paint-pots
.\scripts\minipaintdex.ps1 cli --format json --wait datasets import --input datasets/workshop/paint-pots/my-paint-pots --apply
```

Ce paquet n'est pas une sauvegarde des journaux personnels ou des médias. Ceux-ci restent sous
`data/ledger/` et `media/workshop/`. L'ancien fichier de quantités est conservé localement sous
`imports/workshop-paints/runs/paint-pot-conversion-2026-09-03/previous-quantities.yaml`.
