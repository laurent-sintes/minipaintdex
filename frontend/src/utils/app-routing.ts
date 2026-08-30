export type AppView = 'home' | 'marketPaints' | 'marketProducts' | 'workshopPaints' | 'workshop' | 'shopping' | 'product';

export type AppRoute = {
  view: AppView;
  productId?: string;
  catalogItemId?: string;
  workshopProduct?: boolean;
};

export function parseAppRoute(pathname: string): AppRoute {
  const parts = pathname.split('/').filter(Boolean);
  if (parts[0] === 'market' && parts[1] === 'paints') return { view: 'marketPaints' };
  if (parts[0] === 'market' && parts[1] === 'paintable-products' && parts[2]) {
    return { view: 'product', productId: parts[2], catalogItemId: parts[4] };
  }
  if (parts[0] === 'market' && parts[1] === 'paintable-products') return { view: 'marketProducts' };
  if (parts[0] === 'workshop' && parts[1] === 'paints') return { view: 'workshopPaints' };
  if (parts[0] === 'workshop' && parts[1] === 'paintable-products' && parts[2]) {
    return { view: 'product', productId: parts[2], catalogItemId: parts[4], workshopProduct: true };
  }
  if (parts[0] === 'workshop') return { view: 'workshop' };
  if (parts[0] === 'shopping') return { view: 'shopping' };
  return { view: 'home' };
}

export function appRoutePath(route: AppRoute) {
  if (route.view === 'home') return '/';
  if (route.view === 'marketPaints') return '/market/paints';
  if (route.view === 'marketProducts') return '/market/paintable-products';
  if (route.view === 'workshopPaints') return '/workshop/paints';
  if (route.view === 'workshop') return '/workshop';
  if (route.view === 'shopping') return '/shopping';
  const root = route.workshopProduct ? '/workshop/paintable-products/' : '/market/paintable-products/';
  const item = route.catalogItemId ? `/items/${route.catalogItemId}` : '';
  return `${root}${route.productId ?? ''}${item}`;
}
