export type AppView = 'home' | 'paintProducts' | 'marketProducts' | 'marketRacks' | 'workshopRacks' | 'workshopRack' | 'workshopPaints' | 'paintPots' | 'paintPot' | 'workshop' | 'shopping' | 'paintableProduct' | 'workshopPaintable' | 'aboutUser' | 'aboutAdmin' | 'aboutPaintModel' | 'aboutApi' | 'aboutVersion';

export type AppRoute = {
  workshopRackId?: string;
  view: AppView;
  paintPotId?: string;
  paintProductId?: string;
  paintableProductId?: string;
  paintableComponentId?: string;
  paintingProjectId?: string;
  workshopPaintableId?: string;
};

export function isNavigationDestinationCurrent(route: AppRoute, destination: AppView) {
  if (route.view === 'workshopRack') return destination === 'workshopRacks';
  if (route.view === 'paintPots' || route.view === 'paintPot') return destination === 'workshopPaints';
  if (route.view === destination) return true;
  if (route.view === 'paintableProduct') return destination === (route.paintingProjectId ? 'workshop' : 'marketProducts');
  return route.view === 'workshopPaintable' && destination === 'workshop';
}

export function parseAppRoute(pathname: string): AppRoute {
  const parts = pathname.split('/').filter(Boolean);
  if (parts[0] === 'market' && parts[1] === 'racks') return { view: 'marketRacks' };
  if (parts[0] === 'workshop' && parts[1] === 'racks') return parts[2] ? { view: 'workshopRack', workshopRackId: parts[2] } : { view: 'workshopRacks' };
  if (parts[0] === 'market' && parts[1] === 'paint-products') return { view: 'paintProducts' };
  if (parts[0] === 'market' && parts[1] === 'paintable-products' && parts[2]) {
    return { view: 'paintableProduct', paintableProductId: parts[2], paintableComponentId: parts[4] };
  }
  if (parts[0] === 'market' && parts[1] === 'paintable-products') return { view: 'marketProducts' };
  if (parts[0] === 'workshop' && parts[1] === 'paint-pots') return parts[2] ? { view: 'paintPot', paintPotId: parts[2] } : { view: 'paintPots' };
  if (parts[0] === 'workshop' && parts[1] === 'paint-stocks' && parts[2]) return { view: 'paintPots', paintProductId: parts[2] };
  if (parts[0] === 'workshop' && parts[1] === 'paints') return { view: 'workshopPaints' };
  if (parts[0] === 'workshop' && parts[1] === 'paintables' && parts[2]) return { view: 'workshopPaintable', workshopPaintableId: parts[2] };
  if (parts[0] === 'workshop' && parts[1] === 'shopping-list') return { view: 'shopping' };
  if (parts[0] === 'workshop' && parts[1] === 'painting-projects' && parts[2]) {
    return { view: 'paintableProduct', paintingProjectId: parts[2], paintableProductId: parts[4], paintableComponentId: parts[6] };
  }
  if (parts[0] === 'workshop') return { view: 'workshop' };
  if (parts[0] === 'about' && parts[1] === 'admin' && parts[2] === 'paint-model') return { view: 'aboutPaintModel' };
  if (parts[0] === 'about' && parts[1] === 'admin') return { view: 'aboutAdmin' };
  if (parts[0] === 'about' && parts[1] === 'api') return { view: 'aboutApi' };
  if (parts[0] === 'about' && parts[1] === 'version') return { view: 'aboutVersion' };
  if (parts[0] === 'about') return { view: 'aboutUser' };
  return { view: 'home' };
}

export function appRoutePath(route: AppRoute) {
  if (route.view === 'marketRacks') return '/market/racks';
  if (route.view === 'workshopRacks') return '/workshop/racks';
  if (route.view === 'workshopRack') return '/workshop/racks/' + (route.workshopRackId ?? '');
  if (route.view === 'paintPot') return `/workshop/paint-pots/${route.paintPotId ?? ''}`;
  if (route.view === 'paintPots') return route.paintProductId ? `/workshop/paint-stocks/${route.paintProductId}/pots` : '/workshop/paint-pots';
  if (route.view === 'home') return '/';
  if (route.view === 'paintProducts') return '/market/paint-products';
  if (route.view === 'marketProducts') return '/market/paintable-products';
  if (route.view === 'workshopPaints') return '/workshop/paints';
  if (route.view === 'workshop') return '/workshop';
  if (route.view === 'shopping') return '/workshop/shopping-list';
  if (route.view === 'aboutUser') return '/about/user';
  if (route.view === 'aboutAdmin') return '/about/admin';
  if (route.view === 'aboutPaintModel') return '/about/admin/paint-model';
  if (route.view === 'aboutApi') return '/about/api';
  if (route.view === 'aboutVersion') return '/about/version';
  if (route.view === 'workshopPaintable') return `/workshop/paintables/${route.workshopPaintableId ?? ''}`;
  if (route.paintingProjectId) {
    const item = route.paintableComponentId ? `/paintable-components/${route.paintableComponentId}` : '';
    return `/workshop/painting-projects/${route.paintingProjectId}/paintable-products/${route.paintableProductId ?? ''}${item}`;
  }
  const item = route.paintableComponentId ? `/paintable-components/${route.paintableComponentId}` : '';
  return `/market/paintable-products/${route.paintableProductId ?? ''}${item}`;
}
