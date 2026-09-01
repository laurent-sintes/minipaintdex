import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { formatMetadata, metadataOptions, normalizeSearch, paintFacetSearchParams, paintPageSearchParams, sameMetadata } from './paint-search.ts';

describe('paint search helpers', () => {
  it('normalizes accents and case for tolerant searches', () => {
    assert.equal(normalizeSearch('  Pré-éclairage  '), 'pre-eclairage');
  });

  it('aggregates and sorts facet values', () => {
    assert.deepEqual(metadataOptions(['metallic', 'one_coat_contrast', 'Metallic', '']), [
      { value: 'metallic', count: 2 },
      { value: 'one_coat_contrast', count: 1 },
    ]);
  });

  it('matches metadata independently of case and accents', () => {
    assert.equal(sameMetadata('Acrylique à l’eau', 'ACRYLIQUE A L’EAU'), true);
  });

  it('formats core identifiers only for presentation', () => {
    assert.equal(formatMetadata('one_coat-contrast'), 'One coat contrast');
  });

  it('keeps published toggle filters consistent between pages and facets', () => {
    const filters = { role: 'wash', realResultOnly: 'true', manufacturerSheetOnly: '' };

    assert.equal(paintFacetSearchParams(' ink ', filters).toString(), 'query=ink&role=wash&realResultOnly=true');
    assert.equal(
      paintPageSearchParams(' ink ', filters, 'verifiedAt,desc', 2, 60).toString(),
      'query=ink&role=wash&realResultOnly=true&page=2&size=60&sort=verifiedAt%2Cdesc',
    );
  });
});
