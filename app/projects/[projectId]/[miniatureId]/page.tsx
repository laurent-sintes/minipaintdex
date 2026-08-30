import type { Metadata } from 'next';
import { ArrowLeft, ExternalLink } from 'lucide-react';
import { notFound } from 'next/navigation';
import { findProject, projectCatalog } from '@/lib/catalog';

type PageProps = { params: Promise<{ projectId: string; miniatureId: string }> };

export function generateStaticParams() {
  return projectCatalog.flatMap((project) => project.items.map((item) => ({ projectId: project.id, miniatureId: item.id })));
}

export async function generateMetadata({ params }: PageProps): Promise<Metadata> {
  const { projectId, miniatureId } = await params;
  const item = findProject(projectId)?.items.find((entry) => entry.id === miniatureId);
  return { title: item ? `${item.name} — MiniPaintDex` : 'Figurine introuvable' };
}

export default async function MiniaturePage({ params }: PageProps) {
  const { projectId, miniatureId } = await params;
  const project = findProject(projectId);
  const item = project?.items.find((entry) => entry.id === miniatureId);
  if (!project || !item) notFound();

  return (
    <main className="min-h-screen bg-background px-4 py-8 text-foreground sm:px-6 lg:px-8">
      <article className="mx-auto max-w-5xl">
        <a href={`/projects/${project.id}`} className="inline-flex items-center gap-2 text-sm font-semibold text-primary"><ArrowLeft size={16} />{project.name}</a>
        <header className="mt-7 rounded-[28px] border bg-card p-6 shadow-sm sm:p-9">
          <p className="eyebrow">{item.kind} · × {item.quantity}</p>
          <h1 className="mt-2 text-3xl font-semibold tracking-[-0.045em] sm:text-5xl">{item.name}</h1>
          <p className="mt-4 max-w-3xl text-sm leading-6 text-muted-foreground">{item.description}</p>
          <span className="mt-5 inline-flex rounded-full bg-[#fff0da] px-3 py-1.5 text-xs font-semibold text-[#9a5217]">{item.status}</span>
        </header>

        {item.referenceImages.length > 0 && <section className="mt-7"><h2 className="text-lg font-semibold">Références peintes</h2><div className="mt-3 grid gap-3 sm:grid-cols-2">{item.referenceImages.map((image) => <figure key={image.url} className="overflow-hidden rounded-[24px] border bg-card"><img src={image.url} alt={`Référence peinte de ${item.name}`} className="aspect-[4/3] w-full object-cover" /><figcaption className="p-3 text-[11px] text-muted-foreground"><a href={image.pageUrl} target="_blank" rel="noreferrer" className="font-semibold text-primary">{image.credit}</a>{image.license ? ` · ${image.license}` : ''}</figcaption></figure>)}</div></section>}

        <section className="mt-7 rounded-[26px] border bg-card p-5 shadow-sm sm:p-7">
          <h2 className="text-lg font-semibold">Peintures à utiliser</h2>
          <div className="mt-4 grid gap-2 sm:grid-cols-2">{item.paints.map((paint) => <div key={`${paint.brand}-${paint.name}`} className="flex items-center gap-3 rounded-2xl border bg-background/50 p-3"><span className="size-10 rounded-xl border" style={{ background: paint.colorHex }} /><div><strong className="block text-sm">{paint.name}</strong><span className="text-[11px] text-muted-foreground">{paint.brand} · {paint.role}</span></div></div>)}</div>
        </section>

        <div className="mt-7 grid gap-5 lg:grid-cols-2">
          <Guide title="Préparation du support" steps={item.preparation} />
          <Guide title="Mise en peinture" steps={item.painting} bordered />
        </div>

        {item.sources.length > 0 && <footer className="mt-7 flex flex-wrap gap-4">{item.sources.map((source) => <a key={source.url} href={source.url} target="_blank" rel="noreferrer" className="inline-flex items-center gap-2 text-xs font-semibold text-primary"><ExternalLink size={14} />{source.label}</a>)}</footer>}
      </article>
    </main>
  );
}

function Guide({ title, steps, bordered = false }: { title: string; steps: { title: string; detail: string }[]; bordered?: boolean }) {
  return <section className={`rounded-[24px] p-5 ${bordered ? 'border bg-card' : 'bg-secondary/60'}`}><h2 className="text-sm font-semibold">{title}</h2><ol className="mt-4 space-y-4">{steps.map((step, index) => <li key={`${step.title}-${index}`} className="flex gap-3"><span className="step-number">{index + 1}</span><div><strong className="text-xs">{step.title}</strong><p className="mt-1 text-xs leading-5 text-muted-foreground">{step.detail}</p></div></li>)}</ol></section>;
}
