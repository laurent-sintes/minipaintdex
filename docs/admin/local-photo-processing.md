# Détourage local des photos de pots

Les photos personnelles appartiennent à `PaintPot`. Le catalogue `PaintProduct` conserve ses
visuels fabricant. Depuis une fiche peinture, « Ajouter la photo » permet de choisir un pot
déjà enregistré, comparer original et aperçu, puis enregistrer la photo. La page du pot offre
le même formulaire. Décocher le détourage conserve uniquement l’original.

## Installation

Exécuter `scripts/install-photo-model.ps1` une fois avant le lancement. Le script télécharge
U²-Net compact (u2netp) et vérifie son SHA-256. Le modèle reste dans `.tools/models/`, hors Git.
ONNX Runtime Java est une dépendance Maven. L’application n’effectue aucun téléchargement ni
appel réseau pour traiter une photo : le modèle s’exécute sur le CPU local, sans LLM.

Les paramètres sont dans `config/application.yaml`, sous `minipaintdex.paint-pot-photos`.
`enabled=false` désactive le détourage tout en conservant l’ajout de photos originales.
Lorsque le traitement est activé, un modèle absent ou de mauvaise empreinte bloque le démarrage
avec une erreur explicite. Les autres réglages bornent les pixels entrants, le côté de sortie,
les threads CPU, le seuil du cadrage et sa marge. Une seule inférence est admise à la fois.

## Contrats

- `POST /api/v1/workshop/paint-pots/{paintPotId}/photo-preview` : multipart `file`, réponse PNG
  temporaire, `Cache-Control: no-store`, identifiant de corrélation et méthode en en-têtes.
- `POST /api/v1/workshop/paint-pots/{paintPotId}/photos` : multipart `file`, `caption` et
  `removeBackground` (false par défaut), `Idempotency-Key`. Le serveur recalcule le détourage
  à l’enregistrement, garde l’original et accepte un seul événement contenant la dérivée optionnelle.
- `minipaintdex --format json workshop paint-pots photo-preview --paint-pot-id ID --file photo.jpg --output preview.png` :
  écrit uniquement l’aperçu demandé et refuse d’écraser un fichier existant.
- `minipaintdex --format json --wait workshop paint-pots photo --paint-pot-id ID --file photo.jpg --remove-background --idempotency-key KEY` :
  mêmes effets que REST, via le serveur quand il est disponible.

Le CLI peut calculer l’aperçu en processus local : il s’agit d’une lecture sans mutation du
stockage applicatif. Les uploads conservent les sémantiques habituelles d’acceptation durable
et de notification SSE après commit. Une répétition de clé ne crée pas une deuxième photo.
Les originaux et dérivées sont inclus dans la sauvegarde habituelle de `media/workshop`.

## Modèle et limites

Le modèle générique peut supprimer un détail du pot ou conserver une ombre, surtout sur un
fond chargé, transparent ou proche de la couleur du pot. Utiliser une photo avec un seul pot,
bien éclairé, et vérifier l’aperçu. Il ne reconnaît pas la référence et n’estime pas le niveau.
JPEG, PNG et WebP sont décodés localement ; les dimensions sont contrôlées avant allocation,
l’orientation EXIF est appliquée et la transparence existante est préservée. La sortie est
un PNG carré transparent de 1200 pixels maximum avec marge.

Sources : [U²-Net et sa licence Apache-2.0](https://github.com/xuebinqin/U-2-Net),
[conversion ONNX u2netp distribuée par rembg](https://github.com/danielgatis/rembg/releases/tag/v0.0.0),
[prétraitement u2netp de référence](https://github.com/danielgatis/rembg/blob/main/rembg/sessions/u2netp.py),
[ONNX Runtime Java](https://onnxruntime.ai/docs/get-started/with-java.html).
L’empreinte attendue est `309c8469258dda742793dce0ebea8e6dd393174f89934733ecc8b14c76f4ddd8`.
