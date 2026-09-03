export function normalizeSearch(value: string) {
  return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLocaleLowerCase('fr').trim();
}

export function formatMetadata(value: string) {
  if (!value) return value;
  const formatted = value.replace(/[_-]+/g, ' ');
  return formatted.charAt(0).toLocaleUpperCase('fr') + formatted.slice(1);
}

export function metadataOptions(values: string[]) {
  const counts = new Map<string, { value: string; count: number }>();
  values.filter(Boolean).forEach((value) => {
    const key = normalizeSearch(value);
    const current = counts.get(key);
    counts.set(key, { value: current?.value ?? value, count: (current?.count ?? 0) + 1 });
  });
  return Array.from(counts.values())
    .sort((left, right) => formatMetadata(left.value).localeCompare(formatMetadata(right.value), 'fr'));
}

export function sameMetadata(left: string, right: string) {
  return normalizeSearch(left) === normalizeSearch(right);
}

export function paintPageSearchParams(
  query: string, filters: PaintFilters, sort: string, page: number, size: number,
) {
  const params = paintFacetSearchParams(query, filters);
  params.set('page', String(page));
  params.set('size', String(size));
  if (sort) params.set('sort', sort);
  return params;
}

export function paintFacetSearchParams(query: string, filters: PaintFilters) {
  const params = new URLSearchParams();
  if (query.trim()) params.set('query', query.trim());
  Object.entries(filters).forEach(([key, values]) => values.forEach(value => { if (value) params.append(key, value); }));
  return params;
}

export type PaintFilters = Record<string, string[]>;
export type PaintSearchState = { query: string; filters: PaintFilters; sort: string; page: number };

export function togglePaintFilter(filters: PaintFilters, key: string, value: string): PaintFilters {
  const selected = filters[key] ?? [];
  const next = selected.includes(value) ? selected.filter(entry => entry !== value) : [...selected, value];
  const result = { ...filters, [key]: next };
  if (!next.length) delete result[key];
  return result;
}

export function readPaintSearch(search: string, keys: string[], sorts: string[]): PaintSearchState {
  const params = new URLSearchParams(search);
  const page = Number(params.get('page') ?? 0);
  const filters = Object.fromEntries(keys.map(key => [key, [...new Set(params.getAll(key).filter(Boolean))]])
    .filter(([, values]) => values.length > 0));
  return {
    query: params.get('query') ?? '', filters,
    sort: sorts.includes(params.get('sort') ?? '') ? params.get('sort')! : sorts[0] ?? 'name,asc',
    page: Number.isSafeInteger(page) && page >= 0 && page <= 100000 ? page : 0,
  };
}

export function paintBrowserSearchParams(state: PaintSearchState) {
  const params = paintFacetSearchParams(state.query, state.filters);
  if (state.sort !== 'relevance,desc') params.set('sort', state.sort);
  if (state.page > 0) params.set('page', String(state.page));
  return params;
}
