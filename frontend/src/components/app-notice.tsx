import { useId, useState } from 'react';
import { CircleAlert } from 'lucide-react';
import type { Notice } from '@/utils/api-errors';

export function AppNotice({ notice, className = '' }: { notice: Notice; className?: string }) {
  const id = useId();
  const [expanded, setExpanded] = useState(false);
  if (!notice) return null;
  if (typeof notice === 'string') return <output className={`app-notice ${className}`}>{notice}</output>;
  return <div className={`app-error ${className}`} role="alert">
    <button type="button" className="app-error-trigger" aria-describedby={expanded ? id : undefined}
      onMouseEnter={() => setExpanded(true)} onMouseLeave={() => setExpanded(false)}
      onFocus={() => setExpanded(true)} onBlur={() => setExpanded(false)}
      onClick={() => setExpanded(true)} onKeyDown={event => { if (event.key === 'Escape') { setExpanded(false); event.stopPropagation(); } }}>
      <CircleAlert size={20} aria-hidden="true" /><span>{notice.message}</span>
      <span id={id} role="tooltip" className="app-error-tooltip" hidden={!expanded}>{notice.detail}</span>
    </button>
  </div>;
}
