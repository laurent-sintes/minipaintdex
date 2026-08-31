'use client';

import { useEffect, useState } from 'react';
import { PaintApp } from '@/components/paint-app';
import type { Paint, ShoppingItem } from '@/models/paint-model';
import type { PaintableProduct, WorkshopItem, WorkshopOverview } from '@/models/paintable-product-model';
import type { SiteConfig } from '@/models/site-config-model';

type BootstrapResponse = {
  paints: Paint[];
  workshopPaints: Paint[];
  paintStats: { total: number; owned: number; brands: number };
  marketPaintableProducts: PaintableProduct[];
  workshop: WorkshopOverview;
  workshopItems: WorkshopItem[];
  shoppingSeed: ShoppingItem[];
  config: SiteConfig;
};

type PaintPage = { paints: Paint[]; total: number; offset: number; limit: number };
type Facets = Record<'types' | 'colors' | 'brands' | 'manufacturers' | 'ranges' | 'finishes' | 'mediums' | 'opacities' | 'lifecycles' | 'volumes' | 'tags', { value: string; count: number }[]>;
type FacetResponse = { total: number; facets: Facets };

export function PaintAppLoader() {
  const [data, setData] = useState<BootstrapResponse | null>(null);
  const [error, setError] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    Promise.all([
      fetch('/api/v1/bootstrap?includeMarketPaints=false', { signal: controller.signal, headers: { accept: 'application/json' } }),
      fetch('/api/v1/market/paints?offset=0&limit=60', { signal: controller.signal, headers: { accept: 'application/json' } }),
      fetch('/api/v1/market/paints/facets', { signal: controller.signal, headers: { accept: 'application/json' } }),
    ])
      .then(async ([bootstrap, paints, facets]) => {
        if (!bootstrap.ok || !paints.ok || !facets.ok) throw new Error('Initial application load failed');
        return {
          bootstrap: await bootstrap.json() as BootstrapResponse,
          paintPage: await paints.json() as PaintPage,
          facetResponse: await facets.json() as FacetResponse,
        };
      })
      .then(({ bootstrap, paintPage, facetResponse }) => setData({ ...bootstrap, paints: paintPage.paints, initialPaintTotal: paintPage.total, initialPaintFacets: facetResponse.facets } as BootstrapResponse & { initialPaintTotal: number; initialPaintFacets: Facets }))
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

  const initial = data as BootstrapResponse & { initialPaintTotal: number; initialPaintFacets: Facets };
  return <PaintApp initialPaints={initial.paints} initialWorkshopPaints={initial.workshopPaints} initialPaintStats={initial.paintStats} initialPaintTotal={initial.initialPaintTotal} initialPaintFacets={initial.initialPaintFacets} initialProducts={initial.marketPaintableProducts} initialWorkshop={initial.workshop} initialWorkshopItems={initial.workshopItems} shoppingSeed={initial.shoppingSeed} config={initial.config} />;
}
