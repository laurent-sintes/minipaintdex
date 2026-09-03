import { AppNotice } from './app-notice';
import { apiFetch, errorDetail } from '@/utils/api-errors';
import { useEffect, useId, useRef, useState } from 'react';
import { Search } from 'lucide-react';
import type { PaintProductSuggestion } from '@/models/paint-model';
import type { SiteConfig } from '@/models/site-config-model';
import type { PaintFilters } from '@/utils/paint-search';
import { nextSuggestionIndex } from '@/utils/paint-suggestions';

import { paintSearchRequest } from '@/utils/paint-search-request';

type Props = {
  query: string; setQuery: (value: string) => void; filters: PaintFilters;
  collection: string; revision: number; config: SiteConfig;
  onSelect: (suggestion: PaintProductSuggestion) => void;
};

/* oxlint-disable jsx-a11y/prefer-tag-over-role -- An editable ARIA combobox needs a separate rich listbox; native select/datalist cannot render these image/name/range candidates. */
export function PaintSearchCombobox({ query, setQuery, filters, collection, revision, config, onSelect }: Props) {
  const id = useId();
  const list = useRef<HTMLDivElement>(null);
  const [focused, setFocused] = useState(false);
  const [dismissed, setDismissed] = useState('');
  const [active, setActive] = useState({ key: '', index: -1 });
  const [response, setResponse] = useState<{ key: string; suggestions: PaintProductSuggestion[]; failed: string } | null>(null);
  const request = paintSearchRequest(collection, query, filters, { include: ['suggestions'] });
  const url = request.url;
  const body = JSON.stringify(request.body);
  const requestKey = `${revision}:${url}:${body}`;
  const open = focused && Boolean(query.trim()) && dismissed !== requestKey;
  const current = response?.key === requestKey ? response : null;
  const suggestions = current?.suggestions ?? [];
  const activeIndex = active.key === requestKey && active.index < suggestions.length ? active.index : -1;

  useEffect(() => {
    if (!open) return;
    const controller = new AbortController();
    const timer = window.setTimeout(() => {
      apiFetch(url, { method: 'POST', body, signal: controller.signal, headers: { accept: 'application/json', 'content-type': 'application/json' } })
        .then(response => { return response.json() as Promise<{ suggestions: PaintProductSuggestion[] }>; })
        .then(result => { if (!controller.signal.aborted) setResponse({ key: requestKey, suggestions: result.suggestions, failed: '' }); })
        .catch(reason => { if (!controller.signal.aborted) setResponse({ key: requestKey, suggestions: [], failed: errorDetail(reason) }); });
    }, 180);
    return () => { window.clearTimeout(timer); controller.abort(); };
  }, [open, requestKey, url, body]);

  useEffect(() => {
    if (open && activeIndex >= 0) list.current?.children[activeIndex]?.scrollIntoView({ block: 'nearest' });
  }, [open, activeIndex]);

  function select(suggestion: PaintProductSuggestion) {
    setDismissed(requestKey);
    setActive({ key: requestKey, index: -1 });
    onSelect(suggestion);
  }

  return <div className="paint-combobox">
    <Search size={18} className="paint-combobox-icon" aria-hidden="true" />
    <input className="paint-search-input" role="combobox" aria-autocomplete="list" aria-haspopup="listbox"
      aria-expanded={open} aria-controls={open ? `${id}-list` : undefined}
      aria-activedescendant={open && activeIndex >= 0 ? `${id}-option-${activeIndex}` : undefined}
      aria-describedby={open ? `${id}-hint` : undefined} autoComplete="off"
      value={query} placeholder={config.header.searchPlaceholder} aria-label={config.header.searchAriaLabel}
      onChange={event => { setDismissed(''); setActive({ key: '', index: -1 }); setQuery(event.target.value); }}
      onFocus={() => { setFocused(true); setDismissed(''); }} onBlur={() => setFocused(false)}
      onKeyDown={event => {
        if (event.nativeEvent.isComposing) return;
        if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
          event.preventDefault(); setDismissed('');
          setActive({ key: requestKey, index: nextSuggestionIndex(activeIndex, suggestions.length, event.key === 'ArrowDown' ? 1 : -1) });
        } else if (event.key === 'Escape') {
          if (open) event.preventDefault();
          setDismissed(requestKey); setActive({ key: '', index: -1 });
        } else if (event.key === 'Enter') {
          if (open && activeIndex >= 0) { event.preventDefault(); select(suggestions[activeIndex]); }
          else setDismissed(requestKey);
        }
      }} />
    {open && <div className="paint-suggestions-panel">
      <div ref={list} id={`${id}-list`} role="listbox" aria-label={config.collection.suggestions} aria-busy={!current}
        className="paint-suggestions-list">
        {suggestions.map((suggestion, index) => <button type="button" role="option" tabIndex={-1}
          id={`${id}-option-${index}`} key={suggestion.paintProductId} aria-selected={activeIndex === index}
          className="paint-suggestion" onPointerDown={event => event.preventDefault()}
          onClick={() => select(suggestion)} onMouseEnter={() => setActive({ key: requestKey, index })}>
          <span className="paint-suggestion-image" aria-hidden="true"
            style={/^#[0-9a-f]{6}$/i.test(suggestion.colorHex) ? { borderBottomColor: suggestion.colorHex } : undefined}>
            {suggestion.manufacturerImage && <img src={suggestion.manufacturerImage} alt="" loading="lazy"
              onError={event => { event.currentTarget.style.visibility = 'hidden'; }} />}
          </span>
          <span className="paint-suggestion-text"><strong>{suggestion.name}</strong>
            <span>{suggestion.brand} · {suggestion.range}</span><small>{suggestion.reference}</small></span>
        </button>)}
      </div>
      {current?.failed ? <AppNotice notice={{ message: config.collection.suggestionsFailed, detail: current.failed }} />
        : <output className="paint-suggestions-status">{!current ? config.errors.loading
          : !suggestions.length ? config.collection.noSuggestions : `${suggestions.length} ${config.collection.suggestions.toLowerCase()}`}</output>}
      <p id={`${id}-hint`} className="paint-suggestions-hint">{config.collection.suggestionsHint}</p>
    </div>}
  </div>;
}
