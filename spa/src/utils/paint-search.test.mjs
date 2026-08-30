import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { formatMetadata, metadataOptions, normalizeSearch, sameMetadata } from './paint-search.ts';

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
});
