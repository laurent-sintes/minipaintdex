'use client';

import {
  BookOpen, Camera, Check, ChevronDown, ChevronRight, CircleAlert, Download, Droplets, ExternalLink, FileImage,
  FolderOpen, Grid2X2, ImagePlus, ListChecks, ListFilter, PackageOpen, Paintbrush, Plus, Search,
  ShoppingBasket, Sparkles, Upload, X,
} from 'lucide-react';
import { useEffect, useMemo, useRef, useState } from 'react';
import type { Paint, ShoppingItem } from '@/lib/paint-model';
import type { PaintingProject } from '@/lib/project-model';

type View = 'collection' | 'projects' | 'shopping' | 'imports';

const viewLabels: Record<View, string> = {
  collection: 'Collection',
  projects: 'Projets',
  shopping: 'Achats',
  imports: 'Imports',
};

function NavButton({
  icon, label, active, badge, onClick,
}: {
  icon: React.ReactNode;
  label: string;
  active: boolean;
  badge?: string;
  onClick: () => void;
}) {
  return (
    <button className={'nav-item w-full ' + (active ? 'nav-item-active' : '')} onClick={onClick}>
      {icon}<span>{label}</span>
      {badge && <span className="ml-auto rounded-full bg-current/10 px-2 py-0.5 text-[11px]">{badge}</span>}
    </button>
  );
}

function PaintCard({ paint, onOpen }: { paint: Paint; onOpen: () => void }) {
  const enlarged = ['cit-contrast-briar-queen-chill', 'cit-contrast-ironjawz-yellow', 'cit-contrast-kroxigor-scales', 'cit-contrast-pylar-glacier'].includes(paint.id);
  return (
    <button type="button" className="paint-card group w-full text-left" onClick={onOpen}>
      <div className="paint-swatch" style={{ background: `color-mix(in srgb, ${paint.colorHex} 12%, white)` }}>
        {paint.manufacturerImage
          ? <img src={paint.manufacturerImage} alt={`Pot ${paint.brand} ${paint.name}`} className={'h-full w-full object-contain p-0.5 transition-transform ' + (enlarged ? 'scale-[1.34]' : 'scale-[1.06]')} />
          : <span className="absolute inset-0" style={{ background: paint.colorHex }} />}
        <span className="absolute bottom-3 left-3 z-[1] rounded-full bg-black/30 px-2 py-1 text-[10px] font-semibold uppercase tracking-wider text-white backdrop-blur-sm">
          {paint.reference || paint.range}
        </span>
      </div>
      <div className="min-w-0 flex-1 py-0.5">
        <p className="text-[11px] font-semibold uppercase tracking-[0.08em] text-muted-foreground">{paint.brand}</p>
        <h3 className="mt-0.5 truncate text-[15px] font-semibold tracking-tight">{paint.name}</h3>
        <div className="mt-3 flex flex-wrap gap-1.5">
          <span className="inline-flex rounded-full bg-secondary px-2.5 py-1 text-[11px] font-medium text-secondary-foreground">{paint.colorFamily || paint.finish}</span>
          {paint.quantity > 1 && <span className="inline-flex rounded-full bg-primary/8 px-2.5 py-1 text-[11px] font-semibold text-primary">× {paint.quantity}</span>}
        </div>
        {paint.manufacturerUrl && <span className="mt-2 inline-flex items-center gap-1 text-[11px] font-semibold text-primary"><BookOpen size={12} />Fiche fabricant</span>}
      </div>
    </button>
  );
}

function EmptyState({ text }: { text: string }) {
  return (
    <div className="col-span-full rounded-[24px] border border-dashed bg-card/50 px-6 py-14 text-center">
      <Search className="mx-auto size-7 text-muted-foreground/60" />
      <p className="mt-3 text-sm font-semibold">{text}</p>
      <p className="mt-1 text-xs text-muted-foreground">Essayez un autre terme ou retirez un filtre.</p>
    </div>
  );
}

export function PaintApp({ initialPaints, projects, shoppingSeed }: {
  initialPaints: Paint[];
  projects: PaintingProject[];
  shoppingSeed: ShoppingItem[];
}) {
  const [paints, setPaints] = useState<Paint[]>(initialPaints);
  const [view, setView] = useState<View>('collection');
  const [query, setQuery] = useState('');
  const [brand, setBrand] = useState('Toutes');
  const [modalOpen, setModalOpen] = useState(false);
  const [selectedPaint, setSelectedPaint] = useState<Paint | null>(null);
  const [photo, setPhoto] = useState<File | null>(null);
  const [photoUrl, setPhotoUrl] = useState('');
  const [saving, setSaving] = useState(false);
  const [notice, setNotice] = useState('');
  const [selectedProjectId, setSelectedProjectId] = useState(projects[0]?.id ?? '');
  const activeProject = (projects.find((project) => project.id === selectedProjectId) ?? projects[0])!;
  const [selectedProjectItemId, setSelectedProjectItemId] = useState(projects[0]?.items[0]?.id ?? '');
  const selectedProjectItem = (activeProject.items.find((item) => item.id === selectedProjectItemId) ?? activeProject.items[0])!;
  const [checkedBuys, setCheckedBuys] = useState<string[]>([]);
  const [form, setForm] = useState({
    brand: '', range: '', reference: '', name: '', colorHex: '#6f746f', finish: 'mat', quantity: 1,
  });
  const fileInput = useRef<HTMLInputElement>(null);

  useEffect(() => () => {
    if (photoUrl) URL.revokeObjectURL(photoUrl);
  }, [photoUrl]);

  const brands = useMemo(() => ['Toutes', ...Array.from(new Set(paints.map((paint) => paint.brand))).sort()], [paints]);
  const filteredPaints = useMemo(() => {
    const normalized = query.trim().toLocaleLowerCase('fr');
    return paints.filter((paint) => {
      const matchBrand = brand === 'Toutes' || paint.brand === brand;
      const haystack = [paint.brand, paint.range, paint.reference, paint.name, paint.colorFamily, ...paint.tags, ...paint.recommendedUses].join(' ').toLocaleLowerCase('fr');
      return matchBrand && (!normalized || haystack.includes(normalized));
    });
  }, [paints, brand, query]);

  const totalPots = paints.reduce((sum, paint) => sum + paint.quantity, 0);
  const manufacturerSheetCount = paints.filter((paint) => paint.manufacturerUrl && paint.manufacturerImage).length;
  const projectPaintStatus = (name: string, pendingImport?: boolean) => {
    if (paints.some((paint) => paint.name.toLocaleLowerCase('fr') === name.toLocaleLowerCase('fr'))) return 'Disponible';
    return pendingImport ? 'Import en attente' : 'Manquante';
  };

  function choosePhoto(file: File | null) {
    if (!file) return;
    if (photoUrl) URL.revokeObjectURL(photoUrl);
    setPhoto(file);
    setPhotoUrl(URL.createObjectURL(file));
    const basename = file.name.replace(/\.[^.]+$/, '').replace(/[_-]+/g, ' ');
    setForm((current) => ({ ...current, name: current.name || basename }));
  }

  function closeModal() {
    setModalOpen(false);
    setPhoto(null);
    if (photoUrl) URL.revokeObjectURL(photoUrl);
    setPhotoUrl('');
    setForm({ brand: '', range: '', reference: '', name: '', colorHex: '#6f746f', finish: 'mat', quantity: 1 });
  }

  async function savePaint(event: React.SyntheticEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!form.brand.trim() || !form.name.trim()) {
      setNotice('Renseignez au minimum la marque et le nom.');
      return;
    }
    setSaving(true);
    try {
      const now = new Date().toISOString();
      const paint: Paint = {
        id: `session-${crypto.randomUUID()}`,
        ...form,
        medium: 'acrylique',
        tags: [],
        notes: photo ? `Photo sélectionnée : ${photo.name}` : '',
        createdAt: now,
        updatedAt: now,
        manufacturerUrl: '',
        manufacturerImage: '',
        manufacturerImageCredit: photo ? 'Aperçu local non enregistré' : '',
        volumeMl: 0,
        colorFamily: '',
        manufacturerDescription: '',
        recommendedUses: [],
        manufacturerVerifiedAt: '',
      };
      setPaints((current) => [paint, ...current]);
      setNotice(`${paint.name} est visible pour cette session. Utilisez le skill import-miniature-paints pour l’enregistrer dans data/peintures.yaml.`);
      closeModal();
      setView('collection');
    } catch {
      setNotice('La prévisualisation locale a échoué.');
    } finally {
      setSaving(false);
    }
  }

  return (
    <main className="min-h-screen bg-background pb-20 text-foreground lg:pb-0">
      <header className="sticky top-0 z-30 border-b bg-background/90 backdrop-blur-xl">
        <div className="mx-auto flex h-[72px] max-w-[1500px] items-center gap-4 px-4 sm:px-6 lg:px-8">
          <button className="flex min-w-fit items-center gap-3 text-left" onClick={() => setView('collection')}>
            <span className="grid size-10 place-items-center rounded-[14px] bg-primary text-primary-foreground shadow-sm"><Droplets size={21} strokeWidth={2.2} /></span>
            <span className="hidden sm:block"><strong className="block text-[15px] leading-4 tracking-[-0.02em]">Nuancier</strong><span className="text-xs text-muted-foreground">Atelier de peinture</span></span>
          </button>

          <label className="relative ml-auto hidden w-full max-w-xl md:block">
            <Search className="absolute left-4 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
            <input value={query} onChange={(event) => setQuery(event.target.value)} onFocus={() => setView('collection')} className="h-11 w-full rounded-2xl border bg-card pl-11 pr-4 text-sm shadow-sm outline-none transition focus:border-primary focus:ring-4 focus:ring-primary/10" placeholder="Rechercher une teinte, une marque, un usage…" aria-label="Rechercher dans les peintures" />
          </label>

          <div className="ml-auto hidden items-center gap-1 rounded-xl border bg-card p-1 sm:flex">
            <a className="rounded-lg px-2.5 py-1.5 text-xs font-semibold text-muted-foreground hover:bg-secondary" href="/api/export/yaml"><Download className="mr-1 inline size-3.5" />YAML</a>
            <a className="rounded-lg px-2.5 py-1.5 text-xs font-semibold text-muted-foreground hover:bg-secondary" href="/api/export/csv">CSV</a>
          </div>
          <button onClick={() => setModalOpen(true)} className="inline-flex h-11 items-center gap-2 rounded-2xl bg-primary px-4 text-sm font-semibold text-primary-foreground shadow-sm transition hover:brightness-110">
            <Camera size={17} /><span className="hidden sm:inline">Ajouter par photo</span><span className="sm:hidden">Ajouter</span>
          </button>
        </div>
      </header>

      <div className="mx-auto grid max-w-[1500px] lg:grid-cols-[224px_minmax(0,1fr)]">
        <aside className="hidden min-h-[calc(100vh-72px)] border-r px-4 py-6 lg:block">
          <nav aria-label="Navigation principale" className="space-y-1">
            <NavButton icon={<Grid2X2 size={18} />} label="Collection" active={view === 'collection'} badge={String(paints.length)} onClick={() => setView('collection')} />
            <NavButton icon={<FolderOpen size={18} />} label="Projets" active={view === 'projects'} badge={String(projects.length)} onClick={() => setView('projects')} />
            <NavButton icon={<ShoppingBasket size={18} />} label="Liste d’achats" active={view === 'shopping'} badge={String(shoppingSeed.length)} onClick={() => setView('shopping')} />
            <NavButton icon={<Camera size={18} />} label="Imports photo" active={view === 'imports'} onClick={() => setView('imports')} />
          </nav>

          <div className="mt-8 rounded-[22px] border bg-card p-4 shadow-sm">
            <div className="mb-3 grid size-9 place-items-center rounded-xl bg-secondary text-primary"><Sparkles size={17} /></div>
            <p className="text-sm font-semibold">Projet en cours</p>
            <p className="mt-1 text-xs leading-5 text-muted-foreground">{activeProject.name}</p>
            <div className="mt-4 h-1.5 overflow-hidden rounded-full bg-secondary"><div className="h-full w-[8%] rounded-full bg-primary" /></div>
            <p className="mt-2 text-[11px] text-muted-foreground">{activeProject.items.reduce((sum, item) => sum + item.quantity, 0)} figurines · {activeProject.items.length} fiches</p>
          </div>
        </aside>

        <section className="min-w-0 px-4 py-7 sm:px-6 lg:px-9 lg:py-9">
          <div className="flex flex-wrap items-end justify-between gap-4">
            <div>
              <p className="eyebrow">Mon atelier · {viewLabels[view]}</p>
              <h1 className="mt-1 text-3xl font-semibold tracking-[-0.045em] sm:text-4xl">
                {view === 'collection' && 'Ma collection'}
                {view === 'projects' && activeProject.name}
                {view === 'shopping' && 'Ce qu’il me manque'}
                {view === 'imports' && 'Importer mes pots'}
              </h1>
              <p className="mt-2 max-w-2xl text-sm text-muted-foreground">
                {view === 'collection' && 'Retrouvez vos teintes, filtrez par marque et exportez le référentiel.'}
                {view === 'projects' && 'Peintures et mode opératoire, figurine par figurine.'}
                {view === 'shopping' && 'Une liste courte, motivée par les figurines que vous préparez.'}
                {view === 'imports' && 'Ajoutez une photo, puis confirmez l’étiquette avant l’enregistrement.'}
              </p>
            </div>
            {view === 'collection' && (
              <div className="flex items-center gap-2">
                <ListFilter size={16} className="text-muted-foreground" />
                <select value={brand} onChange={(event) => setBrand(event.target.value)} className="h-10 rounded-xl border bg-card px-3 pr-8 text-sm font-medium shadow-sm outline-none appearance-none">
                  {brands.map((item) => <option key={item}>{item}</option>)}
                </select>
                <ChevronDown className="-ml-8 size-3.5 pointer-events-none" />
              </div>
            )}
          </div>

          {notice && (
            <button onClick={() => setNotice('')} className="mt-5 flex w-full items-center gap-3 rounded-2xl border border-primary/15 bg-primary/5 px-4 py-3 text-left text-xs text-primary">
              <CircleAlert size={16} /><span className="flex-1">{notice}</span><X size={14} />
            </button>
          )}

          {view === 'collection' && (
            <>
              <label className="relative mt-5 block md:hidden">
                <Search className="absolute left-4 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
                <input value={query} onChange={(event) => setQuery(event.target.value)} className="h-11 w-full rounded-2xl border bg-card pl-11 pr-4 text-sm outline-none" placeholder="Rechercher…" />
              </label>
              <div className="mt-7 grid gap-3 sm:grid-cols-3">
                <article className="metric-card"><div className="metric-icon bg-[#e5ecff] text-[#3157a4]"><PackageOpen size={19} /></div><div><strong>{totalPots}</strong><span>pots référencés</span></div></article>
                <article className="metric-card"><div className="metric-icon bg-[#e5f4ec] text-[#207650]"><Check size={19} /></div><div><strong>{paints.length}</strong><span>teintes distinctes</span></div></article>
                <article className="metric-card"><div className="metric-icon bg-[#fff0da] text-[#a45713]"><BookOpen size={19} /></div><div><strong>{manufacturerSheetCount}</strong><span>fiches fabricant</span></div></article>
              </div>
              <div className="mt-8 flex items-center justify-between gap-4"><div><h2 className="text-lg font-semibold tracking-tight">{query || brand !== 'Toutes' ? 'Résultats' : 'Toutes les peintures'}</h2><p className="text-xs text-muted-foreground">{filteredPaints.length} teinte{filteredPaints.length > 1 ? 's' : ''}</p></div><button onClick={() => setModalOpen(true)} className="inline-flex items-center gap-1.5 text-sm font-semibold text-primary"><Plus size={15} />Ajouter</button></div>
              <div className="mt-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
                {filteredPaints.length ? filteredPaints.map((paint) => <PaintCard key={paint.id} paint={paint} onOpen={() => setSelectedPaint(paint)} />) : <EmptyState text="Aucune peinture trouvée" />}
              </div>
            </>
          )}

          {view === 'projects' && (
            <>
            <div className="mt-6 flex items-start gap-3 rounded-2xl border border-primary/15 bg-primary/5 p-4 text-xs leading-5 text-muted-foreground"><CircleAlert className="mt-0.5 size-4 flex-none text-primary" /><span>{activeProject.edition.note} {activeProject.edition.url && <a className="font-semibold text-primary" href={activeProject.edition.url} target="_blank" rel="noreferrer">Consulter la source d’édition</a>}</span></div>
            <div className="mt-5 grid gap-5 xl:grid-cols-[320px_minmax(0,1fr)]">
              <aside className="overflow-hidden rounded-[26px] border bg-card shadow-sm">
                <div className="border-b p-5">
                  <p className="eyebrow">Projet actif</p>
                  {projects.length > 1 && <select value={activeProject.id} onChange={(event) => { const project = projects.find((item) => item.id === event.target.value); setSelectedProjectId(event.target.value); setSelectedProjectItemId(project?.items[0]?.id ?? ''); }} className="mt-2 h-10 w-full rounded-xl border bg-background px-3 text-sm font-semibold">{projects.map((project) => <option key={project.id} value={project.id}>{project.name}</option>)}</select>}
                  <h2 className="mt-2 text-lg font-semibold">{activeProject.name}</h2>
                  <p className="mt-1 text-xs leading-5 text-muted-foreground">{activeProject.scope}</p>
                  <a href={`/projects/${activeProject.id}`} className="mt-3 inline-flex items-center gap-1 text-xs font-semibold text-primary"><BookOpen size={13} />Voir toutes les fiches</a>
                </div>
                <div className="max-h-[70vh] overflow-y-auto p-2">
                  {activeProject.items.map((item) => (
                    <button key={item.id} onClick={() => setSelectedProjectItemId(item.id)} className={'flex w-full items-center gap-3 rounded-2xl p-3 text-left transition ' + (selectedProjectItem.id === item.id ? 'bg-primary text-primary-foreground' : 'hover:bg-secondary')}>
                      <span className={'grid size-10 flex-none place-items-center rounded-xl ' + (selectedProjectItem.id === item.id ? 'bg-white/12' : 'bg-secondary text-primary')}><Paintbrush size={17} /></span>
                      <span className="min-w-0 flex-1"><strong className="block truncate text-sm">{item.name}</strong><span className={'mt-0.5 block text-[11px] ' + (selectedProjectItem.id === item.id ? 'text-white/65' : 'text-muted-foreground')}>{item.kind} · × {item.quantity}</span></span>
                      <ChevronRight size={16} className="flex-none opacity-60" />
                    </button>
                  ))}
                </div>
              </aside>

              <article className="min-w-0 rounded-[26px] border bg-card p-5 shadow-sm sm:p-7">
                <div className="flex flex-wrap items-start justify-between gap-4 border-b pb-5">
                  <div><p className="eyebrow">{selectedProjectItem.kind} · × {selectedProjectItem.quantity}</p><h2 className="mt-1 text-2xl font-semibold tracking-tight">{selectedProjectItem.name}</h2><p className="mt-2 max-w-2xl text-sm leading-6 text-muted-foreground">{selectedProjectItem.description}</p></div>
                  <span className="rounded-full bg-[#fff0da] px-3 py-1.5 text-xs font-semibold text-[#9a5217]">{selectedProjectItem.status}</span>
                </div>

                <section className="mt-6">
                  <div className="flex items-center gap-2"><ListChecks size={17} className="text-primary" /><h3 className="text-sm font-semibold">Peintures à utiliser</h3></div>
                  <div className="mt-3 grid gap-2 sm:grid-cols-2">
                    {selectedProjectItem.paints.map((paint) => {
                      const status = projectPaintStatus(paint.name, paint.pendingImport);
                      return <div key={paint.brand + paint.name} className="flex items-center gap-3 rounded-2xl border bg-background/50 p-3"><span className="size-10 flex-none rounded-xl border" style={{ background: paint.colorHex }} /><span className="min-w-0 flex-1"><strong className="block truncate text-sm">{paint.name}</strong><span className="block truncate text-[11px] text-muted-foreground">{paint.brand} · {paint.role}</span></span><span className={'rounded-full px-2 py-1 text-[10px] font-semibold ' + (status === 'Disponible' ? 'bg-[#e5f4ec] text-[#207650]' : status === 'Import en attente' ? 'bg-[#e5ecff] text-[#3157a4]' : 'bg-[#ffe5df] text-[#a6402c]')}>{status}</span></div>;
                    })}
                  </div>
                </section>

                <div className="mt-7 grid gap-5 lg:grid-cols-2">
                  <section className="rounded-[22px] bg-secondary/60 p-5"><h3 className="text-sm font-semibold">Préparation du support</h3><ol className="mt-4 space-y-4">{selectedProjectItem.preparation.map((step, index) => <li key={step.title} className="flex gap-3"><span className="step-number">{index + 1}</span><div><strong className="text-xs">{step.title}</strong><p className="mt-1 text-xs leading-5 text-muted-foreground">{step.detail}</p></div></li>)}</ol></section>
                  <section className="rounded-[22px] border p-5"><h3 className="text-sm font-semibold">Mise en peinture</h3><ol className="mt-4 space-y-4">{selectedProjectItem.painting.map((step, index) => <li key={step.title} className="flex gap-3"><span className="step-number">{index + 1}</span><div><strong className="text-xs">{step.title}</strong><p className="mt-1 text-xs leading-5 text-muted-foreground">{step.detail}</p></div></li>)}</ol></section>
                </div>
                <div className="mt-5 flex flex-wrap gap-4">
                  <a href={`/projects/${activeProject.id}/${selectedProjectItem.id}`} className="inline-flex items-center gap-2 text-xs font-semibold text-primary"><BookOpen size={14} />Ouvrir la fiche complète</a>
                  {selectedProjectItem.sources.map((source) => <a key={source.url} href={source.url} target="_blank" rel="noreferrer" className="inline-flex items-center gap-2 text-xs font-semibold text-primary"><ExternalLink size={14} />{source.label}</a>)}
                </div>
              </article>
            </div>
            </>
          )}

          {view === 'shopping' && (
            <div className="mt-7 grid gap-5 xl:grid-cols-[minmax(0,1fr)_320px]">
              <div className="overflow-hidden rounded-[26px] border bg-card shadow-sm">
                {shoppingSeed.map((item, index) => {
                  const checked = checkedBuys.includes(item.id);
                  return <label key={item.id} className={'shopping-row ' + (index ? 'border-t ' : '') + (checked ? 'opacity-45' : '')}><input className="sr-only" type="checkbox" checked={checked} onChange={() => setCheckedBuys((current) => checked ? current.filter((id) => id !== item.id) : [...current, item.id])} /><span className={'grid size-5 place-items-center rounded-md border ' + (checked ? 'border-primary bg-primary text-white' : 'bg-background')}>{checked && <Check size={13} />}</span><span className="size-12 rounded-[14px] border shadow-inner" style={{ background: item.colorHex }} /><span className="min-w-0 flex-1"><span className="block text-xs font-semibold uppercase tracking-wider text-muted-foreground">{item.brand} · {item.reference}</span><strong className="mt-0.5 block">{item.name}</strong><span className="mt-1 block text-xs text-muted-foreground">{item.reason}</span></span><span className={'rounded-full px-2.5 py-1 text-[11px] font-semibold ' + (item.priority === 'haute' ? 'bg-[#ffe5df] text-[#a6402c]' : 'bg-secondary text-muted-foreground')}>{item.priority}</span></label>;
                })}
              </div>
              <aside className="rounded-[26px] border bg-card p-5 shadow-sm">
                <div className="grid size-11 place-items-center rounded-2xl bg-[#fff0da] text-[#a45713]"><ShoppingBasket size={20} /></div>
                <h2 className="mt-4 text-lg font-semibold">Prêt pour le magasin</h2>
                <p className="mt-1 text-sm leading-6 text-muted-foreground">{shoppingSeed.length - checkedBuys.length} peinture{shoppingSeed.length - checkedBuys.length > 1 ? 's' : ''} encore à trouver.</p>
                <button className="mt-5 inline-flex h-10 w-full items-center justify-center gap-2 rounded-xl border bg-background text-sm font-semibold"><Download size={15} />Exporter la liste</button>
              </aside>
            </div>
          )}

          {view === 'imports' && (
            <div className="mt-7 grid gap-5 lg:grid-cols-2">
              <button onClick={() => setModalOpen(true)} className="group flex min-h-[280px] flex-col items-center justify-center rounded-[28px] border-2 border-dashed border-primary/20 bg-card p-8 text-center transition hover:border-primary/50 hover:bg-primary/[0.025]">
                <span className="grid size-16 place-items-center rounded-[22px] bg-primary/8 text-primary transition group-hover:scale-105"><ImagePlus size={28} /></span>
                <h2 className="mt-5 text-xl font-semibold">Choisir une photo</h2>
                <p className="mt-2 max-w-sm text-sm leading-6 text-muted-foreground">JPEG, PNG ou HEIC selon le navigateur. Vous vérifierez chaque étiquette avant l’ajout.</p>
                <span className="mt-5 inline-flex h-10 items-center gap-2 rounded-xl bg-primary px-4 text-sm font-semibold text-primary-foreground"><Upload size={16} />Parcourir</span>
              </button>
              <div className="rounded-[28px] border bg-card p-6 shadow-sm sm:p-8">
                <p className="eyebrow">Depuis l’iPhone</p>
                <h2 className="mt-2 text-xl font-semibold">Le dossier OneDrive est prêt</h2>
                <ol className="mt-5 space-y-4 text-sm text-muted-foreground">
                  <li className="flex gap-3"><span className="step-number">1</span><span>Dans Fichiers ou OneDrive, ouvrez <strong className="text-foreground">Documents › ChatGPT › Peinture figurines › imports › photos</strong>.</span></li>
                  <li className="flex gap-3"><span className="step-number">2</span><span>Déposez des photos de 4 à 8 pots, étiquettes face à l’objectif.</span></li>
                  <li className="flex gap-3"><span className="step-number">3</span><span>Revenez dans cette tâche et indiquez que la synchronisation est terminée.</span></li>
                </ol>
                <div className="mt-6 rounded-2xl bg-secondary p-4 text-xs leading-5 text-muted-foreground"><strong className="text-foreground">Astuce photo :</strong> lumière diffuse, cadrage rapproché et peu de reflets. Le JPEG est le format le plus simple à traiter.</div>
              </div>
            </div>
          )}
        </section>
      </div>

      <nav className="fixed inset-x-0 bottom-0 z-30 grid grid-cols-4 border-t bg-card/95 px-2 pb-[max(0.5rem,env(safe-area-inset-bottom))] pt-2 backdrop-blur-xl lg:hidden" aria-label="Navigation mobile">
        {([
          ['collection', <Grid2X2 key="c" size={19} />],
          ['projects', <FolderOpen key="p" size={19} />],
          ['shopping', <ShoppingBasket key="s" size={19} />],
          ['imports', <Camera key="i" size={19} />],
        ] as [View, React.ReactNode][]).map(([item, icon]) => <button key={item} onClick={() => setView(item)} className={'flex flex-col items-center gap-1 rounded-xl py-1.5 text-[10px] font-semibold ' + (view === item ? 'text-primary' : 'text-muted-foreground')}>{icon}{viewLabels[item]}</button>)}
      </nav>

      {selectedPaint && (
        <dialog open aria-label={`Fiche fabricant ${selectedPaint.name}`} className="fixed inset-0 z-50 m-0 grid h-full max-h-none w-full max-w-none place-items-end bg-black/40 p-0 backdrop-blur-sm sm:place-items-center sm:p-5">
          <div className="max-h-[94vh] w-full overflow-y-auto rounded-t-[28px] border bg-background shadow-2xl sm:max-w-3xl sm:rounded-[28px]">
            <div className="sticky top-0 z-10 flex items-center justify-between border-b bg-background/95 px-5 py-4 backdrop-blur-sm">
              <div><p className="eyebrow">Fiche peinture</p><h2 className="mt-0.5 text-lg font-semibold">{selectedPaint.name}</h2></div>
              <button onClick={() => setSelectedPaint(null)} className="grid size-9 place-items-center rounded-xl border bg-card" aria-label="Fermer la fiche"><X size={17} /></button>
            </div>
            <div className="grid gap-6 p-5 sm:grid-cols-[280px_minmax(0,1fr)] sm:p-6">
              <div>
                <div className="relative aspect-square overflow-hidden rounded-[24px] border bg-white" style={{ boxShadow: `inset 0 -10px 50px color-mix(in srgb, ${selectedPaint.colorHex} 10%, transparent)` }}>
                  {selectedPaint.manufacturerImage
                    ? <img src={selectedPaint.manufacturerImage} alt={`Packshot ${selectedPaint.brand} ${selectedPaint.name}`} className={'h-full w-full object-contain p-3 ' + (['cit-contrast-briar-queen-chill', 'cit-contrast-ironjawz-yellow', 'cit-contrast-kroxigor-scales', 'cit-contrast-pylar-glacier'].includes(selectedPaint.id) ? 'scale-[1.18]' : '')} />
                    : <div className="h-full w-full" style={{ background: selectedPaint.colorHex }} />}
                </div>
                <p className="mt-3 text-[10px] leading-4 text-muted-foreground">{selectedPaint.manufacturerImageCredit || 'Aucun visuel fabricant enregistré.'}</p>
              </div>
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="rounded-full bg-primary/8 px-3 py-1 text-xs font-semibold text-primary">{selectedPaint.brand} · {selectedPaint.range}</span>
                  {selectedPaint.reference && <span className="rounded-full bg-secondary px-3 py-1 text-xs font-semibold">Réf. {selectedPaint.reference}</span>}
                  {selectedPaint.volumeMl > 0 && <span className="rounded-full bg-secondary px-3 py-1 text-xs font-semibold">{selectedPaint.volumeMl} ml</span>}
                </div>
                <div className="mt-5 flex items-center gap-3 rounded-2xl border bg-card p-4">
                  <span className="size-10 flex-none rounded-xl border" style={{ background: selectedPaint.colorHex }} />
                  <div><p className="text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">Famille de couleur</p><p className="mt-0.5 text-sm font-semibold">{selectedPaint.colorFamily || 'À qualifier'}</p></div>
                </div>
                <section className="mt-5">
                  <h3 className="text-xs font-semibold uppercase tracking-[0.1em] text-muted-foreground">Caractéristiques fabricant</h3>
                  <p className="mt-2 text-sm leading-6">{selectedPaint.manufacturerDescription || 'Aucune description fabricant enregistrée.'}</p>
                </section>
                <section className="mt-5">
                  <h3 className="text-xs font-semibold uppercase tracking-[0.1em] text-muted-foreground">Usages conseillés</h3>
                  <div className="mt-2 flex flex-wrap gap-2">
                    {selectedPaint.recommendedUses.map((use) => <span key={use} className="rounded-full border bg-card px-3 py-1.5 text-xs font-medium">{use}</span>)}
                  </div>
                </section>
                <div className="mt-6 flex flex-col gap-2 sm:flex-row sm:items-center">
                  {!selectedPaint.id.startsWith('session-') && <a href={`/paints/${selectedPaint.id}`} className="inline-flex h-11 items-center justify-center gap-2 rounded-xl border bg-card px-4 text-sm font-semibold"><BookOpen size={15} />Ouvrir la fiche</a>}
                  {selectedPaint.manufacturerUrl && <a href={selectedPaint.manufacturerUrl} target="_blank" rel="noreferrer" className="inline-flex h-11 items-center justify-center gap-2 rounded-xl bg-primary px-4 text-sm font-semibold text-primary-foreground"><ExternalLink size={15} />Ouvrir la fiche fabricant</a>}
                  <span className="text-[10px] text-muted-foreground">Vérifié le {selectedPaint.manufacturerVerifiedAt}</span>
                </div>
              </div>
            </div>
          </div>
        </dialog>
      )}

      {modalOpen && (
        <div role="presentation" className="fixed inset-0 z-50 grid place-items-end bg-black/35 p-0 backdrop-blur-sm sm:place-items-center sm:p-5" onMouseDown={(event) => event.target === event.currentTarget && closeModal()}>
          <div className="max-h-[94vh] w-full overflow-y-auto rounded-t-[28px] border bg-background shadow-2xl sm:max-w-3xl sm:rounded-[28px]">
            <div className="sticky top-0 z-10 flex items-center justify-between border-b bg-background/95 px-5 py-4 backdrop-blur-sm">
              <div><p className="eyebrow">Nouvelle peinture</p><h2 className="mt-0.5 text-lg font-semibold">Ajouter à partir d’une photo</h2></div>
              <button onClick={closeModal} className="grid size-9 place-items-center rounded-xl border bg-card" aria-label="Fermer"><X size={17} /></button>
            </div>
            <form onSubmit={savePaint} className="grid gap-6 p-5 sm:grid-cols-[240px_minmax(0,1fr)] sm:p-6">
              <div>
                <input ref={fileInput} className="sr-only" type="file" accept="image/*,.heic,.heif" onChange={(event) => choosePhoto(event.target.files?.[0] ?? null)} />
                <button type="button" onClick={() => fileInput.current?.click()} className="relative flex aspect-[4/5] w-full flex-col items-center justify-center overflow-hidden rounded-[22px] border-2 border-dashed bg-card text-center">
                  {photoUrl ? <img src={photoUrl} alt="Aperçu du pot à importer" className="absolute inset-0 h-full w-full object-cover" /> : <><FileImage size={30} className="text-primary" /><strong className="mt-3 text-sm">Choisir une photo</strong><span className="mt-1 px-5 text-xs leading-5 text-muted-foreground">Photographiez l’étiquette de face</span></>}
                  {photoUrl && <span className="absolute bottom-3 rounded-full bg-black/55 px-3 py-1.5 text-xs font-semibold text-white backdrop-blur">Changer la photo</span>}
                </button>
                <div className="mt-3 flex items-start gap-2 rounded-xl bg-secondary p-3 text-[11px] leading-4 text-muted-foreground"><CircleAlert className="mt-0.5 size-3.5 flex-none" /><span>Cette saisie sert d’aperçu. Le skill import-miniature-paints valide et écrit ensuite le référentiel YAML.</span></div>
              </div>
              <div className="grid content-start gap-4">
                <div className="grid gap-4 sm:grid-cols-2">
                  <label className="form-field"><span>Marque *</span><input required value={form.brand} onChange={(event) => setForm({ ...form, brand: event.target.value })} placeholder="Citadel, Vallejo…" /></label>
                  <label className="form-field"><span>Gamme</span><input value={form.range} onChange={(event) => setForm({ ...form, range: event.target.value })} placeholder="Model Color, Layer…" /></label>
                </div>
                <label className="form-field"><span>Nom de la teinte *</span><input required value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} placeholder="Mephiston Red" /></label>
                <div className="grid gap-4 sm:grid-cols-2">
                  <label className="form-field"><span>Référence</span><input value={form.reference} onChange={(event) => setForm({ ...form, reference: event.target.value })} placeholder="70.950" /></label>
                  <label className="form-field"><span>Fini</span><select value={form.finish} onChange={(event) => setForm({ ...form, finish: event.target.value })}><option>mat</option><option>satiné</option><option>brillant</option><option>métallique</option><option>transparent</option></select></label>
                </div>
                <div className="grid gap-4 sm:grid-cols-2">
                  <label className="form-field"><span>Couleur approchée</span><span className="flex h-11 items-center gap-2 rounded-xl border bg-card px-2"><input className="size-8 border-0 p-0" type="color" value={form.colorHex} onChange={(event) => setForm({ ...form, colorHex: event.target.value })} /><code className="text-xs">{form.colorHex}</code></span></label>
                  <label className="form-field"><span>Quantité</span><input type="number" min="1" max="20" value={form.quantity} onChange={(event) => setForm({ ...form, quantity: Number(event.target.value) })} /></label>
                </div>
                <div className="mt-2 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
                  <button type="button" onClick={closeModal} className="h-11 rounded-xl border bg-card px-4 text-sm font-semibold">Annuler</button>
                  <button disabled={saving} className="inline-flex h-11 items-center justify-center gap-2 rounded-xl bg-primary px-5 text-sm font-semibold text-primary-foreground disabled:opacity-50">{saving ? 'Enregistrement…' : <><Check size={16} />Valider la peinture</>}</button>
                </div>
              </div>
            </form>
          </div>
        </div>
      )}
    </main>
  );
}
