import { useState } from 'react';
import type { PaintCardModel } from '@/models/paint-model';
import type { SiteConfig } from '@/models/site-config-model';
import { displayedPaintVisual, paintVisualLevel } from '@/utils/paint-visual';

export function usePaintVisual(paint: PaintCardModel) {
  const [failed, setFailed] = useState<string[]>([]);
  const visual = displayedPaintVisual(paint, failed);
  return { ...visual, onError: () => setFailed(previous => [...previous, visual.url]) };
}

export function PaintVisualQuality({ quality, config }: {
  quality: PaintCardModel['manufacturerImageQuality']; config: SiteConfig;
}) {
  const labels = config.paintDetail;
  const level = paintVisualLevel(quality);
  const label = labels.imageQualityLabels[quality.replace(/_([a-z])/g, (_, letter: string) => letter.toUpperCase())];
  return <div className="paint-visual-quality">
    <p className="text-xs text-muted-foreground">{labels.imageQuality}</p>
    <div className="paint-quality-meter" title={labels.imageQualityHelp}>
      <meter min={1} max={6} value={level} aria-label={labels.imageQuality} aria-valuetext={label} className="sr-only" />
      <div className="paint-quality-track" aria-hidden="true">
        <span className="paint-quality-fill" style={{ width: `${(level - 1) * 20}%` }} />
        {Array.from({ length: 6 }, (_, index) => <i key={index} data-filled={index < level} />)}
        <span className="paint-quality-thumb" style={{ left: `${(level - 1) * 20}%` }} />
      </div>
    </div>
    <p className="text-sm font-semibold">{label}</p>
  </div>;
}
