'use client';

import {
  BookOpen, Check, ChevronDown, ChevronLeft, ChevronRight, CircleAlert, Droplets, ExternalLink,
  FolderCog, Grid2X2, House, Info, ListChecks, ListFilter, PackageOpen, Paintbrush, Search,
  ShoppingBasket, Sparkles, X,
} from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { useRef } from 'react';
import type { CSSProperties, ReactNode } from 'react';
import type { Paint, PaintFacets, PaintModelSchema, ShoppingItem } from '@/models/paint-model';
import type {
  Dashboard, PaintableCatalogItem, PaintableProduct, PaintableProductImportPreview, PaintableProductSummary,
  WorkshopItem, WorkshopItemDetail, WorkshopOverview, PaintingProjectSummary,
} from '@/models/paintable-product-model';
import type { SiteConfig } from '@/models/site-config-model';
import { appRoutePath, parseAppRoute } from '@/utils/app-routing';
import type { AppRoute as Route } from '@/utils/app-routing';
import { formatMetadata } from '@/utils/paint-search';

type PaintFilters = Record<string, string>;
type FilterOptions = Record<string, { value: string; count: number }[]>;
type AboutData = { name: string; version: string; author: string };
type DocumentationData = { documents: Array<{ id: string; audience: 'user' | 'administrator'; markdown: string }> };

const emptyPaintFilters: PaintFilters = {};
const PAINT_PAGE_SIZE = 60;

function isPaintRoute(route: Route) {
  return route.view === 'marketPaints' || route.view === 'workshopPaints';
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

function configuredLabel(config: SiteConfig, labelKey: string) {
  const [section, key] = labelKey.split('.');
  const configKey = key?.replace(/_([a-z])/g, (_, letter: string) => letter.toUpperCase());
  const values = (config as unknown as Record<string, Record<string, unknown>>)[section];
  const label = values?.[configKey];
  return typeof label === 'string' ? label : formatMetadata(key ?? labelKey);
}

function NavButton({ icon, label, active, badge, onClick }: {
  icon: ReactNode; label: string; active: boolean; badge?: string; onClick: () => void;
}) {
  return (
    <button type="button" className={'nav-item w-full ' + (active ? 'nav-item-active' : '')} onClick={onClick}>
      {icon}<span className="truncate">{label}</span>
      {badge && <span className="ml-auto rounded-full bg-current/10 px-2 py-0.5 text-[11px]">{badge}</span>}
    </button>
  );
}

function FacetSelect({ label, allLabel, value, options, onChange }: {
  label: string; allLabel: string; value: string; options: { value: string; count: number }[]; onChange: (value: string) => void;
}) {
  return (
    <label className="form-field">
      <span>{label}</span>
      <span className="relative">
        <select value={value} onChange={(event) => onChange(event.target.value)} className="h-11 w-full appearance-none rounded-xl border bg-card px-3 pr-9 text-sm outline-none focus:border-primary focus:ring-4 focus:ring-primary/10">
          <option value="">{allLabel}</option>
          {options.map((option) => <option key={option.value} value={option.value}>{formatMetadata(option.value)} ({option.count})</option>)}
        </select>
        <ChevronDown className="pointer-events-none absolute right-3 top-1/2 size-3.5 -translate-y-1/2 text-muted-foreground" />
      </span>
    </label>
  );
}

function ResilientPaintImage({ primary, fallback, alt }: { primary: string; fallback: string; alt: string }) {
  const [source, setSource] = useState(primary || fallback);
  if (!source) return null;
  return (
    <img
      src={source}
      alt={alt}
      className="h-full w-full object-contain"
      onError={() => setSource((current) => fallback && current !== fallback ? fallback : '')}
    />
  );
}

function PaintCard({ paint, config, onOpen }: { paint: Paint; config: SiteConfig; onOpen: () => void }) {
  const image = paint.resultImage || paint.manufacturerImage;
  const hasColor = validColor(paint.colorHex);
  return (
    <button type="button" className="paint-card group w-full text-left" onClick={onOpen}>
      <div className={'paint-swatch ' + (!hasColor ? 'unknown-color' : '')} style={hasColor ? { background: `color-mix(in srgb, ${paint.colorHex} 14%, white)` } : undefined}>
        {image
          ? <ResilientPaintImage key={`${image}|${paint.manufacturerImageSource}`} primary={image} fallback={paint.manufacturerImageSource} alt={`${config.paintDetail.productVisual} ${paint.brand} ${paint.name}`} />
          : hasColor ? <span className="absolute inset-0" style={{ background: paint.colorHex }} /> : <span className="absolute inset-0 grid place-items-center px-2 text-center text-[10px] font-semibold text-muted-foreground">{config.paintDetail.toQualify}</span>}
        <span className="absolute bottom-3 left-3 z-[1] rounded-full bg-black/35 px-2 py-1 text-[10px] font-semibold uppercase tracking-wider text-white backdrop-blur-sm">{paint.reference || paint.range}</span>
      </div>
      <div className="min-w-0 flex-1 py-0.5">
        <p className="text-[11px] font-semibold uppercase tracking-[0.08em] text-muted-foreground">{paint.brand}</p>
        <h3 className="mt-0.5 truncate text-[15px] font-semibold tracking-tight">{paint.name}</h3>
        <div className="mt-3 flex flex-wrap gap-1.5">
          <span className="rounded-full bg-secondary px-2.5 py-1 text-[11px] font-medium">{formatMetadata(paint.profile.roles[0] ?? paint.profile.applicationSystem)}</span>
          {(paint.quantity ?? 0) > 0 && <span className="rounded-full bg-primary/8 px-2.5 py-1 text-[11px] font-semibold text-primary">× {paint.quantity}</span>}
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
  id: 'my-workshop', paintingProjects: [], projectCount: 0, itemCount: 0,
  completedItemCount: 0, progressPercentage: 0, recentActivity: [],
};

export function PaintApp({ initialDashboard, config, paintModel }: { initialDashboard: Dashboard; config: SiteConfig; paintModel: PaintModelSchema }) {
  const [route, setRoute] = useState<Route>(() => parseAppRoute(window.location.pathname));
  const [dashboard, setDashboard] = useState(initialDashboard);
  const [paints, setPaints] = useState<Paint[]>([]);
  const [paintResultCount, setPaintResultCount] = useState(0);
  const [paintCatalogTotal, setPaintCatalogTotal] = useState(0);
  const [paintOffset, setPaintOffset] = useState(0);
  const [filterOptions, setFilterOptions] = useState<FilterOptions>(emptyFacets);
  const [productSummaries, setProductSummaries] = useState<PaintableProductSummary[]>([]);
  const [activeProduct, setActiveProduct] = useState<PaintableProduct | null>(null);
  const [loadingProduct, setLoadingProduct] = useState(route.view === 'product');
  const [workshop, setWorkshop] = useState<WorkshopOverview>(emptyWorkshop);
  const [workshopItems, setWorkshopItems] = useState<WorkshopItem[]>([]);
  const [query, setQuery] = useState('');
  const [filters, setFilters] = useState<PaintFilters>(emptyPaintFilters);
  const [filtersOpen, setFiltersOpen] = useState(true);
  const [manufacturerSheetOnly, setManufacturerSheetOnly] = useState(false);
  const [realResultOnly, setRealResultOnly] = useState(false);
  const [selectedPaint, setSelectedPaint] = useState<Paint | null>(null);
  const [shoppingItems, setShoppingItems] = useState<ShoppingItem[]>([]);
  const [importPreviewState, setImportPreviewState] = useState<{ productId: string; preview: PaintableProductImportPreview } | null>(null);
  const [importing, setImporting] = useState(false);
  const [notice, setNotice] = useState('');
  const [workshopItemDetail, setWorkshopItemDetail] = useState<WorkshopItemDetail | null>(null);
  const [savingItem, setSavingItem] = useState(false);
  const [aboutData, setAboutData] = useState<AboutData | null>(null);
  const [documentation, setDocumentation] = useState<DocumentationData | null>(null);
  const [serverRevision, setServerRevision] = useState(0);

  const activateRoute = useCallback((next: Route) => {
    const sameView = route.view === next.view;
    const sameProduct = route.view === 'product' && next.view === 'product'
      && route.productId === next.productId && route.paintingProjectId === next.paintingProjectId;
    if (!sameView || !isPaintRoute(next)) { setPaints([]); setFilterOptions(emptyFacets); setSelectedPaint(null); }
    if (!sameView || next.view !== 'marketProducts') setProductSummaries([]);
    if (!sameProduct) { setActiveProduct(null); setWorkshopItems([]); setImportPreviewState(null); }
    setLoadingProduct(!sameProduct && next.view === 'product');
    if (!sameView || next.view !== 'shopping') setShoppingItems([]);
    if (!sameView || (next.view !== 'aboutUser' && next.view !== 'aboutAdmin')) setDocumentation(null);
    if (!sameView || next.view !== 'aboutVersion') setAboutData(null);
    setPaintOffset(0);
    setRoute(next);
  }, [route]);

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
    events.addEventListener('domain-events-committed', invalidate);
    events.addEventListener('resync-required', invalidate);
    return () => events.close();
  }, []);

  useEffect(() => {
    if (route.view !== 'home' || serverRevision === 0) return;
    const controller = new AbortController();
    fetch('/api/v1/dashboard', { signal: controller.signal, headers: { accept: 'application/json' } })
      .then((response) => { if (!response.ok) throw new Error(String(response.status)); return response.json() as Promise<Dashboard>; })
      .then(setDashboard)
      .catch((reason) => { if (!(reason instanceof DOMException && reason.name === 'AbortError')) setNotice(config.errors.requestFailed); });
    return () => controller.abort();
  }, [config.errors.requestFailed, route.view, serverRevision]);

  useEffect(() => {
    if (route.view !== 'aboutUser' && route.view !== 'aboutAdmin') return;
    const controller = new AbortController();
    const audience = route.view === 'aboutUser' ? 'user' : 'administrator';
    fetch(`/api/v1/documentation?audience=${audience}`, { signal: controller.signal, headers: { accept: 'application/json' } })
      .then((response) => { if (!response.ok) throw new Error(String(response.status)); return response.json() as Promise<DocumentationData>; })
      .then(setDocumentation)
      .catch((reason) => { if (!(reason instanceof DOMException && reason.name === 'AbortError')) setNotice(config.errors.requestFailed); });
    return () => controller.abort();
  }, [config.errors.requestFailed, route.view]);

  useEffect(() => {
    if (route.view !== 'aboutVersion') return;
    const controller = new AbortController();
    fetch('/api/v1/about', { signal: controller.signal, headers: { accept: 'application/json' } })
      .then((response) => { if (!response.ok) throw new Error(String(response.status)); return response.json() as Promise<AboutData>; })
      .then(setAboutData)
      .catch((reason) => { if (!(reason instanceof DOMException && reason.name === 'AbortError')) setNotice(config.errors.requestFailed); });
    return () => controller.abort();
  }, [config.errors.requestFailed, route.view]);

  const activeCatalogItem = activeProduct?.items.find((item) => item.id === route.catalogItemId) ?? activeProduct?.items[0];
  const activePaintingProject = workshop.paintingProjects.find((project) => project.projectId === route.paintingProjectId);
  const importPreview = importPreviewState && importPreviewState.productId === route.productId
    ? importPreviewState.preview
    : null;

  useEffect(() => {
    if (route.view !== 'product' || !route.productId) return;
    const productId = route.productId;
    const controller = new AbortController();
    const requests: Promise<Response>[] = [
      fetch(`/api/v1/market/paintable-products/${productId}`, { signal: controller.signal, headers: { accept: 'application/json' } }),
      fetch(`/api/v1/workshop/painting-project-import-previews/${productId}`, { signal: controller.signal, headers: { accept: 'application/json' } }),
    ];
    if (route.paintingProjectId) {
      requests.push(fetch('/api/v1/workshop', { signal: controller.signal, headers: { accept: 'application/json' } }));
      requests.push(fetch(`/api/v1/workshop/items?paintingProjectId=${encodeURIComponent(route.paintingProjectId)}`, { signal: controller.signal, headers: { accept: 'application/json' } }));
    }
    Promise.all(requests)
      .then(async (responses) => {
        if (responses.some((response) => !response.ok)) throw new Error('Product load failed');
        const product = await responses[0].json() as { paintableProduct: PaintableProduct };
        const preview = await responses[1].json() as { preview: PaintableProductImportPreview };
        const workshopResult = responses[2] ? await responses[2].json() as { workshop: WorkshopOverview } : null;
        const itemResult = responses[3] ? await responses[3].json() as { items: WorkshopItem[] } : null;
        return { product: product.paintableProduct, preview: preview.preview, workshop: workshopResult?.workshop, items: itemResult?.items };
      })
      .then((result) => {
        setActiveProduct(result.product);
        setLoadingProduct(false);
        setImportPreviewState({ productId, preview: result.preview });
        if (result.workshop) setWorkshop(result.workshop);
        if (result.items) setWorkshopItems(result.items);
      })
      .catch((reason) => {
        if (!(reason instanceof DOMException && reason.name === 'AbortError')) { setLoadingProduct(false); setNotice(config.errors.requestFailed); }
      });
    return () => controller.abort();
  }, [config.errors.requestFailed, route.paintingProjectId, route.productId, route.view, serverRevision]);

  useEffect(() => {
    if (route.view !== 'marketProducts') return;
    const controller = new AbortController();
    Promise.all([
      fetch('/api/v1/market/paintable-products', { signal: controller.signal, headers: { accept: 'application/json' } }),
      fetch('/api/v1/workshop', { signal: controller.signal, headers: { accept: 'application/json' } }),
    ])
      .then(async ([marketResponse, workshopResponse]) => {
        if (!marketResponse.ok || !workshopResponse.ok) throw new Error('Product collections load failed');
        const marketResult = await marketResponse.json() as { paintableProducts: PaintableProductSummary[] };
        const workshopResult = await workshopResponse.json() as { workshop: WorkshopOverview };
        return { products: marketResult.paintableProducts, workshop: workshopResult.workshop };
      })
      .then((result) => { setProductSummaries(result.products); setWorkshop(result.workshop); })
      .catch((reason) => { if (!(reason instanceof DOMException && reason.name === 'AbortError')) setNotice(config.errors.requestFailed); });
    return () => controller.abort();
  }, [config.errors.requestFailed, route.view, serverRevision]);

  useEffect(() => {
    if (route.view !== 'workshop') return;
    const controller = new AbortController();
    fetch('/api/v1/workshop', { signal: controller.signal, headers: { accept: 'application/json' } })
      .then((response) => { if (!response.ok) throw new Error(String(response.status)); return response.json() as Promise<{ workshop: WorkshopOverview }>; })
      .then((result) => setWorkshop(result.workshop))
      .catch((reason) => { if (!(reason instanceof DOMException && reason.name === 'AbortError')) setNotice(config.errors.requestFailed); });
    return () => controller.abort();
  }, [config.errors.requestFailed, route.view, serverRevision]);

  useEffect(() => {
    if (route.view !== 'shopping') return;
    const controller = new AbortController();
    fetch('/api/v1/shopping/items', { signal: controller.signal, headers: { accept: 'application/json' } })
      .then((response) => { if (!response.ok) throw new Error(String(response.status)); return response.json() as Promise<{ items: ShoppingItem[] }>; })
      .then((result) => setShoppingItems(result.items))
      .catch((reason) => { if (!(reason instanceof DOMException && reason.name === 'AbortError')) setNotice(config.errors.requestFailed); });
    return () => controller.abort();
  }, [config.errors.requestFailed, route.view, serverRevision]);

  async function fetchWorkshopItem(itemId: string, signal?: AbortSignal) {
    const response = await fetch(`/api/v1/workshop/items/${encodeURIComponent(itemId)}`, {
      signal, headers: { accept: 'application/json' },
    });
    if (!response.ok) throw new Error(String(response.status));
    const result = await response.json() as { item: WorkshopItemDetail };
    return result.item;
  }

  useEffect(() => {
    if (route.view !== 'item' || !route.itemId) return;
    const itemId = route.itemId;
    const controller = new AbortController();
    Promise.all([
      fetchWorkshopItem(itemId, controller.signal),
      fetch('/api/v1/workshop', { signal: controller.signal, headers: { accept: 'application/json' } })
        .then((response) => { if (!response.ok) throw new Error(String(response.status)); return response.json() as Promise<{ workshop: WorkshopOverview }>; }),
    ])
      .then(([item, result]) => { setWorkshopItemDetail(item); setWorkshop(result.workshop); })
      .catch((reason) => {
        if (!(reason instanceof DOMException && reason.name === 'AbortError')) setNotice(config.errors.requestFailed);
      });
    return () => controller.abort();
  }, [config.errors.requestFailed, route.itemId, route.view, serverRevision]);

  const isPaintView = route.view === 'marketPaints' || route.view === 'workshopPaints';
  const paintSearchUrl = useCallback((offset: number) => {
    const params = new URLSearchParams({ page: String(Math.floor(offset / PAINT_PAGE_SIZE)), size: String(PAINT_PAGE_SIZE) });
    if (query.trim()) params.set('query', query.trim());
    Object.entries(filters).forEach(([key, value]) => { if (value) params.set(key, value); });
    if (manufacturerSheetOnly) params.set('manufacturerSheetOnly', 'true');
    if (realResultOnly) params.set('realResultOnly', 'true');
    const collection = route.view === 'workshopPaints' ? '/api/v1/workshop/paints' : '/api/v1/market/paints';
    return `${collection}?${params.toString()}`;
  }, [filters, manufacturerSheetOnly, query, realResultOnly, route.view]);

  const paintFacetUrl = useCallback(() => {
    const params = new URLSearchParams();
    if (query.trim()) params.set('query', query.trim());
    Object.entries(filters).forEach(([key, value]) => { if (value) params.set(key, value); });
    const collection = route.view === 'workshopPaints' ? '/api/v1/workshop/paints/facets' : '/api/v1/market/paints/facets';
    return params.size === 0 ? collection : `${collection}?${params.toString()}`;
  }, [filters, query, route.view]);

  useEffect(() => {
    if (!isPaintView) return;
    const controller = new AbortController();
    const timer = window.setTimeout(() => {
      fetch(paintSearchUrl(paintOffset), { signal: controller.signal, headers: { accept: 'application/json' } })
        .then((response) => { if (!response.ok) throw new Error(String(response.status)); return response.json() as Promise<{ paints: Array<Paint | { marketPaint: Paint; quantity: number }>; total: number }>; })
        .then((result) => {
          const paints = result.paints.map((paint) => 'marketPaint' in paint
            ? { ...paint.marketPaint, quantity: paint.quantity }
            : paint);
          setPaints(paints); setPaintResultCount(result.total);
        })
        .catch((reason) => { if (!(reason instanceof DOMException && reason.name === 'AbortError')) setNotice(config.errors.requestFailed); });
    }, 180);
    return () => { window.clearTimeout(timer); controller.abort(); };
  }, [config.errors.requestFailed, isPaintView, paintOffset, paintSearchUrl, serverRevision]);

  useEffect(() => {
    if (!isPaintView) return;
    const controller = new AbortController();
    fetch(paintFacetUrl(), { signal: controller.signal, headers: { accept: 'application/json' } })
      .then((response) => { if (!response.ok) throw new Error(String(response.status)); return response.json() as Promise<PaintFacets>; })
      .then((result) => {
        setFilterOptions(Object.fromEntries(result.facets.map((facet) => [facet.id, facet.values])));
        setPaintCatalogTotal(result.total);
      })
      .catch((reason) => { if (!(reason instanceof DOMException && reason.name === 'AbortError')) setNotice(config.errors.requestFailed); });
    return () => controller.abort();
  }, [config.errors.requestFailed, isPaintView, paintFacetUrl, serverRevision]);

  const brands = filterOptions.brands?.length ?? 0;
  const activeFilterCount = Object.values(filters).filter(Boolean).length + Number(manufacturerSheetOnly) + Number(realResultOnly);
  const title = route.view === 'home' ? config.home.title
    : route.view === 'marketPaints' ? config.market.paintsTitle
      : route.view === 'marketProducts' ? config.market.paintableProductsTitle
        : route.view === 'workshopPaints' ? config.collection.title
          : route.view === 'workshop' ? config.workshop.title
            : route.view === 'shopping' ? config.shopping.title
              : route.view === 'aboutUser' ? config.about.userTitle
              : route.view === 'aboutAdmin' ? config.about.administratorTitle
                : route.view === 'aboutPaintModel' ? config.about.paintModelTitle
                  : route.view === 'aboutApi' ? config.about.apiTitle
                  : route.view === 'aboutVersion' ? config.about.versionTitle
              : route.view === 'item' ? workshopItemDetail?.displayName ?? config.workshop.itemDetail
                : activeProduct?.name ?? config.errors.productNotFound;
  const description = route.view === 'home' ? config.home.description
    : route.view === 'marketPaints' ? config.market.paintsDescription
      : route.view === 'marketProducts' ? config.market.paintableProductsDescription
        : route.view === 'workshopPaints' ? config.collection.description
          : route.view === 'workshop' ? config.workshop.description
            : route.view === 'shopping' ? config.shopping.description
              : route.view === 'aboutUser' || route.view === 'aboutAdmin' ? config.about.description
                : route.view === 'aboutPaintModel' ? config.about.paintModelDescription
                  : route.view === 'aboutApi' ? config.about.apiDescription
                : route.view === 'aboutVersion' ? config.about.versionDescription
              : route.view === 'item' ? config.workshop.itemDetail
                : activeProduct?.scope ?? '';

  async function refreshDashboard() {
    const response = await fetch('/api/v1/dashboard', { headers: { accept: 'application/json' } });
    if (!response.ok) throw new Error(String(response.status));
    setDashboard(await response.json() as Dashboard);
  }

  async function importProduct(productId: string) {
    setImporting(true);
    setNotice('');
    try {
      const response = await fetch('/api/v1/workshop/painting-projects', {
        method: 'POST',
        headers: { 'content-type': 'application/json', 'Idempotency-Key': `create-painting-project:${productId}` },
        body: JSON.stringify({ paintableProductId: productId }),
      });
      if (!response.ok) throw new Error(String(response.status));
      await refreshDashboard();
      setImportPreviewState((current) => current?.productId === productId
        ? { ...current, preview: { ...current.preview, alreadyImported: true } }
        : current);
      setNotice(config.productDetail.importSuccess);
    } catch {
      setNotice(config.errors.requestFailed);
    } finally {
      setImporting(false);
    }
  }

  async function transitionItemStage(itemId: string, stage: string, action: string) {
    setSavingItem(true); setNotice('');
    try {
      const response = await fetch(`/api/v1/workshop/items/${encodeURIComponent(itemId)}/stage-transitions`, {
        method: 'POST',
        headers: { 'content-type': 'application/json', 'Idempotency-Key': crypto.randomUUID() },
        body: JSON.stringify({ stage, action }),
      });
      if (!response.ok) throw new Error(String(response.status));
      const [detail] = await Promise.all([fetchWorkshopItem(itemId), refreshDashboard()]);
      setWorkshopItemDetail(detail);
    } catch { setNotice(config.errors.requestFailed); } finally { setSavingItem(false); }
  }

  async function addItemComment(itemId: string, comment: string) {
    setSavingItem(true); setNotice('');
    try {
      const response = await fetch(`/api/v1/workshop/items/${encodeURIComponent(itemId)}/comments`, {
        method: 'POST',
        headers: { 'content-type': 'application/json', 'Idempotency-Key': crypto.randomUUID() },
        body: JSON.stringify({ comment }),
      });
      if (!response.ok) throw new Error(String(response.status));
      setWorkshopItemDetail(await fetchWorkshopItem(itemId));
    } catch { setNotice(config.errors.requestFailed); } finally { setSavingItem(false); }
  }

  async function addItemPhoto(itemId: string, file: File, caption: string, stage: string | null) {
    setSavingItem(true); setNotice('');
    try {
      const body = new FormData();
      body.append('file', file);
      if (caption.trim()) body.append('caption', caption.trim());
      if (stage) body.append('stage', stage);
      const response = await fetch(`/api/v1/workshop/items/${encodeURIComponent(itemId)}/photos`, {
        method: 'POST', headers: { 'Idempotency-Key': crypto.randomUUID() }, body,
      });
      if (!response.ok) throw new Error(String(response.status));
      setWorkshopItemDetail(await fetchWorkshopItem(itemId));
    } catch { setNotice(config.errors.requestFailed); } finally { setSavingItem(false); }
  }

  async function setShoppingStatus(itemId: string, checked: boolean) {
    setNotice('');
    try {
      const response = await fetch(`/api/v1/shopping/items/${encodeURIComponent(itemId)}/status`, {
        method: 'POST',
        headers: { 'content-type': 'application/json', 'Idempotency-Key': crypto.randomUUID() },
        body: JSON.stringify({ checked }),
      });
      if (!response.ok) throw new Error(String(response.status));
      setShoppingItems((current) => current.map((item) => item.id === itemId ? { ...item, checked } : item));
    } catch { setNotice(config.errors.requestFailed); }
  }

  function clearFilters() {
    setQuery(''); setFilters(emptyPaintFilters); setManufacturerSheetOnly(false); setRealResultOnly(false); setPaintOffset(0); setSelectedPaint(null);
  }

  function changeQuery(value: string) { setQuery(value); setPaintOffset(0); setSelectedPaint(null); }
  function changeFilters(value: PaintFilters) { setFilters(value); setPaintOffset(0); setSelectedPaint(null); }
  function changeManufacturerSheetOnly(value: boolean) { setManufacturerSheetOnly(value); setPaintOffset(0); setSelectedPaint(null); }
  function changeRealResultOnly(value: boolean) { setRealResultOnly(value); setPaintOffset(0); setSelectedPaint(null); }

  const marketNavigation = [
    { route: { view: 'marketPaints' } as Route, icon: <Droplets size={18} />, label: config.navigation.marketPaints, badge: String(dashboard.paintStats.total) },
    { route: { view: 'marketProducts' } as Route, icon: <PackageOpen size={18} />, label: config.navigation.marketPaintableProducts, badge: String(dashboard.paintableProductCount) },
  ];
  const workshopNavigation = [
    { route: { view: 'workshopPaints' } as Route, icon: <Paintbrush size={18} />, label: config.navigation.workshopPaints },
    { route: { view: 'workshop' } as Route, icon: <FolderCog size={18} />, label: config.navigation.workshopAdmin, badge: String(dashboard.workshop.projectCount) },
    { route: { view: 'shopping' } as Route, icon: <ShoppingBasket size={18} />, label: config.navigation.shopping },
  ];
  const aboutNavigation = [
    { route: { view: 'aboutUser' } as Route, icon: <BookOpen size={18} />, label: config.navigation.userDocumentation },
    { route: { view: 'aboutAdmin' } as Route, icon: <FolderCog size={18} />, label: config.navigation.adminDocumentation },
    { route: { view: 'aboutPaintModel' } as Route, icon: <ListFilter size={18} />, label: config.navigation.paintModel },
    { route: { view: 'aboutApi' } as Route, icon: <ExternalLink size={18} />, label: config.navigation.restApi },
    { route: { view: 'aboutVersion' } as Route, icon: <Info size={18} />, label: config.navigation.version },
  ];
  const mobileNavigation = [
    { route: { view: 'home' } as Route, icon: <House size={18} />, label: config.navigation.home },
    ...marketNavigation, ...workshopNavigation, aboutNavigation[0],
  ];

  return (
    <div className="min-h-screen bg-background text-foreground">
      <header className="sticky top-0 z-30 border-b bg-card/92 backdrop-blur-xl">
        <div className="mx-auto flex h-16 max-w-[1600px] items-center gap-3 px-4 lg:px-6">
          <button type="button" onClick={() => navigate({ view: 'home' })} className="flex min-w-0 items-center gap-3 text-left">
            <span className="grid size-10 flex-none place-items-center rounded-2xl bg-primary text-primary-foreground"><Paintbrush size={20} /></span>
            <span className="hidden min-w-0 sm:block"><strong className="block truncate text-sm">{config.brand.name}</strong><span className="block truncate text-[11px] text-muted-foreground">{config.brand.subtitle}</span></span>
          </button>
          {isPaintView && <label className="relative ml-auto w-full max-w-xl"><Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" /><input value={query} onChange={(event) => changeQuery(event.target.value)} placeholder={config.header.searchShortPlaceholder} aria-label={config.header.searchAriaLabel} className="h-10 w-full rounded-xl border bg-background pl-9 pr-3 text-sm outline-none focus:border-primary focus:ring-4 focus:ring-primary/10" /></label>}
        </div>
      </header>

      <div className="mx-auto flex max-w-[1600px]">
        <aside className="sticky top-16 hidden h-[calc(100vh-4rem)] w-64 flex-none border-r bg-card/55 p-4 lg:block">
          <nav aria-label={config.navigation.ariaLabel}>
            <NavButton icon={<House size={18} />} label={config.navigation.home} active={route.view === 'home'} onClick={() => navigate({ view: 'home' })} />
            <p className="nav-section-label">{config.navigation.marketSection}</p>
            {marketNavigation.map((item) => <NavButton key={item.route.view} {...item} active={route.view === item.route.view || (route.view === 'product' && !route.paintingProjectId && item.route.view === 'marketProducts')} onClick={() => navigate(item.route)} />)}
            <p className="nav-section-label">{config.navigation.workshopSection}</p>
            {workshopNavigation.map((item) => <NavButton key={item.route.view} {...item} active={route.view === item.route.view || (route.view === 'product' && Boolean(route.paintingProjectId) && item.route.view === 'workshop')} onClick={() => navigate(item.route)} />)}
            <p className="nav-section-label">{config.navigation.aboutSection}</p>
            {aboutNavigation.map((item) => <NavButton key={item.route.view} {...item} active={route.view === item.route.view} onClick={() => navigate(item.route)} />)}
          </nav>
          {dashboard.workshop.projectCount > 0 && <div className="mt-8 rounded-2xl border bg-background p-4"><p className="text-xs font-semibold">{config.workshop.progress}</p><div className="mt-3 h-2 overflow-hidden rounded-full bg-secondary"><div className="h-full rounded-full bg-primary" style={{ width: `${dashboard.workshop.progressPercentage}%` }} /></div><p className="mt-2 text-[11px] text-muted-foreground">{dashboard.workshop.completedItemCount} / {dashboard.workshop.itemCount} · {dashboard.workshop.progressPercentage}%</p></div>}
        </aside>

        <main className="min-w-0 flex-1 px-4 pb-24 pt-8 sm:px-6 lg:px-10 lg:pb-10">
          <div className="mx-auto max-w-6xl">
            <p className="eyebrow">{route.view === 'home' ? config.home.eyebrow : route.view.startsWith('about') ? config.navigation.aboutSection : route.paintingProjectId || ['workshopPaints', 'workshop', 'shopping', 'item'].includes(route.view) ? config.navigation.workshopSection : config.navigation.marketSection}</p>
            <h1 className="mt-2 text-3xl font-semibold tracking-tight sm:text-4xl">{title}</h1>
            <p className="mt-3 max-w-3xl text-sm leading-6 text-muted-foreground">{description}</p>
            {notice && <div className="mt-5 flex items-start gap-2 rounded-2xl border border-primary/20 bg-primary/5 p-4 text-sm"><CircleAlert className="mt-0.5 size-4 flex-none text-primary" /><span>{notice}</span></div>}
            {route.view.startsWith('about') && <div className="mt-6 flex flex-wrap gap-2 lg:hidden">{aboutNavigation.map((item) => <button type="button" key={item.route.view} onClick={() => navigate(item.route)} className={'rounded-xl border px-3 py-2 text-xs font-semibold ' + (route.view === item.route.view ? 'bg-primary text-primary-foreground' : 'bg-card text-foreground')}>{item.label}</button>)}</div>}

            {route.view === 'home' && <HomePage dashboard={dashboard} config={config} navigate={navigate} />}
            {isPaintView && <PaintBrowser paints={paints} resultCount={paintResultCount} visibleCount={paintCatalogTotal} brands={brands} offset={paintOffset} pageSize={PAINT_PAGE_SIZE} filters={filters} setFilters={changeFilters} filterOptions={filterOptions} paintModel={paintModel} filtersOpen={filtersOpen} setFiltersOpen={setFiltersOpen} activeFilterCount={activeFilterCount} manufacturerSheetOnly={manufacturerSheetOnly} setManufacturerSheetOnly={changeManufacturerSheetOnly} realResultOnly={realResultOnly} setRealResultOnly={changeRealResultOnly} clearFilters={clearFilters} config={config} onOpen={setSelectedPaint} onPage={setPaintOffset} />}
            {route.view === 'marketProducts' && <MarketProducts products={productSummaries} ownedProductIds={new Set(workshop.paintingProjects.map((project) => project.productId))} config={config} navigate={navigate} />}
            {route.view === 'workshop' && <WorkshopAdmin workshop={workshop} config={config} navigate={navigate} />}
            {route.view === 'shopping' && <ShoppingPage items={shoppingItems} onToggle={setShoppingStatus} config={config} />}
            {(route.view === 'aboutUser' || route.view === 'aboutAdmin') && <DocumentationPage documentation={documentation} config={config} />}
            {route.view === 'aboutPaintModel' && <PaintModelPage paintModel={paintModel} config={config} />}
            {route.view === 'aboutApi' && <ApiDocumentationPage config={config} />}
            {route.view === 'aboutVersion' && <VersionPage about={aboutData} config={config} />}
            {route.view === 'product' && activeProduct && <ProductPage product={activeProduct} activeItem={activeCatalogItem} paintingProject={activePaintingProject} workshopItems={workshopItems} preview={importPreview} importing={importing} config={config} navigate={navigate} onImport={importProduct} workshopMode={Boolean(route.paintingProjectId)} />}
            {route.view === 'product' && loadingProduct && <p className="mt-8 text-sm text-muted-foreground">{config.errors.loading}</p>}
            {route.view === 'product' && !loadingProduct && !activeProduct && <EmptyState title={config.errors.productNotFound} description={config.errors.requestFailed} />}
            {route.view === 'item' && workshopItemDetail && <WorkshopItemPage item={workshopItemDetail} paintingProject={workshop.paintingProjects.find((project) => project.projectId === workshopItemDetail.paintingProjectId)} config={config} navigate={navigate} saving={savingItem} onTransition={transitionItemStage} onComment={addItemComment} onPhoto={addItemPhoto} />}
          </div>
        </main>
      </div>

      <nav aria-label={config.navigation.mobileAriaLabel} className="fixed inset-x-0 bottom-0 z-30 grid grid-cols-7 border-t bg-card/95 px-1 py-1.5 backdrop-blur-xl lg:hidden">
        {mobileNavigation.map((item) => <button type="button" key={item.route.view} onClick={() => navigate(item.route)} className={'grid min-w-0 place-items-center gap-1 rounded-xl py-1.5 text-[9px] ' + (route.view === item.route.view || (route.view.startsWith('about') && item.route.view === 'aboutUser') ? 'bg-primary text-primary-foreground' : 'text-muted-foreground')}><span>{item.icon}</span><span className="w-full truncate px-0.5">{item.label}</span></button>)}
      </nav>

      {selectedPaint && <PaintDetail paint={selectedPaint} config={config} onClose={() => setSelectedPaint(null)} />}
    </div>
  );
}

function HomePage({ dashboard, config, navigate }: {
  dashboard: Dashboard; config: SiteConfig; navigate: (route: Route) => void;
}) {
  const services: Array<[Route, ReactNode, { title: string; description: string; action: string }]> = [
    [{ view: 'marketPaints' }, <Droplets key="paint" size={20} />, config.home.marketPaints],
    [{ view: 'marketProducts' }, <PackageOpen key="product" size={20} />, config.home.marketPaintableProducts],
    [{ view: 'workshopPaints' }, <Paintbrush key="stock" size={20} />, config.home.workshopPaints],
    [{ view: 'workshop' }, <FolderCog key="workshop" size={20} />, config.home.workshopAdmin],
    [{ view: 'shopping' }, <ShoppingBasket key="shopping" size={20} />, config.home.shopping],
  ];
  return <>
    <div className="mt-8 grid gap-3 sm:grid-cols-3"><Metric icon={<Droplets size={20} />} value={dashboard.paintStats.total} label={config.market.paintsMetric} /><Metric icon={<PackageOpen size={20} />} value={dashboard.paintableProductCount} label={config.navigation.marketPaintableProducts} /><Metric icon={<Grid2X2 size={20} />} value={dashboard.workshop.itemCount} label={config.workshop.items} /></div>
    <section className="mt-10"><h2 className="text-xl font-semibold">{config.home.servicesTitle}</h2><p className="mt-2 text-sm text-muted-foreground">{config.home.servicesDescription}</p><div className="mt-5 grid gap-4 md:grid-cols-2">{services.map(([route, icon, service]) => <button type="button" key={route.view} onClick={() => navigate(route)} className="group rounded-[24px] border bg-card p-5 text-left shadow-sm transition hover:-translate-y-0.5 hover:border-primary/30 hover:shadow-lg"><span className="grid size-10 place-items-center rounded-2xl bg-primary/8 text-primary">{icon}</span><h3 className="mt-4 font-semibold">{service.title}</h3><p className="mt-2 text-xs leading-5 text-muted-foreground">{service.description}</p><span className="mt-4 inline-flex items-center gap-1 text-xs font-semibold text-primary">{service.action}<ChevronRight size={14} /></span></button>)}</div></section>
  </>;
}

function PaintBrowser({ paints, resultCount, visibleCount, brands, offset, pageSize, filters, setFilters, filterOptions, paintModel, filtersOpen, setFiltersOpen, activeFilterCount, manufacturerSheetOnly, setManufacturerSheetOnly, realResultOnly, setRealResultOnly, clearFilters, config, onOpen, onPage }: {
  paints: Paint[]; resultCount: number; visibleCount: number; brands: number; offset: number; pageSize: number; filters: PaintFilters; setFilters: (filters: PaintFilters) => void; filterOptions: FilterOptions; paintModel: PaintModelSchema;
  filtersOpen: boolean; setFiltersOpen: (value: boolean) => void; activeFilterCount: number;
  manufacturerSheetOnly: boolean; setManufacturerSheetOnly: (value: boolean) => void; realResultOnly: boolean; setRealResultOnly: (value: boolean) => void;
  clearFilters: () => void; config: SiteConfig; onOpen: (paint: Paint) => void; onPage: (offset: number) => void;
}) {
  const setFilter = (key: string, value: string) => setFilters({ ...filters, [key]: value });
  const facets = [...paintModel['x-filters']].sort((left, right) => left.order - right.order);
  return <>
    <div className="mt-7 grid gap-3 sm:grid-cols-3"><Metric icon={<Droplets size={19} />} value={visibleCount} label={config.market.paintsMetric} /><Metric icon={<Sparkles size={19} />} value={brands} label={config.market.brandsMetric} /><Metric icon={<ListChecks size={19} />} value={resultCount} label={config.collection.resultsTitle} /></div>
    <section className="mt-6 rounded-[24px] border bg-card p-4 sm:p-5"><div className="flex items-center justify-between gap-3"><button type="button" onClick={() => setFiltersOpen(!filtersOpen)} className="inline-flex items-center gap-2 text-sm font-semibold"><ListFilter size={17} />{config.collection.filters}{activeFilterCount > 0 && <span className="rounded-full bg-primary px-2 py-0.5 text-[10px] text-primary-foreground">{activeFilterCount}</span>}</button>{activeFilterCount > 0 && <button type="button" onClick={clearFilters} className="text-xs font-semibold text-primary">{config.collection.resetFilters}</button>}</div>{filtersOpen && <div className="mt-5 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">{facets.map((facet) => <FacetSelect key={facet.id} label={configuredLabel(config, facet.labelKey)} allLabel={config.collection.allValues} value={filters[facet.queryParameter] ?? ''} options={filterOptions[facet.facetId] ?? []} onChange={(value) => setFilter(facet.queryParameter, value)} />)}<label className="need-chip"><input type="checkbox" checked={manufacturerSheetOnly} onChange={(event) => setManufacturerSheetOnly(event.target.checked)} />{config.collection.manufacturerSheetOnly}</label><label className="need-chip"><input type="checkbox" checked={realResultOnly} onChange={(event) => setRealResultOnly(event.target.checked)} />{config.collection.realResultOnly}</label></div>}</section>
    <div className="mt-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-3">{paints.map((paint) => <PaintCard key={paint.id} paint={paint} config={config} onOpen={() => onOpen(paint)} />)}{resultCount === 0 && <div className="col-span-full"><EmptyState title={config.collection.emptyTitle} description={config.collection.emptyHint} /></div>}</div>
    {resultCount > pageSize && <div className="mt-6 flex items-center justify-center gap-3"><button type="button" disabled={offset === 0} onClick={() => onPage(Math.max(0, offset - pageSize))} className="inline-flex size-11 items-center justify-center rounded-xl border bg-card text-primary disabled:opacity-35"><ChevronLeft size={17} /></button><span className="text-xs font-semibold text-muted-foreground">{offset + 1}–{Math.min(offset + paints.length, resultCount)} / {resultCount}</span><button type="button" disabled={offset + paints.length >= resultCount} onClick={() => onPage(offset + pageSize)} className="inline-flex size-11 items-center justify-center rounded-xl border bg-card text-primary disabled:opacity-35"><ChevronRight size={17} /></button></div>}
  </>;
}

function MarketProducts({ products, ownedProductIds, config, navigate }: { products: PaintableProductSummary[]; ownedProductIds: Set<string>; config: SiteConfig; navigate: (route: Route) => void }) {
  return <div className="mt-8 grid gap-5 lg:grid-cols-2">{products.map((product) => <article key={product.id} className="rounded-[26px] border bg-card p-6 shadow-sm"><div className="flex items-start justify-between gap-4"><div><p className="eyebrow">{formatMetadata(product.productType)}</p><h2 className="mt-2 text-xl font-semibold">{product.name}</h2><p className="mt-1 text-xs text-muted-foreground">{product.line}</p></div>{ownedProductIds.has(product.id) && <span className="rounded-full bg-[#e5f4ec] px-3 py-1 text-[10px] font-semibold text-[#207650]">{config.market.inWorkshop}</span>}</div><p className="mt-4 text-sm leading-6 text-muted-foreground">{product.scope}</p><div className="mt-5 grid grid-cols-2 gap-2 text-xs"><div className="rounded-xl bg-secondary p-3"><strong className="block text-base">{product.catalogItemCount}</strong>{config.market.catalogItems}</div><div className="rounded-xl bg-secondary p-3"><strong className="block text-base">{product.expectedPaintableCount}</strong>{config.market.paintableItems}</div></div><button type="button" onClick={() => navigate({ view: 'product', productId: product.id })} className="mt-5 inline-flex h-10 items-center gap-2 rounded-xl bg-primary px-4 text-sm font-semibold text-primary-foreground"><BookOpen size={15} />{config.market.viewProduct}</button></article>)}</div>;
}

function WorkshopAdmin({ workshop, config, navigate }: { workshop: WorkshopOverview; config: SiteConfig; navigate: (route: Route) => void }) {
  if (workshop.paintingProjects.length === 0) return <div className="mt-8"><EmptyState title={config.workshop.emptyTitle} description={config.workshop.emptyDescription} /></div>;
  return <>
    <div className="mt-7 grid gap-3 sm:grid-cols-4"><Metric icon={<PackageOpen size={19} />} value={workshop.projectCount} label={config.workshop.projects} /><Metric icon={<Grid2X2 size={19} />} value={workshop.itemCount} label={config.workshop.items} /><Metric icon={<Check size={19} />} value={workshop.completedItemCount} label={config.workshop.completed} /><Metric icon={<Sparkles size={19} />} value={`${workshop.progressPercentage}%`} label={config.workshop.progress} /></div>
    <section className="mt-8 space-y-4">{workshop.paintingProjects.map((summary) => {
      return <article key={summary.projectId} className="rounded-[26px] border bg-card p-5 shadow-sm sm:p-6"><div className="flex flex-col gap-5 lg:flex-row lg:items-center"><div className="min-w-0 flex-1"><p className="eyebrow">{summary.productId}</p><h2 className="mt-2 text-xl font-semibold">{summary.name}</h2><div className="mt-4 h-2.5 overflow-hidden rounded-full bg-secondary"><div className="h-full rounded-full bg-primary transition-all" style={{ width: `${summary.progressPercentage}%` }} /></div><p className="mt-2 text-xs text-muted-foreground">{summary.progressPercentage}% · {summary.completedCount} {config.workshop.completed} · {summary.inProgressCount} {config.workshop.inProgress} · {summary.pendingCount} {config.workshop.pending}</p></div><div className="grid grid-cols-2 gap-2 text-xs sm:grid-cols-4 lg:w-[420px]"><SummaryCell value={summary.itemCount} label={config.workshop.items} /><SummaryCell value={summary.requiredPaintCount} label={config.productDetail.requiredPaints} /><SummaryCell value={summary.missingPaintCount} label={config.workshop.missingPaints} /><SummaryCell value={summary.pendingPaintSlotCount} label={config.workshop.pendingPaintSlots} /></div></div><div className="mt-5 flex flex-wrap items-center gap-4">{summary.missingPaints.length > 0 ? <div className="flex flex-wrap gap-2">{summary.missingPaints.map((paint) => <span key={paint.id} className="rounded-full bg-[#ffe5df] px-3 py-1.5 text-[11px] font-semibold text-[#a6402c]">{paint.brand} · {paint.name}</span>)}</div> : <p className="inline-flex items-center gap-2 text-xs font-semibold text-[#207650]"><Check size={14} />{config.workshop.noMissingPaints}</p>}<button type="button" onClick={() => navigate({ view: 'product', productId: summary.productId, paintingProjectId: summary.projectId })} className="inline-flex items-center gap-1 text-xs font-semibold text-primary">{config.workshop.manageProduct}<ChevronRight size={14} /></button></div></article>;
    })}</section>
    <section className="mt-10"><h2 className="text-lg font-semibold">{config.workshop.recentActivity}</h2><div className="mt-4 divide-y overflow-hidden rounded-[22px] border bg-card">{workshop.recentActivity.map((event) => <div key={event.eventId} className="flex min-w-0 items-center gap-3 px-4 py-3"><span className="size-2 flex-none rounded-full bg-primary" /><span className="min-w-0 flex-1 truncate text-xs font-semibold">{config.workshop.eventLabels[event.eventType] ?? event.eventType}</span><time className="flex-none text-[10px] text-muted-foreground">{new Date(event.occurredAt).toLocaleDateString('fr-FR')}</time></div>)}</div></section>
  </>;
}

function SummaryCell({ value, label }: { value: number; label: string }) {
  return <div className="rounded-xl bg-secondary p-3"><strong className="block text-base">{value}</strong><span className="text-[10px] text-muted-foreground">{label}</span></div>;
}

function ProductPage({ product, activeItem, paintingProject, workshopItems, preview, importing, config, navigate, onImport, workshopMode }: {
  product: PaintableProduct; activeItem?: PaintableCatalogItem; paintingProject?: PaintingProjectSummary; workshopItems: WorkshopItem[];
  preview: PaintableProductImportPreview | null; importing: boolean; config: SiteConfig; navigate: (route: Route) => void;
  onImport: (id: string) => void; workshopMode: boolean;
}) {
  const physicalItems = workshopItems.filter((item) => item.paintingProjectId === paintingProject?.projectId);
  const missingPaintIds = new Set(preview?.missingPaints.map((paint) => paint.id) ?? []);
  const itemStates = activeItem ? physicalItems.filter((item) => item.catalogItemId === activeItem.id) : [];
  return <>
    <button type="button" onClick={() => navigate({ view: workshopMode ? 'workshop' : 'marketProducts' })} className="mt-6 inline-flex items-center gap-1 text-xs font-semibold text-primary"><ChevronLeft size={14} />{config.productDetail.back}</button>
    <div className="mt-6 grid gap-4 sm:grid-cols-3"><Metric icon={<PackageOpen size={19} />} value={product.expectedPaintableCount} label={config.market.paintableItems} /><Metric icon={<ListChecks size={19} />} value={product.items.length} label={config.market.catalogItems} /><Metric icon={<BookOpen size={19} />} value={product.items.filter((item) => Boolean(item.marketGuide?.id)).length} label={config.productDetail.paintingSheets} /></div>
    {product.edition.note && <div className="mt-6 rounded-2xl border border-primary/15 bg-primary/5 p-4 text-xs leading-5 text-muted-foreground">{product.edition.note} {product.edition.url && <a href={product.edition.url} target="_blank" rel="noreferrer" className="ml-1 font-semibold text-primary"><ExternalLink className="inline size-3" /> {config.market.source}</a>}</div>}

    {!workshopMode && <ImportPanel product={product} preview={preview} importing={importing} config={config} navigate={navigate} onImport={onImport} />}
    {workshopMode && paintingProject && <section className="mt-6 rounded-[24px] border bg-card p-5"><div className="flex items-center justify-between gap-3"><h2 className="font-semibold">{config.workshop.progress}</h2><strong className="text-primary">{paintingProject.progressPercentage}%</strong></div><div className="mt-3 h-2.5 overflow-hidden rounded-full bg-secondary"><div className="h-full rounded-full bg-primary" style={{ width: `${paintingProject.progressPercentage}%` }} /></div><div className="mt-4 grid grid-cols-3 gap-2"><SummaryCell value={paintingProject.completedCount} label={config.workshop.completed} /><SummaryCell value={paintingProject.inProgressCount} label={config.workshop.inProgress} /><SummaryCell value={paintingProject.pendingCount} label={config.workshop.pending} /></div></section>}

    <div className="mt-8 grid min-w-0 gap-6 lg:grid-cols-[290px_minmax(0,1fr)]">
      <aside className="min-w-0 rounded-[24px] border bg-card p-3"><h2 className="px-2 py-2 text-sm font-semibold">{config.productDetail.contents}</h2><div className="mt-1 max-h-[680px] space-y-1 overflow-y-auto">{product.items.map((item) => {
        const current = item.id === activeItem?.id;
        const counts = physicalItems.filter((entry) => entry.catalogItemId === item.id);
        return <button type="button" key={item.id} onClick={() => navigate({ view: 'product', productId: product.id, catalogItemId: item.id, paintingProjectId: paintingProject?.projectId })} className={'flex w-full min-w-0 items-center gap-3 rounded-xl px-3 py-2.5 text-left ' + (current ? 'bg-primary text-primary-foreground' : 'hover:bg-secondary')}><span className="grid size-8 flex-none place-items-center rounded-lg bg-current/10 text-xs font-bold">{item.quantity}</span><span className="min-w-0 flex-1"><strong className="block truncate text-xs">{item.name}</strong><span className={'block truncate text-[10px] ' + (current ? 'text-primary-foreground/70' : 'text-muted-foreground')}>{config.market.kindLabels[item.kind] ?? formatMetadata(item.kind)}{workshopMode ? ` · ${counts.filter((entry) => entry.completed).length}/${counts.length}` : ''}</span></span></button>;
      })}</div></aside>
      {activeItem && <article className="min-w-0 rounded-[26px] border bg-card p-5 shadow-sm sm:p-7"><div className="flex flex-wrap items-start justify-between gap-3"><div><p className="eyebrow">{config.market.kindLabels[activeItem.kind] ?? formatMetadata(activeItem.kind)} · × {activeItem.quantity}</p><h2 className="mt-2 text-2xl font-semibold">{activeItem.name}</h2></div>{activeItem.assemblyRequired && <span className="rounded-full bg-secondary px-3 py-1 text-[10px] font-semibold">{config.productDetail.assemblyRequired}</span>}</div><p className="mt-4 text-sm leading-6 text-muted-foreground">{activeItem.description}</p>
        <ReferenceImages item={activeItem} config={config} />
        {workshopMode && <PhysicalProgress items={itemStates} config={config} navigate={navigate} />}
        <section className="mt-7"><h3 className="flex items-center gap-2 text-sm font-semibold"><Droplets size={16} className="text-primary" />{config.productDetail.paintGuide}</h3><div className="mt-4 grid min-w-0 gap-3 sm:grid-cols-2">{activeItem.paints.map((paint) => {
          const status = paint.pendingImport ? config.productDetail.paintPending : paint.paintId && !missingPaintIds.has(paint.paintId) ? config.productDetail.paintAvailable : config.productDetail.paintMissing;
          const okay = status === config.productDetail.paintAvailable;
          return <div key={paint.slotId} className="flex min-w-0 items-center gap-3 rounded-2xl border bg-background/60 p-3"><span className={'size-10 flex-none rounded-xl border ' + (!validColor(paint.colorHex) ? 'unknown-color' : '')} style={swatchStyle(paint.colorHex)} /><span className="min-w-0 flex-1"><strong className="block truncate text-sm">{paint.name}</strong><span className="block truncate text-[11px] text-muted-foreground">{paint.brand} · {paint.role}</span></span><span className={'max-w-[42%] break-words rounded-full px-2 py-1 text-center text-[9px] font-semibold ' + (okay ? 'bg-[#e5f4ec] text-[#207650]' : 'bg-[#ffe5df] text-[#a6402c]')}>{status}</span></div>;
        })}</div></section>
        <div className="mt-7 grid gap-4 xl:grid-cols-2"><GuideSteps title={config.productDetail.preparation} steps={activeItem.preparation} /><GuideSteps title={config.productDetail.painting} steps={activeItem.painting} /></div>
      </article>}
    </div>
    <section className="mt-8"><h2 className="text-lg font-semibold">{config.productDetail.sources}</h2><div className="mt-3 flex flex-wrap gap-2">{product.sources.map((source) => <a key={source.url} href={source.url} target="_blank" rel="noreferrer" className="need-chip"><ExternalLink size={13} />{source.label}</a>)}</div></section>
  </>;
}

function ImportPanel({ product, preview, importing, config, navigate, onImport }: { product: PaintableProduct; preview: PaintableProductImportPreview | null; importing: boolean; config: SiteConfig; navigate: (route: Route) => void; onImport: (id: string) => void }) {
  return <section className="mt-6 rounded-[24px] border bg-card p-5 shadow-sm"><div className="flex flex-col gap-5 md:flex-row md:items-center"><div className="min-w-0 flex-1"><p className="eyebrow">{config.productDetail.importPreview}</p><p className="mt-2 text-sm leading-6 text-muted-foreground">{config.productDetail.importDescription}</p>{preview && <div className="mt-4 grid grid-cols-3 gap-2"><SummaryCell value={preview.requiredPaintCount} label={config.productDetail.requiredPaints} /><SummaryCell value={preview.missingPaintCount} label={config.productDetail.missingPaints} /><SummaryCell value={preview.pendingPaintSlotCount} label={config.productDetail.pendingSlots} /></div>}</div><div className="flex-none">{preview?.alreadyImported ? <><p className="mb-3 max-w-xs text-xs font-semibold text-[#207650]">{config.productDetail.alreadyImported}</p><button type="button" onClick={() => navigate({ view: 'workshop' })} className="inline-flex h-11 items-center gap-2 rounded-xl bg-primary px-5 text-sm font-semibold text-primary-foreground"><FolderCog size={16} />{config.productDetail.openWorkshop}</button></> : <button type="button" disabled={!preview || importing} onClick={() => onImport(product.id)} className="inline-flex h-11 items-center gap-2 rounded-xl bg-primary px-5 text-sm font-semibold text-primary-foreground disabled:opacity-50"><PackageOpen size={16} />{importing ? config.productDetail.importing : config.productDetail.importAction}</button>}</div></div>{preview && preview.missingPaints.length > 0 && <div className="mt-4 flex flex-wrap gap-2">{preview.missingPaints.map((paint) => <span key={paint.id} className="rounded-full bg-[#ffe5df] px-3 py-1.5 text-[11px] font-semibold text-[#a6402c]">{paint.brand} · {paint.name}</span>)}</div>}</section>;
}

function ReferenceImages({ item, config }: { item: PaintableCatalogItem; config: SiteConfig }) {
  if (item.referenceImages.length === 0) return <div className="mt-6 rounded-2xl border border-dashed p-5 text-center text-xs text-muted-foreground">{config.productDetail.noLicensedImage}<div className="mt-3 flex flex-wrap justify-center gap-2">{item.sources.map((source) => <a key={source.url} href={source.url} target="_blank" rel="noreferrer" className="font-semibold text-primary">{config.productDetail.externalReferences} <ExternalLink className="inline size-3" /></a>)}</div></div>;
  return <div className="mt-6 grid gap-3 sm:grid-cols-2">{item.referenceImages.map((image) => <figure key={image.url} className="overflow-hidden rounded-2xl border"><img src={image.url} alt={item.name} className="aspect-[4/3] w-full object-cover" /><figcaption className="p-3 text-[10px] text-muted-foreground">{image.credit}</figcaption></figure>)}</div>;
}

function PhysicalProgress({ items, config, navigate }: { items: WorkshopItem[]; config: SiteConfig; navigate: (route: Route) => void }) {
  const counts = new Map<string, number>();
  items.forEach((item) => counts.set(item.completed ? 'completed' : item.currentStage ?? 'pending', (counts.get(item.completed ? 'completed' : item.currentStage ?? 'pending') ?? 0) + 1));
  return <section className="mt-6 rounded-2xl bg-secondary/60 p-4"><h3 className="text-sm font-semibold">{config.workshop.progress}</h3><div className="mt-3 flex flex-wrap gap-2">{Array.from(counts.entries()).map(([stage, count]) => <span key={stage} className="rounded-full bg-card px-3 py-1.5 text-[11px] font-semibold">{workflowLabel(config, stage)} · {count}</span>)}</div><div className="mt-4 grid max-h-72 gap-2 overflow-y-auto sm:grid-cols-2">{items.map((item) => <button type="button" key={item.id} onClick={() => navigate({ view: 'item', itemId: item.id })} className="flex items-center justify-between gap-3 rounded-xl border bg-card px-3 py-2 text-left text-xs hover:border-primary/30"><span className="truncate font-semibold">{item.displayName}</span><span className="flex-none text-[10px] text-muted-foreground">{item.completed ? config.workshop.completed : workflowLabel(config, item.currentStage ?? 'pending')}</span></button>)}</div></section>;
}

function WorkshopItemPage({ item, paintingProject, config, navigate, saving, onTransition, onComment, onPhoto }: {
  item: WorkshopItemDetail; paintingProject?: PaintingProjectSummary; config: SiteConfig; navigate: (route: Route) => void; saving: boolean;
  onTransition: (itemId: string, stage: string, action: string) => Promise<void>;
  onComment: (itemId: string, comment: string) => Promise<void>;
  onPhoto: (itemId: string, file: File, caption: string, stage: string | null) => Promise<void>;
}) {
  const [comment, setComment] = useState('');
  const [photo, setPhoto] = useState<File | null>(null);
  const [caption, setCaption] = useState('');
  let prerequisitesComplete = true;
  const photos = item.activity.filter((event) => event.eventType === 'workshop_item.photo_added' && typeof event.payload.url === 'string');
  return <>
    <button type="button" onClick={() => navigate({ view: 'product', productId: paintingProject?.productId, catalogItemId: item.catalogItemId, paintingProjectId: item.paintingProjectId })} className="mt-6 inline-flex items-center gap-1 text-xs font-semibold text-primary"><ChevronLeft size={14} />{config.workshop.backToProduct}</button>
    <section className="mt-6 rounded-[26px] border bg-card p-5 shadow-sm sm:p-7"><div className="flex flex-wrap items-start justify-between gap-3"><div><p className="eyebrow">{config.workshop.itemDetail}</p><h2 className="mt-2 text-2xl font-semibold">{item.displayName}</h2><p className="mt-1 text-xs text-muted-foreground">{item.catalogItemId}</p></div>{item.recipeId && <span className="rounded-full bg-secondary px-3 py-1 text-[10px] font-semibold">{item.recipeId} · v{item.recipeVersion}</span>}</div>
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

function ShoppingPage({ items, onToggle, config }: { items: ShoppingItem[]; onToggle: (id: string, checked: boolean) => Promise<void>; config: SiteConfig }) {
  const priority: Record<string, string> = config.shopping.priorities;
  const required = items.filter((item) => item.kind === 'required');
  const planned = items.filter((item) => item.kind === 'planned');
  const rows = (entries: ShoppingItem[]) => <div className="mt-4 overflow-hidden rounded-[24px] border bg-card">{entries.map((item) => { const done = item.checked; const context = item.sourceProductNames.length > 0 ? `${config.shopping.requiredBy} ${item.sourceProductNames.join(', ')}` : item.reason; return <label key={item.id} className={'shopping-row w-full text-left ' + (done ? 'opacity-45' : '')}><input type="checkbox" className="size-5 accent-primary" checked={done} onChange={(event) => void onToggle(item.id, event.target.checked)} /><span className={'size-9 flex-none rounded-xl border ' + (!validColor(item.colorHex) ? 'unknown-color' : '')} style={swatchStyle(item.colorHex)} /><span className="min-w-0 flex-1"><strong className={'block truncate text-sm ' + (done ? 'line-through' : '')}>{item.name}</strong><span className="block truncate text-xs text-muted-foreground">{item.brand}{context ? ` · ${context}` : ''}</span></span>{item.planned && <span className="rounded-full bg-secondary px-2 py-1 text-[9px] font-semibold">{config.shopping.plannedTitle}</span>}<span className="rounded-full bg-secondary px-2.5 py-1 text-[10px] font-semibold">{priority[item.priority]}</span></label>; })}</div>;
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

function PaintModelPage({ paintModel, config }: { paintModel: PaintModelSchema; config: SiteConfig }) {
  const filters = [...paintModel['x-filters']].sort((left, right) => left.order - right.order);
  return <div className="mt-8 grid gap-5 lg:grid-cols-[1.4fr_1fr]">
    <section className="rounded-[24px] border bg-card p-5 shadow-sm sm:p-7">
      <div className="flex flex-wrap items-center justify-between gap-3"><div><p className="eyebrow">JSON Schema</p><h2 className="mt-2 text-lg font-semibold">{paintModel.title}</h2></div><span className="rounded-full bg-secondary px-3 py-1 text-xs font-semibold">{config.about.modelVersion} {paintModel['x-model-version']}</span></div>
      <h3 className="mt-7 text-sm font-semibold">{config.about.filterFields}</h3>
      <div className="mt-3 divide-y rounded-2xl border">{filters.map((filter) => <div key={filter.id} className="grid gap-1 px-4 py-3 sm:grid-cols-[1fr_1fr]"><strong className="text-xs">{configuredLabel(config, filter.labelKey)}</strong><code className="text-xs text-muted-foreground">{filter.queryParameter} · {filter.facetId}</code></div>)}</div>
    </section>
    <section className="rounded-[24px] border bg-card p-5 shadow-sm sm:p-7"><h2 className="text-lg font-semibold">{config.about.vocabularies}</h2><div className="mt-4 space-y-4">{Object.entries(paintModel['x-vocabularies']).map(([id, values]) => <div key={id}><code className="text-xs font-semibold">{id}</code><div className="mt-2 flex flex-wrap gap-1.5">{values.map((value) => <span key={value} className="rounded-full bg-secondary px-2.5 py-1 text-[10px]">{formatMetadata(value)}</span>)}</div></div>)}</div><a className="mt-6 inline-flex items-center gap-2 text-xs font-semibold text-primary" href="/api/v1/market/paint-model" target="_blank" rel="noreferrer">{config.about.openPaintSchema}<ExternalLink size={14} /></a></section>
  </div>;
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

function PaintDetail({ paint, config, onClose }: { paint: Paint; config: SiteConfig; onClose: () => void }) {
  const closeButton = useRef<HTMLButtonElement>(null);
  const titleId = `paint-detail-${paint.id}`;
  useEffect(() => {
    closeButton.current?.focus();
    const onKeyDown = (event: KeyboardEvent) => { if (event.key === 'Escape') onClose(); };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [onClose]);
  const hasColor = validColor(paint.colorHex);
  return (
    <div role="presentation" className="fixed inset-0 z-50 grid place-items-center bg-black/45 p-3 backdrop-blur-sm" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}>
      <dialog open aria-labelledby={titleId} className="relative m-0 max-h-[92vh] w-full max-w-3xl overflow-y-auto rounded-[28px] bg-card p-0 text-foreground shadow-2xl">
        <header className="sticky top-0 z-10 flex items-center justify-between border-b bg-card/95 px-5 py-4 backdrop-blur">
          <div><p className="eyebrow">{config.paintDetail.sheet}</p><h2 id={titleId} className="mt-1 text-xl font-semibold">{paint.name}</h2></div>
          <button ref={closeButton} type="button" onClick={onClose} aria-label={config.paintDetail.close} className="grid size-10 place-items-center rounded-xl border"><X size={18} /></button>
        </header>
        <div className="grid gap-6 p-5 md:grid-cols-[220px_1fr]">
          <div>
            <div className={'aspect-square overflow-hidden rounded-[22px] border ' + (!hasColor ? 'unknown-color' : '')} style={hasColor ? { background: `color-mix(in srgb, ${paint.colorHex} 16%, white)` } : undefined}>
              {paint.resultImage || paint.manufacturerImage
                ? <ResilientPaintImage key={`${paint.resultImage || paint.manufacturerImage}|${paint.resultImageSource || paint.manufacturerImageSource}`} primary={paint.resultImage || paint.manufacturerImage} fallback={paint.resultImageSource || paint.manufacturerImageSource} alt={`${config.paintDetail.productVisual} ${paint.name}`} />
                : hasColor ? <div className="h-full w-full" style={{ background: paint.colorHex }} />
                  : <div className="grid h-full place-items-center text-sm font-semibold text-muted-foreground">{config.paintDetail.toQualify}</div>}
            </div>
            <div className="mt-3 grid grid-cols-2 gap-2 text-xs">
              <SummaryText value={paint.reference} label={config.paintDetail.referenceLabel} />
              <SummaryText value={`${paint.volumeMl} ml`} label={config.paintDetail.volumeLabel} />
              <SummaryText value={paint.profile.roles.map(formatMetadata).join(', ')} label={config.collection.roleFilter} />
              <SummaryText value={formatMetadata(paint.profile.applicationSystem)} label={config.collection.applicationSystemFilter} />
              <SummaryText value={formatMetadata(paint.profile.coverage)} label={config.collection.coverageFilter} />
              <SummaryText value={formatMetadata(paint.profile.finish)} label={config.collection.finishFilter} />
            </div>
          </div>
          <div className="min-w-0">
            <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{paint.brand} · {paint.range}</p>
            <p className="mt-4 text-sm leading-6 text-muted-foreground">{paint.manufacturerDescription || config.paintDetail.noManufacturerDescription}</p>
            {paint.recommendedUses.length > 0 && <section className="mt-5"><h3 className="text-sm font-semibold">{config.paintDetail.recommendedUses}</h3><div className="mt-2 flex flex-wrap gap-2">{paint.recommendedUses.map((use) => <span key={use} className="rounded-full bg-secondary px-3 py-1 text-xs">{use}</span>)}</div></section>}
            {paint.usageInstructions.summary && <section className="mt-5 rounded-2xl bg-secondary/60 p-4">
              <div className="flex flex-wrap items-center justify-between gap-2"><h3 className="text-sm font-semibold">{config.paintDetail.usageInstructions}</h3>{paint.usageInstructions.reviewRequired && <span className="rounded-full bg-[#fff0cf] px-2 py-1 text-[9px] font-semibold text-[#8a5a00]">{config.paintDetail.instructionsReviewRequired}</span>}</div>
              <p className="mt-2 text-xs leading-5 text-muted-foreground">{paint.usageInstructions.summary}</p>
              <ol className="mt-3 space-y-2">{paint.usageInstructions.steps.map((step, index) => <li key={step} className="flex gap-2 text-xs"><span className="step-number">{index + 1}</span><span>{step}</span></li>)}</ol>
            </section>}
            {paint.manufacturerUrl && <a href={paint.manufacturerUrl} target="_blank" rel="noreferrer" className="mt-5 inline-flex items-center gap-2 text-xs font-semibold text-primary"><ExternalLink size={14} />{config.paintDetail.openManufacturerSheet}</a>}
          </div>
        </div>
      </dialog>
    </div>
  );
}

function SummaryText({ value, label }: { value: string; label: string }) {
  return <div className="rounded-xl bg-secondary p-3"><span className="block truncate font-semibold">{value || '—'}</span><span className="mt-1 block text-[9px] uppercase tracking-wide text-muted-foreground">{label}</span></div>;
}
