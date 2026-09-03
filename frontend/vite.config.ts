import { readFileSync } from 'node:fs';
import { parse } from 'yaml';
import path from 'node:path';
import tailwindcss from '@tailwindcss/postcss';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

const siteLabels = parse(readFileSync(path.resolve(import.meta.dirname, '../data/site/fr.yaml'), 'utf8'));

export default defineConfig({
  define: { __BOOTSTRAP_LABELS__: JSON.stringify({
    unavailable: siteLabels.errors.unavailable,
    unavailableHelp: siteLabels.errors.unavailable_help,
    loading: siteLabels.errors.loading,
  }) },
  root: import.meta.dirname,
  publicDir: path.resolve(import.meta.dirname, 'public'),
  resolve: {
    alias: { '@': path.resolve(import.meta.dirname, 'src') },
  },
  css: { postcss: { plugins: [tailwindcss()] } },
  plugins: [react()],
  server: {
    proxy: {
      '/api': 'http://127.0.0.1:8080',
      '/media': 'http://127.0.0.1:8080',
    },
  },
  build: {
    outDir: path.resolve(import.meta.dirname, '../target/frontend'),
    emptyOutDir: true,
  },
});
