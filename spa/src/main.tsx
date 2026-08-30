import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { PaintAppLoader } from '@/components/paint-app-loader';
import '@/app/globals.css';

const root = document.getElementById('root');
if (!root) throw new Error('Missing SPA root element.');

createRoot(root).render(
  <StrictMode>
    <PaintAppLoader />
  </StrictMode>,
);
