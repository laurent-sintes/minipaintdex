import type { PaintFilters } from './paint-search.ts';

export type PaintSearchPart = 'results' | 'suggestions';
export type PaintSearchResponse<T, S> = {
  results: { content: T[]; totalElements: number; page: number; size: number } | null;
  suggestions: S[] | null; correlationId: string;
};

export function paintSearchRequest(collection: string, query: string, filters: PaintFilters,
  options: { include?: PaintSearchPart[]; page?: number; size?: number; sort?: string; suggestionLimit?: number } = {}) {
  const params = new URLSearchParams();
  if (options.page !== undefined) params.set('page', String(options.page));
  if (options.size !== undefined) params.set('size', String(options.size));
  if (options.sort) params.set('sort', options.sort);
  const selections: Record<string, string[] | boolean> = {};
  for (const [key, values] of Object.entries(filters)) {
    if (key === 'manufacturerSheetOnly' || key === 'realResultOnly') selections[key] = values.includes('true');
    else selections[key] = [...values];
  }
  return {
    url: `${collection}/search${params.size ? '?' + params : ''}`,
    body: { query: query.trim(), filters: selections, include: options.include ?? ['results'],
      ...(options.suggestionLimit !== undefined ? { suggestionLimit: options.suggestionLimit } : {}) },
  };
}
