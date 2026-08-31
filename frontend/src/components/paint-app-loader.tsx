'use client';

import { useEffect, useState } from 'react';
import { PaintApp } from '@/components/paint-app';
import type { Dashboard } from '@/models/paintable-product-model';
import type { SiteConfig } from '@/models/site-config-model';

type InitialData = { config: SiteConfig; dashboard: Dashboard };

export function PaintAppLoader() {
  const [data, setData] = useState<InitialData | null>(null);
  const [error, setError] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    Promise.all([
      fetch('/api/v1/site/config', { signal: controller.signal, headers: { accept: 'application/json' } }),
      fetch('/api/v1/dashboard', { signal: controller.signal, headers: { accept: 'application/json' } }),
    ])
      .then(async ([config, dashboard]) => {
        if (!config.ok || !dashboard.ok) throw new Error('Initial application load failed');
        return { config: await config.json() as SiteConfig, dashboard: await dashboard.json() as Dashboard };
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
          <p className="mt-2 text-sm text-muted-foreground">Démarrez Mini Paint Dex avec la commande de développement complète, puis rechargez cette page.</p>
        </section>
      </main>
    );
  }

  if (!data) {
    return <main className="grid min-h-screen place-items-center bg-background"><output className="size-8 animate-spin rounded-full border-2 border-primary border-t-transparent" aria-label="Chargement" /></main>;
  }

  return <PaintApp initialDashboard={data.dashboard} config={data.config} />;
}
