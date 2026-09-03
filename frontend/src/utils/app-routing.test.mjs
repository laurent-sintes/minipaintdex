import assert from 'node:assert/strict';
import test from 'node:test';
import { appRoutePath, parseAppRoute, isNavigationDestinationCurrent } from './app-routing.ts';

test('highlights the direct destination without selecting a neighboring context', () => {
  const destinations = ['home', 'marketPaints', 'marketProducts', 'workshopPaints', 'workshop', 'shopping', 'aboutUser', 'aboutAdmin', 'aboutPaintModel', 'aboutApi', 'aboutVersion'];
  for (const view of destinations) {
    assert.deepEqual(destinations.filter(destination => isNavigationDestinationCurrent({ view }, destination)), [view]);
  }
});

test('keeps market details and personal project details under distinct destinations', () => {
  const product = { view: 'product', productId: 'game' };
  assert.equal(isNavigationDestinationCurrent(product, 'marketProducts'), true);
  assert.equal(isNavigationDestinationCurrent(product, 'workshop'), false);
  const project = { ...product, paintingProjectId: 'my-game' };
  assert.equal(isNavigationDestinationCurrent(project, 'marketProducts'), false);
  assert.equal(isNavigationDestinationCurrent(project, 'workshop'), true);
  assert.equal(isNavigationDestinationCurrent({ view: 'item', itemId: 'copy-1' }, 'workshop'), true);
});

test('round-trips a direct market paintable-product item URL', () => {
  const route = parseAppRoute('/market/paintable-products/reichbusters-reloaded/items/red-hawk');
  assert.deepEqual(route, {
    view: 'product',
    productId: 'reichbusters-reloaded',
    catalogItemId: 'red-hawk',
  });
  assert.equal(appRoutePath(route), '/market/paintable-products/reichbusters-reloaded/items/red-hawk');
});

test('distinguishes a painting project from its market reference', () => {
  const route = parseAppRoute('/workshop/painting-projects/paint-reichbusters/products/reichbusters-reloaded');
  assert.deepEqual(route, { view: 'product', paintingProjectId: 'paint-reichbusters', productId: 'reichbusters-reloaded', catalogItemId: undefined });
  assert.equal(appRoutePath(route), '/workshop/painting-projects/paint-reichbusters/products/reichbusters-reloaded');
});

test('routes each about page independently', () => {
  assert.equal(appRoutePath(parseAppRoute('/about/user')), '/about/user');
  assert.equal(appRoutePath(parseAppRoute('/about/admin')), '/about/admin');
  assert.equal(appRoutePath(parseAppRoute('/about/admin/paint-model')), '/about/admin/paint-model');
  assert.equal(appRoutePath(parseAppRoute('/about/api')), '/about/api');
  assert.equal(appRoutePath(parseAppRoute('/about/version')), '/about/version');
});

test('round-trips a physical workshop item URL', () => {
  const route = parseAppRoute('/workshop/items/ws-red-hawk-1');
  assert.deepEqual(route, { view: 'item', itemId: 'ws-red-hawk-1' });
  assert.equal(appRoutePath(route), '/workshop/items/ws-red-hawk-1');
});
