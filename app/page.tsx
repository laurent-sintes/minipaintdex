import { PaintApp } from '@/components/paint-app';
import { paintCatalog, projectCatalog } from '@/lib/catalog';
import { shoppingSeed } from '@/lib/shopping-data';

export default function Home() {
  return <PaintApp initialPaints={paintCatalog} projects={projectCatalog} shoppingSeed={shoppingSeed} />;
}
