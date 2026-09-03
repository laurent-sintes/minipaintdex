import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { formatMetadata, metadataOptions, normalizeSearch, paintFacetSearchParams, paintPageSearchParams, sameMetadata, togglePaintFilter, readPaintSearch, paintBrowserSearchParams } from './paint-search.ts';

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
    const filters = { role: ['wash'], realResultOnly: ['true'], manufacturerSheetOnly: [] };

    assert.equal(paintFacetSearchParams(' ink ', filters).toString(), 'query=ink&role=wash&realResultOnly=true');
    assert.equal(
      paintPageSearchParams(' ink ', filters, 'verifiedAt,desc', 2, 60).toString(),
      'query=ink&role=wash&realResultOnly=true&page=2&size=60&sort=verifiedAt%2Cdesc',
    );
  });

  it('preserves repeated values and brand-qualified ranges in page and facet queries', () => {
    const filters = { brand: ['Vallejo', 'AK Interactive'], range: ['Warhammer Colour::Contrast'], color: ['blue', 'red'] };
    const facets = paintFacetSearchParams('', filters);
    const page = paintPageSearchParams('', filters, 'name,asc', 0, 60);
    for (const [key, values] of Object.entries(filters)) {
      assert.deepEqual(facets.getAll(key), values);
      assert.deepEqual(page.getAll(key), values);
    }
  });

  it('toggles a value without mutating existing selections', () => {
    const initial = { color: ['blue'] };
    assert.deepEqual(togglePaintFilter(initial, 'color', 'red'), { color: ['blue', 'red'] });
    assert.deepEqual(togglePaintFilter(initial, 'color', 'blue'), {});
    assert.deepEqual(initial, { color: ['blue'] });
  });

  it('round-trips search state including OR selections and paging', () => {
    const state = { query: 'Bleu acier', filters: { color: ['blue', 'grey'], range: ['Vallejo::Model Air'] }, sort: 'brand,asc', page: 2 };
    assert.deepEqual(readPaintSearch(paintBrowserSearchParams(state).toString(), ['color', 'range'], ['name,asc', 'brand,asc']), state);
  });

  it('rejects unknown URL parameters and invalid paging or sorts', () => {
    assert.deepEqual(readPaintSearch('?color=blue&color=blue&color=&unknown=x&page=-3&sort=invalid', ['color'], ['name,asc']), {
      query: '', filters: { color: ['blue'] }, sort: 'name,asc', page: 0,
    });
    assert.equal(readPaintSearch('?page=Infinity', [], []).page, 0);
    assert.equal(paintBrowserSearchParams({ query: '', filters: {}, sort: 'name,asc', page: 0 }).toString(), '');
  });
});
