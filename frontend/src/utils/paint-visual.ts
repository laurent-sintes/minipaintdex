import type { PaintCardModel, ManufacturerInfo, PersonalPaintPhoto } from '../models/paint-model';

type Quality = ManufacturerInfo['manufacturerImageQuality'];
const levels: Quality[] = ['none', 'color_swatch', 'generic_visual', 'owned_photo', 'retailer_photo', 'official_photo'];

/** Display level only: the domain keeps its best-first provenance rank. */
export function paintVisualLevel(quality: Quality): number { return levels.indexOf(quality) + 1; }

export type PaintVisual = { url: string; quality: Quality; personalPhoto?: PersonalPaintPhoto };

/** The server has already selected the eligible personal photo; failures only affect rendering. */
export function displayedPaintVisual(paint: PaintCardModel, failed: readonly string[]): PaintVisual {
  const personal = paint.personalPhoto;
  const personalUrl = personal && [personal.url, personal.originalUrl].find(url => url && !failed.includes(url));
  if (personalUrl) return { url: personalUrl, quality: 'owned_photo', personalPhoto: personal };
  const catalogUrl = [paint.manufacturerImage, paint.manufacturerImageSource].find(url => url && !failed.includes(url));
  if (catalogUrl) return { url: catalogUrl, quality: paint.manufacturerImageQuality };
  return { url: '', quality: /^#[0-9a-f]{6}$/i.test(paint.colorHex) ? 'color_swatch' : 'none' };
}
