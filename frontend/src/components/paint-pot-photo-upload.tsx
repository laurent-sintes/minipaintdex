import { useEffect, useId, useRef, useState } from 'react';
import type { PaintPot } from '@/models/paint-pot-model';
import type { SiteConfig } from '@/models/site-config-model';
import { apiFetch, failureNotice, type Notice } from '@/utils/api-errors';
import { AppNotice } from './app-notice';

const button = 'rounded-xl border px-4 py-2 text-sm font-semibold disabled:opacity-40';

function BlobImage({ blob, alt }: { blob: Blob; alt: string }) {
  const ref = useRef<HTMLImageElement>(null);
  useEffect(() => {
    const value = URL.createObjectURL(blob);
    if (ref.current) ref.current.src = value;
    return () => URL.revokeObjectURL(value);
  }, [blob]);
  return <img ref={ref} alt={alt} className="h-full w-full object-contain" />;
}

export function PaintPotPhotoUpload({ paintPotId, config, onSaved, submitLabel, help }: {
  paintPotId: string; config: SiteConfig; onSaved: () => void; submitLabel?: string; help?: string;
}) {
  const labels = config.paintPots;
  const inputId = useId();
  const [file, setFile] = useState<File | null>(null);
  const [caption, setCaption] = useState('');
  const [removeBackground, setRemoveBackground] = useState(true);
  const [preview, setPreview] = useState<Blob | null>(null);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<Notice>('');
  const [previewNotice, setPreviewNotice] = useState<Notice>('');
  const [fileKey, setFileKey] = useState(0);
  const mutationKey = useRef('');
  const mutation = useRef<AbortController | null>(null);
  const processing = Boolean(file && removeBackground && !preview && !previewNotice);
  const base = '/api/v1/workshop/paint-pots/' + encodeURIComponent(paintPotId);

  useEffect(() => () => mutation.current?.abort(), []);
  useEffect(() => { mutationKey.current = crypto.randomUUID(); }, [file, caption, removeBackground, paintPotId]);
  useEffect(() => {
    const controller = new AbortController();
    if (file && removeBackground) {
      const body = new FormData(); body.append('file', file);
      void apiFetch(base + '/photo-preview', { method: 'POST', body, signal: controller.signal })
        .then(response => response.blob())
        .then(blob => { if (!controller.signal.aborted) setPreview(blob); })
        .catch(error => { if (!controller.signal.aborted) setPreviewNotice(failureNotice(labels.previewFailed, error)); });
    }
    return () => controller.abort();
  }, [file, removeBackground, base, labels.previewFailed]);

  async function save() {
    if (!file || busy || (removeBackground && !preview)) return;
    const controller = new AbortController(); mutation.current = controller;
    setBusy(true); setNotice('');
    try {
      const body = new FormData(); body.append('file', file); body.append('caption', caption);
      body.append('removeBackground', String(removeBackground));
      const response = await apiFetch(base + '/photos', { method: 'POST', body, signal: controller.signal,
        headers: { 'Idempotency-Key': mutationKey.current } });
      const receipt = await response.json() as { publication: { status: string } };
      let status = receipt.publication.status.toLowerCase();
      const location = response.headers.get('location');
      if (status !== 'completed') setNotice(labels.pending);
      while (status !== 'completed') {
        if (!location) throw new Error('Publication status location is missing.');
        await new Promise<void>((resolve, reject) => {
          const aborted = () => { window.clearTimeout(timer); reject(new DOMException('Aborted', 'AbortError')); };
          const timer = window.setTimeout(() => { controller.signal.removeEventListener('abort', aborted); resolve(); }, 1500);
          if (controller.signal.aborted) aborted(); else controller.signal.addEventListener('abort', aborted, { once: true });
        });
        let publication: { status: string; error?: string };
        try {
          const update = await apiFetch(location, { signal: controller.signal });
          publication = await update.json() as typeof publication;
        } catch (error) {
          if (controller.signal.aborted) throw error;
          setNotice(failureNotice(labels.pending, error)); continue;
        }
        status = publication.status.toLowerCase();
        if (status === 'failed') throw new Error(publication.error || labels.failed);
      }
      if (!controller.signal.aborted) {
        setNotice(labels.saved); setFile(null); setPreview(null); setPreviewNotice(''); setCaption(''); setFileKey(value => value + 1); onSaved();
      }
    } catch (error) { if (!controller.signal.aborted) setNotice(failureNotice(labels.failed, error)); }
    finally { if (!controller.signal.aborted) setBusy(false); }
  }

  return <form className="mt-4 space-y-3" onSubmit={event => { event.preventDefault(); void save(); }}>
    <p className="text-sm leading-6 text-muted-foreground">{help ?? labels.photoHelp}</p>
    <label htmlFor={inputId} className="block text-sm font-medium">{labels.personalPhoto}</label>
    <input id={inputId} key={fileKey} type="file" required accept="image/jpeg,image/png,image/webp" disabled={busy}
      className="block w-full rounded-xl border p-3 text-sm" onChange={event => { setFile(event.target.files?.[0] ?? null); setPreview(null); setPreviewNotice(''); setNotice(''); }} />
    {file && <>
      <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={removeBackground} disabled={busy}
        onChange={event => { setRemoveBackground(event.target.checked); setPreview(null); setPreviewNotice(''); }} />{labels.removeBackground}</label>
      <div className="grid grid-cols-2 gap-3">
        <figure><div className="aspect-square rounded-xl border bg-white"><BlobImage blob={file} alt={labels.originalPhoto} /></div><figcaption className="mt-1 text-xs">{labels.originalPhoto}</figcaption></figure>
        {removeBackground && <figure><div className="grid aspect-square place-items-center rounded-xl border bg-secondary p-2" aria-busy={processing}>
          {preview ? <BlobImage blob={preview} alt={labels.cutoutPhoto} /> : <output className="text-center text-sm">{processing ? labels.processingPhoto : labels.previewUnavailable}</output>}
        </div><figcaption className="mt-1 text-xs">{labels.cutoutPhoto}</figcaption></figure>}
      </div>
      <AppNotice notice={previewNotice} />
      <label className="block text-sm">{labels.caption}<input value={caption} disabled={busy} onChange={event => setCaption(event.target.value)} className="mt-1 block w-full rounded-xl border bg-card p-3" /></label>
      <button type="submit" disabled={busy || processing || (removeBackground && !preview)} className={button}>{busy ? labels.pending : submitLabel ?? labels.addPhoto}</button>
    </>}
    <AppNotice notice={notice} />
  </form>;
}

/** Replaces the representative workshop visual by appending to one identified pot's journal. */
export function PaintProductPotPhotoReplacement({ paintProductId, config, submitLabel, onSaved }: {
  paintProductId: string; config: SiteConfig; submitLabel: string; onSaved: () => void;
}) {
  const editor = useRef<HTMLElement>(null);
  useEffect(() => { editor.current?.scrollIntoView({ block: 'start', behavior: 'smooth' }); }, []);
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<{ pots: PaintPot[]; totalPages: number } | null>(null);
  const [selectedId, setSelectedId] = useState('');
  const [notice, setNotice] = useState<Notice>('');
  const [revision, setRevision] = useState(0);
  const labels = config.paintPots;
  const selected = result?.pots.find(pot => pot.paintPotId === selectedId) ?? result?.pots[0];
  useEffect(() => {
    const controller = new AbortController();
    const params = new URLSearchParams({ paintProductId, size: '20', page: String(page) });
    void apiFetch('/api/v1/workshop/paint-pots?' + params, { signal: controller.signal })
      .then(response => response.json()).then(value => { if (!controller.signal.aborted) { setResult(value); setNotice(''); } })
      .catch(error => { if (!controller.signal.aborted) setNotice(failureNotice(config.errors.requestFailed, error)); });
    return () => controller.abort();
  }, [paintProductId, page, revision, config.errors.requestFailed]);
  return <section ref={editor} className="paint-detail-section paint-photo-replacement">
    <h3>{submitLabel}</h3>
    <div className="mt-3">
      <AppNotice notice={notice} />
      {notice && <button type="button" className={button} onClick={() => { setNotice(''); setRevision(value => value + 1); }}>{config.paintDetail.retry}</button>}
      {!result && !notice && <output>{config.errors.loading}</output>}
      {result?.pots.length === 0 && <p className="text-sm">{labels.photoNeedsPot}</p>}
      {selected && <>
        <label className="block text-sm">{labels.choosePot}<select value={selected.paintPotId} onChange={event => setSelectedId(event.target.value)} className="mt-1 block w-full rounded-xl border bg-card p-3">
          {result!.pots.map(pot => <option key={pot.paintPotId} value={pot.paintPotId}>{pot.paintPotId}</option>)}
        </select></label>
        <PaintPotPhotoUpload key={selected.paintPotId} paintPotId={selected.paintPotId} config={config}
          submitLabel={submitLabel} help={config.paintDetail.replacementHelp} onSaved={onSaved} />
      </>}
      {result && result.totalPages > 1 && <div className="mt-3 flex gap-2"><button type="button" disabled={page === 0} className={button} onClick={() => { setResult(null); setPage(value => value - 1); }}>{labels.previous}</button>
        <button type="button" disabled={page + 1 >= result.totalPages} className={button} onClick={() => { setResult(null); setPage(value => value + 1); }}>{labels.next}</button></div>}
    </div>
  </section>;
}
