import assert from 'node:assert/strict';
import { test } from 'node:test';
import { paintSearchRequest } from './paint-search-request.ts';
import { nextSuggestionIndex } from './paint-suggestions.ts';

test('suggestions preserve all filters and their context, without page or sort parameters', () => {
  for (const collection of ['/api/v1/market/paint-products', '/api/v1/workshop/paint-stocks']) {
    const request = paintSearchRequest(collection, ' kar ', { brand: ['A', 'B'], range: ['C::Air'], manufacturerSheetOnly: ['true'] }, { include: ['suggestions'] });
    const url = new URL(request.url, 'http://localhost');
    assert.equal(url.pathname, `${collection}/search`);
    assert.equal(request.body.query, 'kar');
    assert.deepEqual(request.body.filters.brand, ['A', 'B']);
    assert.deepEqual(request.body.filters.range, ['C::Air']);
    assert.equal(request.body.filters.manufacturerSheetOnly, true);
    assert.equal(url.searchParams.has('sort'), false);
    assert.equal(url.searchParams.has('page'), false);
  }
});

test('keyboard navigation wraps and handles an empty or initially unselected list', () => {
  assert.equal(nextSuggestionIndex(-1, 0, 1), -1);
  assert.equal(nextSuggestionIndex(-1, 3, 1), 0);
  assert.equal(nextSuggestionIndex(-1, 3, -1), 2);
  assert.equal(nextSuggestionIndex(2, 3, 1), 0);
  assert.equal(nextSuggestionIndex(0, 3, -1), 2);
});

test('search selects both parts with paging in the URL and immutable filters in the body', () => {
  const filters = { color: ['red', 'blue'], realResultOnly: ['false'] };
  const request = paintSearchRequest('/api/v1/market/paint-products', ' red ', filters,
    { include: ['results', 'suggestions'], page: 2, size: 10, sort: 'name,desc', suggestionLimit: 3 });
  const url = new URL(request.url, 'http://localhost');
  assert.equal(url.searchParams.get('page'), '2');
  assert.equal(url.searchParams.get('size'), '10');
  assert.equal(url.searchParams.get('sort'), 'name,desc');
  assert.equal(url.searchParams.has('query'), false);
  assert.deepEqual(request.body.include, ['results', 'suggestions']);
  assert.equal(request.body.suggestionLimit, 3);
  assert.equal(request.body.filters.realResultOnly, false);
  filters.color.push('green');
  assert.deepEqual(request.body.filters.color, ['red', 'blue']);
  assert.deepEqual(paintSearchRequest('/collection', '', {}).body.include, ['results']);
});
