import assert from 'node:assert/strict';
import test from 'node:test';
import { appRoutePath, parseAppRoute } from './app-routing.ts';

test('round-trips a direct market paintable-product item URL', () => {
  const route = parseAppRoute('/market/paintable-products/reichbusters-reloaded/items/red-hawk');
  assert.deepEqual(route, {
    view: 'product',
    productId: 'reichbusters-reloaded',
    catalogItemId: 'red-hawk',
  });
  assert.equal(appRoutePath(route), '/market/paintable-products/reichbusters-reloaded/items/red-hawk');
});

test('distinguishes a workshop product from the market reference', () => {
  const route = parseAppRoute('/workshop/paintable-products/reichbusters-reloaded');
  assert.equal(route.workshopProduct, true);
  assert.equal(appRoutePath(route), '/workshop/paintable-products/reichbusters-reloaded');
});
