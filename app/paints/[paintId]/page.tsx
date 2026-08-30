import type { Metadata } from 'next';
import { ArrowLeft, ExternalLink } from 'lucide-react';
import { notFound } from 'next/navigation';
import { findPaint, paintCatalog, siteConfig } from '@/lib/catalog';

type PageProps = { params: Promise<{ paintId: string }> };

export function generateStaticParams() {
  return paintCatalog.map((paint) => ({ paintId: paint.id }));
}

export async function generateMetadata({ params }: PageProps): Promise<Metadata> {
  const { paintId } = await params;
  const paint = findPaint(paintId);
  return { title: paint ? `${paint.name} — ${siteConfig.metadata.shortTitle}` : siteConfig.errors.paintNotFound };
}

export default async function PaintPage({ params }: PageProps) {
  const { paintId } = await params;
  const paint = findPaint(paintId);
  if (!paint) notFound();
  return (
    <main className="min-h-screen bg-background px-4 py-8 text-foreground sm:px-6">
      <article className="mx-auto max-w-3xl">
        <a href="/" className="inline-flex items-center gap-2 text-sm font-semibold text-primary"><ArrowLeft size={16} />{siteConfig.paintPage.back}</a>
        <div className="mt-7 grid gap-7 rounded-[28px] border bg-card p-6 shadow-sm sm:grid-cols-[280px_minmax(0,1fr)] sm:p-8">
          <div>
            <div className="relative aspect-square overflow-hidden rounded-[24px] border bg-white">{paint.manufacturerImage ? <img src={paint.manufacturerImage} alt={`${siteConfig.paintDetail.productVisual} ${paint.brand} ${paint.name}`} className="h-full w-full object-contain p-3" /> : <div className="h-full w-full" style={{ background: paint.colorHex }} />}</div>
            <p className="mt-2 text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">{siteConfig.paintDetail.productVisual}</p>
            <p className="mt-1 text-[10px] leading-4 text-muted-foreground">{paint.manufacturerImageCredit || siteConfig.paintDetail.noProductVisual}</p>
            <section className="mt-5">
              <h2 className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">{paint.resultImage ? siteConfig.paintDetail.appliedResult : siteConfig.paintDetail.digitalPreview}</h2>
              {paint.resultImage
                ? <figure className="mt-2 overflow-hidden rounded-2xl border bg-white"><img src={paint.resultImage} alt={`${siteConfig.paintDetail.appliedResult} ${paint.name}`} className="aspect-[4/3] w-full object-cover" /><figcaption className="p-2 text-[10px] text-muted-foreground">{paint.resultImageSource ? <a href={paint.resultImageSource} target="_blank" rel="noreferrer" className="font-semibold text-primary">{paint.resultImageCredit}</a> : paint.resultImageCredit}{paint.resultImageLicense ? ` · ${paint.resultImageLicense}` : ''}</figcaption></figure>
                : <div className="mt-2 rounded-2xl border p-3"><div className="h-20 rounded-xl border" style={{ background: paint.colorHex }} /><p className="mt-2 text-[10px] leading-4 text-muted-foreground">{siteConfig.paintDetail.digitalPreviewHelp}</p></div>}
              {paint.resultReferenceUrl && <a href={paint.resultReferenceUrl} target="_blank" rel="noreferrer" className="mt-2 inline-flex items-center gap-1 text-xs font-semibold text-primary"><ExternalLink size={13} />{siteConfig.paintDetail.realResultSource}</a>}
            </section>
          </div>
          <div>
            <p className="eyebrow">{paint.brand} · {paint.range}</p>
            <h1 className="mt-2 text-3xl font-semibold tracking-tight">{paint.name}</h1>
            <div className="mt-5 flex flex-wrap gap-2">{paint.reference && <span className="rounded-full bg-secondary px-3 py-1.5 text-xs font-semibold">{siteConfig.paintPage.referencePrefix} {paint.reference}</span>}{paint.volumeMl > 0 && <span className="rounded-full bg-secondary px-3 py-1.5 text-xs font-semibold">{paint.volumeMl} ml</span>}<span className="rounded-full bg-secondary px-3 py-1.5 text-xs font-semibold">{paint.quantity} {siteConfig.paintPage.stockSuffix}</span></div>
            <div className="mt-5 flex items-center gap-3 rounded-2xl border p-4"><span className="size-10 rounded-xl border" style={{ background: paint.colorHex }} /><div><p className="text-[11px] uppercase tracking-wider text-muted-foreground">{siteConfig.paintPage.color}</p><strong className="text-sm">{paint.colorFamily || paint.colorHex}</strong></div></div>
            {paint.notes && <p className="mt-5 text-sm leading-6 text-muted-foreground">{paint.notes}</p>}
            {paint.recommendedUses.length > 0 && <div className="mt-5 flex flex-wrap gap-2">{paint.recommendedUses.map((use) => <span key={use} className="rounded-full border px-3 py-1.5 text-xs">{use}</span>)}</div>}
            {paint.manufacturerUrl && <a href={paint.manufacturerUrl} target="_blank" rel="noreferrer" className="mt-6 inline-flex h-11 items-center gap-2 rounded-xl bg-primary px-4 text-sm font-semibold text-primary-foreground"><ExternalLink size={15} />{siteConfig.collection.manufacturerSheet}</a>}
          </div>
        </div>
      </article>
    </main>
  );
}
