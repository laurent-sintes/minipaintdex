import type { PaintProduct } from './paint-model';

export type PaintPot = {
  paintPotId: string; paintProductId: string; version: number; paintProduct: PaintProduct;
  condition: string; remainingLevel: string; possession: string; available: boolean;
  registeredAt: string; acquiredAt: string | null; openedAt: string | null;
  photos: Array<{ mediaId: string; url: string; caption: string; addedAt: string }>;
  notes: Array<{ text: string; addedAt: string }>;
  _links?: Record<string, { href: string }>;
};
