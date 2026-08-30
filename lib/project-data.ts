export type ProjectPaintRequirement = {
  brand: string;
  name: string;
  role: string;
  colorHex: string;
  pendingImport?: boolean;
};

export type ProjectGuideStep = {
  title: string;
  detail: string;
};

export type ProjectItem = {
  id: string;
  name: string;
  kind: 'Héros' | 'Ennemis' | 'Décor';
  quantity: number;
  status: 'à préparer' | 'en cours' | 'terminé';
  description: string;
  paints: ProjectPaintRequirement[];
  preparation: ProjectGuideStep[];
  painting: ProjectGuideStep[];
  sourceLabel?: string;
  sourceUrl?: string;
};

export type PaintingProject = {
  id: string;
  name: string;
  game: string;
  scope: string;
  sourceUrl: string;
  editionNote: string;
  editionUrl: string;
  items: ProjectItem[];
};

const sharedPreparation: ProjectGuideStep[] = [
  { title: 'Nettoyer', detail: 'Laver la figurine à l’eau tiède avec une goutte de liquide vaisselle, rincer puis laisser sécher complètement.' },
  { title: 'Ébarber', detail: 'Retirer doucement les lignes de moulage. Éviter de forcer sur les armes et accessoires fins.' },
  { title: 'Sous-coucher', detail: 'Appliquer une couche noire fine, puis un zénithal blanc léger depuis le dessus pour rendre les volumes lisibles.' },
];

const generalTutorial = 'https://www.emaginarock.fr/2020/chroniques/tutoriel-peinture-reich-busters-de-mythic-games-no-more-grey-studio/';
const reichbustersProjectSource = 'https://cdn.1j1ju.com/medias/67/7f/83-reichbusters-projekt-vril-rulebook.pdf';

const batchPainting: ProjectGuideStep[] = [
  { title: 'Travail en série', detail: 'Préparer quatre figurines à la fois et terminer une couleur sur tout le groupe avant de passer à la suivante.' },
  { title: 'Aplats', detail: 'Poser les couleurs principales en couches fines, sans remplir les détails ni les creux.' },
  { title: 'Ombres', detail: 'Appliquer un lavis sombre localisé, puis laisser sécher complètement.' },
  { title: 'Reliefs', detail: 'Reprendre les épaules, têtes, genoux et armes avec la couleur de base ou une teinte plus claire.' },
  { title: 'Socles', detail: 'Traiter tous les socles de la même manière pour rendre le groupe identifiable sur le plateau.' },
];

const heroicFinish: ProjectGuideStep[] = [
  { title: 'Grandes zones', detail: 'Poser d’abord les vêtements et l’équipement, en gardant les zones de peau propres.' },
  { title: 'Peau', detail: 'Appliquer la teinte chair en couche contrôlée puis reprendre les reliefs du visage et des mains.' },
  { title: 'Matériaux', detail: 'Distinguer cuir, tissu et métal par des finitions et des éclaircissements différents.' },
  { title: 'Point focal', detail: 'Renforcer le visage et l’arme principale : ce sont les zones qui doivent se lire en premier.' },
  { title: 'Protection', detail: 'Après séchage complet, protéger la figurine avec un vernis mat en couche fine.' },
];

export const reichbustersProject: PaintingProject = {
  id: 'reichbusters-reloaded',
  name: 'Reichbusters Reloaded',
  game: 'Reichbusters: Projekt Vril',
  scope: 'Inventaire initial · boîte de base et décors',
  sourceUrl: reichbustersProjectSource,
  editionNote: 'La première liste reprend les 72 figurines de la boîte de base historique. Le contenu additionnel de Reloaded dépend de la Red Box ou du Green Upgrade et sera complété après inventaire de vos boîtes.',
  editionUrl: 'https://monolithedition.com/produit/rbr-green-upg-en/',
  items: [
    {
      id: 'red-hawk',
      name: 'Red Hawk',
      kind: 'Héros',
      quantity: 1,
      status: 'à préparer',
      description: 'Tireuse d’élite : tons naturels, cuir, peau et détails froids sur les armes.',
      paints: [
        { brand: 'Warhammer Colour', name: 'Guilliman Flesh', role: 'Peau — base et ombres', colorHex: '#a76550' },
        { brand: 'Warhammer Colour', name: 'Aggaros Dunes', role: 'Uniforme et toile', colorHex: '#806b43' },
        { brand: 'Warhammer Colour', name: 'Wyldwood', role: 'Cuir et bois sombre', colorHex: '#2e241e' },
        { brand: 'Warhammer Colour', name: 'Gore-Grunta Fur', role: 'Cuir chaud et cheveux', colorHex: '#6d3524' },
        { brand: 'Vallejo', name: 'Silver', role: 'Armes et boucles métalliques', colorHex: '#bfc3c2', pendingImport: true },
      ],
      preparation: sharedPreparation,
      painting: [
        { title: 'Peau', detail: 'Poser Guilliman Flesh en couche contrôlée. Reprendre les reliefs avec une teinte chair claire si nécessaire.' },
        { title: 'Uniforme', detail: 'Appliquer Aggaros Dunes sur les tissus, en évitant les cuirs. Renforcer les plis profonds avec une seconde passe localisée.' },
        { title: 'Cuir et bois', detail: 'Utiliser Wyldwood dans les creux et Gore-Grunta Fur sur les zones plus chaudes ou exposées.' },
        { title: 'Métaux', detail: 'Peindre l’arme en sombre, poser Silver sur les arêtes et garder du noir dans les creux.' },
        { title: 'Finitions', detail: 'Corriger les débordements, éclaircir les volumes supérieurs puis protéger avec un vernis mat.' },
      ],
      sourceLabel: 'Tutoriel Red Hawk · OnTableTop',
      sourceUrl: 'https://www.ontabletop.com/project/1515465/',
    },
    {
      id: 'sarge', name: 'Sarge', kind: 'Héros', quantity: 1, status: 'à préparer',
      description: 'Chef d’escouade : uniforme militaire brun-vert, cuir sombre et équipement très lisible.',
      paints: [
        { brand: 'Warhammer Colour', name: 'Aggaros Dunes', role: 'Uniforme', colorHex: '#806b43' },
        { brand: 'Warhammer Colour', name: 'Wyldwood', role: 'Bottes et sangles', colorHex: '#2e241e' },
        { brand: 'Warhammer Colour', name: 'Guilliman Flesh', role: 'Visage et mains', colorHex: '#a76550' },
        { brand: 'Vallejo', name: 'Silver', role: 'Arme et boucles', colorHex: '#bfc3c2', pendingImport: true },
      ],
      preparation: sharedPreparation, painting: heroicFinish,
      sourceLabel: 'Tutoriel général Reichbusters · No More Grey Studio', sourceUrl: generalTutorial,
    },
    {
      id: 'brick', name: 'Brick', kind: 'Héros', quantity: 1, status: 'à préparer',
      description: 'Combattant lourd : volumes francs, équipement sombre et accents froids pour séparer les pièces d’armure.',
      paints: [
        { brand: 'Warhammer Colour', name: 'Leviadon Blue', role: 'Équipement bleu sombre', colorHex: '#112c4f' },
        { brand: 'Warhammer Colour', name: 'Cygor Brown', role: 'Cuir profond', colorHex: '#3a2322' },
        { brand: 'The Army Painter', name: 'Crusader Skin', role: 'Peau', colorHex: '#c98b72', pendingImport: true },
        { brand: 'Vallejo', name: 'Silver', role: 'Armes lourdes', colorHex: '#bfc3c2', pendingImport: true },
      ],
      preparation: sharedPreparation, painting: heroicFinish,
      sourceLabel: 'Tutoriel général Reichbusters · No More Grey Studio', sourceUrl: generalTutorial,
    },
    {
      id: 'claudine', name: 'Claudine', kind: 'Héros', quantity: 1, status: 'à préparer',
      description: 'Opératrice française : silhouette sombre, peau chaude et quelques accents froids sur l’équipement.',
      paints: [
        { brand: 'Warhammer Colour', name: 'Darkoath Flesh', role: 'Peau', colorHex: '#9a6b58' },
        { brand: 'Warhammer Colour', name: 'Cygor Brown', role: 'Vêtements et cuir sombres', colorHex: '#3a2322' },
        { brand: 'Warhammer Colour', name: 'Talassar Blue', role: 'Détails froids', colorHex: '#155cc4' },
        { brand: 'Vallejo', name: 'Silver', role: 'Armes et boucles', colorHex: '#bfc3c2', pendingImport: true },
      ],
      preparation: sharedPreparation, painting: heroicFinish,
      sourceLabel: 'Tutoriel général Reichbusters · No More Grey Studio', sourceUrl: generalTutorial,
    },
    {
      id: 'quentin', name: 'Quentin', kind: 'Héros', quantity: 1, status: 'à préparer',
      description: 'Archer britannique : verts militaires, bois chaud, cuirs sombres et peau naturelle.',
      paints: [
        { brand: 'Vallejo', name: 'Uniform Green', role: 'Uniforme', colorHex: '#496145', pendingImport: true },
        { brand: 'Warhammer Colour', name: 'Gore-Grunta Fur', role: 'Arc et cuir chaud', colorHex: '#6d3524' },
        { brand: 'Warhammer Colour', name: 'Wyldwood', role: 'Bottes et carquois', colorHex: '#2e241e' },
        { brand: 'Warhammer Colour', name: 'Guilliman Flesh', role: 'Peau', colorHex: '#a76550' },
      ],
      preparation: sharedPreparation, painting: heroicFinish,
      sourceLabel: 'Tutoriel général Reichbusters · No More Grey Studio', sourceUrl: generalTutorial,
    },
    {
      id: 'nazi-soldiers',
      name: 'Soldats',
      kind: 'Ennemis',
      quantity: 16,
      status: 'à préparer',
      description: 'Groupe principal à traiter en série pour conserver un uniforme cohérent et gagner du temps.',
      paints: [
        { brand: 'Vallejo', name: 'Uniform Green', role: 'Uniformes', colorHex: '#496145', pendingImport: true },
        { brand: 'Warhammer Colour', name: 'Wyldwood', role: 'Bottes, sangles et crosses', colorHex: '#2e241e' },
        { brand: 'Vallejo', name: 'Silver', role: 'Armes et équipements', colorHex: '#bfc3c2', pendingImport: true },
        { brand: 'Warhammer Colour', name: 'Guilliman Flesh', role: 'Visages et mains', colorHex: '#a76550' },
      ],
      preparation: sharedPreparation,
      painting: batchPainting,
      sourceLabel: 'Inventaire des figurines · livret de règles',
      sourceUrl: 'https://cdn.1j1ju.com/medias/67/7f/83-reichbusters-projekt-vril-rulebook.pdf',
    },
    {
      id: 'nazi-officers', name: 'Officiers', kind: 'Ennemis', quantity: 4, status: 'à préparer',
      description: 'Chefs d’unité à différencier des soldats par des uniformes plus sombres et des détails rouges.',
      paints: [
        { brand: 'Warhammer Colour', name: 'Leviadon Blue', role: 'Uniformes très sombres', colorHex: '#112c4f' },
        { brand: 'The Army Painter', name: 'Blood Red', role: 'Insignes et liserés', colorHex: '#a72f32', pendingImport: true },
        { brand: 'Warhammer Colour', name: 'Darkoath Flesh', role: 'Peau', colorHex: '#9a6b58' },
        { brand: 'Vallejo', name: 'Silver', role: 'Armes et décorations', colorHex: '#bfc3c2', pendingImport: true },
      ], preparation: sharedPreparation, painting: batchPainting,
      sourceLabel: 'Inventaire des figurines · livret de règles', sourceUrl: reichbustersProjectSource,
    },
    {
      id: 'sturmsoldat-gunners', name: 'Sturmsoldat Gunners', kind: 'Ennemis', quantity: 4, status: 'à préparer',
      description: 'Troupes lourdes : armure sombre, métal usé et énergie Vril froide.',
      paints: [
        { brand: 'Warhammer Colour', name: 'Leviadon Blue', role: 'Armure sombre', colorHex: '#112c4f' },
        { brand: 'Vallejo', name: 'Silver', role: 'Blindage et armes', colorHex: '#bfc3c2', pendingImport: true },
        { brand: 'Warhammer Colour', name: 'Briar Queen Chill', role: 'Énergie Vril', colorHex: '#74c4c9' },
        { brand: 'Warhammer Colour', name: 'Talassar Blue', role: 'Lueurs intenses', colorHex: '#155cc4' },
      ], preparation: sharedPreparation, painting: batchPainting,
      sourceLabel: 'Inventaire des figurines · livret de règles', sourceUrl: reichbustersProjectSource,
    },
    {
      id: 'sturmsoldat-assault', name: 'Sturmsoldat Assault Troopers', kind: 'Ennemis', quantity: 4, status: 'à préparer',
      description: 'Troupes d’assaut : métal sombre, protections froides et marquages rouges.',
      paints: [
        { brand: 'Warhammer Colour', name: 'Cygor Brown', role: 'Sous-combinaison et sangles', colorHex: '#3a2322' },
        { brand: 'Vallejo', name: 'Silver', role: 'Armure et armes', colorHex: '#bfc3c2', pendingImport: true },
        { brand: 'The Army Painter', name: 'Blood Red', role: 'Marquages', colorHex: '#a72f32', pendingImport: true },
        { brand: 'Warhammer Colour', name: 'Briar Queen Chill', role: 'Énergie Vril', colorHex: '#74c4c9' },
      ], preparation: sharedPreparation, painting: batchPainting,
      sourceLabel: 'Inventaire des figurines · livret de règles', sourceUrl: reichbustersProjectSource,
    },
    {
      id: 'scientists', name: 'Scientifiques', kind: 'Ennemis', quantity: 4, status: 'à préparer',
      description: 'Blouses claires salies, gants et accessoires de laboratoire colorés.',
      paints: [
        { brand: 'Vallejo', name: 'Army White', role: 'Blouses', colorHex: '#d8d7ca', pendingImport: true },
        { brand: 'Warhammer Colour', name: 'Guilliman Flesh', role: 'Peau', colorHex: '#a76550' },
        { brand: 'Warhammer Colour', name: 'Aggaros Dunes', role: 'Salissures', colorHex: '#806b43' },
        { brand: 'Warhammer Colour', name: 'Talassar Blue', role: 'Fioles et écrans', colorHex: '#155cc4' },
      ], preparation: sharedPreparation, painting: batchPainting,
      sourceLabel: 'Inventaire des figurines · livret de règles', sourceUrl: reichbustersProjectSource,
    },
    {
      id: 'zombies', name: 'Zombies', kind: 'Ennemis', quantity: 12, status: 'à préparer',
      description: 'Peaux désaturées, uniformes sales et blessures rouges traitées rapidement en série.',
      paints: [
        { brand: 'The Army Painter', name: 'Pallid Bone', role: 'Peaux mortes', colorHex: '#c6b78f', pendingImport: true },
        { brand: 'Vallejo', name: 'Uniform Green', role: 'Restes d’uniformes', colorHex: '#496145', pendingImport: true },
        { brand: 'The Army Painter', name: 'Blood Red', role: 'Blessures', colorHex: '#a72f32', pendingImport: true },
        { brand: 'Warhammer Colour', name: 'Wyldwood', role: 'Boue et cuirs', colorHex: '#2e241e' },
      ], preparation: sharedPreparation, painting: batchPainting,
      sourceLabel: 'Inventaire des figurines · livret de règles', sourceUrl: reichbustersProjectSource,
    },
    {
      id: 'nazi-dogs', name: 'Chiens', kind: 'Ennemis', quantity: 4, status: 'à préparer',
      description: 'Pelages sombres lisibles par brossage, museaux et harnais bruns.',
      paints: [
        { brand: 'Warhammer Colour', name: 'Cygor Brown', role: 'Pelage sombre', colorHex: '#3a2322' },
        { brand: 'Warhammer Colour', name: 'Gore-Grunta Fur', role: 'Éclaircissements du pelage', colorHex: '#6d3524' },
        { brand: 'Warhammer Colour', name: 'Wyldwood', role: 'Harnais', colorHex: '#2e241e' },
        { brand: 'The Army Painter', name: 'Blood Red', role: 'Langue et blessures', colorHex: '#a72f32', pendingImport: true },
      ], preparation: sharedPreparation, painting: batchPainting,
      sourceLabel: 'Inventaire des figurines · livret de règles', sourceUrl: reichbustersProjectSource,
    },
    {
      id: 'tracking-bombers', name: 'Tracking Bombers', kind: 'Ennemis', quantity: 4, status: 'à préparer',
      description: 'Unités explosives : métal, cuir, corps sombre et charge très visible en orange.',
      paints: [
        { brand: 'Warhammer Colour', name: 'Cygor Brown', role: 'Corps et harnais', colorHex: '#3a2322' },
        { brand: 'Vallejo', name: 'Silver', role: 'Mécanismes', colorHex: '#bfc3c2', pendingImport: true },
        { brand: 'The Army Painter', name: 'Fire Giant Orange', role: 'Charge explosive', colorHex: '#d56a31', pendingImport: true },
        { brand: 'The Army Painter', name: 'Zealot Yellow', role: 'Signalétique', colorHex: '#d5b83f', pendingImport: true },
      ], preparation: sharedPreparation, painting: batchPainting,
      sourceLabel: 'Inventaire des figurines · livret de règles', sourceUrl: reichbustersProjectSource,
    },
    {
      id: 'experiment-601', name: 'Experiment 601', kind: 'Ennemis', quantity: 4, status: 'à préparer',
      description: 'Créatures de laboratoire : chair maladive, bleus spectraux et zones de mutation contrastées.',
      paints: [
        { brand: 'The Army Painter', name: 'Pallid Bone', role: 'Chair pâle', colorHex: '#c6b78f', pendingImport: true },
        { brand: 'Warhammer Colour', name: 'Briar Queen Chill', role: 'Chair froide', colorHex: '#74c4c9' },
        { brand: 'The Army Painter', name: 'Hive Dweller Purple', role: 'Mutations et ombres', colorHex: '#5d3f72', pendingImport: true },
        { brand: 'The Army Painter', name: 'Blood Red', role: 'Plaies', colorHex: '#a72f32', pendingImport: true },
      ], preparation: sharedPreparation, painting: batchPainting,
      sourceLabel: 'Inventaire des figurines · livret de règles', sourceUrl: reichbustersProjectSource,
    },
    {
      id: 'experiment-6xx', name: 'Experiment 6XX', kind: 'Ennemis', quantity: 8, status: 'à préparer',
      description: 'Variantes mutantes : conserver une base commune et varier les zones Vril pour identifier les sculptures.',
      paints: [
        { brand: 'Vallejo', name: 'Fairy Skin', role: 'Chair mutante', colorHex: '#dc9a91', pendingImport: true },
        { brand: 'Warhammer Colour', name: 'Pylar Glacier', role: 'Lueurs froides', colorHex: '#45c5e9' },
        { brand: 'The Army Painter', name: 'Hive Dweller Purple', role: 'Mutations', colorHex: '#5d3f72', pendingImport: true },
        { brand: 'Warhammer Colour', name: 'Briar Queen Chill', role: 'Transitions Vril', colorHex: '#74c4c9' },
      ], preparation: sharedPreparation, painting: batchPainting,
      sourceLabel: 'Inventaire des figurines · livret de règles', sourceUrl: reichbustersProjectSource,
    },
    {
      id: 'gisela-gruber', name: 'Gisela Gruber', kind: 'Ennemis', quantity: 1, status: 'à préparer',
      description: 'Personnage nommé : uniforme froid, peau claire et détails Vril soignés.',
      paints: [
        { brand: 'Warhammer Colour', name: 'Leviadon Blue', role: 'Uniforme sombre', colorHex: '#112c4f' },
        { brand: 'Vallejo', name: 'Fairy Skin', role: 'Peau', colorHex: '#dc9a91', pendingImport: true },
        { brand: 'Warhammer Colour', name: 'Briar Queen Chill', role: 'Détails Vril', colorHex: '#74c4c9' },
        { brand: 'Vallejo', name: 'Silver', role: 'Équipement', colorHex: '#bfc3c2', pendingImport: true },
      ], preparation: sharedPreparation, painting: heroicFinish,
      sourceLabel: 'Galerie de références peintes · Paintedfigs', sourceUrl: 'https://www.paintedfigs.com/blog/paintingpainting-mythic-games-reichbusters-miniature-painting-service',
    },
    {
      id: 'general-wolff', name: 'General Wolff', kind: 'Ennemis', quantity: 1, status: 'à préparer',
      description: 'Boss humain : uniforme presque noir, insignes rouges et métal propre pour attirer le regard.',
      paints: [
        { brand: 'Warhammer Colour', name: 'Leviadon Blue', role: 'Uniforme noir bleuté', colorHex: '#112c4f' },
        { brand: 'The Army Painter', name: 'Blood Red', role: 'Insignes', colorHex: '#a72f32', pendingImport: true },
        { brand: 'Warhammer Colour', name: 'Darkoath Flesh', role: 'Peau', colorHex: '#9a6b58' },
        { brand: 'Vallejo', name: 'Silver', role: 'Décorations et arme', colorHex: '#bfc3c2', pendingImport: true },
      ], preparation: sharedPreparation, painting: heroicFinish,
      sourceLabel: 'Galerie de références peintes · Paintedfigs', sourceUrl: 'https://www.paintedfigs.com/blog/paintingpainting-mythic-games-reichbusters-miniature-painting-service',
    },
    {
      id: 'vrilpanzer', name: 'Vrilpanzer', kind: 'Ennemis', quantity: 1, status: 'à préparer',
      description: 'Pièce centrale mécanique : blindage sombre, métal usé et énergie Vril très lumineuse.',
      paints: [
        { brand: 'Warhammer Colour', name: 'Leviadon Blue', role: 'Blindage sombre', colorHex: '#112c4f' },
        { brand: 'Vallejo', name: 'Silver', role: 'Métal et impacts', colorHex: '#bfc3c2', pendingImport: true },
        { brand: 'Warhammer Colour', name: 'Talassar Blue', role: 'Énergie Vril', colorHex: '#155cc4' },
        { brand: 'Warhammer Colour', name: 'Pylar Glacier', role: 'Cœur des lueurs', colorHex: '#45c5e9' },
      ], preparation: sharedPreparation,
      painting: [
        { title: 'Blindage', detail: 'Poser la base sombre sur tous les panneaux sans couvrir les conduites et zones lumineuses.' },
        { title: 'Métal', detail: 'Brosser Silver sur les arêtes, rivets et mécanismes ; garder les creux sombres.' },
        { title: 'Énergie Vril', detail: 'Peindre les sources en blanc, couvrir de Pylar Glacier puis renforcer autour avec Talassar Blue très dilué.' },
        { title: 'Usure', detail: 'Ajouter quelques impacts Silver et des jus bruns sous les plaques et articulations.' },
        { title: 'Contraste final', detail: 'Rétablir les points les plus clairs au centre des sources d’énergie.' },
      ],
      sourceLabel: 'Galerie de références peintes · Paintedfigs', sourceUrl: 'https://www.paintedfigs.com/blog/paintingpainting-mythic-games-reichbusters-miniature-painting-service',
    },
    {
      id: 'doors-lab',
      name: 'Portes et laboratoire',
      kind: 'Décor',
      quantity: 1,
      status: 'à préparer',
      description: 'Lot de décors à inventorier précisément selon les extensions possédées.',
      paints: [
        { brand: 'Vallejo', name: 'Silver', role: 'Métal apparent', colorHex: '#bfc3c2', pendingImport: true },
        { brand: 'Warhammer Colour', name: 'Talassar Blue', role: 'Énergie et écrans', colorHex: '#155cc4' },
        { brand: 'Warhammer Colour', name: 'Briar Queen Chill', role: 'Lueurs froides', colorHex: '#74c4c9' },
      ],
      preparation: [
        { title: 'Inventorier', detail: 'Séparer portes, mobilier et accessoires. Noter le nombre de pièces avant la sous-couche.' },
        { title: 'Préparer', detail: 'Nettoyer, ébarber et tester les assemblages mobiles avant peinture.' },
        { title: 'Sous-coucher', detail: 'Utiliser une sous-couche noire pour les éléments métalliques, avec un zénithal gris.' },
      ],
      painting: [
        { title: 'Métal', detail: 'Brosser Silver sur une base sombre en laissant les creux noirs.' },
        { title: 'Écrans et Vril', detail: 'Poser Talassar Blue ou Briar Queen Chill sur une base blanche localisée.' },
        { title: 'Vieillissement', detail: 'Ajouter des jus bruns dans les angles et quelques éraflures claires sur les arêtes.' },
      ],
    },
  ],
};
