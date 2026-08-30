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

export type ShoppingItem = {
  id: string;
  brand: string;
  name: string;
  reference: string;
  colorHex: string;
  reason: string;
  priority: 'haute' | 'moyenne' | 'basse';
};
