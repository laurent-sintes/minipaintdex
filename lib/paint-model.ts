export type ManufacturerInfo = {
  manufacturerUrl: string;
  manufacturerImage: string;
  manufacturerImageCredit: string;
  volumeMl: number;
  colorFamily: string;
  manufacturerDescription: string;
  recommendedUses: string[];
  manufacturerVerifiedAt: string;
  resultImage: string;
  resultImageCredit: string;
  resultImageSource: string;
  resultImageLicense: string;
  resultReferenceUrl: string;
};

export type Paint = ManufacturerInfo & {
  id: string;
  brand: string;
  manufacturer: string;
  brandAliases: string[];
  range: string;
  paintType: string;
  reference: string;
  name: string;
  colorHex: string;
  finish: string;
  medium: string;
  quantity: number;
  status: string;
  warnings: string;
  tags: string[];
  notes: string;
  createdAt: string;
  updatedAt: string;
};

export type ShoppingItem = {
  id: string;
  brand: string;
  name: string;
  reference: string;
  colorHex: string;
  reason: string;
  priority: 'haute' | 'moyenne' | 'basse';
};
