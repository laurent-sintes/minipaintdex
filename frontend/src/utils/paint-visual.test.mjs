import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { displayedPaintVisual, paintVisualLevel } from './paint-visual.ts';

const paint = { manufacturerImage: '/catalog.png', manufacturerImageSource: 'https://example.com/photo.png',
  manufacturerImageQuality: 'generic_visual', colorHex: '#abcdef' };
const personalPhoto = { paintPotId: 'pot', url: '/cutout.png', originalUrl: '/original.png', addedAt: '2026-09-01T00:00:00Z' };

describe('representative paint visual', () => {
  it('fills the six-level gauge toward the best provenance, not the best-first rank', () => {
    assert.deepEqual(['none', 'color_swatch', 'generic_visual', 'owned_photo', 'retailer_photo', 'official_photo'].map(paintVisualLevel), [1, 2, 3, 4, 5, 6]);
  });
  it('uses the server-selected photo and retains its provenance when falling back to its original', () => {
    assert.equal(displayedPaintVisual({ ...paint, personalPhoto }, []).url, '/cutout.png');
    const fallback = displayedPaintVisual({ ...paint, personalPhoto }, ['/cutout.png']);
    assert.equal(fallback.url, '/original.png');
    assert.equal(fallback.quality, 'owned_photo');
    assert.equal(fallback.personalPhoto, personalPhoto);
  });
  it('updates provenance as unavailable images fall back to catalog, swatch, then no visual', () => {
    const failed = ['/cutout.png', '/original.png'];
    assert.deepEqual(displayedPaintVisual({ ...paint, personalPhoto }, failed), { url: '/catalog.png', quality: 'generic_visual' });
    failed.push('/catalog.png');
    assert.equal(displayedPaintVisual(paint, failed).url, paint.manufacturerImageSource);
    failed.push(paint.manufacturerImageSource);
    assert.deepEqual(displayedPaintVisual(paint, failed), { url: '', quality: 'color_swatch' });
    assert.deepEqual(displayedPaintVisual({ ...paint, colorHex: '' }, failed), { url: '', quality: 'none' });
  });
  it('keeps official and retailer labels when the server publishes no personal selection', () => {
    for (const quality of ['official_photo', 'retailer_photo']) {
      assert.equal(displayedPaintVisual({ ...paint, personalPhoto: null, manufacturerImageQuality: quality }, []).quality, quality);
    }
  });
});
