'use client';

import { PaintProductPotPhotoReplacement } from './paint-pot-photo-upload';
import { PaintVisualQuality, usePaintVisual } from './paint-visual';

import {
  BookOpen, Check, ChevronLeft, ChevronRight, Droplets, ExternalLink,
  FolderCog, Grid2X2, ListChecks, PackageOpen, Paintbrush,
  ShoppingBasket, Sparkles, X,
} from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { useRef } from 'react';
import type { CSSProperties, ReactNode } from 'react';
import type { PaintProduct, PaintProductSuggestion, WorkshopPaintStock, PaintCardModel, PaintCatalogQuality, PaintFacets, PaintModelSchema, ShoppingListEntry } from '@/models/paint-model';
import type {
  Dashboard, PaintableComponent, PaintableProduct, PaintingProjectImportPreview, PaintableProductSummary,
  WorkshopPaintable, WorkshopPaintableDetail, WorkshopOverview, PaintingProjectSummary,
} from '@/models/paintable-product-model';
import type { SiteConfig } from '@/models/site-config-model';
import { appRoutePath, parseAppRoute } from '@/utils/app-routing';
import type { AppRoute as Route } from '@/utils/app-routing';
import { formatMetadata, paintFacetSearchParams, readPaintSearch, paintBrowserSearchParams } from '@/utils/paint-search';
import type { PaintFilters, PaintSearchState } from '@/utils/paint-search';
import { paintSearchRequest } from '@/utils/paint-search-request';
import type { PaintSearchResponse } from '@/utils/paint-search-request';
import { configuredLabel, metadataLabel } from '@/utils/site-labels';
import { AppNotice } from './app-notice';
import { apiFetch, failureNotice } from '@/utils/api-errors';
import type { Notice } from '@/utils/api-errors';
import { PaintPotsPage } from './paint-pots';
import { RacksPage } from './racks';
import { PaintUsageGuides, UsageContent } from './paint-usage-guides';
import { PaintBrowser } from './paint-browser';
import { SiteNavigation } from './site-navigation';
import type { FilterOptions } from './paint-browser';

type AboutData = { name: string; version: string; author: string };
type DocumentationData = { documents: Array<{ id: string; audience: 'user' | 'administrator'; markdown: string }> };

const emptyPaintFilters: PaintFilters = {};
const PAINT_PAGE_SIZE = 60;

function isPaintRoute(route: Route) {
  return route.view === 'paintProducts' || route.view === 'workshopPaints';
}

function validColor(value: string) {
  return /^#[0-9a-f]{6}$/i.test(value);
}

function swatchStyle(colorHex: string): CSSProperties | undefined {
  return validColor(colorHex) ? { background: colorHex } : undefined;
}

function workflowLabel(config: SiteConfig, coreId: string) {
  const configKey = coreId.replace(/_([a-z])/g, (_, letter: string) => letter.toUpperCase());
  return config.workflow[configKey] ?? config.workflow[coreId] ?? formatMetadata(coreId);
}

function ResilientPaintImage({ primary, fallback, alt, onFallback }: { primary: string; fallback: string; alt: string; onFallback?: () => void }) {
  const [source, setSource] = useState(primary || fallback);
  if (!source) return null;
  return (
    <img
      src={source}
      alt={alt}
      className="h-full w-full object-contain"
      onError={() => { onFallback?.(); setSource((current) => fallback && current !== fallback ? fallback : ''); }}
    />
  );
}

function PaintCard({ paint, config, onOpen }: { paint: PaintCardModel; config: SiteConfig; onOpen: () => void }) {
  const visual = usePaintVisual(paint);
  const personal = visual.personalPhoto;
  const image = visual.url;
  const hasColor = validColor(paint.colorHex);
  return (
    <button type="button" className="paint-card group w-full text-left" onClick={onOpen}>
      <div
        className={'paint-swatch ' + (image ? 'paint-image-surface' : !hasColor ? 'unknown-color' : '')}
        style={!image && hasColor ? { background: `color-mix(in srgb, ${paint.colorHex} 14%, white)` } : undefined}
      >
        {image
          ? <img src={image} onError={visual.onError} className="h-full w-full object-contain" alt={`${config.paintDetail.productVisual} ${paint.brand} ${paint.name}`} />
          : hasColor ? <span className="absolute inset-0" style={{ background: paint.colorHex }} /> : <span className="absolute inset-0 grid place-items-center px-2 text-center text-[10px] font-semibold text-muted-foreground">{config.paintDetail.toQualify}</span>}
      </div>
      <div className="min-w-0 flex-1 py-0.5">
        <p className="text-[11px] font-semibold uppercase tracking-[0.08em] text-muted-foreground">{paint.brand} · {paint.range}</p>
        <h3 className="mt-1 text-[15px] font-semibold leading-5 tracking-tight">{paint.name}</h3>
        {paint.quantity !== undefined && <p className="mt-1 text-[10px] text-muted-foreground">{personal ? config.paintPots.personalPhoto : config.paintPots.catalogPhoto}</p>}
        {paint.reference && <p className="paint-reference">{config.paintDetail.referenceLabel} {paint.reference}</p>}
        <div className="mt-3 flex flex-wrap gap-1.5">
          {[...paint.profile.applicationMethods, ...paint.profile.roles.filter(role => role !== 'color_paint'),
            ...(paint.profile.applicationSystem === 'one_coat_shading' ? ['one_coat_shading'] : [])].map(value =>
            <span key={value} className="rounded-full bg-secondary px-2.5 py-1 text-[11px] font-medium">{metadataLabel(config, value)}</span>)}
          {(paint.quantity ?? 0) > 0 && <span className="rounded-full bg-primary/8 px-2.5 py-1 text-[11px] font-semibold text-primary">× {paint.quantity}</span>}
          {paint.availableQuantity !== undefined && paint.availableQuantity < (paint.quantity ?? 0) && <span className="rounded-full bg-secondary px-2.5 py-1 text-[11px]">{config.paintPots.availableCount} : {paint.availableQuantity}</span>}
        </div>
        {paint.manufacturerUrl && <span className="mt-2 inline-flex items-center gap-1 text-[11px] font-semibold text-primary"><BookOpen size={12} />{config.collection.manufacturerSheet}</span>}
      </div>
    </button>
  );
}

function Metric({ icon, value, label }: { icon: ReactNode; value: number | string; label: string }) {
  return <div className="metric-card"><span className="metric-icon bg-primary/8 text-primary">{icon}</span><div><strong>{value}</strong><span>{label}</span></div></div>;
}

function EmptyState({ title, description }: { title: string; description: string }) {
  return <section className="rounded-[24px] border border-dashed bg-card/60 px-6 py-14 text-center"><PackageOpen className="mx-auto size-8 text-muted-foreground/60" /><h2 className="mt-3 text-sm font-semibold">{title}</h2><p className="mx-auto mt-2 max-w-lg text-xs leading-5 text-muted-foreground">{description}</p></section>;
}

const emptyFacets: FilterOptions = {};

const emptyWorkshop: WorkshopOverview = {
  id: 'my-workshop', paintingProjects: [], projectCount: 0, paintableCount: 0,
  completedPaintableCount: 0, progressPercentage: 0, recentActivity: [],
};

export function PaintApp({ initialDashboard, config, paintModel }: { initialDashboard: Dashboard; config: SiteConfig; paintModel: PaintModelSchema }) {
  const [route, setRoute] = useState<Route>(() => parseAppRoute(window.location.pathname));
  const readSearch = useCallback(() => readPaintSearch(window.location.search,
    paintModel['x-filters'].map(filter => filter.queryParameter),
    paintModel['x-sort-options'].map(sort => sort.queryValue)), [paintModel]);
  const [dashboard, setDashboard] = useState(initialDashboard);
  const [paints, setPaints] = useState<PaintCardModel[]>([]);
  const [paintResultCount, setPaintResultCount] = useState(0);
  const [paintOffset, setPaintOffset] = useState(() => readSearch().page * PAINT_PAGE_SIZE);
  const [paintSort, setPaintSort] = useState(() => readSearch().sort);
  const [paintsLoading, setPaintsLoading] = useState(isPaintRoute(route));
  const [filterOptions, setFilterOptions] = useState<FilterOptions>(emptyFacets);
  const [productSummaries, setProductSummaries] = useState<PaintableProductSummary[]>([]);
  const [activePaintableProduct, setActivePaintableProduct] = useState<PaintableProduct | null>(null);
  const [loadingProduct, setLoadingProduct] = useState(route.view === 'paintableProduct');
  const [workshop, setWorkshop] = useState<WorkshopOverview>(emptyWorkshop);
  const [workshopPaintables, setWorkshopPaintables] = useState<WorkshopPaintable[]>([]);
  const [query, setQuery] = useState(() => readSearch().query);
  const [filters, setFilters] = useState<PaintFilters>(() => readSearch().filters);
  const [selectedPaint, setSelectedPaint] = useState<PaintCardModel | null>(null);
  const [shoppingListEntries, setShoppingListEntries] = useState<ShoppingListEntry[]>([]);
  const [importPreviewState, setImportPreviewState] = useState<{ paintableProductId: string; preview: PaintingProjectImportPreview } | null>(null);
  const [importing, setImporting] = useState(false);
  const [notice, setNotice] = useState<Notice>('');
  const [connectionError, setConnectionError] = useState(false);
  const [workshopPaintableDetail, setWorkshopPaintableDetail] = useState<WorkshopPaintableDetail | null>(null);
  const [savingItem, setSavingItem] = useState(false);
  const [aboutData, setAboutData] = useState<AboutData | null>(null);
  const [paintQuality, setPaintQuality] = useState<PaintCatalogQuality | null>(null);
  const [documentation, setDocumentation] = useState<DocumentationData | null>(null);
  const [serverRevision, setServerRevision] = useState(0);
  const paintRequestId = useRef(0);
  const suggestionDetailRequest = useRef<AbortController | null>(null);
  useEffect(() => () => { suggestionDetailRequest.current?.abort(); }, [route.view]);


  const activateRoute = useCallback((next: Route) => {
    suggestionDetailRequest.current?.abort();
    const sameView = route.view === next.view;
    const sameProduct = route.view === 'paintableProduct' && next.view === 'paintableProduct'
      && route.paintableProductId === next.paintableProductId && route.paintingProjectId === next.paintingProjectId;
    if (!sameView || !isPaintRoute(next)) { setPaints([]); setFilterOptions(emptyFacets); setSelectedPaint(null); }
    if (isPaintRoute(next)) setPaintsLoading(true);
    if (!sameView || next.view !== 'marketProducts') setProductSummaries([]);
    if (!sameProduct) { setActivePaintableProduct(null); setWorkshopPaintables([]); setImportPreviewState(null); }
    setLoadingProduct(!sameProduct && next.view === 'paintableProduct');
    if (!sameView || next.view !== 'shopping') setShoppingListEntries([]);
    if (!sameView || (next.view !== 'aboutUser' && next.view !== 'aboutAdmin')) setDocumentation(null);
    if (!sameView || next.view !== 'aboutVersion') setAboutData(null);
    if (!sameView || next.view !== 'aboutPaintModel') setPaintQuality(null);
    const search = readSearch();
    setPaintOffset(search.page * PAINT_PAGE_SIZE);
    setQuery(search.query); setFilters(search.filters); setPaintSort(search.sort);
    setRoute(next);
  }, [route, readSearch]);

  function navigate(next: Route) {
    window.history.pushState({}, '', appRoutePath(next));
    activateRoute(next);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  useEffect(() => {
    const onPopState = () => activateRoute(parseAppRoute(window.location.pathname));
    window.addEventListener('popstate', onPopState);
    return () => window.removeEventListener('popstate', onPopState);
  }, [activateRoute]);

  useEffect(() => {
    const events = new EventSource('/api/v1/events');
    const invalidate = () => setServerRevision((revision) => revision + 1);
    let disconnected = false;
    events.addEventListener('error', () => { disconnected = true; setConnectionError(true); });
    events.addEventListener('open', () => {
      setConnectionError(false);
      if (disconnected) { disconnected = false; setNotice(current => typeof current === 'string' ? current : ''); invalidate(); }
    });
    events.addEventListener('domain-events-committed', invalidate);
    events.addEventListener('resync-required', invalidate);
    return () => events.close();
  }, []);

  useEffect(() => {
    if ((route.view !== 'home' && route.view !== 'paintProducts') || serverRevision === 0) return;
    const controller = new AbortController();
    apiFetch('/api/v1/dashboard', { signal: controller.signal, headers: { accept: 'application/json' } })
      .then((response) => { return response.json() as Promise<Dashboard>; })
      .then(setDashboard)
      .catch((reason) => { if (!(reason instanceof DOMException && reason.name === 'AbortError')) setNotice(failureNotice(config.errors.requestFailed, reason)); });
    return () => controller.abort();
  }, [config.errors.requestFailed, route.view, serverRevision]);

  useEffect(() => {
    if (route.view !== 'aboutUser' && route.view !== 'aboutAdmin') return;
    const controller = new AbortController();
    const audience = route.view === 'aboutUser' ? 'user' : 'administrator';
    apiFetch(`/api/v1/documentation?audience=${audience}`, { signal: controller.signal, headers: { accept: 'application/json' } })
      .then((response) => { return response.json() as Promise<DocumentationData>; })
      .then(setDocumentation)
      .catch((reason) => { if (!(reason instanceof DOMException && reason.name === 'AbortError')) setNotice(failureNotice(config.errors.requestFailed, reason)); });
    return () => controller.abort();
  }, [config.errors.requestFailed, route.view]);

  useEffect(() => {
    if (route.view !== 'aboutVersion') return;
    const controller = new AbortController();
    apiFetch('/api/v1/about', { signal: controller.signal, headers: { accept: 'application/json' } })
      .then((response) => { return response.json() as Promise<AboutData>; })
      .then(setAboutData)
      .catch((reason) => { if (!(reason instanceof DOMException && reason.name === 'AbortError')) setNotice(failureNotice(config.errors.requestFailed, reason)); });
    return () => controller.abort();
  }, [config.errors.requestFailed, route.view]);

  useEffect(() => {
    if (route.view !== 'aboutPaintModel') return;
    const controller = new AbortController();
    apiFetch('/api/v1/market/paint-products/quality', { signal: controller.signal, headers: { accept: 'application/json' } })
      .then((response) => { return response.json() as Promise<PaintCatalogQuality>; })
      .then(setPaintQuality)
      .catch((reason) => { if (!(reason instanceof DOMException && reason.name === 'AbortError')) setNotice(failureNotice(config.errors.requestFailed, reason)); });
    return () => controller.abort();
  }, [config.errors.requestFailed, route.view, serverRevision]);

  const activePaintableComponent = activePaintableProduct?.paintableComponents.find((item) => item.id === route.paintableComponentId) ?? activePaintableProduct?.paintableComponents[0];
  const activePaintingProject = workshop.paintingProjects.find((project) => project.paintingProjectId === route.paintingProjectId);
  const importPreview = importPreviewState && importPreviewState.paintableProductId === route.paintableProductId
    ? importPreviewState.preview
    : null;

  useEffect(() => {
    if (route.view !== 'paintableProduct' || !route.paintableProductId) return;
    const paintableProductId = route.paintableProductId;
    const controller = new AbortController();
    const requests: Promise<Response>[] = [
      apiFetch(`/api/v1/market/paintable-products/${paintableProductId}`, { signal: controller.signal, headers: { accept: 'application/json' } }),
      apiFetch(`/api/v1/workshop/painting-project-import-previews/${paintableProductId}`, { signal: controller.signal, headers: { accept: 'application/json' } }),
    ];
    if (route.paintingProjectId) {
      requests.push(apiFetch('/api/v1/workshop', { signal: controller.signal, headers: { accept: 'application/json' } }));
      requests.push(apiFetch(`/api/v1/workshop/paintables?paintingProjectId=${encodeURIComponent(route.paintingProjectId)}`, { signal: controller.signal, headers: { accept: 'application/json' } }));
    }
    Promise.all(requests)
      .then(async (responses) => {
        const product = await responses[0].json() as { paintableProduct: PaintableProduct };
        const preview = await responses[1].json() as { preview: PaintingProjectImportPreview };
        const workshopResult = responses[2] ? await responses[2].json() as { workshop: WorkshopOverview } : null;
        const itemResult = responses[3] ? await responses[3].json() as { paintables: WorkshopPaintable[] } : null;
        return { product: product.paintableProduct, preview: preview.preview, workshop: workshopResult?.workshop, items: itemResult?.paintables };
      })
      .then((result) => {
        setActivePaintableProduct(result.product);
        setLoadingProduct(false);
        setImportPreviewState({ paintableProductId, preview: result.preview });
        if (result.workshop) setWorkshop(result.workshop);
        if (result.items) setWorkshopPaintables(result.items);
      })
      .catch((reason) => {
        if (!(reason instanceof DOMException && reason.name === 'AbortError')) { setLoadingProduct(false); setNotice(failureNotice(config.errors.requestFailed, reason)); }
      });
    return () => controller.abort();
  }, [config.errors.requestFailed, route.paintingProjectId, route.paintableProductId, route.view, serverRevision]);

  useEffect(() => {
    if (route.view !== 'marketProducts') return;
    const controller = new AbortController();
    Promise.all([
      apiFetch('/api/v1/market/paintable-products', { signal: controller.signal, headers: { accept: 'application/json' } }),
      apiFetch('/api/v1/workshop', { signal: controller.signal, headers: { accept: 'application/json' } }),
    ])
      .then(async ([marketResponse, workshopResponse]) => {
        const marketResult = await marketResponse.json() as { paintableProducts: PaintableProductSummary[] };
        const workshopResult = await workshopResponse.json() as { workshop: WorkshopOverview };
        return { products: marketResult.paintableProducts, workshop: workshopResult.workshop };
      })
      .then((result) => { setProductSummaries(result.products); setWorkshop(result.workshop); })
      .catch((reason) => { if (!(reason instanceof DOMException && reason.name === 'AbortError')) setNotice(failureNotice(config.errors.requestFailed, reason)); });
    return () => controller.abort();
  }, [config.errors.requestFailed, route.view, serverRevision]);

  useEffect(() => {
    if (route.view !== 'workshop') return;
    const controller = new AbortController();
    apiFetch('/api/v1/workshop', { signal: controller.signal, headers: { accept: 'application/json' } })
      .then((response) => { return response.json() as Promise<{ workshop: WorkshopOverview }>; })
      .then((result) => setWorkshop(result.workshop))
      .catch((reason) => { if (!(reason instanceof DOMException && reason.name === 'AbortError')) setNotice(failureNotice(config.errors.requestFailed, reason)); });
    return () => controller.abort();
  }, [config.errors.requestFailed, route.view, serverRevision]);

  useEffect(() => {
    if (route.view !== 'shopping') return;
    const controller = new AbortController();
    apiFetch('/api/v1/workshop/shopping-list/entries', { signal: controller.signal, headers: { accept: 'application/json' } })
      .then((response) => { return response.json() as Promise<{ entries: ShoppingListEntry[] }>; })
      .then((result) => setShoppingListEntries(result.entries))
      .catch((reason) => { if (!(reason instanceof DOMException && reason.name === 'AbortError')) setNotice(failureNotice(config.errors.requestFailed, reason)); });
    return () => controller.abort();
  }, [config.errors.requestFailed, route.view, serverRevision]);

  async function fetchWorkshopPaintable(workshopPaintableId: string, signal?: AbortSignal) {
    const response = await apiFetch(`/api/v1/workshop/paintables/${encodeURIComponent(workshopPaintableId)}`, {
      signal, headers: { accept: 'application/json' },
    });
    const result = await response.json() as WorkshopPaintableDetail;
    return result;
  }

  useEffect(() => {
    if (route.view !== 'workshopPaintable' || !route.workshopPaintableId) return;
    const workshopPaintableId = route.workshopPaintableId;
    const controller = new AbortController();
    Promise.all([
      fetchWorkshopPaintable(workshopPaintableId, controller.signal),
      apiFetch('/api/v1/workshop', { signal: controller.signal, headers: { accept: 'application/json' } })
        .then((response) => { return response.json() as Promise<{ workshop: WorkshopOverview }>; }),
    ])
      .then(([item, result]) => { setWorkshopPaintableDetail(item); setWorkshop(result.workshop); })
      .catch((reason) => {
        if (!(reason instanceof DOMException && reason.name === 'AbortError')) setNotice(failureNotice(config.errors.requestFailed, reason));
      });
    return () => controller.abort();
  }, [config.errors.requestFailed, route.workshopPaintableId, route.view, serverRevision]);

  const isPaintView = route.view === 'paintProducts' || route.view === 'workshopPaints';
  const paintPageRequest = useCallback((offset: number) => {
    const collection = route.view === 'workshopPaints' ? '/api/v1/workshop/paint-stocks' : '/api/v1/market/paint-products';
    return paintSearchRequest(collection, query, filters, { page: Math.floor(offset / PAINT_PAGE_SIZE), size: PAINT_PAGE_SIZE, sort: paintSort });
  }, [filters, paintSort, query, route.view]);

  const paintFacetUrl = useCallback(() => {
    const params = paintFacetSearchParams(query, filters);
    const collection = route.view === 'workshopPaints' ? '/api/v1/workshop/paint-stocks/facets' : '/api/v1/market/paint-products/facets';
    return params.size === 0 ? collection : `${collection}?${params.toString()}`;
  }, [filters, query, route.view]);

  useEffect(() => {
    if (!isPaintView) return;
    const controller = new AbortController();
    const requestId = paintRequestId.current + 1;
    paintRequestId.current = requestId;
    const timer = window.setTimeout(() => {
      const request = paintPageRequest(paintOffset);
      apiFetch(request.url, { method: 'POST', body: JSON.stringify(request.body), signal: controller.signal,
        headers: { accept: 'application/json', 'content-type': 'application/json' } })
        .then((response) => response.json() as Promise<PaintSearchResponse<PaintProduct | WorkshopPaintStock, PaintProductSuggestion>>)
        .then((result) => {
          if (controller.signal.aborted || paintRequestId.current !== requestId) return;
          const paints = route.view === 'workshopPaints'
            ? (result.results!.content as WorkshopPaintStock[]).map((stock) => ({ ...stock.paintProduct, quantity: stock.quantity, availableQuantity: stock.availableQuantity, personalPhoto: stock.personalPhoto }))
            : result.results!.content as PaintProduct[];
          setPaints(paints); setPaintResultCount(result.results!.totalElements);
        })
        .catch((reason) => { if (!(reason instanceof DOMException && reason.name === 'AbortError')) setNotice(failureNotice(config.errors.requestFailed, reason)); })
        .finally(() => { if (paintRequestId.current === requestId) setPaintsLoading(false); });
    }, 180);
    return () => { window.clearTimeout(timer); controller.abort(); };
  }, [config.errors.requestFailed, isPaintView, paintOffset, paintPageRequest, route.view, serverRevision]);

  useEffect(() => {
    if (!isPaintView) return;
    const controller = new AbortController();
    const timer = window.setTimeout(() => {
      apiFetch(paintFacetUrl(), { signal: controller.signal, headers: { accept: 'application/json' } })
        .then((response) => { return response.json() as Promise<PaintFacets>; })
        .then((result) => setFilterOptions(Object.fromEntries(result.facets.map((facet) => [facet.id, facet.values]))))
        .catch((reason) => { if (!(reason instanceof DOMException && reason.name === 'AbortError')) setNotice(failureNotice(config.errors.requestFailed, reason)); });
    }, 180);
    return () => { window.clearTimeout(timer); controller.abort(); };
  }, [config.errors.requestFailed, isPaintView, paintFacetUrl, serverRevision]);

  const title = route.view === 'marketRacks' ? config.racks.marketTitle : route.view === 'workshopRacks' || route.view === 'workshopRack' ? config.racks.workshopTitle : route.view === 'paintPots' || route.view === 'paintPot' ? config.paintPots.title : route.view === 'home' ? config.home.title
    : route.view === 'paintProducts' ? config.market.paintsTitle
      : route.view === 'marketProducts' ? config.market.paintableProductsTitle
        : route.view === 'workshopPaints' ? config.collection.title
          : route.view === 'workshop' ? config.workshop.title
            : route.view === 'shopping' ? config.shopping.title
              : route.view === 'aboutUser' ? config.about.userTitle
              : route.view === 'aboutAdmin' ? config.about.administratorTitle
                : route.view === 'aboutPaintModel' ? config.about.paintModelTitle
                  : route.view === 'aboutApi' ? config.about.apiTitle
                  : route.view === 'aboutVersion' ? config.about.versionTitle
              : route.view === 'workshopPaintable' ? workshopPaintableDetail?.displayName ?? config.workshop.itemDetail
                : activePaintableProduct?.name ?? config.errors.productNotFound;
  const description = route.view === 'paintPots' || route.view === 'paintPot' ? config.paintPots.description : route.view === 'home' ? config.home.description
    : route.view === 'paintProducts' ? config.market.paintsDescription
      : route.view === 'marketProducts' ? config.market.paintableProductsDescription
        : route.view === 'workshopPaints' ? config.collection.description
          : route.view === 'workshop' ? config.workshop.description
            : route.view === 'shopping' ? config.shopping.description
              : route.view === 'aboutUser' || route.view === 'aboutAdmin' ? config.about.description
                : route.view === 'aboutPaintModel' ? config.about.paintModelDescription
                  : route.view === 'aboutApi' ? config.about.apiDescription
                : route.view === 'aboutVersion' ? config.about.versionDescription
              : route.view === 'workshopPaintable' ? config.workshop.itemDetail
                : activePaintableProduct?.scope ?? '';

  async function refreshDashboard() {
    const response = await apiFetch('/api/v1/dashboard', { headers: { accept: 'application/json' } });
    setDashboard(await response.json() as Dashboard);
  }

  async function importProduct(paintableProductId: string) {
    setImporting(true);
    setNotice('');
    try {
      await apiFetch('/api/v1/workshop/painting-projects', {
        method: 'POST',
        headers: { 'content-type': 'application/json', 'Idempotency-Key': `create-painting-project:${paintableProductId}` },
        body: JSON.stringify({ paintableProductId: paintableProductId }),
      });
      await refreshDashboard();
      setImportPreviewState((current) => current?.paintableProductId === paintableProductId
        ? { ...current, preview: { ...current.preview, alreadyImported: true } }
        : current);
      setNotice(config.productDetail.importSuccess);
    } catch (reason) {
      setNotice(failureNotice(config.errors.requestFailed, reason));
    } finally {
      setImporting(false);
    }
  }

  async function transitionItemStage(workshopPaintableId: string, stage: string, action: string) {
    setSavingItem(true); setNotice('');
    try {
      await apiFetch(`/api/v1/workshop/paintables/${encodeURIComponent(workshopPaintableId)}/stage-transitions`, {
        method: 'POST',
        headers: { 'content-type': 'application/json', 'Idempotency-Key': crypto.randomUUID() },
        body: JSON.stringify({ stage, action }),
      });
      const [detail] = await Promise.all([fetchWorkshopPaintable(workshopPaintableId), refreshDashboard()]);
      setWorkshopPaintableDetail(detail);
    } catch (reason) { setNotice(failureNotice(config.errors.requestFailed, reason)); } finally { setSavingItem(false); }
  }

  async function addItemComment(workshopPaintableId: string, comment: string) {
    setSavingItem(true); setNotice('');
    try {
      await apiFetch(`/api/v1/workshop/paintables/${encodeURIComponent(workshopPaintableId)}/comments`, {
        method: 'POST',
        headers: { 'content-type': 'application/json', 'Idempotency-Key': crypto.randomUUID() },
        body: JSON.stringify({ comment }),
      });
      setWorkshopPaintableDetail(await fetchWorkshopPaintable(workshopPaintableId));
    } catch (reason) { setNotice(failureNotice(config.errors.requestFailed, reason)); } finally { setSavingItem(false); }
  }

  async function addItemPhoto(workshopPaintableId: string, file: File, caption: string, stage: string | null) {
    setSavingItem(true); setNotice('');
    try {
      const body = new FormData();
      body.append('file', file);
      if (caption.trim()) body.append('caption', caption.trim());
      if (stage) body.append('stage', stage);
      await apiFetch(`/api/v1/workshop/paintables/${encodeURIComponent(workshopPaintableId)}/photos`, {
        method: 'POST', headers: { 'Idempotency-Key': crypto.randomUUID() }, body,
      });
      setWorkshopPaintableDetail(await fetchWorkshopPaintable(workshopPaintableId));
    } catch (reason) { setNotice(failureNotice(config.errors.requestFailed, reason)); } finally { setSavingItem(false); }
  }

  async function setShoppingStatus(shoppingListEntryId: string, checked: boolean) {
    setNotice('');
    try {
      await apiFetch(`/api/v1/workshop/shopping-list/entries/${encodeURIComponent(shoppingListEntryId)}/checked`, {
        method: 'POST',
        headers: { 'content-type': 'application/json', 'Idempotency-Key': crypto.randomUUID() },
        body: JSON.stringify({ checked }),
      });
      setShoppingListEntries((current) => current.map((item) => item.id === shoppingListEntryId ? { ...item, checked } : item));
    } catch (reason) { setNotice(failureNotice(config.errors.requestFailed, reason)); }
  }

  async function openPaintSuggestion(suggestion: PaintProductSuggestion) {
    suggestionDetailRequest.current?.abort();
    if (route.view === 'workshopPaints') { navigate({ view: 'paintPots', paintProductId: suggestion.paintProductId }); return; }
    const controller = new AbortController();
    suggestionDetailRequest.current = controller;
    setNotice(config.errors.loading);
    try {
      const response = await apiFetch('/api/v1/market/paint-products/' + encodeURIComponent(suggestion.paintProductId),
        { signal: controller.signal, headers: { accept: 'application/json' } });
      const paint = await response.json() as PaintProduct;
      if (!controller.signal.aborted) { setSelectedPaint(paint); setNotice(''); }
    } catch (reason) {
      if (!controller.signal.aborted) setNotice(failureNotice(config.errors.requestFailed, reason));
    }
  }

  function changeSearch(patch: Partial<PaintSearchState>, replace = false) {
    const state = { query, filters, sort: paintSort, page: Math.floor(paintOffset / PAINT_PAGE_SIZE), ...patch };
    if (state.query === query && state.filters === filters && state.sort === paintSort && state.page * PAINT_PAGE_SIZE === paintOffset) return;
    const params = paintBrowserSearchParams(state).toString();
    window.history[replace ? 'replaceState' : 'pushState']({}, '', appRoutePath(route) + (params ? '?' + params : ''));
    suggestionDetailRequest.current?.abort();
    setPaintsLoading(true); setSelectedPaint(null); setNotice('');
    setQuery(state.query); setFilters(state.filters); setPaintSort(state.sort); setPaintOffset(state.page * PAINT_PAGE_SIZE);
  }
  function clearFilters() { changeSearch({ query: '', filters: emptyPaintFilters, page: 0 }); }
  function changeQuery(value: string) { changeSearch({ query: value, page: 0 }, true); }
  function changeFilters(value: PaintFilters) { changeSearch({ filters: value, page: 0 }); }
  function changePaintSort(value: string) { changeSearch({ sort: value, page: 0 }); }
  function changePaintPage(value: number) {
    changeSearch({ page: Math.floor(value / PAINT_PAGE_SIZE) });
    window.scrollTo({ top: 0 });
  }

  return (
    <div className="min-h-screen bg-background text-foreground">
      <SiteNavigation config={config} route={route} navigate={navigate} />

      <div className="mx-auto max-w-[1600px]">
        <main className="min-w-0 px-4 py-7 sm:px-6 lg:px-8">
          <div className="mx-auto max-w-[1440px]">
            <p className="eyebrow">{route.view === 'home' ? config.home.eyebrow : route.view.startsWith('about') ? config.navigation.aboutSection : route.paintingProjectId || ['workshopRacks', 'workshopRack', 'workshopPaints', 'paintPots', 'paintPot', 'workshop', 'shopping', 'workshopPaintable'].includes(route.view) ? config.navigation.workshopSection : config.navigation.marketSection}</p>
            <h1 className="mt-2 text-3xl font-semibold tracking-tight sm:text-4xl">{title}</h1>
            <p className="mt-3 max-w-3xl text-sm leading-6 text-muted-foreground">{description}</p>
            {connectionError && <AppNotice className="mt-5" notice={{ message: config.errors.connectionLost, detail: config.errors.connectionDetail }} />}
            <AppNotice className="mt-5" notice={notice} />

            {route.view === 'home' && <HomePage dashboard={dashboard} config={config} navigate={navigate} />}
            {['marketRacks', 'workshopRacks', 'workshopRack'].includes(route.view) && <RacksPage key={appRoutePath(route)} route={route} config={config} navigate={navigate} revision={serverRevision} />}
            {route.view === 'workshopPaints' && <button type="button" className="mt-4 text-sm font-semibold text-primary" onClick={() => navigate({ view: 'paintPots' })}>{config.paintPots.all}</button>}
            {(route.view === 'paintPots' || route.view === 'paintPot') && <PaintPotsPage key={appRoutePath(route)} route={route} config={config} navigate={navigate} revision={serverRevision} />}
            {isPaintView && <PaintBrowser collection={route.view === 'workshopPaints' ? '/api/v1/workshop/paint-stocks' : '/api/v1/market/paint-products'}
              revision={serverRevision} onSelectSuggestion={openPaintSuggestion} paints={paints} resultCount={paintResultCount}
              referenceTotal={route.view === 'paintProducts' ? dashboard.paintStats.total : undefined} offset={paintOffset} pageSize={PAINT_PAGE_SIZE}
              filters={filters} setFilters={changeFilters} filterOptions={filterOptions} paintModel={paintModel}
              query={query} setQuery={changeQuery} sort={paintSort} setSort={changePaintSort} loading={paintsLoading}
              clearFilters={clearFilters} config={config} onPage={changePaintPage}
              renderPaint={paint => <PaintCard key={paint.id} paint={paint} config={config} onOpen={() => route.view === 'workshopPaints' ? navigate({ view: 'paintPots', paintProductId: paint.id }) : setSelectedPaint(paint)} />} />}
            {route.view === 'marketProducts' && <MarketProducts products={productSummaries} ownedProductIds={new Set(workshop.paintingProjects.map((project) => project.paintableProductId))} config={config} navigate={navigate} />}
            {route.view === 'workshop' && <WorkshopAdmin workshop={workshop} config={config} navigate={navigate} />}
            {route.view === 'shopping' && <ShoppingPage items={shoppingListEntries} onToggle={setShoppingStatus} config={config} />}
            {(route.view === 'aboutUser' || route.view === 'aboutAdmin') && <DocumentationPage documentation={documentation} config={config} />}
            {route.view === 'aboutPaintModel' && <PaintModelPage paintModel={paintModel} quality={paintQuality} config={config} />}
            {route.view === 'aboutApi' && <ApiDocumentationPage config={config} />}
            {route.view === 'aboutVersion' && <VersionPage about={aboutData} config={config} />}
            {route.view === 'paintableProduct' && activePaintableProduct && <PaintableProductPage product={activePaintableProduct} activeItem={activePaintableComponent} paintingProject={activePaintingProject} workshopPaintables={workshopPaintables} preview={importPreview} importing={importing} config={config} navigate={navigate} onImport={importProduct} workshopMode={Boolean(route.paintingProjectId)} />}
            {route.view === 'paintableProduct' && loadingProduct && <p className="mt-8 text-sm text-muted-foreground">{config.errors.loading}</p>}
            {route.view === 'paintableProduct' && !loadingProduct && !activePaintableProduct && <EmptyState title={config.errors.productNotFound} description={config.errors.requestFailed} />}
            {route.view === 'workshopPaintable' && workshopPaintableDetail && <WorkshopPaintablePage item={workshopPaintableDetail} paintingProject={workshop.paintingProjects.find((project) => project.paintingProjectId === workshopPaintableDetail.paintingProjectId)} config={config} navigate={navigate} saving={savingItem} onTransition={transitionItemStage} onComment={addItemComment} onPhoto={addItemPhoto} />}
          </div>
        </main>
      </div>

      {selectedPaint && <PaintDetail key={selectedPaint.id} initialPaint={selectedPaint} config={config} revision={serverRevision}
        onPhotoSaved={() => setServerRevision(value => value + 1)} onClose={() => setSelectedPaint(null)} />}
    </div>
  );
}

function HomePage({ dashboard, config, navigate }: {
  dashboard: Dashboard; config: SiteConfig; navigate: (route: Route) => void;
}) {
  const services: Array<[Route, ReactNode, { title: string; description: string; action: string }]> = [
    [{ view: 'paintProducts' }, <Droplets key="paint" size={20} />, config.home.paintProducts],
    [{ view: 'marketProducts' }, <PackageOpen key="product" size={20} />, config.home.marketPaintableProducts],
    [{ view: 'workshopPaints' }, <Paintbrush key="stock" size={20} />, config.home.workshopPaints],
    [{ view: 'workshop' }, <FolderCog key="workshop" size={20} />, config.home.workshopAdmin],
    [{ view: 'shopping' }, <ShoppingBasket key="shopping" size={20} />, config.home.shopping],
  ];
  return <>
    <div className="mt-8 grid gap-3 sm:grid-cols-3"><Metric icon={<Droplets size={20} />} value={dashboard.paintStats.total} label={config.market.paintsMetric} /><Metric icon={<PackageOpen size={20} />} value={dashboard.paintableProductCount} label={config.navigation.marketPaintableProducts} /><Metric icon={<Grid2X2 size={20} />} value={dashboard.workshop.paintableCount} label={config.workshop.items} /></div>
    <section className="mt-10"><h2 className="text-xl font-semibold">{config.home.servicesTitle}</h2><p className="mt-2 text-sm text-muted-foreground">{config.home.servicesDescription}</p><div className="mt-5 grid gap-4 md:grid-cols-2">{services.map(([route, icon, service]) => <button type="button" key={route.view} onClick={() => navigate(route)} className="group rounded-[24px] border bg-card p-5 text-left shadow-sm transition hover:-translate-y-0.5 hover:border-primary/30 hover:shadow-lg"><span className="grid size-10 place-items-center rounded-2xl bg-primary/8 text-primary">{icon}</span><h3 className="mt-4 font-semibold">{service.title}</h3><p className="mt-2 text-xs leading-5 text-muted-foreground">{service.description}</p><span className="mt-4 inline-flex items-center gap-1 text-xs font-semibold text-primary">{service.action}<ChevronRight size={14} /></span></button>)}</div></section>
  </>;
}

function MarketProducts({ products, ownedProductIds, config, navigate }: { products: PaintableProductSummary[]; ownedProductIds: Set<string>; config: SiteConfig; navigate: (route: Route) => void }) {
  return <div className="mt-8 grid gap-5 lg:grid-cols-2">{products.map((product) => <article key={product.id} className="rounded-[26px] border bg-card p-6 shadow-sm"><div className="flex items-start justify-between gap-4"><div><p className="eyebrow">{formatMetadata(product.productType)}</p><h2 className="mt-2 text-xl font-semibold">{product.name}</h2><p className="mt-1 text-xs text-muted-foreground">{product.line}</p></div>{ownedProductIds.has(product.id) && <span className="rounded-full bg-[#e5f4ec] px-3 py-1 text-[10px] font-semibold text-[#207650]">{config.market.inWorkshop}</span>}</div><p className="mt-4 text-sm leading-6 text-muted-foreground">{product.scope}</p><div className="mt-5 grid grid-cols-2 gap-2 text-xs"><div className="rounded-xl bg-secondary p-3"><strong className="block text-base">{product.paintableComponentCount}</strong>{config.market.catalogItems}</div><div className="rounded-xl bg-secondary p-3"><strong className="block text-base">{product.expectedPaintableCount}</strong>{config.market.paintableItems}</div></div><button type="button" onClick={() => navigate({ view: 'paintableProduct', paintableProductId: product.id })} className="mt-5 inline-flex h-10 items-center gap-2 rounded-xl bg-primary px-4 text-sm font-semibold text-primary-foreground"><BookOpen size={15} />{config.market.viewProduct}</button></article>)}</div>;
}

function WorkshopAdmin({ workshop, config, navigate }: { workshop: WorkshopOverview; config: SiteConfig; navigate: (route: Route) => void }) {
  if (workshop.paintingProjects.length === 0) return <div className="mt-8"><EmptyState title={config.workshop.emptyTitle} description={config.workshop.emptyDescription} /></div>;
  return <>
    <div className="mt-7 grid gap-3 sm:grid-cols-4"><Metric icon={<PackageOpen size={19} />} value={workshop.projectCount} label={config.workshop.projects} /><Metric icon={<Grid2X2 size={19} />} value={workshop.paintableCount} label={config.workshop.items} /><Metric icon={<Check size={19} />} value={workshop.completedPaintableCount} label={config.workshop.completed} /><Metric icon={<Sparkles size={19} />} value={`${workshop.progressPercentage}%`} label={config.workshop.progress} /></div>
    <section className="mt-8 space-y-4">{workshop.paintingProjects.map((summary) => {
      return <article key={summary.paintingProjectId} className="rounded-[26px] border bg-card p-5 shadow-sm sm:p-6"><div className="flex flex-col gap-5 lg:flex-row lg:items-center"><div className="min-w-0 flex-1"><p className="eyebrow">{summary.paintableProductId}</p><h2 className="mt-2 text-xl font-semibold">{summary.name}</h2><div className="mt-4 h-2.5 overflow-hidden rounded-full bg-secondary"><div className="h-full rounded-full bg-primary transition-all" style={{ width: `${summary.progressPercentage}%` }} /></div><p className="mt-2 text-xs text-muted-foreground">{summary.progressPercentage}% · {summary.completedCount} {config.workshop.completed} · {summary.inProgressCount} {config.workshop.inProgress} · {summary.pendingCount} {config.workshop.pending}</p></div><div className="grid grid-cols-2 gap-2 text-xs sm:grid-cols-4 lg:w-[420px]"><SummaryCell value={summary.paintableCount} label={config.workshop.items} /><SummaryCell value={summary.requiredPaintCount} label={config.productDetail.requiredPaints} /><SummaryCell value={summary.missingPaintCount} label={config.workshop.missingPaints} /><SummaryCell value={summary.pendingPaintSlotCount} label={config.workshop.pendingPaintSlots} /></div></div><div className="mt-5 flex flex-wrap items-center gap-4">{summary.missingPaints.length > 0 ? <div className="flex flex-wrap gap-2">{summary.missingPaints.map((paint) => <span key={paint.id} className="rounded-full bg-[#ffe5df] px-3 py-1.5 text-[11px] font-semibold text-[#a6402c]">{paint.brand} · {paint.name}</span>)}</div> : <p className="inline-flex items-center gap-2 text-xs font-semibold text-[#207650]"><Check size={14} />{config.workshop.noMissingPaints}</p>}<button type="button" onClick={() => navigate({ view: 'paintableProduct', paintableProductId: summary.paintableProductId, paintingProjectId: summary.paintingProjectId })} className="inline-flex items-center gap-1 text-xs font-semibold text-primary">{config.workshop.manageProduct}<ChevronRight size={14} /></button></div></article>;
    })}</section>
    <section className="mt-10"><h2 className="text-lg font-semibold">{config.workshop.recentActivity}</h2><div className="mt-4 divide-y overflow-hidden rounded-[22px] border bg-card">{workshop.recentActivity.map((event) => <div key={event.eventId} className="flex min-w-0 items-center gap-3 px-4 py-3"><span className="size-2 flex-none rounded-full bg-primary" /><span className="min-w-0 flex-1 truncate text-xs font-semibold">{config.workshop.eventLabels[event.eventType] ?? event.eventType}</span><time className="flex-none text-[10px] text-muted-foreground">{new Date(event.occurredAt).toLocaleDateString('fr-FR')}</time></div>)}</div></section>
  </>;
}

function SummaryCell({ value, label }: { value: number; label: string }) {
  return <div className="rounded-xl bg-secondary p-3"><strong className="block text-base">{value}</strong><span className="text-[10px] text-muted-foreground">{label}</span></div>;
}

function PaintableProductPage({ product, activeItem, paintingProject, workshopPaintables, preview, importing, config, navigate, onImport, workshopMode }: {
  product: PaintableProduct; activeItem?: PaintableComponent; paintingProject?: PaintingProjectSummary; workshopPaintables: WorkshopPaintable[];
  preview: PaintingProjectImportPreview | null; importing: boolean; config: SiteConfig; navigate: (route: Route) => void;
  onImport: (id: string) => void; workshopMode: boolean;
}) {
  const physicalItems = workshopPaintables.filter((item) => item.paintingProjectId === paintingProject?.paintingProjectId);
  const missingPaintIds = new Set(preview?.missingPaints.map((paint) => paint.id) ?? []);
  const itemStates = activeItem ? physicalItems.filter((item) => item.paintableComponentId === activeItem.id) : [];
  return <>
    <button type="button" onClick={() => navigate({ view: workshopMode ? 'workshop' : 'marketProducts' })} className="mt-6 inline-flex items-center gap-1 text-xs font-semibold text-primary"><ChevronLeft size={14} />{config.productDetail.back}</button>
    <div className="mt-6 grid gap-4 sm:grid-cols-3"><Metric icon={<PackageOpen size={19} />} value={product.expectedPaintableCount} label={config.market.paintableItems} /><Metric icon={<ListChecks size={19} />} value={product.paintableComponents.length} label={config.market.catalogItems} /><Metric icon={<BookOpen size={19} />} value={product.paintableComponents.filter((item) => Boolean(item.marketGuide?.id)).length} label={config.productDetail.paintingSheets} /></div>
    {product.edition.note && <div className="mt-6 rounded-2xl border border-primary/15 bg-primary/5 p-4 text-xs leading-5 text-muted-foreground">{product.edition.note} {product.edition.url && <a href={product.edition.url} target="_blank" rel="noreferrer" className="ml-1 font-semibold text-primary"><ExternalLink className="inline size-3" /> {config.market.source}</a>}</div>}

    {!workshopMode && <ImportPanel product={product} preview={preview} importing={importing} config={config} navigate={navigate} onImport={onImport} />}
    {workshopMode && paintingProject && <section className="mt-6 rounded-[24px] border bg-card p-5"><div className="flex items-center justify-between gap-3"><h2 className="font-semibold">{config.workshop.progress}</h2><strong className="text-primary">{paintingProject.progressPercentage}%</strong></div><div className="mt-3 h-2.5 overflow-hidden rounded-full bg-secondary"><div className="h-full rounded-full bg-primary" style={{ width: `${paintingProject.progressPercentage}%` }} /></div><div className="mt-4 grid grid-cols-3 gap-2"><SummaryCell value={paintingProject.completedCount} label={config.workshop.completed} /><SummaryCell value={paintingProject.inProgressCount} label={config.workshop.inProgress} /><SummaryCell value={paintingProject.pendingCount} label={config.workshop.pending} /></div></section>}

    <div className="mt-8 grid min-w-0 gap-6 lg:grid-cols-[290px_minmax(0,1fr)]">
      <aside className="min-w-0 rounded-[24px] border bg-card p-3"><h2 className="px-2 py-2 text-sm font-semibold">{config.productDetail.contents}</h2><div className="mt-1 max-h-[680px] space-y-1 overflow-y-auto">{product.paintableComponents.map((item) => {
        const current = item.id === activeItem?.id;
        const counts = physicalItems.filter((entry) => entry.paintableComponentId === item.id);
        return <button type="button" key={item.id} onClick={() => navigate({ view: 'paintableProduct', paintableProductId: product.id, paintableComponentId: item.id, paintingProjectId: paintingProject?.paintingProjectId })} className={'flex w-full min-w-0 items-center gap-3 rounded-xl px-3 py-2.5 text-left ' + (current ? 'bg-primary text-primary-foreground' : 'hover:bg-secondary')}><span className="grid size-8 flex-none place-items-center rounded-lg bg-current/10 text-xs font-bold">{item.quantity}</span><span className="min-w-0 flex-1"><strong className="block truncate text-xs">{item.name}</strong><span className={'block truncate text-[10px] ' + (current ? 'text-primary-foreground/70' : 'text-muted-foreground')}>{config.market.kindLabels[item.kind] ?? formatMetadata(item.kind)}{workshopMode ? ` · ${counts.filter((entry) => entry.completed).length}/${counts.length}` : ''}</span></span></button>;
      })}</div></aside>
      {activeItem && <article className="min-w-0 rounded-[26px] border bg-card p-5 shadow-sm sm:p-7"><div className="flex flex-wrap items-start justify-between gap-3"><div><p className="eyebrow">{config.market.kindLabels[activeItem.kind] ?? formatMetadata(activeItem.kind)} · × {activeItem.quantity}</p><h2 className="mt-2 text-2xl font-semibold">{activeItem.name}</h2></div>{activeItem.assemblyRequired && <span className="rounded-full bg-secondary px-3 py-1 text-[10px] font-semibold">{config.productDetail.assemblyRequired}</span>}</div><p className="mt-4 text-sm leading-6 text-muted-foreground">{activeItem.description}</p>
        <ReferenceImages item={activeItem} config={config} />
        {workshopMode && <PhysicalProgress items={itemStates} config={config} navigate={navigate} />}
        <section className="mt-7"><h3 className="flex items-center gap-2 text-sm font-semibold"><Droplets size={16} className="text-primary" />{config.productDetail.paintGuide}</h3><div className="mt-4 grid min-w-0 gap-3 sm:grid-cols-2">{activeItem.paints.map((paint) => {
          const status = paint.pendingImport ? config.productDetail.paintPending : paint.paintProductId && !missingPaintIds.has(paint.paintProductId) ? config.productDetail.paintAvailable : config.productDetail.paintMissing;
          const okay = status === config.productDetail.paintAvailable;
          return <div key={paint.slotId} className="flex min-w-0 items-center gap-3 rounded-2xl border bg-background/60 p-3"><span className={'size-10 flex-none rounded-xl border ' + (!validColor(paint.colorHex) ? 'unknown-color' : '')} style={swatchStyle(paint.colorHex)} /><span className="min-w-0 flex-1"><strong className="block truncate text-sm">{paint.name}</strong><span className="block truncate text-[11px] text-muted-foreground">{paint.brand} · {paint.role}</span></span><span className={'max-w-[42%] break-words rounded-full px-2 py-1 text-center text-[9px] font-semibold ' + (okay ? 'bg-[#e5f4ec] text-[#207650]' : 'bg-[#ffe5df] text-[#a6402c]')}>{status}</span></div>;
        })}</div></section>
        <div className="mt-7 grid gap-4 xl:grid-cols-2"><GuideSteps title={config.productDetail.preparation} steps={activeItem.preparation} /><GuideSteps title={config.productDetail.painting} steps={activeItem.painting} /></div>
      </article>}
    </div>
    <section className="mt-8"><h2 className="text-lg font-semibold">{config.productDetail.sources}</h2><div className="mt-3 flex flex-wrap gap-2">{product.sources.map((source) => <a key={source.url} href={source.url} target="_blank" rel="noreferrer" className="need-chip"><ExternalLink size={13} />{source.label}</a>)}</div></section>
  </>;
}

function ImportPanel({ product, preview, importing, config, navigate, onImport }: { product: PaintableProduct; preview: PaintingProjectImportPreview | null; importing: boolean; config: SiteConfig; navigate: (route: Route) => void; onImport: (id: string) => void }) {
  return <section className="mt-6 rounded-[24px] border bg-card p-5 shadow-sm"><div className="flex flex-col gap-5 md:flex-row md:items-center"><div className="min-w-0 flex-1"><p className="eyebrow">{config.productDetail.importPreview}</p><p className="mt-2 text-sm leading-6 text-muted-foreground">{config.productDetail.importDescription}</p>{preview && <div className="mt-4 grid grid-cols-3 gap-2"><SummaryCell value={preview.requiredPaintCount} label={config.productDetail.requiredPaints} /><SummaryCell value={preview.missingPaintCount} label={config.productDetail.missingPaints} /><SummaryCell value={preview.pendingPaintSlotCount} label={config.productDetail.pendingSlots} /></div>}</div><div className="flex-none">{preview?.alreadyImported ? <><p className="mb-3 max-w-xs text-xs font-semibold text-[#207650]">{config.productDetail.alreadyImported}</p><button type="button" onClick={() => navigate({ view: 'workshop' })} className="inline-flex h-11 items-center gap-2 rounded-xl bg-primary px-5 text-sm font-semibold text-primary-foreground"><FolderCog size={16} />{config.productDetail.openWorkshop}</button></> : <button type="button" disabled={!preview || importing} onClick={() => onImport(product.id)} className="inline-flex h-11 items-center gap-2 rounded-xl bg-primary px-5 text-sm font-semibold text-primary-foreground disabled:opacity-50"><PackageOpen size={16} />{importing ? config.productDetail.importing : config.productDetail.importAction}</button>}</div></div>{preview && preview.missingPaints.length > 0 && <div className="mt-4 flex flex-wrap gap-2">{preview.missingPaints.map((paint) => <span key={paint.id} className="rounded-full bg-[#ffe5df] px-3 py-1.5 text-[11px] font-semibold text-[#a6402c]">{paint.brand} · {paint.name}</span>)}</div>}</section>;
}

function ReferenceImages({ item, config }: { item: PaintableComponent; config: SiteConfig }) {
  if (item.referenceImages.length === 0) return <div className="mt-6 rounded-2xl border border-dashed p-5 text-center text-xs text-muted-foreground">{config.productDetail.noLicensedImage}<div className="mt-3 flex flex-wrap justify-center gap-2">{item.sources.map((source) => <a key={source.url} href={source.url} target="_blank" rel="noreferrer" className="font-semibold text-primary">{config.productDetail.externalReferences} <ExternalLink className="inline size-3" /></a>)}</div></div>;
  return <div className="mt-6 grid gap-3 sm:grid-cols-2">{item.referenceImages.map((image) => <figure key={image.url} className="overflow-hidden rounded-2xl border"><img src={image.url} alt={item.name} className="aspect-[4/3] w-full object-cover" /><figcaption className="p-3 text-[10px] text-muted-foreground">{image.credit}</figcaption></figure>)}</div>;
}

function PhysicalProgress({ items, config, navigate }: { items: WorkshopPaintable[]; config: SiteConfig; navigate: (route: Route) => void }) {
  const counts = new Map<string, number>();
  items.forEach((item) => counts.set(item.completed ? 'completed' : item.currentStage ?? 'pending', (counts.get(item.completed ? 'completed' : item.currentStage ?? 'pending') ?? 0) + 1));
  return <section className="mt-6 rounded-2xl bg-secondary/60 p-4"><h3 className="text-sm font-semibold">{config.workshop.progress}</h3><div className="mt-3 flex flex-wrap gap-2">{Array.from(counts.entries()).map(([stage, count]) => <span key={stage} className="rounded-full bg-card px-3 py-1.5 text-[11px] font-semibold">{workflowLabel(config, stage)} · {count}</span>)}</div><div className="mt-4 grid max-h-72 gap-2 overflow-y-auto sm:grid-cols-2">{items.map((item) => <button type="button" key={item.id} onClick={() => navigate({ view: 'workshopPaintable', workshopPaintableId: item.id })} className="flex items-center justify-between gap-3 rounded-xl border bg-card px-3 py-2 text-left text-xs hover:border-primary/30"><span className="truncate font-semibold">{item.displayName}</span><span className="flex-none text-[10px] text-muted-foreground">{item.completed ? config.workshop.completed : workflowLabel(config, item.currentStage ?? 'pending')}</span></button>)}</div></section>;
}

function WorkshopPaintablePage({ item, paintingProject, config, navigate, saving, onTransition, onComment, onPhoto }: {
  item: WorkshopPaintableDetail; paintingProject?: PaintingProjectSummary; config: SiteConfig; navigate: (route: Route) => void; saving: boolean;
  onTransition: (workshopPaintableId: string, stage: string, action: string) => Promise<void>;
  onComment: (workshopPaintableId: string, comment: string) => Promise<void>;
  onPhoto: (workshopPaintableId: string, file: File, caption: string, stage: string | null) => Promise<void>;
}) {
  const [comment, setComment] = useState('');
  const [photo, setPhoto] = useState<File | null>(null);
  const [caption, setCaption] = useState('');
  let prerequisitesComplete = true;
  const photos = item.activity.filter((event) => event.eventType === 'workshop_item.photo_added' && typeof event.payload.url === 'string');
  return <>
    <button type="button" onClick={() => navigate({ view: 'paintableProduct', paintableProductId: paintingProject?.paintableProductId, paintableComponentId: item.paintableComponentId, paintingProjectId: item.paintingProjectId })} className="mt-6 inline-flex items-center gap-1 text-xs font-semibold text-primary"><ChevronLeft size={14} />{config.workshop.backToProduct}</button>
    <section className="mt-6 rounded-[26px] border bg-card p-5 shadow-sm sm:p-7"><div className="flex flex-wrap items-start justify-between gap-3"><div><p className="eyebrow">{config.workshop.itemDetail}</p><h2 className="mt-2 text-2xl font-semibold">{item.displayName}</h2><p className="mt-1 text-xs text-muted-foreground">{item.paintableComponentId}</p></div>{item.recipeId && <span className="rounded-full bg-secondary px-3 py-1 text-[10px] font-semibold">{item.recipeId} · v{item.recipeVersion}</span>}</div>
      <div className="mt-6 grid gap-3 md:grid-cols-2 xl:grid-cols-3">{Object.entries(item.workflow).map(([stage, status]) => {
        const allowed = prerequisitesComplete;
        const action = status === 'pending' ? 'start' : status === 'in_progress' ? 'complete' : 'reopen';
        const actionLabel = action === 'start' ? config.workshop.startStage : action === 'complete' ? config.workshop.completeStage : config.workshop.reopenStage;
        prerequisitesComplete = prerequisitesComplete && (status === 'completed' || status === 'skipped');
        return <article key={stage} className="rounded-2xl border bg-background/60 p-4"><div className="flex items-center justify-between gap-3"><h3 className="text-sm font-semibold">{workflowLabel(config, stage)}</h3><span className="rounded-full bg-secondary px-2 py-1 text-[9px] font-semibold">{workflowLabel(config, status)}</span></div>{(action === 'reopen' || allowed) && <button type="button" disabled={saving} onClick={() => onTransition(item.id, stage, action)} className="mt-4 rounded-lg bg-primary px-3 py-2 text-xs font-semibold text-primary-foreground disabled:opacity-50">{saving ? config.workshop.saving : actionLabel}</button>}</article>;
      })}</div>
    </section>
    <section className="mt-6 rounded-[24px] border bg-card p-5"><h2 className="text-base font-semibold">{config.workshop.addPhoto}</h2><form className="mt-4 grid gap-3 sm:grid-cols-[1fr_1fr_auto]" onSubmit={async (event) => { event.preventDefault(); if (!photo) return; await onPhoto(item.id, photo, caption, item.currentStage); setPhoto(null); setCaption(''); event.currentTarget.reset(); }}><input type="file" accept="image/jpeg,image/png,image/webp" required onChange={(event) => setPhoto(event.target.files?.[0] ?? null)} className="min-w-0 rounded-xl border bg-background px-3 py-2 text-xs" /><input value={caption} onChange={(event) => setCaption(event.target.value)} placeholder={config.workshop.photoCaption} className="min-w-0 rounded-xl border bg-background px-3 py-2 text-sm outline-none focus:border-primary" /><button type="submit" disabled={saving || !photo} className="rounded-xl bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground disabled:opacity-50">{saving ? config.workshop.saving : config.workshop.addPhoto}</button></form><div className="mt-5 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">{photos.map((event) => <figure key={event.eventId} className="overflow-hidden rounded-2xl border"><img src={String(event.payload.url)} alt={typeof event.payload.caption === 'string' ? event.payload.caption : item.displayName} className="aspect-square w-full object-cover" /><figcaption className="p-3 text-[10px] text-muted-foreground">{typeof event.payload.caption === 'string' ? event.payload.caption : new Date(event.occurredAt).toLocaleDateString('fr-FR')}</figcaption></figure>)}{photos.length === 0 && <p className="col-span-full text-xs text-muted-foreground">{config.workshop.noPhotos}</p>}</div></section>
    <section className="mt-6 rounded-[24px] border bg-card p-5"><h2 className="text-base font-semibold">{config.workshop.recentActivity}</h2><form className="mt-4 flex flex-col gap-3 sm:flex-row" onSubmit={async (event) => { event.preventDefault(); const value = comment.trim(); if (!value) return; await onComment(item.id, value); setComment(''); }}><label className="sr-only" htmlFor="workshop-comment">{config.workshop.commentPlaceholder}</label><textarea id="workshop-comment" value={comment} onChange={(event) => setComment(event.target.value)} placeholder={config.workshop.commentPlaceholder} rows={2} className="min-h-12 flex-1 resize-y rounded-xl border bg-background px-3 py-2 text-sm outline-none focus:border-primary" /><button type="submit" disabled={saving || !comment.trim()} className="rounded-xl bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground disabled:opacity-50">{saving ? config.workshop.saving : config.workshop.addComment}</button></form><div className="mt-5 divide-y">{item.activity.map((event) => <div key={event.eventId} className="py-3"><div className="flex items-center justify-between gap-3"><strong className="text-xs">{config.workshop.eventLabels[event.eventType] ?? formatMetadata(event.eventType)}</strong><time className="text-[10px] text-muted-foreground">{new Date(event.occurredAt).toLocaleString('fr-FR')}</time></div>{typeof event.payload.comment === 'string' && <p className="mt-2 text-xs leading-5 text-muted-foreground">{event.payload.comment}</p>}</div>)}{item.activity.length === 0 && <p className="py-4 text-xs text-muted-foreground">{config.workshop.noActivity}</p>}</div></section>
  </>;
}

function GuideSteps({ title, steps }: { title: string; steps: Array<{ title: string; detail: string }> }) {
  return <section className="rounded-[22px] bg-secondary/60 p-5"><h3 className="text-sm font-semibold">{title}</h3><ol className="mt-4 space-y-4">{steps.map((step, index) => <li key={`${step.title}-${index}`} className="flex gap-3"><span className="step-number">{index + 1}</span><div><strong className="text-xs">{step.title}</strong><p className="mt-1 text-xs leading-5 text-muted-foreground">{step.detail}</p></div></li>)}</ol></section>;
}

function ShoppingPage({ items, onToggle, config }: { items: ShoppingListEntry[]; onToggle: (id: string, checked: boolean) => Promise<void>; config: SiteConfig }) {
  const priority: Record<string, string> = config.shopping.priorities;
  const required = items.filter((item) => item.kind === 'required');
  const planned = items.filter((item) => item.kind === 'planned');
  const rows = (entries: ShoppingListEntry[]) => <div className="mt-4 overflow-hidden rounded-[24px] border bg-card">{entries.map((item) => { const done = item.checked; const context = item.sourcePaintableProductNames.length > 0 ? `${config.shopping.requiredBy} ${item.sourcePaintableProductNames.join(', ')}` : item.reason; return <label key={item.id} className={'shopping-row w-full text-left ' + (done ? 'opacity-45' : '')}><input type="checkbox" className="size-5 accent-primary" checked={done} onChange={(event) => void onToggle(item.id, event.target.checked)} /><span className={'size-9 flex-none rounded-xl border ' + (!validColor(item.colorHex) ? 'unknown-color' : '')} style={swatchStyle(item.colorHex)} /><span className="min-w-0 flex-1"><strong className={'block truncate text-sm ' + (done ? 'line-through' : '')}>{item.name}</strong><span className="block truncate text-xs text-muted-foreground">{item.brand}{context ? ` · ${context}` : ''}</span></span>{item.planned && <span className="rounded-full bg-secondary px-2 py-1 text-[9px] font-semibold">{config.shopping.plannedTitle}</span>}<span className="rounded-full bg-secondary px-2.5 py-1 text-[10px] font-semibold">{priority[item.priority]}</span></label>; })}</div>;
  return <div className="mt-8 space-y-8">{required.length > 0 && <section><h2 className="text-lg font-semibold">{config.shopping.requiredTitle}</h2><p className="mt-1 text-xs text-muted-foreground">{config.shopping.derivedHint}</p>{rows(required)}</section>}{planned.length > 0 && <section><h2 className="text-lg font-semibold">{config.shopping.plannedTitle}</h2><p className="mt-1 text-xs text-muted-foreground">{config.shopping.plannedHint}</p>{rows(planned)}</section>}</div>;
}

function DocumentationPage({ documentation, config }: { documentation: DocumentationData | null; config: SiteConfig }) {
  if (!documentation) return <p className="mt-8 text-sm text-muted-foreground">{config.about.loading}</p>;
  return <div className="mt-8 grid gap-4">{documentation.documents.map((document) => <article key={document.id} className="rounded-[24px] border bg-card p-5 shadow-sm sm:p-7"><h2 className="text-lg font-semibold">{config.about.documentTitles[document.id] ?? document.id}</h2><MarkdownDocument markdown={document.markdown} /></article>)}</div>;
}

function VersionPage({ about, config }: { about: AboutData | null; config: SiteConfig }) {
  if (!about) return <p className="mt-8 text-sm text-muted-foreground">{config.about.loading}</p>;
  return <dl className="mt-8 grid max-w-2xl gap-4 sm:grid-cols-2"><div className="rounded-[24px] border bg-card p-6 shadow-sm"><dt className="text-[10px] font-semibold uppercase tracking-wide text-muted-foreground">{config.about.versionLabel}</dt><dd className="mt-2 text-xl font-semibold">{about.version}</dd></div><div className="rounded-[24px] border bg-card p-6 shadow-sm"><dt className="text-[10px] font-semibold uppercase tracking-wide text-muted-foreground">{config.about.authorLabel}</dt><dd className="mt-2 text-xl font-semibold">{about.author}</dd></div></dl>;
}

function ApiDocumentationPage({ config }: { config: SiteConfig }) {
  return <section className="mt-8 overflow-hidden rounded-[24px] border bg-card shadow-sm"><div className="flex items-center justify-between gap-4 border-b px-5 py-4"><p className="text-sm text-muted-foreground">{config.about.apiDescription}</p><a className="inline-flex items-center gap-2 text-xs font-semibold text-primary" href="/swagger-ui/index.html" target="_blank" rel="noreferrer">OpenAPI <ExternalLink size={14} /></a></div><iframe className="h-[72vh] w-full bg-white" title={config.about.apiTitle} src="/swagger-ui/index.html" /></section>;
}

function PaintModelPage({ paintModel, quality, config }: { paintModel: PaintModelSchema; quality: PaintCatalogQuality | null; config: SiteConfig }) {
  const filters = [...paintModel['x-filters']].sort((left, right) => left.order - right.order);
  const sortOptions = [...paintModel['x-sort-options']].sort((left, right) => left.order - right.order);
  return <><section className="mt-8 rounded-[24px] border bg-card p-5 shadow-sm sm:p-7"><h2 className="text-lg font-semibold">{config.about.qualityTitle}</h2><p className="mt-2 text-xs leading-5 text-muted-foreground">{config.about.qualityDescription}</p>{quality ? <><div className="mt-5 grid grid-cols-2 gap-3 lg:grid-cols-4"><SummaryCell value={quality.missingColorHex} label={config.about.missingColorHex} /><SummaryCell value={quality.missingColorFamily} label={config.about.missingColorFamily} /><SummaryCell value={quality.unknownFinish} label={config.about.unknownFinish} /><SummaryCell value={quality.unknownCoverage} label={config.about.unknownCoverage} /><SummaryCell value={quality.technicalReviewRequired} label={config.about.technicalReviewRequired} /><SummaryCell value={quality.sourcedImagesWithoutLicense} label={config.about.sourcedImagesWithoutLicense} /><SummaryCell value={quality.realResultImages} label={config.about.realResultImages} /></div><h3 className="mt-6 text-sm font-semibold">{config.about.imageQualityBreakdown}</h3><div className="mt-3 flex flex-wrap gap-2">{quality.imageQualities.map((entry) => <span key={entry.quality} className="rounded-full bg-secondary px-3 py-1.5 text-xs"><strong>{entry.count}</strong> · {metadataLabel(config, entry.quality)}</span>)}</div><h3 className="mt-6 text-sm font-semibold">{config.about.imageQualityLimitations}</h3>{quality.imageLimitations.length > 0 ? <div className="mt-3 grid gap-2 sm:grid-cols-2 lg:grid-cols-3">{quality.imageLimitations.map((entry) => <div key={`${entry.brand}|${entry.code}`} className="rounded-2xl border bg-secondary/40 px-4 py-3 text-xs"><strong>{entry.count} · {entry.brand}</strong><span className="mt-1 block text-muted-foreground">{metadataLabel(config, entry.code)}</span></div>)}</div> : <p className="mt-3 text-xs text-muted-foreground">{config.about.noImageQualityLimitations}</p>}</> : <p className="mt-5 text-sm text-muted-foreground">{config.errors.loading}</p>}</section><div className="mt-5 grid gap-5 lg:grid-cols-[1.4fr_1fr]">
    <section className="rounded-[24px] border bg-card p-5 shadow-sm sm:p-7">
      <div className="flex flex-wrap items-center justify-between gap-3"><div><p className="eyebrow">JSON Schema</p><h2 className="mt-2 text-lg font-semibold">{paintModel.title}</h2></div><span className="rounded-full bg-secondary px-3 py-1 text-xs font-semibold">{config.about.modelVersion} {paintModel['x-model-version']}</span></div>
      <h3 className="mt-7 text-sm font-semibold">{config.about.filterFields}</h3>
      <div className="mt-3 divide-y rounded-2xl border">{filters.map((filter) => <div key={filter.id} className="grid gap-1 px-4 py-3 sm:grid-cols-[1fr_1fr]"><strong className="text-xs">{configuredLabel(config, filter.labelKey)}</strong><code className="text-xs text-muted-foreground">{filter.queryParameter}{filter.facetId ? ` · ${filter.facetId}` : ''} · {filter.control}</code></div>)}</div>
      <h3 className="mt-7 text-sm font-semibold">{config.collection.sort}</h3>
      <div className="mt-3 divide-y rounded-2xl border">{sortOptions.map((option) => <div key={option.id} className="grid gap-1 px-4 py-3 sm:grid-cols-[1fr_1fr]"><strong className="text-xs">{configuredLabel(config, option.labelKey)}</strong><code className="text-xs text-muted-foreground">{option.queryValue}</code></div>)}</div>
    </section>
    <section className="rounded-[24px] border bg-card p-5 shadow-sm sm:p-7"><h2 className="text-lg font-semibold">{config.about.vocabularies}</h2><div className="mt-4 space-y-4">{Object.entries(paintModel['x-vocabularies']).map(([id, values]) => <div key={id}><code className="text-xs font-semibold">{id}</code><div className="mt-2 flex flex-wrap gap-1.5">{values.map((value) => <span key={value} className="rounded-full bg-secondary px-2.5 py-1 text-[10px]">{metadataLabel(config, value)}</span>)}</div></div>)}</div><a className="mt-6 inline-flex items-center gap-2 text-xs font-semibold text-primary" href="/api/v1/market/paint-product-model" target="_blank" rel="noreferrer">{config.about.openPaintSchema}<ExternalLink size={14} /></a></section>
  </div></>;
}

function MarkdownDocument({ markdown }: { markdown: string }) {
  const blocks = markdown.trim().split(/\n\s*\n/);
  return <div className="mt-5 space-y-4 text-sm leading-6 text-muted-foreground">{blocks.map((block, index) => {
    const value = block.trim();
    if (value.startsWith('### ')) return <h4 key={index} className="pt-2 text-sm font-semibold text-foreground">{value.slice(4)}</h4>;
    if (value.startsWith('## ')) return <h3 key={index} className="pt-3 text-base font-semibold text-foreground">{value.slice(3)}</h3>;
    if (value.startsWith('# ')) return null;
    if (value.startsWith('```') && value.endsWith('```')) return <pre key={index} className="overflow-x-auto rounded-xl bg-secondary p-4 text-xs text-foreground"><code>{value.replace(/^```[^\n]*\n?/, '').replace(/```$/, '')}</code></pre>;
    const lines = value.split('\n');
    if (lines.every((line) => line.startsWith('- '))) return <ul key={index} className="list-disc space-y-1 pl-5">{lines.map((line) => <li key={line}>{line.slice(2)}</li>)}</ul>;
    return <p key={index}>{lines.join(' ')}</p>;
  })}</div>;
}

function PaintDetail({ initialPaint, config, revision, onClose, onPhotoSaved }: {
  initialPaint: PaintCardModel; config: SiteConfig; revision: number; onClose: () => void; onPhotoSaved: () => void;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  const photoAction = useRef<HTMLButtonElement>(null);
  const [stock, setStock] = useState<WorkshopPaintStock | null>(null);
  const [notice, setNotice] = useState<Notice>('');
  const [retry, setRetry] = useState(0);
  const [replacing, setReplacing] = useState(false);
  const [replaced, setReplaced] = useState(false);
  const [loadedKey, setLoadedKey] = useState('');
  const readKey = `${revision}:${retry}`;
  const loading = loadedKey !== readKey;
  const paint: PaintCardModel = stock ? { ...stock.paintProduct, personalPhoto: stock.personalPhoto } : initialPaint;
  const visual = usePaintVisual(paint);
  useEffect(() => {
    const controller = new AbortController();
    apiFetch('/api/v1/workshop/paint-stocks/' + encodeURIComponent(initialPaint.id), { signal: controller.signal })
      .then(response => response.json() as Promise<{ stock: WorkshopPaintStock }>)
      .then(result => { if (!controller.signal.aborted) { setStock(result.stock); setNotice(''); } })
      .catch(error => { if (!controller.signal.aborted) setNotice(failureNotice(config.errors.requestFailed, error)); })
      .finally(() => { if (!controller.signal.aborted) setLoadedKey(readKey); });
    return () => controller.abort();
  }, [initialPaint.id, readKey, config.errors.requestFailed]);
  const titleId = `paint-detail-${paint.id}`;
  useEffect(() => {
    const element = dialog.current;
    const opener = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    element?.showModal();
    return () => { element?.close(); opener?.focus(); };
  }, []);
  const labels = config.paintDetail;
  const hasColor = validColor(paint.colorHex);
  const image = visual.url;
  const photoLabel = visual.quality === 'none' || visual.quality === 'color_swatch' ? labels.definePhoto : labels.replacePhoto;
  const characteristics = [
    [labels.referenceLabel, paint.reference],
    [labels.volumeLabel, paint.volumeMl > 0 ? `${paint.volumeMl} ml` : '—'],
    [labels.colorFamily, metadataLabel(config, paint.colorFamily)],
    [config.collection.lifecycleFilter, metadataLabel(config, paint.lifecycleStatus)],
    [config.collection.roleFilter, paint.profile.roles.map(value => metadataLabel(config, value)).join(', ')],
    [config.collection.applicationMethodFilter, paint.profile.applicationMethods.map(value => metadataLabel(config, value)).join(', ')],
    [config.collection.applicationSystemFilter, metadataLabel(config, paint.profile.applicationSystem)],
    [config.collection.coverageFilter, metadataLabel(config, paint.profile.coverage)],
    [config.collection.finishFilter, metadataLabel(config, paint.profile.finish)],
    [config.collection.mediumFilter, metadataLabel(config, paint.profile.medium)],
    ...(paint.profile.effects.length > 0 ? [[config.collection.effectFilter, paint.profile.effects.map(value => metadataLabel(config, value)).join(', ')]] : []),
    ...(paint.profile.undercoatTone && paint.profile.undercoatTone !== 'unknown' ? [[config.collection.undercoatFilter, metadataLabel(config, paint.profile.undercoatTone)]] : []),
    ...(paint.profile.preHighlightedSurfaceRecommended ? [[labels.preHighlight, labels.recommended]] : []),
  ];
  const localUsage = paint.usageInstructions;
  const provenanceTitleId = `${titleId}-provenance`;
  return <dialog ref={dialog} aria-labelledby={titleId} className="paint-detail-dialog" onCancel={event => { event.preventDefault(); onClose(); }}>
    <header className="paint-detail-header">
      <div className="paint-detail-heading"><p className="eyebrow">{labels.sheet}</p><h2 id={titleId} className="mt-1 text-xl font-semibold">{paint.name}</h2>
        <p className="mt-1 text-sm text-muted-foreground">{paint.brand} · {paint.range}</p></div>
      <div className="paint-detail-actions">
        {stock?.canReplacePhoto && !notice && <button ref={photoAction} type="button" disabled={loading} aria-expanded={replacing}
          className="rounded-xl border px-3 py-2.5 text-sm font-semibold disabled:opacity-40"
          onClick={() => { setReplacing(value => !value); setReplaced(false); }}>{replacing ? labels.cancelReplacement : photoLabel}</button>}
        {paint.manufacturerUrl && <a href={paint.manufacturerUrl} target="_blank" rel="noreferrer" className="inline-flex items-center gap-2 rounded-xl border px-3 py-2.5 text-sm font-semibold text-primary"><ExternalLink size={16} />{labels.openManufacturerSheet}</a>}
        <button type="button" onClick={onClose} aria-label={labels.close} className="grid size-10 shrink-0 place-items-center rounded-xl border"><X size={18} /></button>
      </div>
    </header>
    <div className="paint-detail-body">
      <section className="paint-detail-overview" aria-label={labels.characteristics}>
        <figure className="paint-detail-visual">
        <div className={'paint-detail-image ' + (image ? 'paint-image-surface' : !hasColor ? 'unknown-color' : '')}
          style={!image && hasColor ? { backgroundColor: paint.colorHex } : undefined}>
          {image ? <img src={image} onError={visual.onError} className="h-full w-full object-contain" alt={`${labels.productVisual} ${paint.name}`} />
            : !hasColor && <span>{labels.toQualify}</span>}
        </div>
        <figcaption>
          <PaintVisualQuality quality={visual.quality} config={config} />
          <section className="paint-detail-provenance" aria-labelledby={provenanceTitleId}>
            <h3 id={provenanceTitleId} className="font-semibold">{labels.imageProvenance}</h3>
            <p className="mt-3 text-muted-foreground">{labels.imageQualityHelp}</p>
            {visual.personalPhoto ? <>
              <p className="mt-3">{config.paintPots.personalPhoto} · {visual.personalPhoto.paintPotId}</p>
              <p className="mt-2">{labels.photoAddedOn} {new Date(visual.personalPhoto.addedAt).toLocaleString('fr-FR')}</p>
              {visual.personalPhoto.caption && <p className="mt-2">{visual.personalPhoto.caption}</p>}
              {visual.personalPhoto.processingMethod && visual.url !== visual.personalPhoto.originalUrl && <p className="mt-2">{labels.photoProcessing} : {visual.personalPhoto.processingMethod}</p>}
              <a href={visual.personalPhoto.originalUrl} target="_blank" rel="noreferrer" className="mt-2 inline-block underline">{config.paintPots.originalPhoto}</a>
            </> : visual.url ? <>
              {paint.manufacturerImageCredit && <p className="mt-3">{paint.manufacturerImageCredit}</p>}
              {paint.manufacturerImageSource && <a href={paint.manufacturerImageSource} target="_blank" rel="noreferrer" className="mt-2 inline-block underline">{labels.source}</a>}
              {paint.manufacturerImageQualityVerifiedAt && <p className="mt-2">{labels.imageVerifiedOn} {paint.manufacturerImageQualityVerifiedAt}</p>}
              {paint.manufacturerImageQualityLimitationDetail && <div className="mt-3">
                <h4 className="font-semibold">{labels.imageQualityLimitation}</h4><p className="mt-2">{paint.manufacturerImageQualityLimitationDetail}</p>
                <p className="mt-2 text-muted-foreground">{metadataLabel(config, paint.manufacturerImageQualityLimitationCode)} · {labels.imageQualityLimitationObservedOn} {paint.manufacturerImageQualityLimitationObservedAt}</p>
              </div>}
            </> : <p className="mt-3">{labels.noProductVisual}</p>}
          </section>
          {replaced && <output className="mt-3 block text-sm">{labels.photoReplaced}</output>}
        </figcaption>
        </figure>
        <div className="min-w-0"><h3 className="mb-3 text-base font-semibold">{labels.characteristics}</h3>
          <dl className="paint-characteristics">{characteristics.map(([label, value]) => <div key={label}>
            <dt>{label}</dt><dd>{value || '—'}</dd>
          </div>)}
          <div className="paint-characteristic-wide"><dt>{labels.catalogEditions}</dt><dd>
            {paint.catalogMemberships.length > 0 ? <ul className="space-y-2">{paint.catalogMemberships.map(membership => <li key={membership.catalogEditionId}>
              <a href={membership.sourceUrl} target="_blank" rel="noreferrer" className="underline">{membership.title} · {membership.editionLabel}</a>
              {membership.publicationYear && !membership.editionLabel.includes(String(membership.publicationYear))
                && !membership.title.includes(String(membership.publicationYear)) && <span> ({membership.publicationYear})</span>}
              {membership.locator && <span className="block text-xs font-normal text-muted-foreground">{membership.locator}</span>}
            </li>)}</ul> : labels.notDocumented}
          </dd></div>
          {paint.manufacturerVerifiedAt && <div className="paint-characteristic-wide"><dt>{labels.verifiedOn}</dt><dd>{paint.manufacturerVerifiedAt}</dd></div>}
          </dl>
        </div>
      </section>
      <AppNotice notice={notice} />
      {notice && <button type="button" className="justify-self-start rounded-xl border px-4 py-2 text-sm" onClick={() => setRetry(value => value + 1)}>{labels.retry}</button>}
      {replacing && stock?.canReplacePhoto && !notice && <PaintProductPotPhotoReplacement paintProductId={paint.id} config={config}
        submitLabel={photoLabel} onSaved={() => {
          setReplacing(false); setReplaced(true);
          photoAction.current?.focus({ preventScroll: true });
          photoAction.current?.scrollIntoView({ block: 'center', behavior: 'smooth' });
          onPhotoSaved();
        }} />}
      {paint.warnings && <aside className="guide-notice"><h3 className="font-semibold">{labels.warnings}</h3><p className="mt-2">{paint.warnings}</p></aside>}
      {paint.manufacturerDescription && <p className="text-sm leading-6 text-muted-foreground">{paint.manufacturerDescription}</p>}
      {paint.recommendedUses.length > 0 && <section className="paint-detail-section"><h3>{labels.recommendedUses}</h3>
        <ul className="mt-3 flex flex-wrap gap-2">{paint.recommendedUses.map(use => <li key={use} className="rounded-lg border px-3 py-2 text-sm">{use}</li>)}</ul>
      </section>}
      {paint.usageGuideIds.length > 0 && <PaintUsageGuides key={paint.id} paintProductId={paint.id} config={config} />}
      {(localUsage.summary || localUsage.steps.length > 0 || localUsage.tips.length > 0) && <section className="paint-detail-section">
        <h3>{paint.usageGuideIds.length > 0 ? labels.specificInstructions : labels.usageInstructions}</h3>
        {localUsage.reviewRequired && <p className="guide-notice">{labels.instructionsReviewRequired}</p>}
        <UsageContent content={localUsage} config={config} />
      </section>}
      {paint.resultImage && <section className="paint-detail-section"><h3>{labels.appliedResult}</h3>
        <div className="mt-3 aspect-video overflow-hidden rounded-2xl border bg-secondary"><ResilientPaintImage primary={paint.resultImage} fallback={paint.resultImageSource} alt={`${labels.appliedResult} ${paint.name}`} /></div>
        {paint.resultReferenceUrl && <a href={paint.resultReferenceUrl} target="_blank" rel="noreferrer" className="mt-2 inline-flex text-sm underline">{labels.realResultSource}</a>}
      </section>}
    </div>
  </dialog>;
}
