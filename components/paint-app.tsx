'use client';

import {
  BookOpen, Camera, Check, ChevronDown, ChevronRight, CircleAlert, Download, Droplets, ExternalLink, FileImage,
  FolderOpen, Grid2X2, ImagePlus, ListChecks, ListFilter, PackageOpen, Paintbrush, Plus, Search,
  ShoppingBasket, Sparkles, Upload, X,
} from 'lucide-react';
import { useEffect, useMemo, useRef, useState } from 'react';
import type { Paint, ShoppingItem } from '@/lib/paint-model';
import type { PaintingProject } from '@/lib/project-model';
import type { SiteConfig } from '@/lib/site-config-model';

type View = 'marketPaints' | 'marketGames' | 'collection' | 'projects' | 'shopping' | 'imports';

type PaintFilterKey = 'type' | 'color' | 'brand' | 'range' | 'finish' | 'volume' | 'tag';
type PaintFilters = Record<PaintFilterKey, string>;

const emptyPaintFilters: PaintFilters = {
  type: '', color: '', brand: '', range: '', finish: '', volume: '', tag: '',
};

function normalizeSearch(value: string) {
  return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLocaleLowerCase('fr').trim();
}

function formatMetadata(value: string) {
  if (!value) return value;
  const formatted = value.replace(/[_-]+/g, ' ');
  return formatted.charAt(0).toLocaleUpperCase('fr') + formatted.slice(1);
}

function metadataOptions(values: string[]) {
  const counts = new Map<string, number>();
  values.filter(Boolean).forEach((value) => counts.set(value, (counts.get(value) ?? 0) + 1));
  return Array.from(counts, ([value, count]) => ({ value, count }))
    .sort((left, right) => formatMetadata(left.value).localeCompare(formatMetadata(right.value), 'fr'));
}

function FacetSelect({ label, allLabel, value, options, onChange }: {
  label: string;
  allLabel: string;
  value: string;
  options: { value: string; count: number }[];
  onChange: (value: string) => void;
}) {
  return (
    <label className="form-field">
      <span>{label}</span>
      <span className="relative">
        <select value={value} onChange={(event) => onChange(event.target.value)} className="h-11 w-full appearance-none rounded-xl border bg-card px-3 pr-9 text-sm outline-none transition focus:border-primary focus:ring-4 focus:ring-primary/10">
          <option value="">{allLabel}</option>
          {options.map((option) => <option key={option.value} value={option.value}>{formatMetadata(option.value)} ({option.count})</option>)}
        </select>
        <ChevronDown className="pointer-events-none absolute right-3 top-1/2 size-3.5 -translate-y-1/2 text-muted-foreground" />
      </span>
    </label>
  );
}

function NavButton({
  icon, label, active, badge, onClick,
}: {
  icon: React.ReactNode;
  label: string;
  active: boolean;
  badge?: string;
  onClick: () => void;
}) {
  return (
    <button className={'nav-item w-full ' + (active ? 'nav-item-active' : '')} onClick={onClick}>
      {icon}<span>{label}</span>
      {badge && <span className="ml-auto rounded-full bg-current/10 px-2 py-0.5 text-[11px]">{badge}</span>}
    </button>
  );
}

function PaintCard({ paint, onOpen, manufacturerSheetLabel, productVisualLabel }: { paint: Paint; onOpen: () => void; manufacturerSheetLabel: string; productVisualLabel: string }) {
  const enlarged = ['cit-contrast-briar-queen-chill', 'cit-contrast-ironjawz-yellow', 'cit-contrast-kroxigor-scales', 'cit-contrast-pylar-glacier'].includes(paint.id);
  const cardImage = paint.resultImage || paint.manufacturerImage;
  return (
    <button type="button" className="paint-card group w-full text-left" onClick={onOpen}>
      <div className="paint-swatch" style={{ background: `color-mix(in srgb, ${paint.colorHex} 12%, white)` }}>
        {cardImage
          ? <img src={cardImage} alt={`${productVisualLabel} ${paint.brand} ${paint.name}`} className={'h-full w-full object-contain p-0.5 transition-transform ' + (!paint.resultImage && enlarged ? 'scale-[1.34]' : 'scale-[1.06]')} />
          : <span className="absolute inset-0" style={{ background: paint.colorHex }} />}
        <span className="absolute bottom-3 left-3 z-[1] rounded-full bg-black/30 px-2 py-1 text-[10px] font-semibold uppercase tracking-wider text-white backdrop-blur-sm">
          {paint.reference || paint.range}
        </span>
      </div>
      <div className="min-w-0 flex-1 py-0.5">
        <p className="text-[11px] font-semibold uppercase tracking-[0.08em] text-muted-foreground">{paint.brand}</p>
        <h3 className="mt-0.5 truncate text-[15px] font-semibold tracking-tight">{paint.name}</h3>
        <div className="mt-3 flex flex-wrap gap-1.5">
          <span className="inline-flex rounded-full bg-secondary px-2.5 py-1 text-[11px] font-medium text-secondary-foreground">{paint.colorFamily || paint.finish}</span>
          {paint.quantity > 1 && <span className="inline-flex rounded-full bg-primary/8 px-2.5 py-1 text-[11px] font-semibold text-primary">× {paint.quantity}</span>}
        </div>
        {paint.manufacturerUrl && <span className="mt-2 inline-flex items-center gap-1 text-[11px] font-semibold text-primary"><BookOpen size={12} />{manufacturerSheetLabel}</span>}
      </div>
    </button>
  );
}

function EmptyState({ text, hint }: { text: string; hint: string }) {
  return (
    <div className="col-span-full rounded-[24px] border border-dashed bg-card/50 px-6 py-14 text-center">
      <Search className="mx-auto size-7 text-muted-foreground/60" />
      <p className="mt-3 text-sm font-semibold">{text}</p>
      <p className="mt-1 text-xs text-muted-foreground">{hint}</p>
    </div>
  );
}

export function PaintApp({ initialPaints, projects, shoppingSeed, config }: {
  initialPaints: Paint[];
  projects: PaintingProject[];
  shoppingSeed: ShoppingItem[];
  config: SiteConfig;
}) {
  const viewLabels: Record<View, string> = {
    marketPaints: config.navigation.marketPaints,
    marketGames: config.navigation.marketGames,
    collection: config.navigation.workshopPaints,
    projects: config.navigation.workshopProjects,
    shopping: config.navigation.shopping,
    imports: config.navigation.imports,
  };
  const [paints, setPaints] = useState<Paint[]>(initialPaints);
  const [view, setView] = useState<View>('marketPaints');
  const [query, setQuery] = useState('');
  const [filters, setFilters] = useState<PaintFilters>(emptyPaintFilters);
  const [filtersOpen, setFiltersOpen] = useState(true);
  const [manufacturerSheetOnly, setManufacturerSheetOnly] = useState(false);
  const [realResultOnly, setRealResultOnly] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [selectedPaint, setSelectedPaint] = useState<Paint | null>(null);
  const [photo, setPhoto] = useState<File | null>(null);
  const [photoUrl, setPhotoUrl] = useState('');
  const [saving, setSaving] = useState(false);
  const [notice, setNotice] = useState('');
  const [selectedProjectId, setSelectedProjectId] = useState(projects[0]?.id ?? '');
  const activeProject = (projects.find((project) => project.id === selectedProjectId) ?? projects[0])!;
  const [selectedProjectItemId, setSelectedProjectItemId] = useState(projects[0]?.items[0]?.id ?? '');
  const selectedProjectItem = (activeProject.items.find((item) => item.id === selectedProjectItemId) ?? activeProject.items[0])!;
  const [checkedBuys, setCheckedBuys] = useState<string[]>([]);
  const [form, setForm] = useState({
    brand: '', range: '', reference: '', name: '', colorHex: '#6f746f', finish: config.imports.finishOptions[0] ?? '', quantity: 1,
  });
  const fileInput = useRef<HTMLInputElement>(null);

  useEffect(() => () => {
    if (photoUrl) URL.revokeObjectURL(photoUrl);
  }, [photoUrl]);

  const isPaintView = view === 'marketPaints' || view === 'collection';
  const visiblePaints = useMemo(
    () => view === 'collection' ? paints.filter((paint) => paint.quantity > 0) : paints,
    [paints, view],
  );
  const filterOptions = useMemo(() => ({
    types: metadataOptions(visiblePaints.map((paint) => paint.paintType)),
    colors: metadataOptions(visiblePaints.map((paint) => paint.colorFamily)),
    brands: metadataOptions(visiblePaints.map((paint) => paint.brand)),
    ranges: metadataOptions(visiblePaints.map((paint) => paint.range)),
    finishes: metadataOptions(visiblePaints.map((paint) => paint.finish)),
    volumes: metadataOptions(visiblePaints.filter((paint) => paint.volumeMl > 0).map((paint) => `${paint.volumeMl} ml`)),
    tags: metadataOptions(visiblePaints.flatMap((paint) => paint.tags)),
  }), [visiblePaints]);

  const filteredPaints = useMemo(() => {
    const normalized = normalizeSearch(query);
    return visiblePaints.filter((paint) => {
      const haystack = normalizeSearch([
        paint.brand, paint.manufacturer, ...paint.brandAliases, paint.range, paint.paintType,
        paint.reference, paint.name, paint.colorFamily, paint.colorHex, paint.finish, paint.medium,
        paint.status, paint.warnings, paint.notes, `${paint.volumeMl} ml`, ...paint.tags,
        ...paint.recommendedUses,
      ].join(' '));
      return (!normalized || haystack.includes(normalized))
        && (!filters.type || paint.paintType === filters.type)
        && (!filters.color || paint.colorFamily === filters.color)
        && (!filters.brand || paint.brand === filters.brand)
        && (!filters.range || paint.range === filters.range)
        && (!filters.finish || paint.finish === filters.finish)
        && (!filters.volume || `${paint.volumeMl} ml` === filters.volume)
        && (!filters.tag || paint.tags.includes(filters.tag))
        && (!manufacturerSheetOnly || Boolean(paint.manufacturerUrl))
        && (!realResultOnly || Boolean(paint.resultImage || paint.resultReferenceUrl));
    });
  }, [visiblePaints, filters, manufacturerSheetOnly, query, realResultOnly]);

  const activeFilterCount = Object.values(filters).filter(Boolean).length
    + Number(manufacturerSheetOnly) + Number(realResultOnly);
  const hasActiveSearch = Boolean(query.trim()) || activeFilterCount > 0;
  const activeFilters = [
    ...(query.trim() ? [{ key: 'query', label: `${config.collection.searchFilter} : ${query.trim()}` }] : []),
    ...Object.entries(filters).filter((entry): entry is [PaintFilterKey, string] => Boolean(entry[1])).map(([key, value]) => ({
      key,
      label: `${({ type: config.collection.typeFilter, color: config.collection.colorFilter, brand: config.collection.brandFilter, range: config.collection.rangeFilter, finish: config.collection.finishFilter, volume: config.collection.volumeFilter, tag: config.collection.tagFilter })[key]} : ${formatMetadata(value)}`,
    })),
    ...(manufacturerSheetOnly ? [{ key: 'manufacturerSheetOnly', label: config.collection.manufacturerSheetOnly }] : []),
    ...(realResultOnly ? [{ key: 'realResultOnly', label: config.collection.realResultOnly }] : []),
  ];

  function setFilter(key: PaintFilterKey, value: string) {
    setFilters((current) => ({ ...current, [key]: value }));
  }

  function clearFilters() {
    setQuery('');
    setFilters(emptyPaintFilters);
    setManufacturerSheetOnly(false);
    setRealResultOnly(false);
  }

  function removeActiveFilter(key: string) {
    if (key === 'query') setQuery('');
    else if (key === 'manufacturerSheetOnly') setManufacturerSheetOnly(false);
    else if (key === 'realResultOnly') setRealResultOnly(false);
    else setFilter(key as PaintFilterKey, '');
  }

  const totalPots = visiblePaints.reduce((sum, paint) => sum + paint.quantity, 0);
  const distinctBrandCount = new Set(visiblePaints.map((paint) => paint.brand)).size;
  const manufacturerSheetCount = visiblePaints.filter((paint) => paint.manufacturerUrl && paint.manufacturerImage).length;
  const projectPaintStatus = (name: string, pendingImport?: boolean) => {
    if (paints.some((paint) => paint.name.toLocaleLowerCase('fr') === name.toLocaleLowerCase('fr'))) return config.projects.available;
    return pendingImport ? config.projects.importPending : config.projects.missing;
  };

  function choosePhoto(file: File | null) {
    if (!file) return;
    if (photoUrl) URL.revokeObjectURL(photoUrl);
    setPhoto(file);
    setPhotoUrl(URL.createObjectURL(file));
    const basename = file.name.replace(/\.[^.]+$/, '').replace(/[_-]+/g, ' ');
    setForm((current) => ({ ...current, name: current.name || basename }));
  }

  function closeModal() {
    setModalOpen(false);
    setPhoto(null);
    if (photoUrl) URL.revokeObjectURL(photoUrl);
    setPhotoUrl('');
    setForm({ brand: '', range: '', reference: '', name: '', colorHex: '#6f746f', finish: config.imports.finishOptions[0] ?? '', quantity: 1 });
  }

  async function savePaint(event: React.SyntheticEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!form.brand.trim() || !form.name.trim()) {
      setNotice(config.imports.requiredNotice);
      return;
    }
    setSaving(true);
    try {
      const now = new Date().toISOString();
      const paint: Paint = {
        id: `session-${crypto.randomUUID()}`,
        ...form,
        manufacturer: '',
        brandAliases: [],
        paintType: '',
        medium: 'acrylique',
        status: 'session',
        warnings: '',
        tags: [],
        notes: photo ? `Photo sélectionnée : ${photo.name}` : '',
        createdAt: now,
        updatedAt: now,
        manufacturerUrl: '',
        manufacturerImage: '',
        manufacturerImageCredit: photo ? config.imports.previewCredit : '',
        volumeMl: 0,
        colorFamily: '',
        manufacturerDescription: '',
        recommendedUses: [],
        manufacturerVerifiedAt: '',
        resultImage: '',
        resultImageCredit: '',
        resultImageSource: '',
        resultImageLicense: '',
        resultReferenceUrl: '',
      };
      setPaints((current) => [paint, ...current]);
      setNotice(`${paint.name} ${config.imports.sessionPreviewSuffix}`);
      closeModal();
      setView('collection');
    } catch {
      setNotice(config.imports.previewFailed);
    } finally {
      setSaving(false);
    }
  }

  return (
    <main className="min-h-screen bg-background pb-20 text-foreground lg:pb-0">
      <header className="sticky top-0 z-30 border-b bg-background/90 backdrop-blur-xl">
        <div className="mx-auto flex h-[72px] max-w-[1500px] items-center gap-4 px-4 sm:px-6 lg:px-8">
          <button className="flex min-w-fit items-center gap-3 text-left" onClick={() => setView('marketPaints')}>
            <span className="grid size-10 place-items-center rounded-[14px] bg-primary text-primary-foreground shadow-sm"><Droplets size={21} strokeWidth={2.2} /></span>
            <span className="hidden sm:block"><strong className="block text-[15px] leading-4 tracking-[-0.02em]">{config.brand.name}</strong><span className="text-xs text-muted-foreground">{config.brand.subtitle}</span></span>
          </button>

          <label className="relative ml-auto hidden w-full max-w-xl md:block">
            <Search className="absolute left-4 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
            <input value={query} onChange={(event) => setQuery(event.target.value)} onFocus={() => setView('marketPaints')} className="h-11 w-full rounded-2xl border bg-card pl-11 pr-4 text-sm shadow-sm outline-none transition focus:border-primary focus:ring-4 focus:ring-primary/10" placeholder={config.header.searchPlaceholder} aria-label={config.header.searchAriaLabel} />
          </label>

          <div className="ml-auto hidden items-center gap-1 rounded-xl border bg-card p-1 sm:flex">
            <a className="rounded-lg px-2.5 py-1.5 text-xs font-semibold text-muted-foreground hover:bg-secondary" href="/api/v1/exports/yaml"><Download className="mr-1 inline size-3.5" />YAML</a>
            <a className="rounded-lg px-2.5 py-1.5 text-xs font-semibold text-muted-foreground hover:bg-secondary" href="/api/v1/exports/csv">CSV</a>
          </div>
          <button onClick={() => setModalOpen(true)} className="inline-flex h-11 items-center gap-2 rounded-2xl bg-primary px-4 text-sm font-semibold text-primary-foreground shadow-sm transition hover:brightness-110">
            <Camera size={17} /><span className="hidden sm:inline">{config.header.addByPhoto}</span><span className="sm:hidden">{config.header.add}</span>
          </button>
        </div>
      </header>

      <div className="mx-auto grid max-w-[1500px] lg:grid-cols-[224px_minmax(0,1fr)]">
        <aside className="hidden min-h-[calc(100vh-72px)] border-r px-4 py-6 lg:block">
          <nav aria-label={config.navigation.ariaLabel}>
            <p className="mb-2 px-3 text-[10px] font-bold uppercase tracking-[0.14em] text-muted-foreground">{config.navigation.marketSection}</p>
            <div className="space-y-1">
              <NavButton icon={<Grid2X2 size={18} />} label={config.navigation.marketPaints} active={view === 'marketPaints'} badge={String(paints.length)} onClick={() => setView('marketPaints')} />
              <NavButton icon={<BookOpen size={18} />} label={config.navigation.marketGames} active={view === 'marketGames'} badge={String(projects.length)} onClick={() => setView('marketGames')} />
            </div>
            <div className="my-5 border-t" />
            <p className="mb-2 px-3 text-[10px] font-bold uppercase tracking-[0.14em] text-muted-foreground">{config.navigation.workshopSection}</p>
            <div className="space-y-1">
              <NavButton icon={<Droplets size={18} />} label={config.navigation.workshopPaints} active={view === 'collection'} badge={String(paints.filter((paint) => paint.quantity > 0).length)} onClick={() => setView('collection')} />
              <NavButton icon={<FolderOpen size={18} />} label={config.navigation.workshopProjects} active={view === 'projects'} badge={String(projects.length)} onClick={() => setView('projects')} />
              <NavButton icon={<ShoppingBasket size={18} />} label={config.navigation.shopping} active={view === 'shopping'} badge={String(shoppingSeed.length)} onClick={() => setView('shopping')} />
              <NavButton icon={<Camera size={18} />} label={config.navigation.imports} active={view === 'imports'} onClick={() => setView('imports')} />
            </div>
          </nav>

          <div className="mt-8 rounded-[22px] border bg-card p-4 shadow-sm">
            <div className="mb-3 grid size-9 place-items-center rounded-xl bg-secondary text-primary"><Sparkles size={17} /></div>
            <p className="text-sm font-semibold">{config.projects.currentProject}</p>
            <p className="mt-1 text-xs leading-5 text-muted-foreground">{activeProject.name}</p>
            <div className="mt-4 h-1.5 overflow-hidden rounded-full bg-secondary"><div className="h-full w-[8%] rounded-full bg-primary" /></div>
            <p className="mt-2 text-[11px] text-muted-foreground">{activeProject.items.reduce((sum, item) => sum + item.quantity, 0)} {config.projects.figureUnit} · {activeProject.items.length} {config.projects.sheetUnit}</p>
          </div>
        </aside>

        <section className="min-w-0 px-4 py-7 sm:px-6 lg:px-9 lg:py-9">
          <div className="flex flex-wrap items-end justify-between gap-4">
            <div>
              <p className="eyebrow">{view === 'marketPaints' || view === 'marketGames' ? config.navigation.marketSection : config.navigation.workshopSection} · {viewLabels[view]}</p>
              <h1 className="mt-1 text-3xl font-semibold tracking-[-0.045em] sm:text-4xl">
                {view === 'marketPaints' && config.market.paintsTitle}
                {view === 'marketGames' && config.market.gamesTitle}
                {view === 'collection' && config.collection.title}
                {view === 'projects' && activeProject.name}
                {view === 'shopping' && config.shopping.title}
                {view === 'imports' && config.imports.title}
              </h1>
              <p className="mt-2 max-w-2xl text-sm text-muted-foreground">
                {view === 'marketPaints' && config.market.paintsDescription}
                {view === 'marketGames' && config.market.gamesDescription}
                {view === 'collection' && config.collection.description}
                {view === 'projects' && config.projects.description}
                {view === 'shopping' && config.shopping.description}
                {view === 'imports' && config.imports.description}
              </p>
            </div>
            {isPaintView && (
              <button type="button" aria-expanded={filtersOpen} aria-controls="paint-filters" onClick={() => setFiltersOpen((open) => !open)} className={'inline-flex h-10 items-center gap-2 rounded-xl border px-3 text-sm font-semibold shadow-sm transition ' + (filtersOpen || activeFilterCount ? 'border-primary/25 bg-primary/5 text-primary' : 'bg-card text-muted-foreground hover:text-foreground')}>
                <ListFilter size={16} />{config.collection.filters}
                {activeFilterCount > 0 && <span className="grid min-w-5 place-items-center rounded-full bg-primary px-1.5 py-0.5 text-[10px] text-primary-foreground">{activeFilterCount}</span>}
              </button>
            )}
          </div>

          {notice && (
            <button onClick={() => setNotice('')} className="mt-5 flex w-full items-center gap-3 rounded-2xl border border-primary/15 bg-primary/5 px-4 py-3 text-left text-xs text-primary">
              <CircleAlert size={16} /><span className="flex-1">{notice}</span><X size={14} />
            </button>
          )}

          {isPaintView && (
            <>
              <label className="relative mt-5 block md:hidden">
                <Search className="absolute left-4 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
                <input value={query} onChange={(event) => setQuery(event.target.value)} className="h-11 w-full rounded-2xl border bg-card pl-11 pr-4 text-sm outline-none" placeholder={config.header.searchShortPlaceholder} />
              </label>
              {filtersOpen && (
                <section id="paint-filters" aria-label={config.collection.filterAriaLabel} className="mt-5 rounded-[24px] border bg-card p-4 shadow-sm sm:p-5">
                  <div className="flex flex-wrap items-start justify-between gap-3">
                    <div>
                      <h2 className="text-sm font-semibold">{config.collection.filterPanelTitle}</h2>
                      <p className="mt-1 text-xs text-muted-foreground">{config.collection.filterPanelDescription}</p>
                    </div>
                    {hasActiveSearch && <button type="button" onClick={clearFilters} className="inline-flex items-center gap-1.5 text-xs font-semibold text-primary"><X size={13} />{config.collection.resetFilters}</button>}
                  </div>
                  <div className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
                    <FacetSelect label={config.collection.typeFilter} allLabel={config.collection.allTypes} value={filters.type} options={filterOptions.types} onChange={(value) => setFilter('type', value)} />
                    <FacetSelect label={config.collection.colorFilter} allLabel={config.collection.allColors} value={filters.color} options={filterOptions.colors} onChange={(value) => setFilter('color', value)} />
                    <FacetSelect label={config.collection.brandFilter} allLabel={config.collection.allBrands} value={filters.brand} options={filterOptions.brands} onChange={(value) => setFilter('brand', value)} />
                    <FacetSelect label={config.collection.rangeFilter} allLabel={config.collection.allRanges} value={filters.range} options={filterOptions.ranges} onChange={(value) => setFilter('range', value)} />
                    <FacetSelect label={config.collection.finishFilter} allLabel={config.collection.allFinishes} value={filters.finish} options={filterOptions.finishes} onChange={(value) => setFilter('finish', value)} />
                    <FacetSelect label={config.collection.volumeFilter} allLabel={config.collection.allVolumes} value={filters.volume} options={filterOptions.volumes} onChange={(value) => setFilter('volume', value)} />
                    <FacetSelect label={config.collection.tagFilter} allLabel={config.collection.allTags} value={filters.tag} options={filterOptions.tags} onChange={(value) => setFilter('tag', value)} />
                  </div>
                  <div className="mt-4 flex flex-wrap gap-2 border-t pt-4">
                    <label className={'need-chip cursor-pointer ' + (manufacturerSheetOnly ? 'need-chip-selected' : '')}>
                      <input type="checkbox" checked={manufacturerSheetOnly} onChange={(event) => setManufacturerSheetOnly(event.target.checked)} className="size-3.5 accent-current" />
                      {config.collection.manufacturerSheetOnly}
                    </label>
                    <label className={'need-chip cursor-pointer ' + (realResultOnly ? 'need-chip-selected' : '')}>
                      <input type="checkbox" checked={realResultOnly} onChange={(event) => setRealResultOnly(event.target.checked)} className="size-3.5 accent-current" />
                      {config.collection.realResultOnly}
                    </label>
                  </div>
                </section>
              )}
              {activeFilters.length > 0 && (
                <div className="mt-4 flex flex-wrap items-center gap-2" aria-label={config.collection.activeFilters}>
                  <span className="mr-1 text-[11px] font-semibold uppercase tracking-[0.08em] text-muted-foreground">{config.collection.activeFilters}</span>
                  {activeFilters.map((filter) => (
                    <button key={filter.key} type="button" onClick={() => removeActiveFilter(filter.key)} title={`${config.collection.removeFilter} : ${filter.label}`} className="inline-flex min-h-8 items-center gap-1.5 rounded-full border border-primary/20 bg-primary/5 px-3 text-xs font-semibold text-primary transition hover:bg-primary/10">
                      {filter.label}<X size={12} />
                    </button>
                  ))}
                </div>
              )}
              <div className="mt-7 grid gap-3 sm:grid-cols-3">
                <article className="metric-card"><div className="metric-icon bg-[#e5ecff] text-[#3157a4]"><PackageOpen size={19} /></div><div><strong>{view === 'marketPaints' ? visiblePaints.length : totalPots}</strong><span>{view === 'marketPaints' ? config.market.paintsMetric : config.collection.potsMetric}</span></div></article>
                <article className="metric-card"><div className="metric-icon bg-[#e5f4ec] text-[#207650]"><Check size={19} /></div><div><strong>{view === 'marketPaints' ? distinctBrandCount : visiblePaints.length}</strong><span>{view === 'marketPaints' ? config.market.brandsMetric : config.collection.colorsMetric}</span></div></article>
                <article className="metric-card"><div className="metric-icon bg-[#fff0da] text-[#a45713]"><BookOpen size={19} /></div><div><strong>{manufacturerSheetCount}</strong><span>{config.collection.sheetsMetric}</span></div></article>
              </div>
              <div className="mt-8 flex items-center justify-between gap-4"><div><h2 className="text-lg font-semibold tracking-tight">{hasActiveSearch ? config.collection.resultsTitle : config.collection.allPaintsTitle}</h2><p className="text-xs text-muted-foreground">{filteredPaints.length} {filteredPaints.length > 1 ? config.units.colorPlural : config.units.colorSingular}</p></div>{view === 'collection' && <button onClick={() => setModalOpen(true)} className="inline-flex items-center gap-1.5 text-sm font-semibold text-primary"><Plus size={15} />{config.header.add}</button>}</div>
              <div className="mt-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
                {filteredPaints.length ? filteredPaints.map((paint) => <PaintCard key={paint.id} paint={paint} onOpen={() => setSelectedPaint(paint)} manufacturerSheetLabel={config.collection.manufacturerSheet} productVisualLabel={config.paintDetail.productVisual} />) : <EmptyState text={config.collection.emptyTitle} hint={config.collection.emptyHint} />}
              </div>
            </>
          )}

          {view === 'marketGames' && (
            <div className="mt-7 grid gap-5 lg:grid-cols-2">
              {projects.map((project) => {
                const physicalItems = project.items.reduce((sum, item) => sum + item.quantity, 0);
                return (
                  <article key={project.id} className="rounded-[28px] border bg-card p-6 shadow-sm sm:p-7">
                    <div className="flex items-start justify-between gap-4">
                      <div>
                        <p className="eyebrow">{project.game}</p>
                        <h2 className="mt-2 text-xl font-semibold tracking-tight">{project.name}</h2>
                      </div>
                      <span className="grid size-12 flex-none place-items-center rounded-2xl bg-secondary text-primary"><PackageOpen size={21} /></span>
                    </div>
                    <p className="mt-3 text-sm leading-6 text-muted-foreground">{project.scope}</p>
                    <div className="mt-6 grid grid-cols-2 gap-3">
                      <div className="rounded-2xl bg-secondary/60 p-4"><strong className="block text-2xl tracking-tight">{project.items.length}</strong><span className="mt-1 block text-[11px] leading-4 text-muted-foreground">{config.market.catalogItems}</span></div>
                      <div className="rounded-2xl bg-secondary/60 p-4"><strong className="block text-2xl tracking-tight">{physicalItems}</strong><span className="mt-1 block text-[11px] leading-4 text-muted-foreground">{config.market.physicalItems}</span></div>
                    </div>
                    <div className="mt-6 flex flex-wrap items-center gap-4">
                      <button type="button" onClick={() => { setSelectedProjectId(project.id); setSelectedProjectItemId(project.items[0]?.id ?? ''); setView('projects'); }} className="inline-flex h-10 items-center gap-2 rounded-xl bg-primary px-4 text-sm font-semibold text-primary-foreground"><FolderOpen size={15} />{config.market.openWorkshop}</button>
                      {project.edition.url && <a href={project.edition.url} target="_blank" rel="noreferrer" className="inline-flex items-center gap-2 text-xs font-semibold text-primary"><ExternalLink size={14} />{config.market.source}</a>}
                    </div>
                  </article>
                );
              })}
            </div>
          )}

          {view === 'projects' && (
            <>
            <div className="mt-6 flex items-start gap-3 rounded-2xl border border-primary/15 bg-primary/5 p-4 text-xs leading-5 text-muted-foreground"><CircleAlert className="mt-0.5 size-4 flex-none text-primary" /><span>{activeProject.edition.note} {activeProject.edition.url && <a className="font-semibold text-primary" href={activeProject.edition.url} target="_blank" rel="noreferrer">{config.projects.editionSource}</a>}</span></div>
            <div className="mt-5 grid gap-5 xl:grid-cols-[320px_minmax(0,1fr)]">
              <aside className="overflow-hidden rounded-[26px] border bg-card shadow-sm">
                <div className="border-b p-5">
                  <p className="eyebrow">{config.projects.activeProject}</p>
                  {projects.length > 1 && <select value={activeProject.id} onChange={(event) => { const project = projects.find((item) => item.id === event.target.value); setSelectedProjectId(event.target.value); setSelectedProjectItemId(project?.items[0]?.id ?? ''); }} className="mt-2 h-10 w-full rounded-xl border bg-background px-3 text-sm font-semibold">{projects.map((project) => <option key={project.id} value={project.id}>{project.name}</option>)}</select>}
                  <h2 className="mt-2 text-lg font-semibold">{activeProject.name}</h2>
                  <p className="mt-1 text-xs leading-5 text-muted-foreground">{activeProject.scope}</p>
                  <a href={`/projects/${activeProject.id}`} className="mt-3 inline-flex items-center gap-1 text-xs font-semibold text-primary"><BookOpen size={13} />{config.projects.allSheets}</a>
                </div>
                <div className="max-h-[70vh] overflow-y-auto p-2">
                  {activeProject.items.map((item) => (
                    <button key={item.id} onClick={() => setSelectedProjectItemId(item.id)} className={'flex w-full items-center gap-3 rounded-2xl p-3 text-left transition ' + (selectedProjectItem.id === item.id ? 'bg-primary text-primary-foreground' : 'hover:bg-secondary')}>
                      <span className={'grid size-10 flex-none place-items-center rounded-xl ' + (selectedProjectItem.id === item.id ? 'bg-white/12' : 'bg-secondary text-primary')}><Paintbrush size={17} /></span>
                      <span className="min-w-0 flex-1"><strong className="block truncate text-sm">{item.name}</strong><span className={'mt-0.5 block text-[11px] ' + (selectedProjectItem.id === item.id ? 'text-white/65' : 'text-muted-foreground')}>{item.kind} · × {item.quantity}</span></span>
                      <ChevronRight size={16} className="flex-none opacity-60" />
                    </button>
                  ))}
                </div>
              </aside>

              <article className="min-w-0 rounded-[26px] border bg-card p-5 shadow-sm sm:p-7">
                <div className="flex flex-wrap items-start justify-between gap-4 border-b pb-5">
                  <div><p className="eyebrow">{selectedProjectItem.kind} · × {selectedProjectItem.quantity}</p><h2 className="mt-1 text-2xl font-semibold tracking-tight">{selectedProjectItem.name}</h2><p className="mt-2 max-w-2xl text-sm leading-6 text-muted-foreground">{selectedProjectItem.description}</p></div>
                  <span className="rounded-full bg-[#fff0da] px-3 py-1.5 text-xs font-semibold text-[#9a5217]">{selectedProjectItem.status}</span>
                </div>

                <section className="mt-6">
                  <div className="flex items-center gap-2"><ListChecks size={17} className="text-primary" /><h3 className="text-sm font-semibold">{config.projects.paintsToUse}</h3></div>
                  <div className="mt-3 grid gap-2 sm:grid-cols-2">
                    {selectedProjectItem.paints.map((paint) => {
                      const status = projectPaintStatus(paint.name, paint.pendingImport);
                      return <div key={paint.brand + paint.name} className="flex items-center gap-3 rounded-2xl border bg-background/50 p-3"><span className="size-10 flex-none rounded-xl border" style={{ background: paint.colorHex }} /><span className="min-w-0 flex-1"><strong className="block truncate text-sm">{paint.name}</strong><span className="block truncate text-[11px] text-muted-foreground">{paint.brand} · {paint.role}</span></span><span className={'rounded-full px-2 py-1 text-[10px] font-semibold ' + (status === config.projects.available ? 'bg-[#e5f4ec] text-[#207650]' : status === config.projects.importPending ? 'bg-[#e5ecff] text-[#3157a4]' : 'bg-[#ffe5df] text-[#a6402c]')}>{status}</span></div>;
                    })}
                  </div>
                </section>

                <div className="mt-7 grid gap-5 lg:grid-cols-2">
                  <section className="rounded-[22px] bg-secondary/60 p-5"><h3 className="text-sm font-semibold">{config.projects.supportPreparation}</h3><ol className="mt-4 space-y-4">{selectedProjectItem.preparation.map((step, index) => <li key={step.title} className="flex gap-3"><span className="step-number">{index + 1}</span><div><strong className="text-xs">{step.title}</strong><p className="mt-1 text-xs leading-5 text-muted-foreground">{step.detail}</p></div></li>)}</ol></section>
                  <section className="rounded-[22px] border p-5"><h3 className="text-sm font-semibold">{config.projects.paintingSteps}</h3><ol className="mt-4 space-y-4">{selectedProjectItem.painting.map((step, index) => <li key={step.title} className="flex gap-3"><span className="step-number">{index + 1}</span><div><strong className="text-xs">{step.title}</strong><p className="mt-1 text-xs leading-5 text-muted-foreground">{step.detail}</p></div></li>)}</ol></section>
                </div>
                <div className="mt-5 flex flex-wrap gap-4">
                  <a href={`/projects/${activeProject.id}/${selectedProjectItem.id}`} className="inline-flex items-center gap-2 text-xs font-semibold text-primary"><BookOpen size={14} />{config.projects.fullSheet}</a>
                  {selectedProjectItem.sources.map((source) => <a key={source.url} href={source.url} target="_blank" rel="noreferrer" className="inline-flex items-center gap-2 text-xs font-semibold text-primary"><ExternalLink size={14} />{source.label}</a>)}
                </div>
              </article>
            </div>
            </>
          )}

          {view === 'shopping' && (
            <div className="mt-7 grid gap-5 xl:grid-cols-[minmax(0,1fr)_320px]">
              <div className="overflow-hidden rounded-[26px] border bg-card shadow-sm">
                {shoppingSeed.map((item, index) => {
                  const checked = checkedBuys.includes(item.id);
                  return <label key={item.id} className={'shopping-row ' + (index ? 'border-t ' : '') + (checked ? 'opacity-45' : '')}><input className="sr-only" type="checkbox" checked={checked} onChange={() => setCheckedBuys((current) => checked ? current.filter((id) => id !== item.id) : [...current, item.id])} /><span className={'grid size-5 place-items-center rounded-md border ' + (checked ? 'border-primary bg-primary text-white' : 'bg-background')}>{checked && <Check size={13} />}</span><span className="size-12 rounded-[14px] border shadow-inner" style={{ background: item.colorHex }} /><span className="min-w-0 flex-1"><span className="block text-xs font-semibold uppercase tracking-wider text-muted-foreground">{item.brand} · {item.reference}</span><strong className="mt-0.5 block">{item.name}</strong><span className="mt-1 block text-xs text-muted-foreground">{item.reason}</span></span><span className={'rounded-full px-2.5 py-1 text-[11px] font-semibold ' + (item.priority === 'haute' ? 'bg-[#ffe5df] text-[#a6402c]' : 'bg-secondary text-muted-foreground')}>{item.priority}</span></label>;
                })}
              </div>
              <aside className="rounded-[26px] border bg-card p-5 shadow-sm">
                <div className="grid size-11 place-items-center rounded-2xl bg-[#fff0da] text-[#a45713]"><ShoppingBasket size={20} /></div>
                <h2 className="mt-4 text-lg font-semibold">{config.shopping.ready}</h2>
                <p className="mt-1 text-sm leading-6 text-muted-foreground">{shoppingSeed.length - checkedBuys.length} {shoppingSeed.length - checkedBuys.length > 1 ? config.units.paintPlural : config.units.paintSingular} {config.shopping.remainingSuffix}</p>
                <button className="mt-5 inline-flex h-10 w-full items-center justify-center gap-2 rounded-xl border bg-background text-sm font-semibold"><Download size={15} />{config.shopping.export}</button>
              </aside>
            </div>
          )}

          {view === 'imports' && (
            <div className="mt-7 grid gap-5 lg:grid-cols-2">
              <button onClick={() => setModalOpen(true)} className="group flex min-h-[280px] flex-col items-center justify-center rounded-[28px] border-2 border-dashed border-primary/20 bg-card p-8 text-center transition hover:border-primary/50 hover:bg-primary/[0.025]">
                <span className="grid size-16 place-items-center rounded-[22px] bg-primary/8 text-primary transition group-hover:scale-105"><ImagePlus size={28} /></span>
                <h2 className="mt-5 text-xl font-semibold">{config.imports.choosePhoto}</h2>
                <p className="mt-2 max-w-sm text-sm leading-6 text-muted-foreground">{config.imports.photoHelp}</p>
                <span className="mt-5 inline-flex h-10 items-center gap-2 rounded-xl bg-primary px-4 text-sm font-semibold text-primary-foreground"><Upload size={16} />{config.imports.browse}</span>
              </button>
              <div className="rounded-[28px] border bg-card p-6 shadow-sm sm:p-8">
                <p className="eyebrow">{config.imports.fromIphone}</p>
                <h2 className="mt-2 text-xl font-semibold">{config.imports.folderReady}</h2>
                <ol className="mt-5 space-y-4 text-sm text-muted-foreground">
                  <li className="flex gap-3"><span className="step-number">1</span><span>{config.imports.folderStep}</span></li>
                  <li className="flex gap-3"><span className="step-number">2</span><span>{config.imports.photosStep}</span></li>
                  <li className="flex gap-3"><span className="step-number">3</span><span>{config.imports.returnStep}</span></li>
                </ol>
                <div className="mt-6 rounded-2xl bg-secondary p-4 text-xs leading-5 text-muted-foreground"><strong className="text-foreground">{config.imports.photoTipTitle} :</strong> {config.imports.photoTip}</div>
              </div>
            </div>
          )}
        </section>
      </div>

      <nav className="fixed inset-x-0 bottom-0 z-30 grid grid-cols-5 border-t bg-card/95 px-2 pb-[max(0.5rem,env(safe-area-inset-bottom))] pt-2 backdrop-blur-xl lg:hidden" aria-label={config.navigation.mobileAriaLabel}>
        {([
          ['marketPaints', <Grid2X2 key="m" size={19} />],
          ['marketGames', <BookOpen key="g" size={19} />],
          ['collection', <Droplets key="c" size={19} />],
          ['projects', <FolderOpen key="p" size={19} />],
          ['shopping', <ShoppingBasket key="s" size={19} />],
        ] as [View, React.ReactNode][]).map(([item, icon]) => <button key={item} onClick={() => setView(item)} className={'flex flex-col items-center gap-1 rounded-xl py-1.5 text-[10px] font-semibold ' + (view === item ? 'text-primary' : 'text-muted-foreground')}>{icon}{viewLabels[item]}</button>)}
      </nav>

      {selectedPaint && (
        <dialog open aria-label={`${config.paintDetail.sheet} ${selectedPaint.name}`} className="fixed inset-0 z-50 m-0 grid h-full max-h-none w-full max-w-none place-items-end bg-black/40 p-0 backdrop-blur-sm sm:place-items-center sm:p-5">
          <div className="max-h-[94vh] w-full overflow-y-auto rounded-t-[28px] border bg-background shadow-2xl sm:max-w-3xl sm:rounded-[28px]">
            <div className="sticky top-0 z-10 flex items-center justify-between border-b bg-background/95 px-5 py-4 backdrop-blur-sm">
              <div><p className="eyebrow">{config.paintDetail.sheet}</p><h2 className="mt-0.5 text-lg font-semibold">{selectedPaint.name}</h2></div>
              <button onClick={() => setSelectedPaint(null)} className="grid size-9 place-items-center rounded-xl border bg-card" aria-label={config.paintDetail.close}><X size={17} /></button>
            </div>
            <div className="grid gap-6 p-5 sm:grid-cols-[280px_minmax(0,1fr)] sm:p-6">
              <div>
                <div className="relative aspect-square overflow-hidden rounded-[24px] border bg-white" style={{ boxShadow: `inset 0 -10px 50px color-mix(in srgb, ${selectedPaint.colorHex} 10%, transparent)` }}>
                  {selectedPaint.manufacturerImage
                    ? <img src={selectedPaint.manufacturerImage} alt={`${config.paintDetail.productVisual} ${selectedPaint.brand} ${selectedPaint.name}`} className={'h-full w-full object-contain p-3 ' + (['cit-contrast-briar-queen-chill', 'cit-contrast-ironjawz-yellow', 'cit-contrast-kroxigor-scales', 'cit-contrast-pylar-glacier'].includes(selectedPaint.id) ? 'scale-[1.18]' : '')} />
                    : <div className="h-full w-full" style={{ background: selectedPaint.colorHex }} />}
                </div>
                <p className="mt-2 text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">{config.paintDetail.productVisual}</p>
                <p className="mt-1 text-[10px] leading-4 text-muted-foreground">{selectedPaint.manufacturerImageCredit || config.paintDetail.noProductVisual}</p>
                <div className="mt-5">
                  <p className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">{selectedPaint.resultImage ? config.paintDetail.appliedResult : config.paintDetail.digitalPreview}</p>
                  {selectedPaint.resultImage
                    ? <figure className="mt-2 overflow-hidden rounded-2xl border bg-white"><img src={selectedPaint.resultImage} alt={`${config.paintDetail.appliedResult} ${selectedPaint.name}`} className="aspect-[4/3] w-full object-cover" /><figcaption className="p-2 text-[10px] text-muted-foreground">{selectedPaint.resultImageSource ? <a href={selectedPaint.resultImageSource} target="_blank" rel="noreferrer" className="font-semibold text-primary">{selectedPaint.resultImageCredit}</a> : selectedPaint.resultImageCredit}{selectedPaint.resultImageLicense ? ` · ${selectedPaint.resultImageLicense}` : ''}</figcaption></figure>
                    : <div className="mt-2 rounded-2xl border p-3"><div className="h-16 rounded-xl border" style={{ background: selectedPaint.colorHex }} /><p className="mt-2 text-[10px] leading-4 text-muted-foreground">{config.paintDetail.digitalPreviewHelp}</p></div>}
                  {selectedPaint.resultReferenceUrl && <a href={selectedPaint.resultReferenceUrl} target="_blank" rel="noreferrer" className="mt-2 inline-flex items-center gap-1 text-[11px] font-semibold text-primary"><ExternalLink size={12} />{config.paintDetail.realResultSource}</a>}
                </div>
              </div>
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="rounded-full bg-primary/8 px-3 py-1 text-xs font-semibold text-primary">{selectedPaint.brand} · {selectedPaint.range}</span>
                  {selectedPaint.reference && <span className="rounded-full bg-secondary px-3 py-1 text-xs font-semibold">{config.paintPage.referencePrefix} {selectedPaint.reference}</span>}
                  {selectedPaint.volumeMl > 0 && <span className="rounded-full bg-secondary px-3 py-1 text-xs font-semibold">{selectedPaint.volumeMl} ml</span>}
                </div>
                <div className="mt-5 flex items-center gap-3 rounded-2xl border bg-card p-4">
                  <span className="size-10 flex-none rounded-xl border" style={{ background: selectedPaint.colorHex }} />
                  <div><p className="text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">{config.paintDetail.colorFamily}</p><p className="mt-0.5 text-sm font-semibold">{selectedPaint.colorFamily || config.paintDetail.toQualify}</p></div>
                </div>
                <section className="mt-5">
                  <h3 className="text-xs font-semibold uppercase tracking-[0.1em] text-muted-foreground">{config.paintDetail.manufacturerFeatures}</h3>
                  <p className="mt-2 text-sm leading-6">{selectedPaint.manufacturerDescription || config.paintDetail.noManufacturerDescription}</p>
                </section>
                <section className="mt-5">
                  <h3 className="text-xs font-semibold uppercase tracking-[0.1em] text-muted-foreground">{config.paintDetail.recommendedUses}</h3>
                  <div className="mt-2 flex flex-wrap gap-2">
                    {selectedPaint.recommendedUses.map((use) => <span key={use} className="rounded-full border bg-card px-3 py-1.5 text-xs font-medium">{use}</span>)}
                  </div>
                </section>
                <div className="mt-6 flex flex-col gap-2 sm:flex-row sm:items-center">
                  {!selectedPaint.id.startsWith('session-') && <a href={`/paints/${selectedPaint.id}`} className="inline-flex h-11 items-center justify-center gap-2 rounded-xl border bg-card px-4 text-sm font-semibold"><BookOpen size={15} />{config.paintDetail.openSheet}</a>}
                  {selectedPaint.manufacturerUrl && <a href={selectedPaint.manufacturerUrl} target="_blank" rel="noreferrer" className="inline-flex h-11 items-center justify-center gap-2 rounded-xl bg-primary px-4 text-sm font-semibold text-primary-foreground"><ExternalLink size={15} />{config.paintDetail.openManufacturerSheet}</a>}
                  <span className="text-[10px] text-muted-foreground">{config.paintDetail.verifiedOn} {selectedPaint.manufacturerVerifiedAt}</span>
                </div>
              </div>
            </div>
          </div>
        </dialog>
      )}

      {modalOpen && (
        <div role="presentation" className="fixed inset-0 z-50 grid place-items-end bg-black/35 p-0 backdrop-blur-sm sm:place-items-center sm:p-5" onMouseDown={(event) => event.target === event.currentTarget && closeModal()}>
          <div className="max-h-[94vh] w-full overflow-y-auto rounded-t-[28px] border bg-background shadow-2xl sm:max-w-3xl sm:rounded-[28px]">
            <div className="sticky top-0 z-10 flex items-center justify-between border-b bg-background/95 px-5 py-4 backdrop-blur-sm">
              <div><p className="eyebrow">{config.imports.newPaint}</p><h2 className="mt-0.5 text-lg font-semibold">{config.header.addByPhoto}</h2></div>
              <button onClick={closeModal} className="grid size-9 place-items-center rounded-xl border bg-card" aria-label={config.paintDetail.close}><X size={17} /></button>
            </div>
            <form onSubmit={savePaint} className="grid gap-6 p-5 sm:grid-cols-[240px_minmax(0,1fr)] sm:p-6">
              <div>
                <input ref={fileInput} className="sr-only" type="file" accept="image/*,.heic,.heif" onChange={(event) => choosePhoto(event.target.files?.[0] ?? null)} />
                <button type="button" onClick={() => fileInput.current?.click()} className="relative flex aspect-[4/5] w-full flex-col items-center justify-center overflow-hidden rounded-[22px] border-2 border-dashed bg-card text-center">
                  {photoUrl ? <img src={photoUrl} alt={config.imports.choosePhoto} className="absolute inset-0 h-full w-full object-cover" /> : <><FileImage size={30} className="text-primary" /><strong className="mt-3 text-sm">{config.imports.choosePhoto}</strong><span className="mt-1 px-5 text-xs leading-5 text-muted-foreground">{config.imports.labelPhotoHelp}</span></>}
                  {photoUrl && <span className="absolute bottom-3 rounded-full bg-black/55 px-3 py-1.5 text-xs font-semibold text-white backdrop-blur">{config.imports.changePhoto}</span>}
                </button>
                <div className="mt-3 flex items-start gap-2 rounded-xl bg-secondary p-3 text-[11px] leading-4 text-muted-foreground"><CircleAlert className="mt-0.5 size-3.5 flex-none" /><span>{config.imports.previewNotice}</span></div>
              </div>
              <div className="grid content-start gap-4">
                <div className="grid gap-4 sm:grid-cols-2">
                  <label className="form-field"><span>{config.imports.brand} *</span><input required value={form.brand} onChange={(event) => setForm({ ...form, brand: event.target.value })} placeholder={config.imports.brandPlaceholder} /></label>
                  <label className="form-field"><span>{config.imports.range}</span><input value={form.range} onChange={(event) => setForm({ ...form, range: event.target.value })} placeholder={config.imports.rangePlaceholder} /></label>
                </div>
                <label className="form-field"><span>{config.imports.paintName} *</span><input required value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} placeholder={config.imports.paintNamePlaceholder} /></label>
                <div className="grid gap-4 sm:grid-cols-2">
                  <label className="form-field"><span>{config.imports.reference}</span><input value={form.reference} onChange={(event) => setForm({ ...form, reference: event.target.value })} placeholder={config.imports.referencePlaceholder} /></label>
                  <label className="form-field"><span>{config.imports.finish}</span><select value={form.finish} onChange={(event) => setForm({ ...form, finish: event.target.value })}>{config.imports.finishOptions.map((option) => <option key={option}>{option}</option>)}</select></label>
                </div>
                <div className="grid gap-4 sm:grid-cols-2">
                  <label className="form-field"><span>{config.imports.approximateColor}</span><span className="flex h-11 items-center gap-2 rounded-xl border bg-card px-2"><input className="size-8 border-0 p-0" type="color" value={form.colorHex} onChange={(event) => setForm({ ...form, colorHex: event.target.value })} /><code className="text-xs">{form.colorHex}</code></span></label>
                  <label className="form-field"><span>{config.imports.quantity}</span><input type="number" min="1" max="20" value={form.quantity} onChange={(event) => setForm({ ...form, quantity: Number(event.target.value) })} /></label>
                </div>
                <div className="mt-2 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
                  <button type="button" onClick={closeModal} className="h-11 rounded-xl border bg-card px-4 text-sm font-semibold">{config.imports.cancel}</button>
                  <button disabled={saving} className="inline-flex h-11 items-center justify-center gap-2 rounded-xl bg-primary px-5 text-sm font-semibold text-primary-foreground disabled:opacity-50">{saving ? config.imports.saving : <><Check size={16} />{config.imports.validate}</>}</button>
                </div>
              </div>
            </form>
          </div>
        </div>
      )}
    </main>
  );
}
