export type AppView = 'home' | 'marketPaints' | 'marketProducts' | 'workshopPaints' | 'workshop' | 'shopping' | 'product' | 'item' | 'aboutUser' | 'aboutAdmin' | 'aboutPaintModel' | 'aboutApi' | 'aboutVersion';

export type AppRoute = {
  view: AppView;
  productId?: string;
  catalogItemId?: string;
  paintingProjectId?: string;
  itemId?: string;
};

export function parseAppRoute(pathname: string): AppRoute {
  const parts = pathname.split('/').filter(Boolean);
  if (parts[0] === 'market' && parts[1] === 'paints') return { view: 'marketPaints' };
  if (parts[0] === 'market' && parts[1] === 'paintable-products' && parts[2]) {
    return { view: 'product', productId: parts[2], catalogItemId: parts[4] };
  }
  if (parts[0] === 'market' && parts[1] === 'paintable-products') return { view: 'marketProducts' };
  if (parts[0] === 'workshop' && parts[1] === 'paints') return { view: 'workshopPaints' };
  if (parts[0] === 'workshop' && parts[1] === 'items' && parts[2]) return { view: 'item', itemId: parts[2] };
  if (parts[0] === 'workshop' && parts[1] === 'painting-projects' && parts[2]) {
    return { view: 'product', paintingProjectId: parts[2], productId: parts[4], catalogItemId: parts[6] };
  }
  if (parts[0] === 'workshop') return { view: 'workshop' };
  if (parts[0] === 'shopping') return { view: 'shopping' };
  if (parts[0] === 'about' && parts[1] === 'admin' && parts[2] === 'paint-model') return { view: 'aboutPaintModel' };
  if (parts[0] === 'about' && parts[1] === 'admin') return { view: 'aboutAdmin' };
  if (parts[0] === 'about' && parts[1] === 'api') return { view: 'aboutApi' };
  if (parts[0] === 'about' && parts[1] === 'version') return { view: 'aboutVersion' };
  if (parts[0] === 'about') return { view: 'aboutUser' };
  return { view: 'home' };
}

export function appRoutePath(route: AppRoute) {
  if (route.view === 'home') return '/';
  if (route.view === 'marketPaints') return '/market/paints';
  if (route.view === 'marketProducts') return '/market/paintable-products';
  if (route.view === 'workshopPaints') return '/workshop/paints';
  if (route.view === 'workshop') return '/workshop';
  if (route.view === 'shopping') return '/shopping';
  if (route.view === 'aboutUser') return '/about/user';
  if (route.view === 'aboutAdmin') return '/about/admin';
  if (route.view === 'aboutPaintModel') return '/about/admin/paint-model';
  if (route.view === 'aboutApi') return '/about/api';
  if (route.view === 'aboutVersion') return '/about/version';
  if (route.view === 'item') return `/workshop/items/${route.itemId ?? ''}`;
  if (route.paintingProjectId) {
    const item = route.catalogItemId ? `/items/${route.catalogItemId}` : '';
    return `/workshop/painting-projects/${route.paintingProjectId}/products/${route.productId ?? ''}${item}`;
  }
  const item = route.catalogItemId ? `/items/${route.catalogItemId}` : '';
  return `/market/paintable-products/${route.productId ?? ''}${item}`;
}
