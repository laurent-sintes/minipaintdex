import { AppNotice } from './app-notice';
import { apiFetch, errorDetail } from '@/utils/api-errors';
import { useEffect, useState } from 'react';
import type { PaintUsageGuide, GuideContent } from '@/models/paint-usage-guide-model';
import type { SiteConfig } from '@/models/site-config-model';

export function UsageContent({ content, config }: { content: GuideContent; config: SiteConfig }) {
  return <>
    {content.summary && <p className="leading-6">{content.summary}</p>}
    {content.steps.length > 0 && <ol className="mt-4 list-decimal space-y-3 pl-6 leading-6">
      {content.steps.map((step, index) => <li key={index} className="pl-1">{step}</li>)}
    </ol>}
    {content.tips.length > 0 && <aside className="mt-5 rounded-xl border border-amber-200 bg-amber-50 p-4 text-amber-950">
      <h4 className="font-semibold">{config.paintDetail.usageTips}</h4>
      <ul className="mt-2 list-disc space-y-2 pl-5 leading-6">{content.tips.map((tip, index) => <li key={index}>{tip}</li>)}</ul>
    </aside>}
  </>;
}

export function PaintUsageGuides({ paintProductId, config }: { paintProductId: string; config: SiteConfig }) {
  const [language, setLanguage] = useState('fr');
  const [page, setPage] = useState(0);
  const [retry, setRetry] = useState(0);
  const [response, setResponse] = useState<{ key: string; guides: PaintUsageGuide[]; totalPages: number; error: string } | null>(null);
  const key = `${paintProductId}:${language}:${page}:${retry}`;
  const current = response?.key === key ? response : null;
  useEffect(() => {
    const controller = new AbortController();
    const params = new URLSearchParams({ paintProductId, language, page: String(page), size: '20' });
    apiFetch('/api/v1/market/paint-usage-guides?' + params, { signal: controller.signal })
      .then(r => { return r.json() as Promise<{ guides: PaintUsageGuide[]; totalPages: number }>; })
      .then(r => { if (!controller.signal.aborted) setResponse({ key, ...r, error: '' }); })
      .catch(reason => { if (!controller.signal.aborted) setResponse({ key, guides: [], totalPages: 0, error: errorDetail(reason) }); });
    return () => controller.abort();
  }, [key, language, page, paintProductId]);
  const labels = config.paintDetail;
  return <section className="paint-detail-section" aria-label={labels.usageInstructions}>
    <div className="flex flex-wrap items-center justify-between gap-3">
      <h3>{labels.usageInstructions}</h3>
      <div className="flex gap-2" aria-label={labels.documentLanguage}>
        <button type="button" className="guide-language" aria-pressed={language === 'fr'} onClick={() => { setLanguage('fr'); setPage(0); }}>{labels.french}</button>
        <button type="button" className="guide-language" aria-pressed={language === 'original'} onClick={() => { setLanguage('original'); setPage(0); }}>{labels.original}</button>
      </div>
    </div>
    {!current ? <output>{config.errors.loading}</output> : current.error
      ? <div><AppNotice notice={{ message: labels.guideLoadFailed, detail: current.error }} /><button type="button" className="mt-2 underline" onClick={() => setRetry(retry + 1)}>{labels.retry}</button></div>
      : current.guides.length === 0 ? <p>{labels.noUsageGuide}</p>
        : current.guides.map(guide => <article key={guide.paintUsageGuideId} className="paint-usage-guide" lang={guide.language === 'mul' ? undefined : guide.language}>
          <div className="mb-4 flex flex-wrap items-start justify-between gap-3">
            <div><h4 className="font-semibold">{guide.title}</h4>
              <p className="mt-1 text-xs text-muted-foreground">{labels.sharedGuide} · {guide.ranges.join(' / ')} · {labels.revision} {guide.revision}</p></div>
            <span className="guide-status">{labels.knowledgeLabels[guide.knowledgeStatus]}</span>
          </div>
          {guide.reviewRequired && <p className="guide-notice">{labels.instructionsReviewRequired}</p>}
          {guide.translationReviewRequired && <p className="guide-notice">{labels.translationReviewRequired}</p>}
          {guide.translationStatus === 'missing-translation' && <p className="guide-notice">{labels.translationMissing}</p>}
          {guide.translationStatus === 'stale-translation' && <p className="guide-notice">{labels.translationStale}</p>}
          <UsageContent content={guide.content} config={config} />
          {guide.sourceUrls.length > 0 && <details className="mt-5 text-sm">
            <summary className="cursor-pointer font-semibold">{labels.sources} ({guide.sourceUrls.length})</summary>
            <ul className="mt-2 space-y-2">{guide.sourceUrls.map((url, index) => <li key={url}><a className="break-all underline" href={url} target="_blank" rel="noreferrer">{labels.source} {index + 1} — {new URL(url).hostname}</a></li>)}</ul>
          </details>}
        </article>)}
    {current && current.totalPages > 1 && <nav className="mt-3 flex justify-between" aria-label={labels.usageInstructions}>
      <button type="button" disabled={page === 0} onClick={() => setPage(page - 1)}>{labels.previous}</button>
      <span>{page + 1} / {current.totalPages}</span>
      <button type="button" disabled={page + 1 >= current.totalPages} onClick={() => setPage(page + 1)}>{labels.next}</button>
    </nav>}
  </section>;
}
