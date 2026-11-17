// The map inside Cindrix Pocket. It is the standalone Cindrix map viewer's map code (the files in ./lib are copied
// from it unchanged), run in a web view. The app serves the open .cmap file's tiles and lettering at
// https://cmap.pocket/tiles/<kind>/<z>/<x>/<y> and /glyphs/<font>/<range>.pbf, and the map's description at /info.json.
// The app tells this page where the phone is (pocketMap.setMe) and the track being walked (pocketMap.setTrack).
import maplibregl from 'maplibre-gl';
import type { Map as MLMap, Marker, StyleSpecification } from 'maplibre-gl';
import { layers as basemapLayers, namedFlavor } from '@protomaps/basemaps';
import { toDms, toMgrs } from './lib/coords';
import { drawingFeatures, fmtLength, labelPoint } from './lib/mapdraw';
import { readOverlays, routeLine, type Overlays } from './lib/overlays';
import { registerTileProtocol } from './tiles';

type Kind = 'street' | 'topo' | 'aerial';
const KIND_NAMES: Record<Kind, string> = { street: 'Street', topo: 'Topo', aerial: 'Aerial' };
const KIND_ORDER: Kind[] = ['street', 'topo', 'aerial'];

type Layer = { kind: string; tile_type: string; compression: string; min_zoom: number; max_zoom: number };
type Info = {
  name: string;
  bbox: [number, number, number, number];
  view: { center: [number, number]; zoom: number };
  layers: Layer[];
  glyphs: number;
  map: unknown;
  unsupported: string[];
  metric: boolean;
};
type Bridge = { locate?: () => void; locateOff?: () => void };
declare global {
  interface Window {
    Android?: Bridge;
    pocketMap: { setMe: (lat: number, lon: number, accM: number) => void; clearMe: () => void; setTrack: (pts: number[][]) => void; note: (text: string) => void };
  }
}

const $ = <T extends HTMLElement>(id: string) => document.getElementById(id) as T;
const lonlat = (p: [number, number]): [number, number] => [p[1], p[0]];
const empty = () => ({ type: 'FeatureCollection' as const, features: [] as unknown[] });

let info: Info;
let overlays: Overlays;
let map: MLMap;
let kind: Kind = 'street';
let kinds: Kind[] = [];
let readout: 'mgrs' | 'dms' | 'deg' = 'mgrs';
let centre: [number, number] = [0, 0];
let me: Marker | null = null;
let locating = false;
const markers: Marker[] = [];
let vecIds: { id: string; type: string }[] = [];
const rasterIds: Partial<Record<Kind, string>> = {};

function note(text: string) {
  const n = $('note');
  n.textContent = text;
  n.hidden = !text;
}

async function begin() {
  try {
    const res = await fetch('/info.json');
    if (!res.ok) throw new Error('no map');
    info = (await res.json()) as Info;
  } catch {
    note('No map is open. Choose a map file first.');
    return;
  }
  overlays = readOverlays(info.map);
  $('name').textContent = info.name;
  kinds = KIND_ORDER.filter((k) => info.layers.some((l) => l.kind === k && !info.unsupported.includes(l.kind)));
  if (!kinds.length) {
    note('This map file holds only pictures Pocket cannot open yet.');
    return;
  }
  if (info.unsupported.length) {
    note('Part of this map (' + info.unsupported.map((k) => KIND_NAMES[k as Kind] ?? k).join(', ') + ') uses a kind of packing Pocket cannot open yet. The rest is shown.');
  }
  kind = kinds.includes('street') ? 'street' : kinds[0];
  drawMap();
  buildKinds();
  buildPanel();
}

function drawMap() {
  const base = location.origin + '/';
  registerTileProtocol(maplibregl as never, base);
  const hasLetters = info.glyphs > 0;
  const usable = info.layers.filter((l) => !info.unsupported.includes(l.kind));
  const sources: Record<string, unknown> = {};
  const rasters: unknown[] = [];
  for (const l of usable) {
    const url = 'cmaptile://' + l.kind + '/{z}/{x}/{y}';
    if (l.kind === 'street') {
      sources.street = { type: 'vector', tiles: [url], minzoom: l.min_zoom, maxzoom: l.max_zoom, attribution: '© OpenStreetMap contributors' };
    } else if (l.kind === 'topo' || l.kind === 'aerial') {
      sources[l.kind] = { type: 'raster', tiles: [url], tileSize: 256, minzoom: l.min_zoom, maxzoom: l.max_zoom, attribution: l.kind === 'topo' ? 'Topo: USGS The National Map' : 'Imagery: USGS The National Map' };
      rasters.push({ id: 'r-' + l.kind, type: 'raster', source: l.kind, layout: { visibility: 'none' } });
      rasterIds[l.kind as Kind] = 'r-' + l.kind;
    }
  }
  // street drawing rules: the same ones the main program uses; letters only when the file carries the fonts
  const keep = (l: { type: string; layout?: Record<string, unknown>; 'source-layer'?: string }) =>
    l.type !== 'symbol' || (hasLetters && l['source-layer'] !== 'pois' && !(l.layout && 'icon-image' in l.layout));
  const vec = (sources.street ? (basemapLayers('street', namedFlavor('light'), { lang: 'en' }) as unknown as { id: string; type: string }[]).filter((l) => keep(l as never)) : []) as { id: string; type: string }[];
  vecIds = vec.map((l) => ({ id: l.id, type: l.type }));
  const bg = vec.length ? [vec[0]] : [{ id: 'bg', type: 'background', paint: { 'background-color': '#e8e4d8' } }];
  const style = {
    version: 8,
    ...(hasLetters ? { glyphs: base + 'glyphs/{fontstack}/{range}.pbf' } : {}),
    sources,
    layers: [...bg, ...rasters, ...vec.slice(vec.length ? 1 : 0)],
  } as unknown as StyleSpecification;
  const [w, s, e, n] = info.bbox;
  const lowest = Math.min(...usable.map((l) => l.min_zoom));
  map = new maplibregl.Map({
    container: 'map',
    style,
    center: [info.view.center[1], info.view.center[0]],
    zoom: info.view.zoom,
    minZoom: Math.max(0, lowest - 1),
    maxZoom: 22,
    attributionControl: { compact: true },
    pixelRatio: window.devicePixelRatio || 1,
    fadeDuration: 0,
    maxTileCacheSize: 120,
    maxBounds: [[w - 0.1, s - 0.1], [e + 0.1, n + 0.1]],
    // north stays up: a grid reference only makes sense on a map that does not turn
    dragRotate: false,
    pitchWithRotate: false,
    touchPitch: false,
  });
  map.touchZoomRotate.disableRotation();
  map.addControl(new maplibregl.ScaleControl({ unit: info.metric ? 'metric' : 'imperial' }), 'bottom-left');
  map.on('error', (ev) => {
    const er = ev as unknown as { error?: { status?: number }; tile?: unknown };
    if (er.error?.status === 404) return; // a square the map does not hold, or a missing letter: not a broken map
    if (er.tile) note('Some of the map could not be read: the file may be damaged.');
  });
  const sync = () => {
    const c = map.getCenter();
    centre = [c.lat, c.lng];
    showReadout();
  };
  map.on('move', sync);
  map.on('load', () => {
    sync();
    wireOverlays();
    applyKind();
  });
  const resize = () => map.resize();
  window.addEventListener('resize', resize);
  window.addEventListener('orientationchange', resize);
}

function wireOverlays() {
  const o = overlays;
  for (const id of ['route', 'nogo', 'draw', 'dir', 'track']) map.addSource(id, { type: 'geojson', data: empty() as never });
  map.addLayer({ id: 'nogo-fill', type: 'fill', source: 'nogo', paint: { 'fill-color': '#e5533d', 'fill-opacity': 0.25 } });
  map.addLayer({ id: 'nogo-line', type: 'line', source: 'nogo', paint: { 'line-color': '#e5533d', 'line-width': 2, 'line-dasharray': [2, 2] } });
  map.addLayer({ id: 'draw-fill', type: 'fill', source: 'draw', filter: ['==', ['geometry-type'], 'Polygon'], paint: { 'fill-color': ['get', 'color'], 'fill-opacity': 0.22 } });
  map.addLayer({ id: 'draw-line', type: 'line', source: 'draw', layout: { 'line-cap': 'round', 'line-join': 'round' }, paint: { 'line-color': ['get', 'color'], 'line-width': ['get', 'width'] } });
  map.addLayer({ id: 'route-line', type: 'line', source: 'route', layout: { 'line-cap': 'round', 'line-join': 'round' }, paint: { 'line-color': '#1a73e8', 'line-width': 4, 'line-dasharray': [1, 1.5] } });
  map.addLayer({ id: 'dir-casing', type: 'line', source: 'dir', layout: { 'line-cap': 'round', 'line-join': 'round' }, paint: { 'line-color': '#ffffff', 'line-width': 9 } });
  map.addLayer({ id: 'dir-line', type: 'line', source: 'dir', layout: { 'line-cap': 'round', 'line-join': 'round' }, paint: { 'line-color': '#1a73e8', 'line-width': 5.5 } });
  map.addLayer({ id: 'track-casing', type: 'line', source: 'track', layout: { 'line-cap': 'round', 'line-join': 'round' }, paint: { 'line-color': '#ffffff', 'line-width': 7 } });
  map.addLayer({ id: 'track-line', type: 'line', source: 'track', layout: { 'line-cap': 'round', 'line-join': 'round' }, paint: { 'line-color': '#d93025', 'line-width': 4 } });
  const src = (id: string, data: unknown) => (map.getSource(id) as { setData: (d: unknown) => void } | undefined)?.setData(data);
  const rp = routeLine(o);
  src('route', rp.length > 1 && !o.directions ? { type: 'Feature', properties: {}, geometry: { type: 'LineString', coordinates: rp.map(lonlat) } } : empty());
  src('nogo', { type: 'FeatureCollection', features: o.nogo.map((a) => ({ type: 'Feature', properties: {}, geometry: { type: 'Polygon', coordinates: [[...a.points, a.points[0]].map(lonlat)] } })) });
  src('draw', drawingFeatures(o.drawings, null));
  src('dir', o.directions ? { type: 'Feature', properties: {}, geometry: { type: 'LineString', coordinates: o.directions.points.map(lonlat) } } : empty());
  // pins and names are page elements: text is set as text, never as markup
  for (const p of o.pins) {
    const d = document.createElement('div');
    d.className = 'pinmark';
    const dot = document.createElement('span');
    dot.className = 'pdot';
    dot.style.background = p.color;
    const lab = document.createElement('span');
    lab.className = 'plab';
    lab.textContent = p.label;
    d.append(dot, lab);
    markers.push(new maplibregl.Marker({ element: d, anchor: 'left' }).setLngLat([p.lon, p.lat]).addTo(map));
  }
  for (const dr of o.drawings) {
    const at = labelPoint(dr);
    if (!at || !dr.label) continue;
    const d = document.createElement('div');
    d.className = dr.kind === 'text' ? 'dtext' : 'dname';
    d.textContent = dr.label;
    d.style.color = dr.kind === 'text' ? dr.color : '#202124';
    markers.push(new maplibregl.Marker({ element: d, anchor: 'center' }).setLngLat(lonlat(at)).addTo(map));
  }
  if (pendingTrack) setTrack(pendingTrack);
}

/** Show the chosen picture; street lines and names stay on top of a topo or aerial picture. */
function applyKind() {
  if (!map || !map.isStyleLoaded()) return;
  for (const k of ['topo', 'aerial'] as Kind[]) {
    const id = rasterIds[k];
    if (id && map.getLayer(id)) map.setLayoutProperty(id, 'visibility', kind === k ? 'visible' : 'none');
  }
  for (const l of vecIds) {
    if (!map.getLayer(l.id)) continue;
    const hide = kind !== 'street' && (l.type === 'fill' || l.type === 'background');
    map.setLayoutProperty(l.id, 'visibility', hide ? 'none' : 'visible');
  }
}

function buildKinds() {
  const box = $('kinds');
  box.replaceChildren();
  if (kinds.length < 2) return;
  for (const k of kinds) {
    const b = document.createElement('button');
    b.textContent = KIND_NAMES[k];
    b.title = 'Show the ' + KIND_NAMES[k].toLowerCase() + ' map';
    if (k === kind) b.className = 'on';
    b.onclick = () => {
      kind = k;
      for (const x of Array.from(box.children)) x.className = x === b ? 'on' : '';
      applyKind();
    };
    box.append(b);
  }
}

function flyTo(lat: number, lon: number) {
  map.flyTo({ center: [lon, lat], zoom: Math.max(map.getZoom(), 14), duration: 500 });
  $('panel').hidden = true;
}

function row(title: string, sub: string, at?: [number, number]): HTMLElement {
  const r = document.createElement('div');
  r.className = 'row';
  r.textContent = title;
  if (sub) {
    const s = document.createElement('small');
    s.textContent = sub;
    r.append(s);
  }
  if (at) r.onclick = () => flyTo(at[0], at[1]);
  return r;
}

function buildPanel() {
  const o = overlays;
  const p = $('panel');
  p.replaceChildren();
  const head = (t: string) => {
    const h = document.createElement('h3');
    h.textContent = t;
    p.append(h);
  };
  const total = o.pins.length + o.nogo.length + o.drawings.length + (o.directions ? 1 : 0);
  $('badge').textContent = total ? String(total) : '';
  if (o.pins.length) {
    head('PINS');
    for (const pin of o.pins) p.append(row(pin.label, (pin.kind === 'rendezvous' ? 'Meeting place' : pin.tag) || (toMgrs(pin.lat, pin.lon, 4) ?? ''), [pin.lat, pin.lon]));
  }
  if (o.nogo.length) {
    head('AREAS TO AVOID');
    for (const a of o.nogo) p.append(row(a.label, '', a.points[0]));
  }
  if (o.drawings.length) {
    head('DRAWINGS');
    for (const d of o.drawings) p.append(row(d.label || d.kind, '', d.points[0]));
  }
  if (o.directions) {
    head('DIRECTIONS');
    p.append(row(fmtLength(o.directions.km, info.metric) + ', about ' + mins(o.directions.minutes), ''));
    for (const s of o.directions.steps) p.append(row(s.name || 'Continue', fmtLength(s.km, info.metric) + ' · ' + mins(s.minutes)));
  }
  if (o.notes) {
    head('NOTES');
    p.append(row(o.notes, ''));
  }
  if (!p.children.length) p.append(row('This map has no pins, areas or directions saved with it.', ''));
}

const mins = (m: number) => (m < 60 ? Math.round(m) + ' min' : Math.floor(m / 60) + ' h ' + Math.round(m % 60) + ' min');

function showReadout() {
  const t = readout === 'mgrs' ? (toMgrs(centre[0], centre[1], 4) ?? toDms(centre[0], centre[1])) : readout === 'dms' ? toDms(centre[0], centre[1]) : centre[0].toFixed(5) + ', ' + centre[1].toFixed(5);
  $('readout').textContent = t;
}

// ---------- the phone's own position and track, told to this page by the app ----------
let pendingTrack: number[][] | null = null;
function setTrack(pts: number[][]) {
  pendingTrack = pts;
  const s = map?.getSource('track') as { setData: (d: unknown) => void } | undefined;
  if (!s) return;
  s.setData(pts.length > 1 ? { type: 'Feature', properties: {}, geometry: { type: 'LineString', coordinates: pts.map((p) => [p[1], p[0]]) } } : empty());
}

let first = true;
window.pocketMap = {
  setMe(lat, lon, accM) {
    if (!map) return;
    const ll: [number, number] = [lon, lat];
    if (!me) {
      const d = document.createElement('div');
      d.className = 'me';
      me = new maplibregl.Marker({ element: d }).setLngLat(ll).addTo(map);
    } else {
      me.setLngLat(ll);
    }
    $('where').textContent = 'Accurate to about ' + Math.round(accM * (info.metric ? 1 : 3.281)) + (info.metric ? ' m' : ' ft');
    if (first) {
      first = false;
      map.flyTo({ center: ll, zoom: Math.max(map.getZoom(), 15), duration: 500 });
    }
  },
  clearMe() {
    me?.remove();
    me = null;
    first = true;
    $('where').textContent = '';
    locating = false;
    $('locate').className = '';
  },
  setTrack,
  note,
};

$('list').onclick = () => {
  const p = $('panel');
  p.hidden = !p.hidden;
};
$('whole').onclick = () => {
  const [w, s, e, n] = info.bbox;
  map.fitBounds([[w, s], [e, n]], { padding: 24, duration: 400 });
};
$('zin').onclick = () => map?.zoomIn({ duration: 250 });
$('zout').onclick = () => map?.zoomOut({ duration: 250 });
$('readout').onclick = () => {
  readout = readout === 'mgrs' ? 'dms' : readout === 'dms' ? 'deg' : 'mgrs';
  showReadout();
};
$('locate').onclick = () => {
  if (!window.Android?.locate) {
    note('This device cannot tell where it is.');
    return;
  }
  if (locating) {
    window.Android.locateOff?.();
    window.pocketMap.clearMe();
    return;
  }
  locating = true;
  $('locate').className = 'on';
  $('where').textContent = 'Looking for your position…';
  window.Android.locate();
};

void begin();
