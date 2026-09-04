import assert from 'node:assert/strict';
import { test as rackRouteTest } from 'node:test';
import { appRoutePath as rackRoutePath, parseAppRoute as parseRackRoute, isNavigationDestinationCurrent as rackNavigation } from './app-routing.ts';

rackRouteTest('round-trips market and workshop racks without changing the paint context', () => {
  for (const route of [{ view: 'marketRacks' }, { view: 'workshopRacks' }, { view: 'workshopRack', workshopRackId: 'rack-one' }]) {
    assert.deepEqual(parseRackRoute(rackRoutePath(route)), route);
  }
  assert.equal(rackNavigation({ view: 'workshopRack', workshopRackId: 'rack-one' }, 'workshopRacks'), true);
  assert.equal(rackNavigation({ view: 'workshopRack', workshopRackId: 'rack-one' }, 'workshopPaints'), false);
});
import test from 'node:test';
import { appRoutePath, parseAppRoute, isNavigationDestinationCurrent } from './app-routing.ts';

test('highlights the direct destination without selecting a neighboring context', () => {
  const destinations = ['home', 'paintProducts', 'marketProducts', 'workshopPaints', 'workshop', 'shopping', 'aboutUser', 'aboutAdmin', 'aboutPaintModel', 'aboutApi', 'aboutVersion'];
  for (const view of destinations) {
    assert.deepEqual(destinations.filter(destination => isNavigationDestinationCurrent({ view }, destination)), [view]);
  }
});

test('keeps market details and personal project details under distinct destinations', () => {
  const product = { view: 'paintableProduct', paintableProductId: 'game' };
  assert.equal(isNavigationDestinationCurrent(product, 'marketProducts'), true);
  assert.equal(isNavigationDestinationCurrent(product, 'workshop'), false);
  const project = { ...product, paintingProjectId: 'my-game' };
  assert.equal(isNavigationDestinationCurrent(project, 'marketProducts'), false);
  assert.equal(isNavigationDestinationCurrent(project, 'workshop'), true);
  assert.equal(isNavigationDestinationCurrent({ view: 'workshopPaintable', workshopPaintableId: 'copy-1' }, 'workshop'), true);
});

test('round-trips a direct market paintable-product item URL', () => {
  const route = parseAppRoute('/market/paintable-products/reichbusters-reloaded/paintable-components/red-hawk');
  assert.deepEqual(route, {
    view: 'paintableProduct',
    paintableProductId: 'reichbusters-reloaded',
    paintableComponentId: 'red-hawk',
  });
  assert.equal(appRoutePath(route), '/market/paintable-products/reichbusters-reloaded/paintable-components/red-hawk');
});

test('distinguishes a painting project from its market reference', () => {
  const route = parseAppRoute('/workshop/painting-projects/paint-reichbusters/paintable-products/reichbusters-reloaded');
  assert.deepEqual(route, { view: 'paintableProduct', paintingProjectId: 'paint-reichbusters', paintableProductId: 'reichbusters-reloaded', paintableComponentId: undefined });
  assert.equal(appRoutePath(route), '/workshop/painting-projects/paint-reichbusters/paintable-products/reichbusters-reloaded');
});

test('routes each about page independently', () => {
  assert.equal(appRoutePath(parseAppRoute('/about/user')), '/about/user');
  assert.equal(appRoutePath(parseAppRoute('/about/admin')), '/about/admin');
  assert.equal(appRoutePath(parseAppRoute('/about/admin/paint-model')), '/about/admin/paint-model');
  assert.equal(appRoutePath(parseAppRoute('/about/api')), '/about/api');
  assert.equal(appRoutePath(parseAppRoute('/about/version')), '/about/version');
});

test('round-trips a physical workshop item URL', () => {
  const route = parseAppRoute('/workshop/paintables/ws-red-hawk-1');
  assert.deepEqual(route, { view: 'workshopPaintable', workshopPaintableId: 'ws-red-hawk-1' });
  assert.equal(appRoutePath(route), '/workshop/paintables/ws-red-hawk-1');
});
test('round-trips paint products, physical pots and grouped stock drilldown', () => {
  for (const path of ['/market/paint-products', '/workshop/paint-pots', '/workshop/paint-pots/pot-one', '/workshop/paint-stocks/paint-red/pots']) {
    assert.equal(appRoutePath(parseAppRoute(path)), path);
  }
  assert.equal(isNavigationDestinationCurrent(parseAppRoute('/workshop/paint-pots/pot-one'), 'workshopPaints'), true);
});
