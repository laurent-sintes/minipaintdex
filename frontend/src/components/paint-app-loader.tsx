'use client';

import { AppNotice } from './app-notice';
import { apiFetch, failureNotice } from '@/utils/api-errors';
import type { FailureNotice } from '@/utils/api-errors';
import { useEffect, useState } from 'react';
import { PaintApp } from '@/components/paint-app';
import type { Dashboard } from '@/models/paintable-product-model';
import type { PaintModelSchema } from '@/models/paint-model';
import type { SiteConfig } from '@/models/site-config-model';

type InitialData = { config: SiteConfig; dashboard: Dashboard; paintModel: PaintModelSchema };

export function PaintAppLoader() {
  const [data, setData] = useState<InitialData | null>(null);
  const [error, setError] = useState<FailureNotice | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    Promise.all([
      apiFetch('/api/v1/site/config', { signal: controller.signal, headers: { accept: 'application/json' } }),
      apiFetch('/api/v1/dashboard', { signal: controller.signal, headers: { accept: 'application/json' } }),
      apiFetch('/api/v1/market/paint-product-model', { signal: controller.signal, headers: { accept: 'application/schema+json' } }),
    ])
      .then(async ([config, dashboard, paintModel]) => {
        return {
          config: await config.json() as SiteConfig,
          dashboard: await dashboard.json() as Dashboard,
          paintModel: await paintModel.json() as PaintModelSchema,
        };
      })
      .then(setData)
      .catch((reason) => {
        if (reason instanceof DOMException && reason.name === 'AbortError') return;
        setError(failureNotice(__BOOTSTRAP_LABELS__.unavailable, reason));
      });
    return () => controller.abort();
  }, []);

  if (error) {
    return (
      <main className="grid min-h-screen place-items-center bg-background p-6 text-foreground">
        <section className="max-w-md rounded-[24px] border bg-card p-6 text-center shadow-sm">
          <AppNotice notice={error} />
          <p className="mt-2 text-sm text-muted-foreground">{__BOOTSTRAP_LABELS__.unavailableHelp}</p>
        </section>
      </main>
    );
  }

  if (!data) {
    return <main className="grid min-h-screen place-items-center bg-background"><output className="size-8 animate-spin rounded-full border-2 border-primary border-t-transparent" aria-label={__BOOTSTRAP_LABELS__.loading} /></main>;
  }

  return <PaintApp initialDashboard={data.dashboard} config={data.config} paintModel={data.paintModel} />;
}
