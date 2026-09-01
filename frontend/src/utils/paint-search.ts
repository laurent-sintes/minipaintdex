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
  query: string, filters: Record<string, string>, sort: string, page: number, size: number,
) {
  const params = paintFacetSearchParams(query, filters);
  params.set('page', String(page));
  params.set('size', String(size));
  if (sort) params.set('sort', sort);
  return params;
}

export function paintFacetSearchParams(query: string, filters: Record<string, string>) {
  const params = new URLSearchParams();
  if (query.trim()) params.set('query', query.trim());
  Object.entries(filters).forEach(([key, value]) => { if (value) params.set(key, value); });
  return params;
}
