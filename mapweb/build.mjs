// Builds the map page into the app's assets (app/src/main/assets/map/). The Android build runs this first
// (needs `npm install` once, which the CI job does). Nothing here runs on the phone.
import { build } from 'esbuild';
import { copyFileSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const out = join(here, '..', 'app', 'src', 'main', 'assets', 'map');
mkdirSync(out, { recursive: true });

await build({
  entryPoints: [join(here, 'src', 'main.ts')],
  bundle: true,
  format: 'iife',
  target: ['chrome90'],
  minify: true,
  outfile: join(out, 'app.js'),
  legalComments: 'none',
  logLevel: 'info',
});
copyFileSync(join(here, 'node_modules', 'maplibre-gl', 'dist', 'maplibre-gl.css'), join(out, 'maplibre-gl.css'));
copyFileSync(join(here, 'src', 'index.html'), join(out, 'index.html'));
copyFileSync(join(here, 'src', 'page.css'), join(out, 'page.css'));
// the licences of what was bundled travel with the app
const lic = (name) => readFileSync(join(here, 'node_modules', name, 'LICENSE'), 'utf8');
writeFileSync(join(out, 'LICENSES.txt'), 'MapLibre GL JS (BSD-3-Clause)\n\n' + lic('maplibre-gl') + '\n\nProtomaps basemaps (BSD-3-Clause)\n\n' + lic('@protomaps/basemaps'));
