import { useEffect, useRef, useState } from 'react';
import type { SiteConfig } from '@/models/site-config-model';
import type { AppRoute } from '@/utils/app-routing';
import type { CatalogPage, Page, Rack, RackDetail, RackProduct, StorageProposal } from '@/models/rack-model';
import { apiFetch, failureNotice } from '@/utils/api-errors';
import type { Notice } from '@/utils/api-errors';
import { AppNotice } from './app-notice';

const button = 'rounded-xl border px-4 py-2 text-sm font-semibold disabled:opacity-40';
const field = 'mt-1 block w-full rounded-xl border bg-card p-2 text-sm';
const card = 'rounded-2xl border bg-card p-5';
const read = async <T,>(url: string, signal?: AbortSignal): Promise<T> => (await apiFetch(url, { signal })).json() as Promise<T>;
type Props = { route: AppRoute; config: SiteConfig; navigate: (route: AppRoute) => void; revision: number };
type Mutate = (url: string, payload: object) => Promise<void>;

function Acquisition({ product, labels, busy, mutate }: { product: RackProduct; labels: Record<string, string>; busy: boolean; mutate: Mutate }) {
  const [quantity, setQuantity] = useState(1);
  const [location, setLocation] = useState('');
  return <form className="mt-4 space-y-3" onSubmit={event => {
    event.preventDefault(); void mutate('/api/v1/workshop/rack-acquisitions', { rackProductId: product.id, quantity, location });
  }}>
    <label className="block text-sm">{labels.quantity}<input className={field} type="number" min="1" max="100" required value={quantity} onChange={event => setQuantity(Number(event.target.value))} /></label>
    <label className="block text-sm">{labels.location}<input className={field} value={location} onChange={event => setLocation(event.target.value)} /></label>
    <button className={button} disabled={busy}>{labels.addToWorkshop}</button>
  </form>;
}
function Organizer({ rackId, labels, busy, mutate, config }: { rackId?: string; labels: Record<string, string>; busy: boolean; mutate: Mutate; config: SiteConfig }) {
  const [mode, setMode] = useState('brand-range');
  const [allowEstimates, setAllowEstimates] = useState(false);
  const [preserveExisting, setPreserveExisting] = useState(true);
  const [proposal, setProposal] = useState<StorageProposal | null>(null);
  const [loading, setLoading] = useState(false);
  const [notice, setNotice] = useState<Notice>('');
  const previewRequest = useRef<AbortController | null>(null);
  useEffect(() => () => previewRequest.current?.abort(), []);
  async function preview() {
    previewRequest.current?.abort();
    const abort = new AbortController(); previewRequest.current = abort;
    setLoading(true); setProposal(null); setNotice('');
    try {
      const response = await apiFetch('/api/v1/workshop/paint-storage/proposals', { method: 'POST', signal: abort.signal, headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ workshopRackIds: rackId ? [rackId] : [], allOwnedPots: true, paintPotIds: [], mode, allowEstimates, preserveExisting }) });
      setProposal(await response.json() as StorageProposal);
    } catch (error) { if (!abort.signal.aborted) setNotice(failureNotice(config.errors.requestFailed, error)); }
    finally { if (!abort.signal.aborted) setLoading(false); }
  }
  return <section className={card + ' space-y-4'}>
    <h2 className="text-lg font-semibold">{labels.propose}</h2><p className="text-sm">{rackId ? labels.singleRackHelp : labels.allRacksHelp}</p>
    <AppNotice notice={notice} />
    <label className="block text-sm">{labels.mode}<select className={field} disabled={loading} value={mode} onChange={event => { setMode(event.target.value); setProposal(null); }}>
      <option value="brand-range">{labels.brandRange}</option><option value="color">{labels.color}</option><option value="usage">{labels.usage}</option>
    </select></label>
    <label className="flex gap-2 text-sm"><input type="checkbox" disabled={loading} checked={allowEstimates} onChange={event => { setAllowEstimates(event.target.checked); setProposal(null); }} />{labels.allowEstimates}</label>
    <label className="flex gap-2 text-sm"><input type="checkbox" disabled={loading} checked={preserveExisting} onChange={event => { setPreserveExisting(event.target.checked); setProposal(null); }} />{labels.preserveExisting}</label>
    <button className={button} disabled={busy || loading} onClick={() => void preview()}>{loading ? labels.loading : labels.propose}</button>
    {proposal && <div className="space-y-3 rounded-xl bg-secondary p-4"><h3 className="font-semibold">{labels.preview}</h3>
      <p>{labels.changed} : {proposal.arrangement.movedCount} · {labels.unplaced} : {proposal.arrangement.unplaced.length}</p>
      {proposal.arrangement.displacedCount > 0 && <p className="font-semibold text-destructive">{labels.displaced} : {proposal.arrangement.displacedCount}</p>}
      <ol className="max-h-80 overflow-auto text-sm">{proposal.arrangement.placements.filter(value => !rackId || value.workshopRackId === rackId).map(value => <li className="border-b py-2 break-words" key={value.paintPotId}>
        {value.workshopRackId} / {value.rackRowId} · {proposal.pots.find(pot => pot.paintPotId === value.paintPotId)?.name ?? value.paintPotId} {value.locked ? '· ' + labels.locked : ''}
      </li>)}</ol>
      {proposal.arrangement.unplaced.length > 0 && <details><summary>{labels.unplaced}</summary><ul className="max-h-60 overflow-auto text-sm">{proposal.arrangement.unplaced.map(value => <li key={value.paintPotId}>
        {proposal.pots.find(pot => pot.paintPotId === value.paintPotId)?.name ?? value.paintPotId} — {value.reason === 'insufficient-capacity' ? labels.fullReason : labels.unknownReason}
      </li>)}</ul></details>}
      <button className={button} disabled={busy || loading} onClick={() => void mutate('/api/v1/workshop/paint-storage/confirmations', { snapshotToken: proposal.snapshotToken, placements: proposal.arrangement.placements, allowEstimates })}>{labels.confirm}</button>
    </div>}
  </section>;
}
export function RacksPage({ route, config, navigate, revision }: Props) {
  const labels = config.racks;
  const market = route.view === 'marketRacks';
  const [page, setPage] = useState(0);
  const [products, setProducts] = useState<CatalogPage<RackProduct> | null>(null);
  const [racks, setRacks] = useState<Page<Rack> | null>(null);
  const [detail, setDetail] = useState<RackDetail | null>(null);
  const [refresh, setRefresh] = useState(0);
  const [loadedKey, setLoadedKey] = useState('');
  const [notice, setNotice] = useState<Notice>('');
  const [busy, setBusy] = useState(false);
  const [pending, setPending] = useState<string | null>(null);
  const requestKey = JSON.stringify([market, route.workshopRackId, page, revision, refresh]);
  const loading = loadedKey !== requestKey;
  useEffect(() => {
    const abort = new AbortController();
    const task = market ? read<CatalogPage<RackProduct>>('/api/v1/market/rack-products?size=24&page=' + page, abort.signal).then(setProducts)
      : route.workshopRackId ? read<RackDetail>('/api/v1/workshop/racks/' + encodeURIComponent(route.workshopRackId), abort.signal).then(setDetail)
        : read<Page<Rack>>('/api/v1/workshop/racks?size=24&page=' + page, abort.signal).then(setRacks);
    void task.catch(error => { if (error.name !== 'AbortError') setNotice(failureNotice(config.errors.requestFailed, error)); })
      .finally(() => { if (!abort.signal.aborted) setLoadedKey(requestKey); });
    return () => abort.abort();
  }, [market, route.workshopRackId, page, revision, refresh, requestKey, config.errors.requestFailed]);
  useEffect(() => {
    if (!pending) return;
    const abort = new AbortController(); let timer = 0;
    const poll = async () => {
      try {
        const result = await read<{ status: string; error?: string }>(pending, abort.signal);
        if (result.status.toLowerCase() === 'completed') { setPending(null); setBusy(false); setNotice(labels.saved); setRefresh(value => value + 1); return; }
        if (result.status.toLowerCase() === 'failed') { setPending(null); setBusy(false); setNotice(failureNotice(labels.failed, result.error ?? labels.failed)); return; }
      } catch (error) { if (abort.signal.aborted) return; setNotice(failureNotice(labels.pending, error)); }
      if (!abort.signal.aborted) timer = window.setTimeout(poll, 1500);
    };
    void poll();
    return () => { abort.abort(); window.clearTimeout(timer); };
  }, [pending, labels]);
  async function mutate(url: string, payload: object) {
    setBusy(true); setNotice('');
    try {
      const response = await apiFetch(url, { method: 'POST', headers: { 'content-type': 'application/json', 'Idempotency-Key': crypto.randomUUID() }, body: JSON.stringify(payload) });
      const body = await response.json() as { publication?: { status: string } };
      const location = response.headers.get('location');
      if (response.status === 202 && location && body.publication?.status.toLowerCase() !== 'completed') { setPending(location); setNotice(labels.pending); }
      else { setBusy(false); setRefresh(value => value + 1); setNotice(labels.saved); }
    } catch (error) { setBusy(false); setNotice(failureNotice(labels.failed, error)); }
  }
  const total = market ? products?.results.totalElements ?? 0 : racks?.totalElements ?? 0;
  return <section className="mt-6 space-y-5">
    <p className="text-sm text-muted-foreground">{market ? labels.marketDescription : labels.description}</p>
    <AppNotice notice={notice} />{loading && <output>{labels.loading}</output>}
    {market && <div className="grid gap-4 md:grid-cols-2">{products?.results.content.map(product => <article key={product.id} className={card}>
      {product.photos?.[0] && <figure className="mb-4"><img className="h-52 w-full rounded-xl object-contain" src={product.photos[0].url} alt={product.name} loading="lazy" referrerPolicy="no-referrer" />
        <figcaption className="mt-1 text-xs text-muted-foreground"><a href={product.photos[0].pageUrl} target="_blank" rel="noreferrer">{product.photos[0].credit}</a> · {product.photos[0].usageStatus}</figcaption></figure>}
      <h2 className="text-lg font-semibold">{product.name}</h2><p className="text-sm">{product.brand} · {product.reference}</p>
      <p className="my-2 text-sm">{product.rows.length} {labels.row}</p><p className="text-sm text-muted-foreground">{product.notes}</p>
      <ul className="my-3 text-sm">{product.rows.map(row => <li key={row.id}>{row.name} · {row.support === 'fixed-slots' ? row.slots.length + ' ' + labels.positions : (row.widthMm ?? '—') + ' × ' + (row.depthMm ?? '—') + ' mm'}
        {row.capacityCalibrations.map((capacity, i) => <span key={i}> · {capacity.potCount} {labels.pots}</span>)}</li>)}</ul>
      {product.sources.map(source => <a key={source} className="block text-xs text-primary" href={source} target="_blank" rel="noreferrer">{labels.manufacturerSource}</a>)}
      <Acquisition product={product} labels={labels} busy={busy || loading} mutate={mutate} />
    </article>)}</div>}
    {!market && !route.workshopRackId && <>
      <button className={button} onClick={() => navigate({ view: 'marketRacks' })}>{labels.chooseMarketRack}</button>
      <div className="grid gap-4 md:grid-cols-2">{racks?.content.map(rack => <button key={rack.workshopRackId} className={card + ' text-left'} onClick={() => navigate({ view: 'workshopRack', workshopRackId: rack.workshopRackId })}>
        <h2 className="font-semibold">{rack.configuration.name}</h2><p className="text-sm">{rack.configuration.location}</p><p className="mt-2 text-sm">{rack.rows.length} {labels.row} · {rack.placedPotCount} {labels.pots}</p>
      </button>)}</div>
    </>}
    {!route.workshopRackId && <><div className="my-4 flex items-center gap-3 text-sm">
      <button className={button} disabled={!page} onClick={() => setPage(page - 1)}>{labels.previous}</button>
      <span>{page + 1} / {Math.max(1, Math.ceil(total / 24))} · {total}</span>
      <button className={button} disabled={(page + 1) * 24 >= total} onClick={() => setPage(page + 1)}>{labels.next}</button>
    </div>{!loading && total === 0 && <p>{market ? labels.noMarketRacks : labels.emptyWorkshop}</p>}</>}
    {!market && route.workshopRackId && detail && <>
      <button className={button} onClick={() => navigate({ view: 'workshopRacks' })}>{labels.back}</button>
      <div className={card}><h2 className="text-2xl font-semibold">{detail.rack.configuration.name}</h2><p>{detail.rack.configuration.location}</p>
        <div className="mt-5 space-y-4">{detail.rack.rows.map(row => <section key={row.id} className="rounded-xl border-b-8 border-amber-900/25 bg-amber-50/30 p-4">
          <h3 className="font-semibold">{row.name}</h3>
          <div className="mt-3 flex flex-wrap gap-2">{detail.pots.filter(pot => pot.placement?.rackRowId === row.id).map(pot => <div key={pot.paintPotId} className="w-40 rounded-xl border bg-card p-3">
            {pot.colorHex && /^#[0-9a-f]{6}$/i.test(pot.colorHex) && <div className="mb-2 h-3 rounded" style={{ backgroundColor: pot.colorHex }} />}
            <button className="text-left text-sm font-semibold" onClick={() => navigate({ view: 'paintPot', paintPotId: pot.paintPotId })}>{pot.name}</button>
            <p className="text-xs">{pot.brand} · {labels[pot.compatibility?.status ?? 'unknown']}</p>
            <p className="text-xs">{labels.offset} : {pot.placement?.slotId ?? (pot.placement?.offsetMm != null ? pot.placement.offsetMm + ' mm' : Math.round((pot.placement?.offsetFraction ?? 0) * 100) + '%')}</p>
            <div className="mt-2 flex flex-wrap gap-2 text-xs">
              <button disabled={busy || loading} onClick={() => void mutate('/api/v1/workshop/paint-storage/placements', { paintPotId: pot.paintPotId, placement: { ...pot.placement, locked: !pot.placement?.locked }, snapshotToken: detail.snapshotToken, allowEstimates: true })}>{pot.placement?.locked ? labels.unlock : labels.lock}</button>
              <button disabled={busy || loading} onClick={() => void mutate('/api/v1/workshop/paint-storage/placements', { paintPotId: pot.paintPotId, placement: null, snapshotToken: detail.snapshotToken, allowEstimates: true })}>{labels.remove}</button>
            </div>
          </div>)}</div>
        </section>)}</div>
      </div>
    </>}
    {!market && <Organizer key={(route.workshopRackId ?? 'all') + ':' + revision + ':' + refresh} rackId={route.workshopRackId} labels={labels} busy={busy || loading} mutate={mutate} config={config} />}
  </section>;
}
