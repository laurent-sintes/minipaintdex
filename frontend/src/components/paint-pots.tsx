import { AppNotice } from './app-notice';
import { PaintPotPhotoUpload } from './paint-pot-photo-upload';
import { apiFetch, failureNotice } from '@/utils/api-errors';
import type { Notice } from '@/utils/api-errors';
import { useEffect, useState } from 'react';
import type { PaintPot } from '@/models/paint-pot-model';
import type { PaintProduct } from '@/models/paint-model';
import type { SiteConfig } from '@/models/site-config-model';
import type { AppRoute } from '@/utils/app-routing';

const button = 'rounded-xl border px-4 py-2 text-sm font-semibold disabled:opacity-40';
const field = 'mt-1 block w-full rounded-xl border bg-card p-3';
type Props = { route: AppRoute; config: SiteConfig; navigate: (route: AppRoute) => void; revision: number };
type Page = { pots: PaintPot[]; total: number; page: number; totalPages: number };

async function json<T>(url: string, signal?: AbortSignal): Promise<T> {
  const response = await apiFetch(url, { signal, headers: { accept: 'application/json' } });
  return response.json() as Promise<T>;
}

function PotImage({ pot, config }: { pot: PaintPot; config: SiteConfig }) {
  const personal = pot.photos.at(-1)?.url;
  const catalog = pot.paintProduct.manufacturerImage || pot.paintProduct.manufacturerImageSource;
  const [failed, setFailed] = useState<string[]>([]);
  const source = [personal, catalog].find(value => value && !failed.includes(value));
  return <figure>
    <div className="grid aspect-square place-items-center overflow-hidden rounded-2xl border bg-white">
      {source ? <img src={source} alt={pot.paintProduct.name} className="h-full w-full object-contain" onError={() => setFailed(current => [...current, source])} /> : <span className="text-xs text-muted-foreground">{config.paintPots.noPhoto}</span>}
    </div>
    <figcaption className="mt-2 text-xs text-muted-foreground">{!source ? config.paintPots.noPhoto : source === personal ? config.paintPots.personalPhoto : config.paintPots.catalogPhoto}</figcaption>
  </figure>;
}

export function PaintPotsPage({ route, config, navigate, revision }: Props) {
  const labels = config.paintPots;
  const [page, setPage] = useState(0);
  const [newPotId] = useState(() => 'pot-' + crypto.randomUUID());
  const [includeRemoved, setIncludeRemoved] = useState(false);
  const [result, setResult] = useState<Page | null>(null);
  const [pot, setPot] = useState<PaintPot | null>(null);
  const [product, setProduct] = useState<PaintProduct | null>(null);
  const [refresh, setRefresh] = useState(0);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<Notice>('');
  const [pending, setPending] = useState<{ url: string; after?: () => void } | null>(null);
  const [condition, setCondition] = useState('unknown');
  const [remainingLevel, setRemainingLevel] = useState('unknown');
  const [possession, setPossession] = useState('owned');
  const [note, setNote] = useState('');

  useEffect(() => {
    const controller = new AbortController();
    if (route.paintPotId) {
      json<PaintPot>('/api/v1/workshop/paint-pots/' + encodeURIComponent(route.paintPotId), controller.signal)
        .then(value => { setPot(value); setCondition(value.condition); setRemainingLevel(value.remainingLevel); setPossession(value.possession); })
        .catch(error => { if (error.name !== 'AbortError') setNotice(failureNotice(config.errors.requestFailed, error)); });
    } else {
      const query = new URLSearchParams({ page: String(page), size: '24', includeRemoved: String(includeRemoved) });
      if (route.paintProductId) query.set('paintProductId', route.paintProductId);
      json<Page>('/api/v1/workshop/paint-pots?' + query, controller.signal).then(setResult)
        .catch(error => { if (error.name !== 'AbortError') setNotice(failureNotice(config.errors.requestFailed, error)); });
      if (route.paintProductId) json<PaintProduct>('/api/v1/market/paint-products/' + encodeURIComponent(route.paintProductId), controller.signal).then(setProduct)
        .catch(error => { if (error.name !== 'AbortError') setNotice(failureNotice(config.errors.requestFailed, error)); });
    }
    return () => controller.abort();
  }, [route.paintPotId, route.paintProductId, page, includeRemoved, revision, refresh, config.errors.requestFailed]);

  // Durable acceptance is not completion: keep the action locked until the ledger acknowledges it.
  useEffect(() => {
    if (!pending) return;
    const controller = new AbortController();
    let timer = 0;
    const poll = async () => {
      try {
        const publication = await json<{ status: string; error?: string }>(pending.url, controller.signal);
        if (publication.status.toLowerCase() === 'completed') {
          setPending(null); setBusy(false); setNotice(labels.saved); setRefresh(value => value + 1); pending.after?.();
          return;
        }
        if (publication.status.toLowerCase() === 'failed') {
          setPending(null); setBusy(false); setNotice(failureNotice(labels.failed, publication.error ?? labels.failed)); return;
        }
      } catch (error) {
        if (error instanceof DOMException && error.name === 'AbortError') return;
        setNotice(failureNotice(labels.pending, error));
      }
      if (!controller.signal.aborted) timer = window.setTimeout(poll, 1500);
    };
    void poll();
    return () => { controller.abort(); window.clearTimeout(timer); };
  }, [pending, labels.saved, labels.failed, labels.pending]);

  async function mutate(url: string, payload: object | FormData, after?: () => void) {
    setBusy(true); setNotice('');
    try {
      const form = payload instanceof FormData;
      const response = await apiFetch(url, {
        method: 'POST',
        headers: { 'Idempotency-Key': crypto.randomUUID(), ...(!form ? { 'content-type': 'application/json' } : {}) },
        body: form ? payload : JSON.stringify(payload),
      });
      const body = await response.json() as { publication?: { status?: string }; result?: { publication?: { status?: string } } };
      const completed = (body.publication ?? body.result?.publication)?.status?.toLowerCase() === 'completed';
      const location = response.headers.get('location');
      if (response.status === 202 && location && !completed) { setNotice(labels.pending); setPending({ url: location, after }); }
      else { setBusy(false); setNotice(labels.saved); setRefresh(value => value + 1); after?.(); }
    } catch (error) { setBusy(false); setNotice(failureNotice(labels.failed, error)); }
  }

  const labelFor = (values: Record<string, string>, value: string) => values[value.replace(/-([a-z])/g, (_, letter: string) => letter.toUpperCase())] ?? value;
  const displayDate = (value: string | null) => value ? new Date(value).toLocaleDateString() : labels.unknown;
  const options = (values: Record<string, string>) => Object.entries(values).map(([value, label]) => <option key={value} value={value.replace(/[A-Z]/g, letter => '-' + letter.toLowerCase())}>{label}</option>);

  return <section className="mt-6">
    <button className={button} onClick={() => navigate(pot ? { view: 'paintPots', paintProductId: pot.paintProductId } : { view: 'workshopPaints' })}>{pot ? labels.title : labels.back}</button>
    <AppNotice className="my-4" notice={notice} />
    {!route.paintPotId && <>
      <div className="my-5 flex flex-wrap items-center justify-between gap-4">
        <div>{product && <><h2 className="text-xl font-semibold">{product.name}</h2><p className="text-sm text-muted-foreground">{product.brand} · {product.range} · {product.reference}</p></>}
          <label className="mt-3 flex gap-2 text-sm"><input type="checkbox" checked={includeRemoved} onChange={event => { setIncludeRemoved(event.target.checked); setPage(0); }} />{labels.includeRemoved}</label>
        </div>
        {product && <div className="max-w-sm"><button className={button + ' bg-primary text-primary-foreground'} disabled={busy} onClick={() => {
          const paintPotId = newPotId;
          void mutate('/api/v1/workshop/paint-pots', { paintPotId, paintProductId: product.id }, () => navigate({ view: 'paintPot', paintPotId }));
        }}>{labels.add}</button><p className="mt-2 text-xs text-muted-foreground">{labels.addHelp}</p></div>}
      </div>
      {!result ? <p>{config.errors.loading}</p> : <>
        <p className="mb-4 text-sm text-muted-foreground">{result.total} · {labels.title}</p>
        {result.pots.length === 0 && <p className="rounded-2xl border border-dashed p-8">{labels.empty}</p>}
        <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-4">{result.pots.map(value => <button type="button" key={value.paintPotId} className="rounded-2xl border bg-card p-4 text-left" onClick={() => navigate({ view: 'paintPot', paintPotId: value.paintPotId })}>
          <PotImage pot={value} config={config} />
          <h3 className="mt-3 font-semibold">{value.paintProduct.name}</h3>
          <p className="text-xs text-muted-foreground">{value.paintProduct.brand} · {value.paintProduct.range} · {value.paintProduct.reference}</p>
          <p className="mt-2 text-xs">{labelFor(labels.conditions, value.condition)} · {labelFor(labels.levels, value.remainingLevel)}</p>
          <p className="mt-1 text-xs">{labelFor(labels.possessions, value.possession)}</p>
          <p className="mt-2 break-all font-mono text-[10px] text-muted-foreground">{value.paintPotId}</p>
        </button>)}</div>
        <div className="mt-5 flex gap-3"><button className={button} disabled={page === 0} onClick={() => setPage(value => value - 1)}>{labels.previous}</button><span className="p-2">{result.totalPages ? page + 1 : 0} / {result.totalPages}</span><button className={button} disabled={page + 1 >= result.totalPages} onClick={() => setPage(value => value + 1)}>{labels.next}</button></div>
      </>}
    </>}
    {route.paintPotId && !pot && <p className="mt-5">{config.errors.loading}</p>}
    {pot && <div className="mt-5 grid gap-7 md:grid-cols-[minmax(200px,320px)_1fr]">
      <aside><PotImage key={pot.photos.at(-1)?.url ?? 'catalog'} pot={pot} config={config} /><p className="mt-3 break-all font-mono text-xs text-muted-foreground">{pot.paintPotId}</p></aside>
      <div>
        <h2 className="text-2xl font-semibold">{pot.paintProduct.name}</h2>
        <p className="mt-1 text-sm text-muted-foreground">{pot.paintProduct.brand} · {pot.paintProduct.range} · {pot.paintProduct.reference}</p>
        <p className="mt-4 text-sm">{pot.available ? labels.available : labels.unavailable}</p>
        <p className="mt-2 text-xs">{labels.acquired} : {displayDate(pot.acquiredAt)} · {labels.opened} : {displayDate(pot.openedAt)}</p>
        {pot._links?.open && <button disabled={busy} className={button + ' mt-3'} onClick={() => void mutate(pot._links!.open.href, {})}>{labels.open}</button>}
        {pot._links?.observe && <form className="mt-6 rounded-2xl border bg-card p-4" onSubmit={event => { event.preventDefault(); void mutate(pot._links!.observe.href, { condition, remainingLevel }); }}>
          <div className="grid gap-4 sm:grid-cols-2"><label className="text-sm">{labels.condition}<select className={field} value={condition} onChange={event => setCondition(event.target.value)}>{options(labels.conditions)}</select></label><label className="text-sm">{labels.remaining}<select className={field} value={remainingLevel} onChange={event => setRemainingLevel(event.target.value)}>{options(labels.levels)}</select></label></div>
          <p className="my-3 text-xs text-muted-foreground">{labels.observationHelp}</p><button disabled={busy} className={button}>{labels.save}</button>
        </form>}
        <form className="mt-5 flex flex-wrap items-end gap-3" onSubmit={event => { event.preventDefault(); void mutate(pot._links!['change-possession'].href, { possession }); }}>
          <label className="text-sm">{labels.possession}<select className={field} value={possession} onChange={event => setPossession(event.target.value)}>{options(labels.possessions)}</select></label><button disabled={busy || possession === pot.possession} className={button}>{labels.save}</button>
        </form>
        <section className="mt-7"><h3 className="font-semibold">{labels.notes}</h3>
          {pot.notes.map((entry, index) => <blockquote key={index} className="mt-3 rounded-xl bg-secondary p-3 text-sm"><p className="whitespace-pre-wrap">{entry.text}</p><time className="mt-1 block text-xs text-muted-foreground">{displayDate(entry.addedAt)}</time></blockquote>)}
          <form onSubmit={event => { event.preventDefault(); void mutate(pot._links!['add-note'].href, { note }, () => setNote('')); }}><textarea aria-label={labels.notes} required value={note} onChange={event => setNote(event.target.value)} className={field + ' mt-3'} /><button disabled={busy || !note.trim()} className={button + ' mt-2'}>{labels.addNote}</button></form>
        </section>
        <section className="mt-7"><h3 className="font-semibold">{labels.photos}</h3>
          <div className="mt-3 grid gap-3 sm:grid-cols-3">{pot.photos.map(entry => <figure key={entry.mediaId}><a href={entry.url} target="_blank" rel="noreferrer"><img src={entry.url} alt={entry.caption || labels.personalPhoto} className="aspect-square w-full rounded-xl border object-contain" /></a><figcaption className="mt-1 text-xs">{entry.caption} · {displayDate(entry.addedAt)}{entry.processingMethod && <a href={entry.originalUrl} target="_blank" rel="noreferrer" className="mt-1 block underline">{labels.originalPhoto}</a>}</figcaption></figure>)}</div>
          <PaintPotPhotoUpload key={pot.paintPotId} paintPotId={pot.paintPotId} config={config} onSaved={() => setRefresh(value => value + 1)} />
        </section>
      </div>
    </div>}
  </section>;
}
