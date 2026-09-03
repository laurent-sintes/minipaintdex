import { useEffect, useRef } from 'react';
import type { MouseEvent, ReactNode } from 'react';
import { BookOpen, ChevronDown, Droplets, ExternalLink, FolderCog, House, Info, ListFilter, PackageOpen, Paintbrush, ShoppingBasket } from 'lucide-react';
import type { SiteConfig } from '@/models/site-config-model';
import { appRoutePath, isNavigationDestinationCurrent } from '@/utils/app-routing';
import type { AppRoute } from '@/utils/app-routing';

type Destination = { view: AppRoute['view']; label: string; icon: ReactNode };

export function SiteNavigation({ config, route, navigate }: {
  config: SiteConfig; route: AppRoute; navigate: (route: AppRoute) => void;
}) {
  const about = useRef<HTMLDetailsElement>(null);
  useEffect(() => {
    const menu = about.current;
    const dismissOutside = (event: PointerEvent) => {
      if (menu && event.target instanceof Node && !menu.contains(event.target)) menu.open = false;
    };
    const dismissWithEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && menu?.open) {
        menu.open = false;
        menu.querySelector('summary')?.focus();
      }
    };
    document.addEventListener('pointerdown', dismissOutside);
    document.addEventListener('keydown', dismissWithEscape);
    return () => {
      document.removeEventListener('pointerdown', dismissOutside);
      document.removeEventListener('keydown', dismissWithEscape);
    };
  }, []);
  useEffect(() => { if (about.current) about.current.open = false; }, [route]);

  const labels = config.navigation;
  const market: Destination[] = [
    { view: 'marketPaints', label: labels.marketPaints, icon: <Droplets size={17} /> },
    { view: 'marketProducts', label: labels.marketPaintableProducts, icon: <PackageOpen size={17} /> },
  ];
  const workshop: Destination[] = [
    { view: 'workshopPaints', label: labels.workshopPaints, icon: <Paintbrush size={17} /> },
    { view: 'workshop', label: labels.workshopSection, icon: <FolderCog size={17} /> },
    { view: 'shopping', label: labels.shopping, icon: <ShoppingBasket size={17} /> },
  ];
  const information: Destination[] = [
    { view: 'aboutUser', label: labels.userDocumentation, icon: <BookOpen size={17} /> },
    { view: 'aboutAdmin', label: labels.adminDocumentation, icon: <FolderCog size={17} /> },
    { view: 'aboutPaintModel', label: labels.paintModel, icon: <ListFilter size={17} /> },
    { view: 'aboutApi', label: labels.restApi, icon: <ExternalLink size={17} /> },
    { view: 'aboutVersion', label: labels.version, icon: <Info size={17} /> },
  ];
  function follow(event: MouseEvent<HTMLAnchorElement>, destination: AppRoute) {
    if (event.ctrlKey || event.metaKey || event.shiftKey || event.altKey || event.button !== 0) return;
    event.preventDefault();
    if (about.current) about.current.open = false;
    navigate(destination);
  }
  function link(destination: Destination) {
    return <a key={destination.view} href={appRoutePath({ view: destination.view })}
      aria-current={isNavigationDestinationCurrent(route, destination.view) ? 'page' : undefined}
      onClick={event => follow(event, { view: destination.view })}>
      {destination.icon}<span>{destination.label}</span>
    </a>;
  }
  return <header className="site-header">
    <div className="site-header-primary">
      <a href="/" onClick={event => follow(event, { view: 'home' })} className="site-brand" aria-label={config.brand.name}>
        <span className="grid size-10 flex-none place-items-center rounded-2xl bg-primary text-primary-foreground"><Paintbrush size={20} /></span>
        <span className="site-brand-name"><strong className="block text-sm">{config.brand.name}</strong><span className="text-[11px] text-muted-foreground">{config.brand.subtitle}</span></span>
      </a>
      <nav aria-label={labels.ariaLabel} className="top-navigation">
        {link({ view: 'home', label: labels.home, icon: <House size={17} /> })}
        <div className="navigation-group">{market.map(link)}</div>
        <div className="navigation-group">{workshop.map(link)}</div>
        <details ref={about} className="about-navigation">
          <summary data-active={route.view.startsWith('about')}><Info size={17} /><span>{labels.aboutSection}</span><ChevronDown size={13} /></summary>
          <div className="about-navigation-links">{information.map(link)}</div>
        </details>
      </nav>
    </div>
  </header>;
}
