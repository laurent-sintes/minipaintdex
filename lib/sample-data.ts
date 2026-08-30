import { importedManufacturerCatalog, importedPaints5311 } from './imported-paints-img-5311';

export type ManufacturerInfo = {
  manufacturerUrl: string;
  manufacturerImage: string;
  manufacturerImageCredit: string;
  volumeMl: number;
  colorFamily: string;
  manufacturerDescription: string;
  recommendedUses: string[];
  manufacturerVerifiedAt: string;
};

export type Paint = ManufacturerInfo & {
  id: string;
  brand: string;
  range: string;
  reference: string;
  name: string;
  colorHex: string;
  finish: string;
  medium: string;
  quantity: number;
  tags: string[];
  notes: string;
  createdAt: string;
  updatedAt: string;
};

const now = '2026-08-30T10:00:00.000Z';
const officialDescription = 'Base, ombre et éclaircissements en une seule application. Formule à l’eau.';
const verifiedAt = '2026-08-30';

export const emptyManufacturerInfo: ManufacturerInfo = {
  manufacturerUrl: '', manufacturerImage: '', manufacturerImageCredit: '', volumeMl: 0,
  colorFamily: '', manufacturerDescription: '', recommendedUses: [], manufacturerVerifiedAt: '',
};

export const manufacturerCatalog: Record<string, ManufacturerInfo> = {
  'cit-contrast-leviadon-blue': {
    manufacturerUrl: 'https://www.warhammer.com/shop/Leviadon-Blue-2019', manufacturerImage: '/manufacturer/leviadon-blue.jpg',
    manufacturerImageCredit: 'Packshot Citadel · copie locale · source de distribution : Ludifolie', volumeMl: 18, colorFamily: 'Bleu sombre',
    manufacturerDescription: officialDescription, recommendedUses: ['Armures bleu nuit', 'Tissus profonds', 'Ombres froides'], manufacturerVerifiedAt: verifiedAt,
  },
  'cit-contrast-terradon-turquoise': {
    manufacturerUrl: 'https://www.warhammer.com/shop/Terradon-Turquoise-2019', manufacturerImage: '/manufacturer/terradon-turquoise.jpg',
    manufacturerImageCredit: 'Packshot Citadel · copie locale · source de distribution : The Art Store', volumeMl: 18, colorFamily: 'Turquoise profond',
    manufacturerDescription: officialDescription, recommendedUses: ['Écailles', 'Eaux profondes', 'Effets magiques'], manufacturerVerifiedAt: verifiedAt,
  },
  'cit-contrast-kroxigor-scales': {
    manufacturerUrl: 'https://www.warhammer.com/shop/contrast-kroxigor-scales-18ml-2022', manufacturerImage: '/manufacturer/kroxigor-scales.png',
    manufacturerImageCredit: 'Packshot Citadel · copie locale · source de distribution : Wargame Portal', volumeMl: 18, colorFamily: 'Turquoise vif',
    manufacturerDescription: officialDescription, recommendedUses: ['Peaux et écailles', 'Gemmes et fumées', 'Vert-de-gris dilué'], manufacturerVerifiedAt: verifiedAt,
  },
  'cit-contrast-pylar-glacier': {
    manufacturerUrl: 'https://www.warhammer.com/shop/contrast-pylar-glacier-18ml-2022', manufacturerImage: '/manufacturer/pylar-glacier.png',
    manufacturerImageCredit: 'Packshot Citadel · copie locale · visuel Games Workshop Trade via Herrick Games', volumeMl: 18, colorFamily: 'Bleu glacier',
    manufacturerDescription: officialDescription, recommendedUses: ['Glace et givre', 'Lueurs sur métal', 'Effets énergétiques pâles'], manufacturerVerifiedAt: verifiedAt,
  },
  'cit-contrast-wyldwood': {
    manufacturerUrl: 'https://www.warhammer.com/shop/Wyldwood-2019', manufacturerImage: '/manufacturer/wyldwood.jpg',
    manufacturerImageCredit: 'Packshot Citadel · copie locale · source de distribution : Pandemonium Books & Games', volumeMl: 18, colorFamily: 'Brun très sombre',
    manufacturerDescription: officialDescription, recommendedUses: ['Bois', 'Cuir sombre', 'Fourrures et ombres brunes'], manufacturerVerifiedAt: verifiedAt,
  },
  'cit-contrast-cygor-brown': {
    manufacturerUrl: 'https://www.warhammer.com/shop/Cygor-Brown-2019', manufacturerImage: '/manufacturer/cygor-brown.png',
    manufacturerImageCredit: 'Packshot Citadel · copie locale · source de distribution : Wargames Emporium', volumeMl: 18, colorFamily: 'Brun profond',
    manufacturerDescription: officialDescription, recommendedUses: ['Bois très sombre', 'Cuir profond', 'Ombres chaudes'], manufacturerVerifiedAt: verifiedAt,
  },
  'cit-contrast-aggaros-dunes': {
    manufacturerUrl: 'https://www.warhammer.com/shop/Aggaros-Dunes-2019', manufacturerImage: '/manufacturer/aggaros-dunes.jpg',
    manufacturerImageCredit: 'Packshot Citadel · copie locale · source de distribution : The Art Store', volumeMl: 18, colorFamily: 'Ocre sable',
    manufacturerDescription: officialDescription, recommendedUses: ['Sable et terre', 'Cuir clair', 'Os vieilli et tissus ocres'], manufacturerVerifiedAt: verifiedAt,
  },
  'cit-contrast-gore-grunta-fur': {
    manufacturerUrl: 'https://www.warhammer.com/shop/Gore-Grunta-Fur-2019', manufacturerImage: '/manufacturer/gore-grunta-fur.jpg',
    manufacturerImageCredit: 'Packshot Citadel · copie locale · source de distribution : Hobby-Max', volumeMl: 18, colorFamily: 'Brun roux',
    manufacturerDescription: officialDescription, recommendedUses: ['Fourrures', 'Cuir roux', 'Bois chaud'], manufacturerVerifiedAt: verifiedAt,
  },
  'cit-contrast-darkoath-flesh': {
    manufacturerUrl: 'https://www.warhammer.com/shop/Darkoath-Flesh-2019', manufacturerImage: '/manufacturer/darkoath-flesh.jpg',
    manufacturerImageCredit: 'Packshot Citadel · copie locale · source de distribution : More Hobby Games', volumeMl: 18, colorFamily: 'Chair hâlée',
    manufacturerDescription: officialDescription, recommendedUses: ['Carnations hâlées', 'Cuir clair', 'Tissus terre cuite'], manufacturerVerifiedAt: verifiedAt,
  },
  'cit-contrast-nazdreg-yellow': {
    manufacturerUrl: 'https://www.warhammer.com/shop/Nazdreg-Yellow-2019', manufacturerImage: '/manufacturer/nazdreg-yellow.png',
    manufacturerImageCredit: 'Packshot Citadel · copie locale · visuel Games Workshop Trade via The Guardtower', volumeMl: 18, colorFamily: 'Jaune moutarde',
    manufacturerDescription: officialDescription, recommendedUses: ['Jaunes ombrés', 'Os chaud', 'Métal doré patiné'], manufacturerVerifiedAt: verifiedAt,
  },
  'cit-contrast-guilliman-flesh': {
    manufacturerUrl: 'https://www.warhammer.com/shop/Guilliman-Flesh-2019', manufacturerImage: '/manufacturer/guilliman-flesh.png',
    manufacturerImageCredit: 'Packshot Citadel · copie locale · source de distribution : The Forge', volumeMl: 18, colorFamily: 'Chair chaude',
    manufacturerDescription: officialDescription, recommendedUses: ['Carnations claires', 'Visages', 'Mains et détails de peau'], manufacturerVerifiedAt: verifiedAt,
  },
  'cit-contrast-talassar-blue': {
    manufacturerUrl: 'https://www.warhammer.com/shop/Talassar-Blue-2019', manufacturerImage: '/manufacturer/talassar-blue.jpg',
    manufacturerImageCredit: 'Packshot Citadel · copie locale · source de distribution : Hobby-Max', volumeMl: 18, colorFamily: 'Bleu vif',
    manufacturerDescription: officialDescription, recommendedUses: ['Armures bleues vives', 'Énergie', 'Tissus saturés'], manufacturerVerifiedAt: verifiedAt,
  },
  'cit-contrast-briar-queen-chill': {
    manufacturerUrl: 'https://www.warhammer.com/shop/contrast-briar-queen-chill-18ml-2022', manufacturerImage: '/manufacturer/briar-queen-chill-clean.png',
    manufacturerImageCredit: 'Packshot Citadel · copie locale · visuel Games Workshop Trade via TC War Room', volumeMl: 18, colorFamily: 'Bleu spectral',
    manufacturerDescription: officialDescription, recommendedUses: ['Fantômes', 'Fumées et apparitions', 'Glace et blancs corrompus'], manufacturerVerifiedAt: verifiedAt,
  },
  'cit-contrast-ironjawz-yellow': {
    manufacturerUrl: 'https://www.warhammer.com/shop/contrast-ironjawz-yellow-18ml-2022', manufacturerImage: '/manufacturer/ironjawz-yellow.png',
    manufacturerImageCredit: 'Packshot Citadel · copie locale · visuel Games Workshop Trade via Kick-Ass Mail Order', volumeMl: 18, colorFamily: 'Jaune ocre',
    manufacturerDescription: officialDescription, recommendedUses: ['Armures jaunes', 'Cuir ocre', 'Grandes surfaces texturées'], manufacturerVerifiedAt: verifiedAt,
  },
  'cit-contrast-imperial-fist': {
    manufacturerUrl: 'https://www.warhammer.com/shop/contrast-imperial-fist-18ml-2022', manufacturerImage: '/manufacturer/imperial-fist.jpg',
    manufacturerImageCredit: 'Packshot Citadel · copie locale · source de distribution : L’Atelier du Train', volumeMl: 18, colorFamily: 'Jaune vif',
    manufacturerDescription: officialDescription, recommendedUses: ['Armures jaune vif', 'Héraldiques', 'Aplats saturés sur sous-couche claire'], manufacturerVerifiedAt: verifiedAt,
  },
  ...importedManufacturerCatalog,
};

type InventoryPaint = Omit<Paint, keyof ManufacturerInfo>;

const manufacturerReferences: Record<string, string> = {
  'cit-contrast-leviadon-blue': '29-17', 'cit-contrast-kroxigor-scales': '29-55',
  'cit-contrast-pylar-glacier': '29-58', 'cit-contrast-wyldwood': '29-30',
  'cit-contrast-darkoath-flesh': '29-33', 'cit-contrast-nazdreg-yellow': '29-21',
  'cit-contrast-guilliman-flesh': '29-32', 'cit-contrast-talassar-blue': '29-39',
  'cit-contrast-briar-queen-chill': '29-56', 'cit-contrast-ironjawz-yellow': '29-52',
  'cit-contrast-imperial-fist': '29-54',
};

const inventoryPaints: InventoryPaint[] = [
  { id: 'cit-contrast-leviadon-blue', brand: 'Citadel', range: 'Contrast', reference: '29-17', name: 'Leviadon Blue', colorHex: '#112c4f', finish: 'transparent', medium: 'acrylique', quantity: 1, tags: ['bleu', 'ombre', 'contrast'], notes: 'Source : IMG_5310.jpeg', createdAt: now, updatedAt: now },
  { id: 'cit-contrast-terradon-turquoise', brand: 'Citadel', range: 'Contrast', reference: '', name: 'Terradon Turquoise', colorHex: '#0f6b70', finish: 'transparent', medium: 'acrylique', quantity: 1, tags: ['turquoise', 'bleu', 'contrast'], notes: 'Source : IMG_5310.jpeg', createdAt: now, updatedAt: now },
  { id: 'cit-contrast-kroxigor-scales', brand: 'Citadel', range: 'Contrast', reference: '29-55', name: 'Kroxigor Scales', colorHex: '#0b7f91', finish: 'transparent', medium: 'acrylique', quantity: 1, tags: ['turquoise', 'bleu', 'contrast'], notes: 'Source : IMG_5310.jpeg', createdAt: now, updatedAt: now },
  { id: 'cit-contrast-pylar-glacier', brand: 'Citadel', range: 'Contrast', reference: '29-58', name: 'Pylar Glacier', colorHex: '#45c5e9', finish: 'transparent', medium: 'acrylique', quantity: 1, tags: ['bleu clair', 'glace', 'contrast'], notes: 'Source : IMG_5310.jpeg', createdAt: now, updatedAt: now },
  { id: 'cit-contrast-wyldwood', brand: 'Citadel', range: 'Contrast', reference: '29-30', name: 'Wyldwood', colorHex: '#2e241e', finish: 'transparent', medium: 'acrylique', quantity: 1, tags: ['brun', 'bois', 'ombre', 'contrast'], notes: 'Source : IMG_5310.jpeg', createdAt: now, updatedAt: now },
  { id: 'cit-contrast-cygor-brown', brand: 'Citadel', range: 'Contrast', reference: '', name: 'Cygor Brown', colorHex: '#3a2322', finish: 'transparent', medium: 'acrylique', quantity: 1, tags: ['brun', 'cuir', 'ombre', 'contrast'], notes: 'Source : IMG_5310.jpeg', createdAt: now, updatedAt: now },
  { id: 'cit-contrast-aggaros-dunes', brand: 'Citadel', range: 'Contrast', reference: '', name: 'Aggaros Dunes', colorHex: '#806b43', finish: 'transparent', medium: 'acrylique', quantity: 1, tags: ['ocre', 'sable', 'cuir', 'contrast'], notes: 'Source : IMG_5310.jpeg', createdAt: now, updatedAt: now },
  { id: 'cit-contrast-gore-grunta-fur', brand: 'Citadel', range: 'Contrast', reference: '', name: 'Gore-Grunta Fur', colorHex: '#6d3524', finish: 'transparent', medium: 'acrylique', quantity: 1, tags: ['brun rouge', 'fourrure', 'cuir', 'contrast'], notes: 'Source : IMG_5310.jpeg', createdAt: now, updatedAt: now },
  { id: 'cit-contrast-darkoath-flesh', brand: 'Citadel', range: 'Contrast', reference: '29-33', name: 'Darkoath Flesh', colorHex: '#9a6b58', finish: 'transparent', medium: 'acrylique', quantity: 1, tags: ['chair', 'peau', 'contrast'], notes: 'Source : IMG_5310.jpeg', createdAt: now, updatedAt: now },
  { id: 'cit-contrast-nazdreg-yellow', brand: 'Citadel', range: 'Contrast', reference: '29-21', name: 'Nazdreg Yellow', colorHex: '#8b7c21', finish: 'transparent', medium: 'acrylique', quantity: 1, tags: ['jaune', 'ocre', 'contrast'], notes: 'Source : IMG_5310.jpeg', createdAt: now, updatedAt: now },
  { id: 'cit-contrast-guilliman-flesh', brand: 'Citadel', range: 'Contrast', reference: '29-32', name: 'Guilliman Flesh', colorHex: '#a76550', finish: 'transparent', medium: 'acrylique', quantity: 1, tags: ['chair', 'peau', 'contrast'], notes: 'Source : IMG_5310.jpeg', createdAt: now, updatedAt: now },
  { id: 'cit-contrast-talassar-blue', brand: 'Citadel', range: 'Contrast', reference: '29-39', name: 'Talassar Blue', colorHex: '#155cc4', finish: 'transparent', medium: 'acrylique', quantity: 1, tags: ['bleu', 'vif', 'contrast'], notes: 'Source : IMG_5310.jpeg', createdAt: now, updatedAt: now },
  { id: 'cit-contrast-briar-queen-chill', brand: 'Citadel', range: 'Contrast', reference: '29-56', name: 'Briar Queen Chill', colorHex: '#74c4c9', finish: 'transparent', medium: 'acrylique', quantity: 1, tags: ['bleu clair', 'spectral', 'glace', 'contrast'], notes: 'Source : IMG_5310.jpeg', createdAt: now, updatedAt: now },
  { id: 'cit-contrast-ironjawz-yellow', brand: 'Citadel', range: 'Contrast', reference: '29-52', name: 'Ironjawz Yellow', colorHex: '#d6a800', finish: 'transparent', medium: 'acrylique', quantity: 1, tags: ['jaune', 'ocre', 'contrast'], notes: 'Source : IMG_5310.jpeg', createdAt: now, updatedAt: now },
  { id: 'cit-contrast-imperial-fist', brand: 'Citadel', range: 'Contrast', reference: '29-54', name: 'Imperial Fist', colorHex: '#ffb300', finish: 'transparent', medium: 'acrylique', quantity: 1, tags: ['jaune', 'vif', 'contrast'], notes: 'Source : IMG_5310.jpeg', createdAt: now, updatedAt: now },
];

export function enrichPaint<T extends InventoryPaint>(paint: T): T & ManufacturerInfo {
  const toSlug = (value: string) => value.toLocaleLowerCase('en').normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
  const semanticId = toSlug(`${paint.brand}-${paint.range}-${paint.name}`);
  const citadelId = `cit-contrast-${toSlug(paint.name)}`;
  return {
    ...paint,
    reference: paint.reference || manufacturerReferences[citadelId] || '',
    ...(manufacturerCatalog[paint.id] ?? manufacturerCatalog[semanticId] ?? manufacturerCatalog[citadelId] ?? emptyManufacturerInfo),
  };
}

export const samplePaints: Paint[] = [...inventoryPaints.map(enrichPaint), ...importedPaints5311];

export const shoppingSeed = [
  { id: 'buy-1', brand: 'The Army Painter', name: 'Matt Black Colour Primer', reference: '', colorHex: '#242526', reason: 'Sous-couche noire du projet Reichbusters', priority: 'haute' },
  { id: 'buy-2', brand: 'Warhammer Colour', name: 'Nuln Oil', reference: '', colorHex: '#2d2e2e', reason: 'Ombres des armes, armures et uniformes', priority: 'haute' },
  { id: 'buy-3', brand: 'Vallejo', name: 'Dead White', reference: '72.001', colorHex: '#f2f1e8', reason: 'Zénithal au pinceau et cœur des lueurs Vril', priority: 'moyenne' },
];
