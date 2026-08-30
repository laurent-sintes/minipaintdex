import type { Metadata } from 'next';
import { ArrowLeft, ExternalLink, Paintbrush } from 'lucide-react';
import { notFound } from 'next/navigation';
import { findProject, projectCatalog, siteConfig } from '@/lib/catalog';

type PageProps = { params: Promise<{ projectId: string }> };

export function generateStaticParams() {
  return projectCatalog.map((project) => ({ projectId: project.id }));
}

export async function generateMetadata({ params }: PageProps): Promise<Metadata> {
  const { projectId } = await params;
  const project = findProject(projectId);
  return { title: project ? `${project.name} — ${siteConfig.metadata.shortTitle}` : siteConfig.errors.projectNotFound };
}

export default async function ProjectPage({ params }: PageProps) {
  const { projectId } = await params;
  const project = findProject(projectId);
  if (!project) notFound();

  const miniatureCount = project.items.reduce((total, item) => total + item.quantity, 0);
  return (
    <main className="min-h-screen bg-background px-4 py-8 text-foreground sm:px-6 lg:px-8">
      <div className="mx-auto max-w-6xl">
        <a href="/" className="inline-flex items-center gap-2 text-sm font-semibold text-primary"><ArrowLeft size={16} />{siteConfig.projectDetail.back}</a>
        <header className="mt-7 rounded-[28px] border bg-card p-6 shadow-sm sm:p-9">
          <p className="eyebrow">{project.game}</p>
          <h1 className="mt-2 text-3xl font-semibold tracking-[-0.045em] sm:text-5xl">{project.name}</h1>
          <p className="mt-4 max-w-3xl text-sm leading-6 text-muted-foreground">{project.scope}</p>
          <div className="mt-6 flex flex-wrap gap-2 text-xs font-semibold">
            <span className="rounded-full bg-secondary px-3 py-1.5">{project.items.length} {siteConfig.projectDetail.sheets}</span>
            <span className="rounded-full bg-secondary px-3 py-1.5">{miniatureCount} {siteConfig.projectDetail.figures}</span>
          </div>
          {project.edition.note && <p className="mt-6 rounded-2xl bg-primary/5 p-4 text-xs leading-5 text-muted-foreground">{project.edition.note}</p>}
        </header>

        <section className="mt-8">
          <h2 className="text-xl font-semibold">{siteConfig.projectDetail.paintingSheets}</h2>
          <div className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {project.items.map((item) => (
              <a key={item.id} href={`/projects/${project.id}/${item.id}`} className="group rounded-[24px] border bg-card p-5 shadow-sm transition hover:-translate-y-0.5 hover:border-primary/30">
                {item.referenceImages[0]
                  ? <img src={item.referenceImages[0].url} alt={`${siteConfig.miniatureDetail.paintedReferences} — ${item.name}`} className="aspect-[16/9] w-full rounded-2xl object-cover" />
                  : <div className="grid size-10 place-items-center rounded-xl bg-secondary text-primary"><Paintbrush size={18} /></div>}
                <p className="mt-4 text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">{item.kind} · × {item.quantity}</p>
                <h3 className="mt-1 text-lg font-semibold">{item.name}</h3>
                <p className="mt-2 line-clamp-3 text-xs leading-5 text-muted-foreground">{item.description}</p>
                <span className="mt-4 inline-block text-xs font-semibold text-primary">{siteConfig.projectDetail.openRecipe}</span>
              </a>
            ))}
          </div>
        </section>

        {project.sources.length > 0 && <section className="mt-9 rounded-[24px] border bg-card p-5"><h2 className="text-sm font-semibold">{siteConfig.projectDetail.sources}</h2><div className="mt-3 flex flex-wrap gap-4">{project.sources.map((source) => <a key={source.url} href={source.url} target="_blank" rel="noreferrer" className="inline-flex items-center gap-1.5 text-xs font-semibold text-primary"><ExternalLink size={13} />{source.label}</a>)}</div></section>}
      </div>
    </main>
  );
}
