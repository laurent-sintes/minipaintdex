'use client';

import { useEffect, useState } from 'react';
import { PaintApp } from '@/components/paint-app';
import type { Paint, ShoppingItem } from '@/lib/paint-model';
import type { PaintingProject } from '@/lib/project-model';
import type { SiteConfig } from '@/lib/site-config-model';

type BootstrapResponse = {
  paints: Paint[];
  projects: PaintingProject[];
  shoppingSeed: ShoppingItem[];
  config: SiteConfig;
};

export function PaintAppLoader() {
  const [data, setData] = useState<BootstrapResponse | null>(null);
  const [error, setError] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    fetch('/api/v1/bootstrap', { signal: controller.signal, headers: { accept: 'application/json' } })
      .then((response) => {
        if (!response.ok) throw new Error(`Bootstrap failed with ${response.status}`);
        return response.json() as Promise<BootstrapResponse>;
      })
      .then(setData)
      .catch((reason) => {
        if (reason instanceof DOMException && reason.name === 'AbortError') return;
        setError(true);
      });
    return () => controller.abort();
  }, []);

  if (error) {
    return (
      <main className="grid min-h-screen place-items-center bg-background p-6 text-foreground">
        <section className="max-w-md rounded-[24px] border bg-card p-6 text-center shadow-sm">
          <h1 className="text-lg font-semibold">Service local indisponible</h1>
          <p className="mt-2 text-sm text-muted-foreground">Démarrez MiniPaintDex avec la commande de développement complète, puis rechargez cette page.</p>
        </section>
      </main>
    );
  }

  if (!data) {
    return <main className="grid min-h-screen place-items-center bg-background"><output className="size-8 animate-spin rounded-full border-2 border-primary border-t-transparent" aria-label="Chargement" /></main>;
  }

  return <PaintApp initialPaints={data.paints} projects={data.projects} shoppingSeed={data.shoppingSeed} config={data.config} />;
}
