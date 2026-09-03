import { useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { ChevronLeft, ChevronRight, ListFilter, X } from 'lucide-react';
import type { PaintCardModel, PaintProductSuggestion, PaintFacetValue, PaintModelSchema } from '@/models/paint-model';
import type { SiteConfig } from '@/models/site-config-model';
import type { PaintFilters } from '@/utils/paint-search';
import { togglePaintFilter } from '@/utils/paint-search';
import { configuredLabel, metadataLabel } from '@/utils/site-labels';

import { PaintSearchCombobox } from './paint-search-combobox';

export type FilterOptions = Record<string, PaintFacetValue[]>;

type Props = {
  collection: string; revision: number; onSelectSuggestion: (suggestion: PaintProductSuggestion) => void;
  paints: PaintCardModel[]; resultCount: number; offset: number; pageSize: number;
  filters: PaintFilters; setFilters: (filters: PaintFilters) => void;
  filterOptions: FilterOptions; paintModel: PaintModelSchema;
  query: string; setQuery: (value: string) => void;
  sort: string; setSort: (value: string) => void; loading: boolean;
  clearFilters: () => void; config: SiteConfig; onPage: (offset: number) => void;
  renderPaint: (paint: PaintCardModel) => ReactNode;
};

function FilterCheckbox({ label, checked, mixed = false, count, onChange }: {
  label: string; checked: boolean; mixed?: boolean; count?: number; onChange: () => void;
}) {
  const input = useRef<HTMLInputElement>(null);
  useEffect(() => { if (input.current) input.current.indeterminate = mixed; }, [mixed]);
  return <label className="facet-option">
    <input ref={input} type="checkbox" checked={checked} disabled={count === 0 && !checked && !mixed}
      onChange={onChange} aria-checked={mixed ? 'mixed' : checked} />
    <span>{label}</span>{count !== undefined && <small>{count}</small>}
  </label>;
}

function FacetSection({ label, values, selected, onToggle, config, defaultOpen = true }: {
  label: string; values: PaintFacetValue[]; selected: string[]; onToggle: (value: string) => void;
  config: SiteConfig; defaultOpen?: boolean;
}) {
  const [expanded, setExpanded] = useState(false);
  const options = [...values, ...selected.filter(value => !values.some(option => option.value === value))
    .map(value => ({ value, label: value, parentValue: null, count: 0 }))];
  const visible = expanded ? options : options.filter((value, index) => index < 7 || selected.includes(value.value));
  return <details className="facet-section" open={defaultOpen}>
    <summary>{label}{selected.length > 0 && <span className="facet-selected-count">{selected.length}</span>}</summary>
    <div className="mt-2">{visible.map(option => <FilterCheckbox key={option.value}
      label={metadataLabel(config, option.label)} count={option.count}
      checked={selected.includes(option.value)} onChange={() => onToggle(option.value)} />)}</div>
    {options.length > 7 && <button type="button" className="facet-more" onClick={() => setExpanded(!expanded)}>
      {expanded ? config.collection.showLess : config.collection.showMore}
    </button>}
  </details>;
}

function CatalogFacet({ filters, options, setFilters, config }: {
  filters: PaintFilters; options: FilterOptions; setFilters: (filters: PaintFilters) => void; config: SiteConfig;
}) {
  const brands = options.brands ?? [];
  const ranges = options.ranges ?? [];
  function toggleBrand(brand: string) {
    const next = togglePaintFilter(filters, 'brand', brand);
    next.range = (next.range ?? []).filter(value => !ranges.some(range => range.value === value && range.parentValue === brand));
    setFilters(next);
  }
  function toggleRange(brand: string, range: string) {
    if ((filters.brand ?? []).includes(brand)) {
      setFilters({
        ...filters,
        brand: filters.brand.filter(value => value !== brand),
        range: [...(filters.range ?? []).filter(value => !ranges.some(option => option.parentValue === brand && option.value === value)),
          ...ranges.filter(option => option.parentValue === brand && option.value !== range).map(option => option.value)],
      });
    } else setFilters(togglePaintFilter(filters, 'range', range));
  }
  return <section className="facet-section">
    <h3 className="font-semibold text-sm">{config.collection.brandRangeFilter}</h3>
    <p className="mt-1 mb-3 text-xs leading-5 text-muted-foreground">{config.collection.catalogFilterHint}</p>
    {brands.map(brand => {
      const children = ranges.filter(range => range.parentValue === brand.value);
      const all = (filters.brand ?? []).includes(brand.value);
      const some = children.some(range => (filters.range ?? []).includes(range.value));
      return <div key={brand.value} className="catalog-brand">
        <FilterCheckbox label={brand.label} count={brand.count} checked={all} mixed={!all && some}
          onChange={() => toggleBrand(brand.value)} />
        <details open={some || all}>
          <summary className="catalog-ranges-toggle">{config.collection.rangeFilter} <span>{children.length}</span></summary>
          <div className="catalog-ranges">{children.map(range => <FilterCheckbox key={range.value}
            label={range.label} count={range.count} checked={all || (filters.range ?? []).includes(range.value)}
            onChange={() => toggleRange(brand.value, range.value)} />)}</div>
        </details>
      </div>;
    })}
  </section>;
}

export function PaintBrowser(props: Props) {
  const { paints, resultCount, offset, pageSize, filters, setFilters, filterOptions, paintModel,
    query, setQuery, sort, setSort, loading, clearFilters, config, onPage, renderPaint } = props;
  const [filtersOpen, setFiltersOpen] = useState(false);
  const drawer = useRef<HTMLDialogElement>(null);
  const opener = useRef<HTMLButtonElement>(null);
  const activeCount = Object.values(filters).reduce((sum, values) => sum + values.length, 0);
  const published = [...paintModel['x-filters']].sort((a, b) => a.order - b.order);
  const primary = published.filter(filter => filter.group === 'primary');
  const advanced = published.filter(filter => filter.group === 'advanced');
  const sorts = [...paintModel['x-sort-options']].sort((a, b) => a.order - b.order);
  useEffect(() => {
    if (filtersOpen) {
      const dialog = drawer.current;
      const trigger = opener.current;
      dialog?.showModal();
      const previous = document.body.style.overflow;
      document.body.style.overflow = 'hidden';
      return () => { document.body.style.overflow = previous; dialog?.close(); trigger?.focus(); };
    }
  }, [filtersOpen]);
  useEffect(() => {
    const media = window.matchMedia('(min-width: 1024px)');
    const closeOnDesktop = () => { if (media.matches) setFiltersOpen(false); };
    media.addEventListener('change', closeOnDesktop);
    return () => media.removeEventListener('change', closeOnDesktop);
  }, []);
  const filterSection = (filter: typeof published[number]) => filter.control === 'toggle'
    ? <FilterCheckbox key={filter.id} label={configuredLabel(config, filter.labelKey)}
      checked={(filters[filter.queryParameter] ?? []).includes('true')}
      onChange={() => setFilters(togglePaintFilter(filters, filter.queryParameter, 'true'))} />
    : <FacetSection key={filter.id} label={configuredLabel(config, filter.labelKey)}
      values={filterOptions[filter.facetId ?? ''] ?? []} selected={filters[filter.queryParameter] ?? []}
      onToggle={value => setFilters(togglePaintFilter(filters, filter.queryParameter, value))} config={config} />;
  const panel = <>
    <div className="flex items-center justify-between gap-3 mb-3">
      <h2 className="flex items-center gap-2 font-semibold"><ListFilter size={17} />{config.collection.filters}</h2>
      {(activeCount > 0 || query) && <button className="text-xs font-semibold text-primary" type="button" onClick={clearFilters}>{config.collection.resetFilters}</button>}
    </div>
    <p className="text-xs leading-5 text-muted-foreground mb-4">{config.collection.filterLogicHint}</p>
    <CatalogFacet filters={filters} options={filterOptions} setFilters={setFilters} config={config} />
    {primary.map(filterSection)}
    <details className="facet-advanced" open={advanced.some(filter => (filters[filter.queryParameter]?.length ?? 0) > 0)}>
      <summary>{config.collection.advancedFilters}</summary>{advanced.map(filterSection)}
    </details>
  </>;

  return <div className="paint-browser">
    <aside aria-label={config.collection.filterAriaLabel} className="paint-filters-desktop">{panel}</aside>
    <section className="paint-results" aria-label={config.collection.resultsTitle} aria-busy={loading}>
      <div className="paint-search-bar">
        <PaintSearchCombobox key={props.collection} query={query} setQuery={setQuery} filters={filters} collection={props.collection}
          revision={props.revision} config={config} onSelect={props.onSelectSuggestion} />
        <button ref={opener} type="button" className="filter-drawer-opener" onClick={() => setFiltersOpen(true)} aria-haspopup="dialog">
          <ListFilter size={17} />{config.collection.filters}{activeCount > 0 && <span>{activeCount}</span>}
        </button>
      </div>
      <div className="paint-results-toolbar">
        <output aria-live="polite" className="text-sm">
          {loading ? config.errors.loading : <><strong>{resultCount.toLocaleString('fr-FR')}</strong> {config.collection.resultsTitle.toLowerCase()}</>}
        </output>
        <label className="flex items-center gap-2 text-xs"><span>{config.collection.sort}</span>
          <select className="paint-sort" value={sort} onChange={event => setSort(event.target.value)}>
            {sorts.map(option => <option key={option.id} value={option.queryValue}>{configuredLabel(config, option.labelKey)}</option>)}
          </select>
        </label>
      </div>
      {(activeCount > 0 || query) && <div className="active-paint-filters" aria-label={config.collection.activeFilters}>
        {query && <button type="button" onClick={() => setQuery('')} aria-label={config.collection.removeFilter + ': ' + query}>{query}<X size={13} /></button>}
        {published.flatMap(filter => (filters[filter.queryParameter] ?? []).map(value => {
          const option = filterOptions[filter.facetId ?? '']?.find(option => option.value === value);
          const label = filter.control === 'toggle' ? configuredLabel(config, filter.labelKey)
            : option?.parentValue ? option.parentValue + ' · ' + option.label
            : metadataLabel(config, option?.label ?? value);
          return <button type="button" key={filter.id + value}
            onClick={() => setFilters(togglePaintFilter(filters, filter.queryParameter, value))}
            aria-label={config.collection.removeFilter + ': ' + label}>{label}<X size={13} /></button>;
        }))}
        <button type="button" className="clear-all-filters" onClick={clearFilters}>{config.collection.resetFilters}</button>
      </div>}
      {loading ? <div className="paint-results-loading">{config.errors.loading}</div>
        : paints.length ? <div className="paint-results-grid">{paints.map(renderPaint)}</div>
          : <div className="paint-results-loading"><h3 className="font-semibold">{config.collection.emptyTitle}</h3><p className="mt-2 text-sm">{config.collection.emptyHint}</p></div>}
      {!loading && resultCount > pageSize && <nav className="paint-pagination" aria-label={config.collection.pagination}>
        <button type="button" disabled={offset === 0} onClick={() => onPage(Math.max(0, offset - pageSize))} aria-label={config.collection.previousPage}><ChevronLeft size={18} /></button>
        <span>{offset + 1}–{Math.min(offset + paints.length, resultCount)} / {resultCount}</span>
        <button type="button" disabled={offset + paints.length >= resultCount} onClick={() => onPage(offset + pageSize)} aria-label={config.collection.nextPage}><ChevronRight size={18} /></button>
      </nav>}
    </section>
    <dialog ref={drawer} className="paint-filters-drawer" aria-label={config.collection.filters}
      onCancel={event => { event.preventDefault(); setFiltersOpen(false); }}>
      <div className="drawer-content">
        <div className="flex justify-end"><button type="button" onClick={() => setFiltersOpen(false)} aria-label={config.collection.closeFilters}><X size={20} /></button></div>
        {panel}
        <button type="button" className="drawer-show-results" onClick={() => setFiltersOpen(false)}>
          {config.collection.showResults} {loading ? '…' : '(' + resultCount + ')'}
        </button>
      </div>
    </dialog>
  </div>;
}
