# MiniPaintDex

Référentiel local de peintures pour figurines, construit avec React, Vinext, Vite et Cloudflare Workers.

## Prérequis

- Node.js 24 (Node.js 22.13 ou supérieur est supporté)
- pnpm 11.19.0

Le gestionnaire et sa version sont verrouillés par le champ `packageManager` de `package.json`.

## Installation

```powershell
corepack enable
pnpm install --frozen-lockfile
```

## Développement

```powershell
pnpm dev
```

## Validation et build

```powershell
pnpm check
```

Cette commande exécute successivement le lint, le contrôle TypeScript et le build Vite de production. Le même cycle est exécuté automatiquement par GitHub Actions à chaque push et pull request.
