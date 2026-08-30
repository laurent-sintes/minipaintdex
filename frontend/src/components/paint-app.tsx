'use client';

import {
  BookOpen, Check, ChevronDown, ChevronLeft, ChevronRight, CircleAlert, Droplets, ExternalLink,
  FolderCog, Grid2X2, House, ListChecks, ListFilter, PackageOpen, Paintbrush, Search,
  ShoppingBasket, Sparkles, X,
} from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import type { Paint, ShoppingItem } from '@/models/paint-model';
import type {
  PaintableCatalogItem, PaintableProduct, PaintableProductImportPreview, WorkshopItem,
  WorkshopOverview, WorkshopProductSummary,
} from '@/models/paintable-product-model';
import type { SiteConfig } from '@/models/site-config-model';
import { appRoutePath, parseAppRoute } from '@/utils/app-routing';
import type { AppRoute as Route } from '@/utils/app-routing';
import { formatMetadata, metadataOptions, normalizeSearch, sameMetadata } from '@/utils/paint-search';

type PaintFilterKey = 'type' | 'color' | 'brand' | 'manufacturer' | 'range' | 'finish' | 'medium' | 'opacity' | 'lifecycle' | 'volume' | 'tag';
type PaintFilters = Record<PaintFilterKey, string>;

const emptyPaintFilters: PaintFilters = {
  type: '', color: '', brand: '', manufacturer: '', range: '', finish: '', medium: '', opacity: '', lifecycle: '', volume: '', tag: '',
};

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

function PaintCard({ paint, config, onOpen }: { paint: Paint; config: SiteConfig; onOpen: () => void }) {
  const image = paint.resultImage || paint.manufacturerImage;
  return (
    <button type="button" className="paint-card group w-full text-left" onClick={onOpen}>
      <div className="paint-swatch" style={{ background: `color-mix(in srgb, ${paint.colorHex} 14%, white)` }}>
        {image
          ? <img src={image} alt={`${config.paintDetail.productVisual} ${paint.brand} ${paint.name}`} className="h-full w-full object-contain" />
          : <span className="absolute inset-0" style={{ background: paint.colorHex }} />}
        <span className="absolute bottom-3 left-3 z-[1] rounded-full bg-black/35 px-2 py-1 text-[10px] font-semibold uppercase tracking-wider text-white backdrop-blur-sm">{paint.reference || paint.range}</span>
      </div>
      <div className="min-w-0 flex-1 py-0.5">
        <p className="text-[11px] font-semibold uppercase tracking-[0.08em] text-muted-foreground">{paint.brand}</p>
        <h3 className="mt-0.5 truncate text-[15px] font-semibold tracking-tight">{paint.name}</h3>
        <div className="mt-3 flex flex-wrap gap-1.5">
          <span className="rounded-full bg-secondary px-2.5 py-1 text-[11px] font-medium">{formatMetadata(paint.paintType)}</span>
          {paint.quantity > 0 && <span className="rounded-full bg-primary/8 px-2.5 py-1 text-[11px] font-semibold text-primary">× {paint.quantity}</span>}
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

export function PaintApp({ initialPaints, initialProducts, initialWorkshop, initialWorkshopItems, shoppingSeed, config }: {
  initialPaints: Paint[];
  initialProducts: PaintableProduct[];
  initialWorkshop: WorkshopOverview;
  initialWorkshopItems: WorkshopItem[];
  shoppingSeed: ShoppingItem[];
  config: SiteConfig;
}) {
  const [route, setRoute] = useState<Route>(() => parseAppRoute(window.location.pathname));
  const [paints, setPaints] = useState(initialPaints);
  const [products, setProducts] = useState(initialProducts);
  const [workshop, setWorkshop] = useState(initialWorkshop);
  const [workshopItems, setWorkshopItems] = useState(initialWorkshopItems);
  const [query, setQuery] = useState('');
  const [filters, setFilters] = useState<PaintFilters>(emptyPaintFilters);
  const [filtersOpen, setFiltersOpen] = useState(true);
  const [manufacturerSheetOnly, setManufacturerSheetOnly] = useState(false);
  const [realResultOnly, setRealResultOnly] = useState(false);
  const [selectedPaint, setSelectedPaint] = useState<Paint | null>(null);
  const [checkedBuys, setCheckedBuys] = useState<string[]>([]);
  const [importPreviewState, setImportPreviewState] = useState<{ productId: string; preview: PaintableProductImportPreview } | null>(null);
  const [importing, setImporting] = useState(false);
  const [notice, setNotice] = useState('');

  function navigate(next: Route) {
    window.history.pushState({}, '', appRoutePath(next));
    setRoute(next);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  useEffect(() => {
    const onPopState = () => setRoute(parseAppRoute(window.location.pathname));
    window.addEventListener('popstate', onPopState);
    return () => window.removeEventListener('popstate', onPopState);
  }, []);

  const activeProduct = products.find((product) => product.id === route.productId);
  const activeCatalogItem = activeProduct?.items.find((item) => item.id === route.catalogItemId) ?? activeProduct?.items[0];
  const activeWorkshopProduct = workshop.products.find((product) => product.productId === route.productId);
  const importPreview = importPreviewState && importPreviewState.productId === route.productId
    ? importPreviewState.preview
    : null;

  useEffect(() => {
    if (route.view !== 'product' || !route.productId) return;
    const productId = route.productId;
    const controller = new AbortController();
    fetch(`/api/v1/market/paintable-products/${productId}/workshop-import-preview`, {
      signal: controller.signal, headers: { accept: 'application/json' },
    })
      .then((response) => {
        if (!response.ok) throw new Error(String(response.status));
        return response.json() as Promise<{ preview: PaintableProductImportPreview }>;
      })
      .then((result) => setImportPreviewState({ productId, preview: result.preview }))
      .catch((reason) => {
        if (!(reason instanceof DOMException && reason.name === 'AbortError')) setNotice(config.errors.requestFailed);
      });
    return () => controller.abort();
  }, [config.errors.requestFailed, route.productId, route.view]);

  const isPaintView = route.view === 'marketPaints' || route.view === 'workshopPaints';
  const visiblePaints = useMemo(
    () => route.view === 'workshopPaints' ? paints.filter((paint) => paint.quantity > 0) : paints,
    [paints, route.view],
  );
  const filterOptions = useMemo(() => ({
    types: metadataOptions(visiblePaints.map((paint) => paint.paintType)),
    colors: metadataOptions(visiblePaints.map((paint) => paint.colorFamily)),
    brands: metadataOptions(visiblePaints.map((paint) => paint.brand)),
    manufacturers: metadataOptions(visiblePaints.map((paint) => paint.manufacturer)),
    ranges: metadataOptions(visiblePaints.map((paint) => paint.range)),
    finishes: metadataOptions(visiblePaints.map((paint) => paint.finish)),
    mediums: metadataOptions(visiblePaints.map((paint) => paint.medium)),
    opacities: metadataOptions(visiblePaints.map((paint) => paint.opacity)),
    lifecycles: metadataOptions(visiblePaints.map((paint) => paint.lifecycleStatus)),
    volumes: metadataOptions(visiblePaints.filter((paint) => paint.volumeMl > 0).map((paint) => `${paint.volumeMl} ml`)),
    tags: metadataOptions(visiblePaints.flatMap((paint) => paint.tags)),
  }), [visiblePaints]);
  const filteredPaints = useMemo(() => {
    const normalized = normalizeSearch(query);
    return visiblePaints.filter((paint) => {
      const haystack = normalizeSearch([
        paint.brand, paint.manufacturer, ...paint.brandAliases, paint.range, paint.paintType, paint.reference,
        paint.name, paint.colorFamily, paint.colorHex, paint.finish, paint.medium, paint.opacity,
        paint.lifecycleStatus, paint.status, paint.warnings, paint.notes, `${paint.volumeMl} ml`,
        ...paint.tags, ...paint.recommendedUses,
      ].join(' '));
      return (!normalized || haystack.includes(normalized))
        && (!filters.type || sameMetadata(paint.paintType, filters.type))
        && (!filters.color || sameMetadata(paint.colorFamily, filters.color))
        && (!filters.brand || sameMetadata(paint.brand, filters.brand))
        && (!filters.manufacturer || sameMetadata(paint.manufacturer, filters.manufacturer))
        && (!filters.range || sameMetadata(paint.range, filters.range))
        && (!filters.finish || sameMetadata(paint.finish, filters.finish))
        && (!filters.medium || sameMetadata(paint.medium, filters.medium))
        && (!filters.opacity || sameMetadata(paint.opacity, filters.opacity))
        && (!filters.lifecycle || sameMetadata(paint.lifecycleStatus, filters.lifecycle))
        && (!filters.volume || `${paint.volumeMl} ml` === filters.volume)
        && (!filters.tag || paint.tags.some((tag) => sameMetadata(tag, filters.tag)))
        && (!manufacturerSheetOnly || Boolean(paint.manufacturerUrl))
        && (!realResultOnly || Boolean(paint.resultImage || paint.resultReferenceUrl));
    });
  }, [filters, manufacturerSheetOnly, query, realResultOnly, visiblePaints]);

  const brands = new Set(visiblePaints.map((paint) => paint.brand)).size;
  const activeFilterCount = Object.values(filters).filter(Boolean).length + Number(manufacturerSheetOnly) + Number(realResultOnly);
  const title = route.view === 'home' ? config.home.title
    : route.view === 'marketPaints' ? config.market.paintsTitle
      : route.view === 'marketProducts' ? config.market.paintableProductsTitle
        : route.view === 'workshopPaints' ? config.collection.title
          : route.view === 'workshop' ? config.workshop.title
            : route.view === 'shopping' ? config.shopping.title
              : activeProduct?.name ?? config.errors.productNotFound;
  const description = route.view === 'home' ? config.home.description
    : route.view === 'marketPaints' ? config.market.paintsDescription
      : route.view === 'marketProducts' ? config.market.paintableProductsDescription
        : route.view === 'workshopPaints' ? config.collection.description
          : route.view === 'workshop' ? config.workshop.description
            : route.view === 'shopping' ? config.shopping.description
              : activeProduct?.scope ?? '';

  async function refreshBootstrap() {
    const response = await fetch('/api/v1/bootstrap', { headers: { accept: 'application/json' } });
    if (!response.ok) throw new Error(String(response.status));
    const data = await response.json() as {
      paints: Paint[]; marketPaintableProducts: PaintableProduct[]; workshop: WorkshopOverview; workshopItems: WorkshopItem[];
    };
    setPaints(data.paints);
    setProducts(data.marketPaintableProducts);
    setWorkshop(data.workshop);
    setWorkshopItems(data.workshopItems);
  }

  async function importProduct(productId: string) {
    setImporting(true);
    setNotice('');
    try {
      const response = await fetch('/api/v1/workshop/paintable-products', {
        method: 'POST',
        headers: { 'content-type': 'application/json', 'Idempotency-Key': `import-product:${productId}` },
        body: JSON.stringify({ productId }),
      });
      if (!response.ok) throw new Error(String(response.status));
      await refreshBootstrap();
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

  function clearFilters() {
    setQuery(''); setFilters(emptyPaintFilters); setManufacturerSheetOnly(false); setRealResultOnly(false);
  }

  const navigation = [
    { route: { view: 'home' } as Route, icon: <House size={18} />, label: config.navigation.home },
    { route: { view: 'marketPaints' } as Route, icon: <Droplets size={18} />, label: config.navigation.marketPaints, badge: String(paints.length) },
    { route: { view: 'marketProducts' } as Route, icon: <PackageOpen size={18} />, label: config.navigation.marketPaintableProducts, badge: String(products.length) },
    { route: { view: 'workshopPaints' } as Route, icon: <Paintbrush size={18} />, label: config.navigation.workshopPaints },
    { route: { view: 'workshop' } as Route, icon: <FolderCog size={18} />, label: config.navigation.workshopAdmin, badge: String(workshop.productCount) },
    { route: { view: 'shopping' } as Route, icon: <ShoppingBasket size={18} />, label: config.navigation.shopping },
  ];

  return (
    <div className="min-h-screen bg-background text-foreground">
      <header className="sticky top-0 z-30 border-b bg-card/92 backdrop-blur-xl">
        <div className="mx-auto flex h-16 max-w-[1600px] items-center gap-3 px-4 lg:px-6">
          <button type="button" onClick={() => navigate({ view: 'home' })} className="flex min-w-0 items-center gap-3 text-left">
            <span className="grid size-10 flex-none place-items-center rounded-2xl bg-primary text-primary-foreground"><Paintbrush size={20} /></span>
            <span className="hidden min-w-0 sm:block"><strong className="block truncate text-sm">{config.brand.name}</strong><span className="block truncate text-[11px] text-muted-foreground">{config.brand.subtitle}</span></span>
          </button>
          {isPaintView && <label className="relative ml-auto w-full max-w-xl"><Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" /><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder={config.header.searchShortPlaceholder} aria-label={config.header.searchAriaLabel} className="h-10 w-full rounded-xl border bg-background pl-9 pr-3 text-sm outline-none focus:border-primary focus:ring-4 focus:ring-primary/10" /></label>}
        </div>
      </header>

      <div className="mx-auto flex max-w-[1600px]">
        <aside className="sticky top-16 hidden h-[calc(100vh-4rem)] w-64 flex-none border-r bg-card/55 p-4 lg:block">
          <nav aria-label={config.navigation.ariaLabel}>
            <NavButton icon={<House size={18} />} label={config.navigation.home} active={route.view === 'home'} onClick={() => navigate({ view: 'home' })} />
            <p className="mb-2 mt-6 px-3 text-[10px] font-bold uppercase tracking-[0.16em] text-muted-foreground">{config.navigation.marketSection}</p>
            {navigation.slice(1, 3).map((item) => <NavButton key={item.route.view} {...item} active={route.view === item.route.view || (route.view === 'product' && !route.workshopProduct && item.route.view === 'marketProducts')} onClick={() => navigate(item.route)} />)}
            <p className="mb-2 mt-6 px-3 text-[10px] font-bold uppercase tracking-[0.16em] text-muted-foreground">{config.navigation.workshopSection}</p>
            {navigation.slice(3).map((item) => <NavButton key={item.route.view} {...item} active={route.view === item.route.view || (route.view === 'product' && Boolean(route.workshopProduct) && item.route.view === 'workshop')} onClick={() => navigate(item.route)} />)}
          </nav>
          {workshop.productCount > 0 && <div className="mt-8 rounded-2xl border bg-background p-4"><p className="text-xs font-semibold">{config.workshop.progress}</p><div className="mt-3 h-2 overflow-hidden rounded-full bg-secondary"><div className="h-full rounded-full bg-primary" style={{ width: `${workshop.progressPercentage}%` }} /></div><p className="mt-2 text-[11px] text-muted-foreground">{workshop.completedItemCount} / {workshop.itemCount} · {workshop.progressPercentage}%</p></div>}
        </aside>

        <main className="min-w-0 flex-1 px-4 pb-24 pt-8 sm:px-6 lg:px-10 lg:pb-10">
          <div className="mx-auto max-w-6xl">
            <p className="eyebrow">{route.view === 'home' ? config.home.eyebrow : route.workshopProduct || ['workshopPaints', 'workshop', 'shopping'].includes(route.view) ? config.navigation.workshopSection : config.navigation.marketSection}</p>
            <h1 className="mt-2 text-3xl font-semibold tracking-tight sm:text-4xl">{title}</h1>
            <p className="mt-3 max-w-3xl text-sm leading-6 text-muted-foreground">{description}</p>
            {notice && <div className="mt-5 flex items-start gap-2 rounded-2xl border border-primary/20 bg-primary/5 p-4 text-sm"><CircleAlert className="mt-0.5 size-4 flex-none text-primary" /><span>{notice}</span></div>}

            {route.view === 'home' && <HomePage paints={paints} products={products} workshop={workshop} config={config} navigate={navigate} />}
            {isPaintView && <PaintBrowser paints={filteredPaints} visibleCount={visiblePaints.length} brands={brands} filters={filters} setFilters={setFilters} filterOptions={filterOptions} filtersOpen={filtersOpen} setFiltersOpen={setFiltersOpen} activeFilterCount={activeFilterCount} manufacturerSheetOnly={manufacturerSheetOnly} setManufacturerSheetOnly={setManufacturerSheetOnly} realResultOnly={realResultOnly} setRealResultOnly={setRealResultOnly} clearFilters={clearFilters} config={config} onOpen={setSelectedPaint} />}
            {route.view === 'marketProducts' && <MarketProducts products={products} config={config} navigate={navigate} />}
            {route.view === 'workshop' && <WorkshopAdmin workshop={workshop} products={products} workshopItems={workshopItems} config={config} navigate={navigate} />}
            {route.view === 'shopping' && <ShoppingPage items={shoppingSeed} checked={checkedBuys} setChecked={setCheckedBuys} config={config} />}
            {route.view === 'product' && activeProduct && <ProductPage product={activeProduct} activeItem={activeCatalogItem} workshopSummary={activeWorkshopProduct} workshopItems={workshopItems} preview={importPreview} importing={importing} config={config} navigate={navigate} onImport={importProduct} workshopMode={Boolean(route.workshopProduct)} paints={paints} />}
            {route.view === 'product' && !activeProduct && <EmptyState title={config.errors.productNotFound} description={config.errors.requestFailed} />}
          </div>
        </main>
      </div>

      <nav aria-label={config.navigation.mobileAriaLabel} className="fixed inset-x-0 bottom-0 z-30 grid grid-cols-6 border-t bg-card/95 px-1 py-1.5 backdrop-blur-xl lg:hidden">
        {navigation.map((item) => <button type="button" key={item.route.view} onClick={() => navigate(item.route)} className={'grid min-w-0 place-items-center gap-1 rounded-xl py-1.5 text-[9px] ' + (route.view === item.route.view ? 'bg-primary text-primary-foreground' : 'text-muted-foreground')}><span>{item.icon}</span><span className="w-full truncate px-0.5">{item.label}</span></button>)}
      </nav>

      {selectedPaint && <PaintDetail paint={selectedPaint} config={config} onClose={() => setSelectedPaint(null)} />}
    </div>
  );
}

function HomePage({ paints, products, workshop, config, navigate }: {
  paints: Paint[]; products: PaintableProduct[]; workshop: WorkshopOverview; config: SiteConfig; navigate: (route: Route) => void;
}) {
  const services: Array<[Route, ReactNode, { title: string; description: string; action: string }]> = [
    [{ view: 'marketPaints' }, <Droplets key="paint" size={20} />, config.home.marketPaints],
    [{ view: 'marketProducts' }, <PackageOpen key="product" size={20} />, config.home.marketPaintableProducts],
    [{ view: 'workshopPaints' }, <Paintbrush key="stock" size={20} />, config.home.workshopPaints],
    [{ view: 'workshop' }, <FolderCog key="workshop" size={20} />, config.home.workshopAdmin],
    [{ view: 'shopping' }, <ShoppingBasket key="shopping" size={20} />, config.home.shopping],
  ];
  return <>
    <div className="mt-8 grid gap-3 sm:grid-cols-3"><Metric icon={<Droplets size={20} />} value={paints.length} label={config.market.paintsMetric} /><Metric icon={<PackageOpen size={20} />} value={products.length} label={config.navigation.marketPaintableProducts} /><Metric icon={<Grid2X2 size={20} />} value={workshop.itemCount} label={config.workshop.items} /></div>
    <section className="mt-10"><h2 className="text-xl font-semibold">{config.home.servicesTitle}</h2><p className="mt-2 text-sm text-muted-foreground">{config.home.servicesDescription}</p><div className="mt-5 grid gap-4 md:grid-cols-2">{services.map(([route, icon, service]) => <button type="button" key={route.view} onClick={() => navigate(route)} className="group rounded-[24px] border bg-card p-5 text-left shadow-sm transition hover:-translate-y-0.5 hover:border-primary/30 hover:shadow-lg"><span className="grid size-10 place-items-center rounded-2xl bg-primary/8 text-primary">{icon}</span><h3 className="mt-4 font-semibold">{service.title}</h3><p className="mt-2 text-xs leading-5 text-muted-foreground">{service.description}</p><span className="mt-4 inline-flex items-center gap-1 text-xs font-semibold text-primary">{service.action}<ChevronRight size={14} /></span></button>)}</div></section>
  </>;
}

type FilterOptions = Record<'types' | 'colors' | 'brands' | 'manufacturers' | 'ranges' | 'finishes' | 'mediums' | 'opacities' | 'lifecycles' | 'volumes' | 'tags', { value: string; count: number }[]>;

function PaintBrowser({ paints, visibleCount, brands, filters, setFilters, filterOptions, filtersOpen, setFiltersOpen, activeFilterCount, manufacturerSheetOnly, setManufacturerSheetOnly, realResultOnly, setRealResultOnly, clearFilters, config, onOpen }: {
  paints: Paint[]; visibleCount: number; brands: number; filters: PaintFilters; setFilters: (filters: PaintFilters) => void; filterOptions: FilterOptions;
  filtersOpen: boolean; setFiltersOpen: (value: boolean) => void; activeFilterCount: number;
  manufacturerSheetOnly: boolean; setManufacturerSheetOnly: (value: boolean) => void; realResultOnly: boolean; setRealResultOnly: (value: boolean) => void;
  clearFilters: () => void; config: SiteConfig; onOpen: (paint: Paint) => void;
}) {
  const setFilter = (key: PaintFilterKey, value: string) => setFilters({ ...filters, [key]: value });
  const facets: Array<[PaintFilterKey, string, string, keyof FilterOptions]> = [
    ['type', config.collection.typeFilter, config.collection.allTypes, 'types'],
    ['color', config.collection.colorFilter, config.collection.allColors, 'colors'],
    ['brand', config.collection.brandFilter, config.collection.allBrands, 'brands'],
    ['manufacturer', config.collection.manufacturerFilter, config.collection.allManufacturers, 'manufacturers'],
    ['range', config.collection.rangeFilter, config.collection.allRanges, 'ranges'],
    ['finish', config.collection.finishFilter, config.collection.allFinishes, 'finishes'],
    ['medium', config.collection.mediumFilter, config.collection.allMediums, 'mediums'],
    ['opacity', config.collection.opacityFilter, config.collection.allOpacities, 'opacities'],
    ['lifecycle', config.collection.lifecycleFilter, config.collection.allLifecycles, 'lifecycles'],
    ['volume', config.collection.volumeFilter, config.collection.allVolumes, 'volumes'],
    ['tag', config.collection.tagFilter, config.collection.allTags, 'tags'],
  ];
  return <>
    <div className="mt-7 grid gap-3 sm:grid-cols-3"><Metric icon={<Droplets size={19} />} value={visibleCount} label={config.collection.resultsTitle} /><Metric icon={<Sparkles size={19} />} value={brands} label={config.market.brandsMetric} /><Metric icon={<ListChecks size={19} />} value={paints.length} label={config.collection.resultsTitle} /></div>
    <section className="mt-6 rounded-[24px] border bg-card p-4 sm:p-5"><div className="flex items-center justify-between gap-3"><button type="button" onClick={() => setFiltersOpen(!filtersOpen)} className="inline-flex items-center gap-2 text-sm font-semibold"><ListFilter size={17} />{config.collection.filters}{activeFilterCount > 0 && <span className="rounded-full bg-primary px-2 py-0.5 text-[10px] text-primary-foreground">{activeFilterCount}</span>}</button>{activeFilterCount > 0 && <button type="button" onClick={clearFilters} className="text-xs font-semibold text-primary">{config.collection.resetFilters}</button>}</div>{filtersOpen && <div className="mt-5 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">{facets.map(([key, label, allLabel, options]) => <FacetSelect key={key} label={label} allLabel={allLabel} value={filters[key]} options={filterOptions[options]} onChange={(value) => setFilter(key, value)} />)}<label className="need-chip"><input type="checkbox" checked={manufacturerSheetOnly} onChange={(event) => setManufacturerSheetOnly(event.target.checked)} />{config.collection.manufacturerSheetOnly}</label><label className="need-chip"><input type="checkbox" checked={realResultOnly} onChange={(event) => setRealResultOnly(event.target.checked)} />{config.collection.realResultOnly}</label></div>}</section>
    <div className="mt-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-3">{paints.map((paint) => <PaintCard key={paint.id} paint={paint} config={config} onOpen={() => onOpen(paint)} />)}{paints.length === 0 && <div className="col-span-full"><EmptyState title={config.collection.emptyTitle} description={config.collection.emptyHint} /></div>}</div>
  </>;
}

function MarketProducts({ products, config, navigate }: { products: PaintableProduct[]; config: SiteConfig; navigate: (route: Route) => void }) {
  return <div className="mt-8 grid gap-5 lg:grid-cols-2">{products.map((product) => <article key={product.id} className="rounded-[26px] border bg-card p-6 shadow-sm"><div className="flex items-start justify-between gap-4"><div><p className="eyebrow">{formatMetadata(product.productType)}</p><h2 className="mt-2 text-xl font-semibold">{product.name}</h2><p className="mt-1 text-xs text-muted-foreground">{product.line}</p></div>{product.inWorkshop && <span className="rounded-full bg-[#e5f4ec] px-3 py-1 text-[10px] font-semibold text-[#207650]">{config.market.inWorkshop}</span>}</div><p className="mt-4 text-sm leading-6 text-muted-foreground">{product.scope}</p><div className="mt-5 grid grid-cols-2 gap-2 text-xs"><div className="rounded-xl bg-secondary p-3"><strong className="block text-base">{product.items.length}</strong>{config.market.catalogItems}</div><div className="rounded-xl bg-secondary p-3"><strong className="block text-base">{product.expectedPaintableCount}</strong>{config.market.paintableItems}</div></div><button type="button" onClick={() => navigate({ view: 'product', productId: product.id })} className="mt-5 inline-flex h-10 items-center gap-2 rounded-xl bg-primary px-4 text-sm font-semibold text-primary-foreground"><BookOpen size={15} />{config.market.viewProduct}</button></article>)}</div>;
}

function WorkshopAdmin({ workshop, products, workshopItems, config, navigate }: { workshop: WorkshopOverview; products: PaintableProduct[]; workshopItems: WorkshopItem[]; config: SiteConfig; navigate: (route: Route) => void }) {
  if (workshop.products.length === 0) return <div className="mt-8"><EmptyState title={config.workshop.emptyTitle} description={config.workshop.emptyDescription} /></div>;
  return <>
    <div className="mt-7 grid gap-3 sm:grid-cols-4"><Metric icon={<PackageOpen size={19} />} value={workshop.productCount} label={config.workshop.products} /><Metric icon={<Grid2X2 size={19} />} value={workshop.itemCount} label={config.workshop.items} /><Metric icon={<Check size={19} />} value={workshop.completedItemCount} label={config.workshop.completed} /><Metric icon={<Sparkles size={19} />} value={`${workshop.progressPercentage}%`} label={config.workshop.progress} /></div>
    <section className="mt-8 space-y-4">{workshop.products.map((summary) => {
      const product = products.find((entry) => entry.id === summary.productId);
      const physical = workshopItems.filter((item) => item.workshopProductId === summary.productId);
      return <article key={summary.productId} className="rounded-[26px] border bg-card p-5 shadow-sm sm:p-6"><div className="flex flex-col gap-5 lg:flex-row lg:items-center"><div className="min-w-0 flex-1"><p className="eyebrow">{product?.line ?? summary.productId}</p><h2 className="mt-2 text-xl font-semibold">{summary.name}</h2><div className="mt-4 h-2.5 overflow-hidden rounded-full bg-secondary"><div className="h-full rounded-full bg-primary transition-all" style={{ width: `${summary.progressPercentage}%` }} /></div><p className="mt-2 text-xs text-muted-foreground">{summary.progressPercentage}% · {summary.completedCount} {config.workshop.completed} · {summary.inProgressCount} {config.workshop.inProgress} · {summary.pendingCount} {config.workshop.pending}</p></div><div className="grid grid-cols-2 gap-2 text-xs sm:grid-cols-4 lg:w-[420px]"><SummaryCell value={summary.itemCount} label={config.workshop.items} /><SummaryCell value={physical.length} label={config.market.paintableItems} /><SummaryCell value={summary.missingPaintCount} label={config.workshop.missingPaints} /><SummaryCell value={summary.pendingPaintSlotCount} label={config.workshop.pendingPaintSlots} /></div></div><div className="mt-5 flex flex-wrap items-center gap-4">{summary.missingPaints.length > 0 ? <div className="flex flex-wrap gap-2">{summary.missingPaints.map((paint) => <span key={paint.id} className="rounded-full bg-[#ffe5df] px-3 py-1.5 text-[11px] font-semibold text-[#a6402c]">{paint.brand} · {paint.name}</span>)}</div> : <p className="inline-flex items-center gap-2 text-xs font-semibold text-[#207650]"><Check size={14} />{config.workshop.noMissingPaints}</p>}<button type="button" onClick={() => navigate({ view: 'product', productId: summary.productId, workshopProduct: true })} className="inline-flex items-center gap-1 text-xs font-semibold text-primary">{config.workshop.manageProduct}<ChevronRight size={14} /></button></div></article>;
    })}</section>
    <section className="mt-10"><h2 className="text-lg font-semibold">{config.workshop.recentActivity}</h2><div className="mt-4 divide-y overflow-hidden rounded-[22px] border bg-card">{workshop.recentActivity.map((event) => <div key={event.eventId} className="flex min-w-0 items-center gap-3 px-4 py-3"><span className="size-2 flex-none rounded-full bg-primary" /><span className="min-w-0 flex-1 truncate text-xs font-semibold">{config.workshop.eventLabels[event.eventType] ?? event.eventType}</span><time className="flex-none text-[10px] text-muted-foreground">{new Date(event.occurredAt).toLocaleDateString('fr-FR')}</time></div>)}</div></section>
  </>;
}

function SummaryCell({ value, label }: { value: number; label: string }) {
  return <div className="rounded-xl bg-secondary p-3"><strong className="block text-base">{value}</strong><span className="text-[10px] text-muted-foreground">{label}</span></div>;
}

function ProductPage({ product, activeItem, workshopSummary, workshopItems, preview, importing, config, navigate, onImport, workshopMode, paints }: {
  product: PaintableProduct; activeItem?: PaintableCatalogItem; workshopSummary?: WorkshopProductSummary; workshopItems: WorkshopItem[];
  preview: PaintableProductImportPreview | null; importing: boolean; config: SiteConfig; navigate: (route: Route) => void;
  onImport: (id: string) => void; workshopMode: boolean; paints: Paint[];
}) {
  const physicalItems = workshopItems.filter((item) => item.workshopProductId === product.id);
  const ownedPaintIds = new Set(paints.filter((paint) => paint.quantity > 0).map((paint) => paint.id));
  const itemStates = activeItem ? physicalItems.filter((item) => item.catalogItemId === activeItem.id) : [];
  return <>
    <button type="button" onClick={() => navigate({ view: workshopMode ? 'workshop' : 'marketProducts' })} className="mt-6 inline-flex items-center gap-1 text-xs font-semibold text-primary"><ChevronLeft size={14} />{config.productDetail.back}</button>
    <div className="mt-6 grid gap-4 sm:grid-cols-3"><Metric icon={<PackageOpen size={19} />} value={product.expectedPaintableCount} label={config.market.paintableItems} /><Metric icon={<ListChecks size={19} />} value={product.items.length} label={config.market.catalogItems} /><Metric icon={<BookOpen size={19} />} value={product.items.filter((item) => 'id' in item.marketGuide).length} label={config.productDetail.paintingSheets} /></div>
    {product.edition.note && <div className="mt-6 rounded-2xl border border-primary/15 bg-primary/5 p-4 text-xs leading-5 text-muted-foreground">{product.edition.note} {product.edition.url && <a href={product.edition.url} target="_blank" rel="noreferrer" className="ml-1 font-semibold text-primary"><ExternalLink className="inline size-3" /> {config.market.source}</a>}</div>}

    {!workshopMode && <ImportPanel product={product} preview={preview} importing={importing} config={config} navigate={navigate} onImport={onImport} />}
    {workshopMode && workshopSummary && <section className="mt-6 rounded-[24px] border bg-card p-5"><div className="flex items-center justify-between gap-3"><h2 className="font-semibold">{config.workshop.progress}</h2><strong className="text-primary">{workshopSummary.progressPercentage}%</strong></div><div className="mt-3 h-2.5 overflow-hidden rounded-full bg-secondary"><div className="h-full rounded-full bg-primary" style={{ width: `${workshopSummary.progressPercentage}%` }} /></div><div className="mt-4 grid grid-cols-3 gap-2"><SummaryCell value={workshopSummary.completedCount} label={config.workshop.completed} /><SummaryCell value={workshopSummary.inProgressCount} label={config.workshop.inProgress} /><SummaryCell value={workshopSummary.pendingCount} label={config.workshop.pending} /></div></section>}

    <div className="mt-8 grid min-w-0 gap-6 lg:grid-cols-[290px_minmax(0,1fr)]">
      <aside className="min-w-0 rounded-[24px] border bg-card p-3"><h2 className="px-2 py-2 text-sm font-semibold">{config.productDetail.contents}</h2><div className="mt-1 max-h-[680px] space-y-1 overflow-y-auto">{product.items.map((item) => {
        const current = item.id === activeItem?.id;
        const counts = physicalItems.filter((entry) => entry.catalogItemId === item.id);
        return <button type="button" key={item.id} onClick={() => navigate({ view: 'product', productId: product.id, catalogItemId: item.id, workshopProduct: workshopMode })} className={'flex w-full min-w-0 items-center gap-3 rounded-xl px-3 py-2.5 text-left ' + (current ? 'bg-primary text-primary-foreground' : 'hover:bg-secondary')}><span className="grid size-8 flex-none place-items-center rounded-lg bg-current/10 text-xs font-bold">{item.quantity}</span><span className="min-w-0 flex-1"><strong className="block truncate text-xs">{item.name}</strong><span className={'block truncate text-[10px] ' + (current ? 'text-primary-foreground/70' : 'text-muted-foreground')}>{config.market.kindLabels[item.kind] ?? formatMetadata(item.kind)}{workshopMode ? ` · ${counts.filter((entry) => entry.completed).length}/${counts.length}` : ''}</span></span></button>;
      })}</div></aside>
      {activeItem && <article className="min-w-0 rounded-[26px] border bg-card p-5 shadow-sm sm:p-7"><div className="flex flex-wrap items-start justify-between gap-3"><div><p className="eyebrow">{config.market.kindLabels[activeItem.kind] ?? formatMetadata(activeItem.kind)} · × {activeItem.quantity}</p><h2 className="mt-2 text-2xl font-semibold">{activeItem.name}</h2></div>{activeItem.assemblyRequired && <span className="rounded-full bg-secondary px-3 py-1 text-[10px] font-semibold">{config.productDetail.assemblyRequired}</span>}</div><p className="mt-4 text-sm leading-6 text-muted-foreground">{activeItem.description}</p>
        <ReferenceImages item={activeItem} config={config} />
        {workshopMode && <PhysicalProgress items={itemStates} config={config} />}
        <section className="mt-7"><h3 className="flex items-center gap-2 text-sm font-semibold"><Droplets size={16} className="text-primary" />{config.productDetail.paintGuide}</h3><div className="mt-4 grid min-w-0 gap-3 sm:grid-cols-2">{activeItem.paints.map((paint) => {
          const status = paint.pendingImport ? config.productDetail.paintPending : paint.paintId && ownedPaintIds.has(paint.paintId) ? config.productDetail.paintAvailable : config.productDetail.paintMissing;
          const okay = status === config.productDetail.paintAvailable;
          return <div key={paint.slotId} className="flex min-w-0 items-center gap-3 rounded-2xl border bg-background/60 p-3"><span className="size-10 flex-none rounded-xl border" style={{ background: paint.colorHex }} /><span className="min-w-0 flex-1"><strong className="block truncate text-sm">{paint.name}</strong><span className="block truncate text-[11px] text-muted-foreground">{paint.brand} · {paint.role}</span></span><span className={'max-w-[42%] break-words rounded-full px-2 py-1 text-center text-[9px] font-semibold ' + (okay ? 'bg-[#e5f4ec] text-[#207650]' : 'bg-[#ffe5df] text-[#a6402c]')}>{status}</span></div>;
        })}</div></section>
        <div className="mt-7 grid gap-4 xl:grid-cols-2"><GuideSteps title={config.productDetail.preparation} steps={activeItem.preparation} /><GuideSteps title={config.productDetail.painting} steps={activeItem.painting} /></div>
      </article>}
    </div>
    <section className="mt-8"><h2 className="text-lg font-semibold">{config.productDetail.sources}</h2><div className="mt-3 flex flex-wrap gap-2">{product.sources.map((source) => <a key={source.url} href={source.url} target="_blank" rel="noreferrer" className="need-chip"><ExternalLink size={13} />{source.label}</a>)}</div></section>
  </>;
}

function ImportPanel({ product, preview, importing, config, navigate, onImport }: { product: PaintableProduct; preview: PaintableProductImportPreview | null; importing: boolean; config: SiteConfig; navigate: (route: Route) => void; onImport: (id: string) => void }) {
  return <section className="mt-6 rounded-[24px] border bg-card p-5 shadow-sm"><div className="flex flex-col gap-5 md:flex-row md:items-center"><div className="min-w-0 flex-1"><p className="eyebrow">{config.productDetail.importPreview}</p><p className="mt-2 text-sm leading-6 text-muted-foreground">{config.productDetail.importDescription}</p>{preview && <div className="mt-4 grid grid-cols-3 gap-2"><SummaryCell value={preview.requiredPaintCount} label={config.productDetail.requiredPaints} /><SummaryCell value={preview.missingPaintCount} label={config.productDetail.missingPaints} /><SummaryCell value={preview.pendingPaintSlotCount} label={config.productDetail.pendingSlots} /></div>}</div><div className="flex-none">{preview?.alreadyImported || product.inWorkshop ? <><p className="mb-3 max-w-xs text-xs font-semibold text-[#207650]">{config.productDetail.alreadyImported}</p><button type="button" onClick={() => navigate({ view: 'product', productId: product.id, workshopProduct: true })} className="inline-flex h-11 items-center gap-2 rounded-xl bg-primary px-5 text-sm font-semibold text-primary-foreground"><FolderCog size={16} />{config.productDetail.openWorkshop}</button></> : <button type="button" disabled={!preview || importing} onClick={() => onImport(product.id)} className="inline-flex h-11 items-center gap-2 rounded-xl bg-primary px-5 text-sm font-semibold text-primary-foreground disabled:opacity-50"><PackageOpen size={16} />{importing ? config.productDetail.importing : config.productDetail.importAction}</button>}</div></div>{preview && preview.missingPaints.length > 0 && <div className="mt-4 flex flex-wrap gap-2">{preview.missingPaints.map((paint) => <span key={paint.id} className="rounded-full bg-[#ffe5df] px-3 py-1.5 text-[11px] font-semibold text-[#a6402c]">{paint.brand} · {paint.name}</span>)}</div>}</section>;
}

function ReferenceImages({ item, config }: { item: PaintableCatalogItem; config: SiteConfig }) {
  if (item.referenceImages.length === 0) return <div className="mt-6 rounded-2xl border border-dashed p-5 text-center text-xs text-muted-foreground">{config.productDetail.noLicensedImage}<div className="mt-3 flex flex-wrap justify-center gap-2">{item.sources.map((source) => <a key={source.url} href={source.url} target="_blank" rel="noreferrer" className="font-semibold text-primary">{config.productDetail.externalReferences} <ExternalLink className="inline size-3" /></a>)}</div></div>;
  return <div className="mt-6 grid gap-3 sm:grid-cols-2">{item.referenceImages.map((image) => <figure key={image.url} className="overflow-hidden rounded-2xl border"><img src={image.url} alt={item.name} className="aspect-[4/3] w-full object-cover" /><figcaption className="p-3 text-[10px] text-muted-foreground">{image.credit}</figcaption></figure>)}</div>;
}

function PhysicalProgress({ items, config }: { items: WorkshopItem[]; config: SiteConfig }) {
  const counts = new Map<string, number>();
  items.forEach((item) => counts.set(item.completed ? 'completed' : item.currentStage ?? 'pending', (counts.get(item.completed ? 'completed' : item.currentStage ?? 'pending') ?? 0) + 1));
  return <section className="mt-6 rounded-2xl bg-secondary/60 p-4"><h3 className="text-sm font-semibold">{config.workshop.progress}</h3><div className="mt-3 flex flex-wrap gap-2">{Array.from(counts.entries()).map(([stage, count]) => <span key={stage} className="rounded-full bg-card px-3 py-1.5 text-[11px] font-semibold">{config.workflow[stage] ?? (stage === 'completed' ? config.workshop.completed : config.workshop.pending)} · {count}</span>)}</div></section>;
}

function GuideSteps({ title, steps }: { title: string; steps: Array<{ title: string; detail: string }> }) {
  return <section className="rounded-[22px] bg-secondary/60 p-5"><h3 className="text-sm font-semibold">{title}</h3><ol className="mt-4 space-y-4">{steps.map((step, index) => <li key={`${step.title}-${index}`} className="flex gap-3"><span className="step-number">{index + 1}</span><div><strong className="text-xs">{step.title}</strong><p className="mt-1 text-xs leading-5 text-muted-foreground">{step.detail}</p></div></li>)}</ol></section>;
}

function ShoppingPage({ items, checked, setChecked, config }: { items: ShoppingItem[]; checked: string[]; setChecked: (ids: string[]) => void; config: SiteConfig }) {
  const priority: Record<string, string> = config.shopping.priorities;
  return <div className="mt-8 overflow-hidden rounded-[24px] border bg-card">{items.map((item) => { const done = checked.includes(item.id); return <button type="button" key={item.id} onClick={() => setChecked(done ? checked.filter((id) => id !== item.id) : [...checked, item.id])} className={'shopping-row w-full text-left ' + (done ? 'opacity-45' : '')}><span className="grid size-6 flex-none place-items-center rounded-lg border" style={{ background: done ? item.colorHex : undefined }}>{done && <Check size={14} className="text-white" />}</span><span className="size-9 flex-none rounded-xl border" style={{ background: item.colorHex }} /><span className="min-w-0 flex-1"><strong className={'block truncate text-sm ' + (done ? 'line-through' : '')}>{item.name}</strong><span className="block truncate text-xs text-muted-foreground">{item.brand} · {item.reason}</span></span><span className="rounded-full bg-secondary px-2.5 py-1 text-[10px] font-semibold">{priority[item.priority]}</span></button>; })}</div>;
}

function PaintDetail({ paint, config, onClose }: { paint: Paint; config: SiteConfig; onClose: () => void }) {
  return <div role="presentation" className="fixed inset-0 z-50 grid place-items-center bg-black/45 p-3 backdrop-blur-sm" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}><article className="max-h-[92vh] w-full max-w-3xl overflow-y-auto rounded-[28px] bg-card shadow-2xl"><header className="sticky top-0 z-10 flex items-center justify-between border-b bg-card/95 px-5 py-4 backdrop-blur"><div><p className="eyebrow">{config.paintDetail.sheet}</p><h2 className="mt-1 text-xl font-semibold">{paint.name}</h2></div><button type="button" onClick={onClose} aria-label={config.paintDetail.close} className="grid size-10 place-items-center rounded-xl border"><X size={18} /></button></header><div className="grid gap-6 p-5 md:grid-cols-[220px_1fr]"><div><div className="aspect-square overflow-hidden rounded-[22px] border" style={{ background: `color-mix(in srgb, ${paint.colorHex} 16%, white)` }}>{paint.resultImage || paint.manufacturerImage ? <img src={paint.resultImage || paint.manufacturerImage} alt={`${config.paintDetail.productVisual} ${paint.name}`} className="h-full w-full object-contain" /> : <div className="h-full w-full" style={{ background: paint.colorHex }} />}</div><div className="mt-3 grid grid-cols-2 gap-2 text-xs"><SummaryText value={paint.reference} label={config.paintDetail.referenceLabel} /><SummaryText value={`${paint.volumeMl} ml`} label={config.paintDetail.volumeLabel} /><SummaryText value={formatMetadata(paint.paintType)} label={config.collection.typeFilter} /><SummaryText value={formatMetadata(paint.finish)} label={config.collection.finishFilter} /></div></div><div className="min-w-0"><p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{paint.brand} · {paint.range}</p><p className="mt-4 text-sm leading-6 text-muted-foreground">{paint.manufacturerDescription || config.paintDetail.noManufacturerDescription}</p>{paint.recommendedUses.length > 0 && <section className="mt-5"><h3 className="text-sm font-semibold">{config.paintDetail.recommendedUses}</h3><div className="mt-2 flex flex-wrap gap-2">{paint.recommendedUses.map((use) => <span key={use} className="rounded-full bg-secondary px-3 py-1 text-xs">{use}</span>)}</div></section>}{paint.usageInstructions.summary && <section className="mt-5 rounded-2xl bg-secondary/60 p-4"><h3 className="text-sm font-semibold">{config.paintDetail.usageInstructions}</h3><p className="mt-2 text-xs leading-5 text-muted-foreground">{paint.usageInstructions.summary}</p><ol className="mt-3 space-y-2">{paint.usageInstructions.steps.map((step, index) => <li key={step} className="flex gap-2 text-xs"><span className="step-number">{index + 1}</span><span>{step}</span></li>)}</ol></section>}{paint.manufacturerUrl && <a href={paint.manufacturerUrl} target="_blank" rel="noreferrer" className="mt-5 inline-flex items-center gap-2 text-xs font-semibold text-primary"><ExternalLink size={14} />{config.paintDetail.openManufacturerSheet}</a>}</div></div></article></div>;
}

function SummaryText({ value, label }: { value: string; label: string }) {
  return <div className="rounded-xl bg-secondary p-3"><span className="block truncate font-semibold">{value || '—'}</span><span className="mt-1 block text-[9px] uppercase tracking-wide text-muted-foreground">{label}</span></div>;
}
