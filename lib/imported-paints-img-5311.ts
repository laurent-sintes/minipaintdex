import type { ManufacturerInfo, Paint } from './sample-data';

const createdAt = '2026-08-30T13:02:00.000Z';
const verifiedAt = '2026-08-30';
const vallejoXpressDescription = 'Peinture mate à forte capillarité : une couche crée teinte, ombres dans les creux et éclaircissements sur les reliefs.';
const armyPainterDescription = 'Speedpaint 2.0 : une couche sur sous-couche claire crée une couleur vive, des ombres et des éclaircissements.';
const vallejoMetallicDescription = 'Acrylique métallique fluide à pigment fin, adaptée au pinceau et à l’aérographe après dilution.';

export const importedManufacturerCatalog: Record<string, ManufacturerInfo> = {
  'vallejo-game-color-metallic-silver': {
    manufacturerUrl: 'https://acrylicosvallejo.com/en/producto/hobby/sets/basic-en/metallic-colors-72303/', manufacturerImage: '/manufacturer/vallejo-silver-72052.jpg',
    manufacturerImageCredit: 'Packshot Acrylicos Vallejo · copie locale · source de distribution : Vallejo-Farben.de', volumeMl: 18, colorFamily: 'Argent clair',
    manufacturerDescription: vallejoMetallicDescription, recommendedUses: ['Arêtes métalliques', 'Armes et armures claires', 'Éclats et usure'], manufacturerVerifiedAt: verifiedAt,
  },
  'vallejo-game-color-metallic-chainmail': {
    manufacturerUrl: 'https://acrylicosvallejo.com/en/producto/hobby/sets/basic-en/metallic-colors-72303/', manufacturerImage: '/manufacturer/vallejo-chainmail-72053.jpg',
    manufacturerImageCredit: 'Packshot Acrylicos Vallejo · copie locale · source de distribution : Vallejo-Farben.de', volumeMl: 18, colorFamily: 'Acier moyen',
    manufacturerDescription: vallejoMetallicDescription, recommendedUses: ['Cottes de mailles', 'Armes', 'Armures et machines'], manufacturerVerifiedAt: verifiedAt,
  },
  'the-army-painter-speedpaint-orc-skin': {
    manufacturerUrl: 'https://thearmypainter.com/products/speedpaint-speedpaint-orc-skin-wp2009p', manufacturerImage: '/manufacturer/army-painter-orc-skin-wp2009p.jpg',
    manufacturerImageCredit: 'Packshot officiel The Army Painter · copie locale', volumeMl: 18, colorFamily: 'Vert peau',
    manufacturerDescription: armyPainterDescription, recommendedUses: ['Peaux d’orcs et de créatures', 'Tissus verts', 'Végétation sombre'], manufacturerVerifiedAt: verifiedAt,
  },
  'vallejo-xpress-color-lizard-green': {
    manufacturerUrl: 'https://acrylicosvallejo.com/en/category/hobby/xpress-color-en/', manufacturerImage: '/manufacturer/vallejo-lizard-green-72418.jpg',
    manufacturerImageCredit: 'Packshot Acrylicos Vallejo · copie locale · source de distribution : Blick Art Materials', volumeMl: 18, colorFamily: 'Vert sombre',
    manufacturerDescription: vallejoXpressDescription, recommendedUses: ['Écailles', 'Peaux vert sombre', 'Feuillages et uniformes'], manufacturerVerifiedAt: verifiedAt,
  },
  'vallejo-xpress-color-khaki-drill': {
    manufacturerUrl: 'https://acrylicosvallejo.com/en/category/hobby/xpress-color-en/', manufacturerImage: '/manufacturer/vallejo-khaki-drill-72451.png',
    manufacturerImageCredit: 'Packshot Acrylicos Vallejo · copie locale · source de distribution : Vallejo-Farben.de', volumeMl: 18, colorFamily: 'Kaki',
    manufacturerDescription: vallejoXpressDescription, recommendedUses: ['Uniformes', 'Sangles et paquetages', 'Toiles et tissus militaires'], manufacturerVerifiedAt: verifiedAt,
  },
  'vallejo-xpress-color-space-grey': {
    manufacturerUrl: 'https://acrylicosvallejo.com/en/category/hobby/xpress-color-en/', manufacturerImage: '/manufacturer/vallejo-space-grey-72422.jpg',
    manufacturerImageCredit: 'Packshot Acrylicos Vallejo · copie locale · source de distribution : Vallejo-Farben.de', volumeMl: 18, colorFamily: 'Gris bleuté',
    manufacturerDescription: vallejoXpressDescription, recommendedUses: ['Uniformes gris-bleu', 'Armures froides', 'Fourrures et tissus'], manufacturerVerifiedAt: verifiedAt,
  },
  'vallejo-xpress-color-intense-viking-grey': {
    manufacturerUrl: 'https://acrylicosvallejo.com/wp-content/uploads/2025/10/Catalogo_2026-R02.pdf', manufacturerImage: '/manufacturer/vallejo-viking-grey-72483.png',
    manufacturerImageCredit: 'Packshot Acrylicos Vallejo · copie locale · source de distribution : Miniaturicum', volumeMl: 18, colorFamily: 'Gris froid intense',
    manufacturerDescription: `${vallejoXpressDescription} Variante Intense plus pigmentée.`, recommendedUses: ['Armures gris froid', 'Uniformes', 'Ombres bleutées'], manufacturerVerifiedAt: verifiedAt,
  },
  'the-army-painter-speedpaint-hive-dweller-purple': {
    manufacturerUrl: 'https://thearmypainter.com/products/speedpaint-speedpaint-hive-dweller-purple-wp2018p', manufacturerImage: '/manufacturer/army-painter-hive-dweller-purple-wp2018p.jpg',
    manufacturerImageCredit: 'Packshot officiel The Army Painter · copie locale', volumeMl: 18, colorFamily: 'Violet très sombre',
    manufacturerDescription: armyPainterDescription, recommendedUses: ['Peaux extraterrestres', 'Tissus violets', 'Ombres surnaturelles'], manufacturerVerifiedAt: verifiedAt,
  },
  'the-army-painter-speedpaint-fire-giant-orange': {
    manufacturerUrl: 'https://thearmypainter.com/products/speedpaint-speedpaint-fire-giant-orange-wp2017p', manufacturerImage: '/manufacturer/army-painter-fire-giant-orange-wp2017p.jpg',
    manufacturerImageCredit: 'Packshot officiel The Army Painter · copie locale', volumeMl: 18, colorFamily: 'Orange brûlé',
    manufacturerDescription: armyPainterDescription, recommendedUses: ['Flammes et lueurs', 'Cuir roux', 'Tissus orange'], manufacturerVerifiedAt: verifiedAt,
  },
  'the-army-painter-speedpaint-blood-red': {
    manufacturerUrl: 'https://thearmypainter.com/products/speedpaint-speedpaint-blood-red-wp2010p', manufacturerImage: '/manufacturer/army-painter-blood-red-wp2010p.jpg',
    manufacturerImageCredit: 'Packshot officiel The Army Painter · copie locale', volumeMl: 18, colorFamily: 'Rouge sang',
    manufacturerDescription: armyPainterDescription, recommendedUses: ['Uniformes rouges', 'Peaux et tissus', 'Zones organiques'], manufacturerVerifiedAt: verifiedAt,
  },
  'the-army-painter-speedpaint-highlord-blue': {
    manufacturerUrl: 'https://thearmypainter.com/products/speedpaint-speedpaint-highlord-blue-wp2015p', manufacturerImage: '/manufacturer/army-painter-highlord-blue-wp2015p.jpg',
    manufacturerImageCredit: 'Packshot officiel The Army Painter · copie locale', volumeMl: 18, colorFamily: 'Bleu soutenu',
    manufacturerDescription: armyPainterDescription, recommendedUses: ['Uniformes bleus', 'Armures', 'Énergie et tissus'], manufacturerVerifiedAt: verifiedAt,
  },
  'the-army-painter-speedpaint-zealot-yellow': {
    manufacturerUrl: 'https://thearmypainter.com/products/speedpaint-speedpaint-zealot-yellow-wp2013p', manufacturerImage: '/manufacturer/army-painter-zealot-yellow-wp2013p.jpg',
    manufacturerImageCredit: 'Packshot officiel The Army Painter · copie locale', volumeMl: 18, colorFamily: 'Jaune chaud',
    manufacturerDescription: armyPainterDescription, recommendedUses: ['Marquages jaunes', 'Tissus', 'Lumières chaudes'], manufacturerVerifiedAt: verifiedAt,
  },
  'the-army-painter-speedpaint-pallid-bone': {
    manufacturerUrl: 'https://thearmypainter.com/products/speedpaint-speedpaint-pallid-bone-wp2006p', manufacturerImage: '/manufacturer/army-painter-pallid-bone-wp2006p.jpg',
    manufacturerImageCredit: 'Packshot officiel The Army Painter · copie locale', volumeMl: 18, colorFamily: 'Os pâle',
    manufacturerDescription: armyPainterDescription, recommendedUses: ['Os et squelettes', 'Parchemins', 'Toiles et tissus beiges'], manufacturerVerifiedAt: verifiedAt,
  },
  'vallejo-xpress-color-mummy-white': {
    manufacturerUrl: 'https://acrylicosvallejo.com/wp-content/uploads/2025/10/Catalogo_2026-R02.pdf', manufacturerImage: '/manufacturer/vallejo-mummy-white-72449.png',
    manufacturerImageCredit: 'Packshot Acrylicos Vallejo · copie locale · source de distribution : Vallejo-Farben.de', volumeMl: 18, colorFamily: 'Blanc cassé',
    manufacturerDescription: vallejoXpressDescription, recommendedUses: ['Tissus blancs ombrés', 'Os pâle', 'Bandages et parchemins'], manufacturerVerifiedAt: verifiedAt,
  },
  'the-army-painter-speedpaint-crusader-skin': {
    manufacturerUrl: 'https://thearmypainter.com/products/speedpaint-speedpaint-crusader-skin-wp2004p', manufacturerImage: '/manufacturer/army-painter-crusader-skin-wp2004p.jpg',
    manufacturerImageCredit: 'Packshot officiel The Army Painter · copie locale', volumeMl: 18, colorFamily: 'Chair rougeâtre pâle',
    manufacturerDescription: armyPainterDescription, recommendedUses: ['Carnations claires', 'Visages et mains', 'Cuir rose-brun'], manufacturerVerifiedAt: verifiedAt,
  },
  'vallejo-xpress-color-fairy-skin': {
    manufacturerUrl: 'https://acrylicosvallejo.com/wp-content/uploads/2025/10/Catalogo_2026-R02.pdf', manufacturerImage: '/manufacturer/vallejo-fairy-skin-72457.png',
    manufacturerImageCredit: 'Packshot Acrylicos Vallejo · copie locale · source de distribution : Vallejo-Farben.de', volumeMl: 18, colorFamily: 'Chair rosée',
    manufacturerDescription: vallejoXpressDescription, recommendedUses: ['Carnations rosées', 'Créatures pâles', 'Tissus rose terre cuite'], manufacturerVerifiedAt: verifiedAt,
  },
};

type ImportedInventoryPaint = Omit<Paint, keyof ManufacturerInfo>;

const importedInventoryPaints: ImportedInventoryPaint[] = [
  { id: 'vallejo-game-color-metallic-silver', brand: 'Vallejo', range: 'Game Color Metallic', reference: '72.052', name: 'Silver', colorHex: '#B9BEC1', finish: 'métallique', medium: 'acrylique à l’eau', quantity: 1, tags: ['argent', 'métal', 'éclaircissement'], notes: 'Source : IMG_5311.jpeg', createdAt, updatedAt: createdAt },
  { id: 'vallejo-game-color-metallic-chainmail', brand: 'Vallejo', range: 'Game Color Metallic', reference: '72.053', name: 'Chainmail', colorHex: '#818A90', finish: 'métallique', medium: 'acrylique à l’eau', quantity: 1, tags: ['acier', 'métal', 'armure'], notes: 'Source : IMG_5311.jpeg', createdAt, updatedAt: createdAt },
  { id: 'the-army-painter-speedpaint-orc-skin', brand: 'The Army Painter', range: 'Speedpaint', reference: 'WP2009P', name: 'Orc Skin', colorHex: '#4E7C43', finish: 'transparent contrasté', medium: 'acrylique à l’eau', quantity: 1, tags: ['vert', 'peau', 'speedpaint'], notes: 'Source : IMG_5311.jpeg', createdAt, updatedAt: createdAt },
  { id: 'vallejo-xpress-color-lizard-green', brand: 'Vallejo', range: 'Xpress Color', reference: '72.418', name: 'Lizard Green', colorHex: '#315A3B', finish: 'mat transparent contrasté', medium: 'acrylique à l’eau', quantity: 1, tags: ['vert', 'écailles', 'xpress color'], notes: 'Source : IMG_5311.jpeg', createdAt, updatedAt: createdAt },
  { id: 'vallejo-xpress-color-khaki-drill', brand: 'Vallejo', range: 'Xpress Color', reference: '72.451', name: 'Khaki Drill', colorHex: '#766F48', finish: 'mat transparent contrasté', medium: 'acrylique à l’eau', quantity: 1, tags: ['kaki', 'uniforme', 'xpress color'], notes: 'Source : IMG_5311.jpeg', createdAt, updatedAt: createdAt },
  { id: 'vallejo-xpress-color-space-grey', brand: 'Vallejo', range: 'Xpress Color', reference: '72.422', name: 'Space Grey', colorHex: '#607383', finish: 'mat transparent contrasté', medium: 'acrylique à l’eau', quantity: 1, tags: ['gris bleu', 'uniforme', 'xpress color'], notes: 'Source : IMG_5311.jpeg', createdAt, updatedAt: createdAt },
  { id: 'vallejo-xpress-color-intense-viking-grey', brand: 'Vallejo', range: 'Xpress Color Intense', reference: '72.483', name: 'Viking Grey', colorHex: '#46515A', finish: 'mat transparent contrasté', medium: 'acrylique à l’eau', quantity: 1, tags: ['gris', 'bleu froid', 'xpress color intense'], notes: 'Source : IMG_5311.jpeg', createdAt, updatedAt: createdAt },
  { id: 'the-army-painter-speedpaint-hive-dweller-purple', brand: 'The Army Painter', range: 'Speedpaint', reference: 'WP2018P', name: 'Hive Dweller Purple', colorHex: '#533B65', finish: 'transparent contrasté', medium: 'acrylique à l’eau', quantity: 1, tags: ['violet', 'sombre', 'speedpaint'], notes: 'Source : IMG_5311.jpeg', createdAt, updatedAt: createdAt },
  { id: 'the-army-painter-speedpaint-fire-giant-orange', brand: 'The Army Painter', range: 'Speedpaint', reference: 'WP2017P', name: 'Fire Giant Orange', colorHex: '#D1662D', finish: 'transparent contrasté', medium: 'acrylique à l’eau', quantity: 1, tags: ['orange', 'feu', 'speedpaint'], notes: 'Source : IMG_5311.jpeg', createdAt, updatedAt: createdAt },
  { id: 'the-army-painter-speedpaint-blood-red', brand: 'The Army Painter', range: 'Speedpaint', reference: 'WP2010P', name: 'Blood Red', colorHex: '#A42E31', finish: 'transparent contrasté', medium: 'acrylique à l’eau', quantity: 1, tags: ['rouge', 'sang', 'speedpaint'], notes: 'Source : IMG_5311.jpeg', createdAt, updatedAt: createdAt },
  { id: 'the-army-painter-speedpaint-highlord-blue', brand: 'The Army Painter', range: 'Speedpaint', reference: 'WP2015P', name: 'Highlord Blue', colorHex: '#335991', finish: 'transparent contrasté', medium: 'acrylique à l’eau', quantity: 1, tags: ['bleu', 'uniforme', 'speedpaint'], notes: 'Source : IMG_5311.jpeg', createdAt, updatedAt: createdAt },
  { id: 'the-army-painter-speedpaint-zealot-yellow', brand: 'The Army Painter', range: 'Speedpaint', reference: 'WP2013P', name: 'Zealot Yellow', colorHex: '#D4B12F', finish: 'transparent contrasté', medium: 'acrylique à l’eau', quantity: 1, tags: ['jaune', 'signalétique', 'speedpaint'], notes: 'Source : IMG_5311.jpeg', createdAt, updatedAt: createdAt },
  { id: 'the-army-painter-speedpaint-pallid-bone', brand: 'The Army Painter', range: 'Speedpaint', reference: 'WP2006P', name: 'Pallid Bone', colorHex: '#C4B486', finish: 'transparent contrasté', medium: 'acrylique à l’eau', quantity: 1, tags: ['os', 'beige', 'speedpaint'], notes: 'Source : IMG_5311.jpeg', createdAt, updatedAt: createdAt },
  { id: 'vallejo-xpress-color-mummy-white', brand: 'Vallejo', range: 'Xpress Color', reference: '72.449', name: 'Mummy White', colorHex: '#D8D5C5', finish: 'mat transparent contrasté', medium: 'acrylique à l’eau', quantity: 1, tags: ['blanc cassé', 'os', 'xpress color'], notes: 'Source : IMG_5311.jpeg', createdAt, updatedAt: createdAt },
  { id: 'the-army-painter-speedpaint-crusader-skin', brand: 'The Army Painter', range: 'Speedpaint', reference: 'WP2004P', name: 'Crusader Skin', colorHex: '#C1846C', finish: 'transparent contrasté', medium: 'acrylique à l’eau', quantity: 1, tags: ['chair', 'peau', 'speedpaint'], notes: 'Source : IMG_5311.jpeg', createdAt, updatedAt: createdAt },
  { id: 'vallejo-xpress-color-fairy-skin', brand: 'Vallejo', range: 'Xpress Color', reference: '72.457', name: 'Fairy Skin', colorHex: '#CA8774', finish: 'mat transparent contrasté', medium: 'acrylique à l’eau', quantity: 1, tags: ['chair', 'rose', 'xpress color'], notes: 'Source : IMG_5311.jpeg', createdAt, updatedAt: createdAt },
];

export const importedPaints5311: Paint[] = importedInventoryPaints.map((paint) => ({
  ...paint,
  ...importedManufacturerCatalog[paint.id],
}));
