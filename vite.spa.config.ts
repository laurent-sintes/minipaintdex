import path from 'node:path';
import tailwindcss from '@tailwindcss/postcss';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

export default defineConfig({
  root: path.resolve(import.meta.dirname, 'spa'),
  publicDir: path.resolve(import.meta.dirname, 'public'),
  resolve: {
    alias: { '@': path.resolve(import.meta.dirname) },
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
    outDir: path.resolve(import.meta.dirname, 'target/spa'),
    emptyOutDir: true,
  },
});
