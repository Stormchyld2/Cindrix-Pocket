// Tiles reach the map engine through its own tile loader so the page, not the web view, unpacks them.
// This is the standalone map viewer's tiles.ts (gzip and plain tiles) with one addition: zstd, which Cindrix Ops can
// write when a map pack was packed that way. The viewer has no zstd decoder yet; this one uses fzstd (MIT).
import type * as MapLibre from 'maplibre-gl';
import { decompress } from 'fzstd';

let registered = false;
let blank: Promise<ArrayBuffer> | null = null;

/** gzip data starts with the two bytes 1f 8b. */
export function isGzip(b: ArrayBuffer): boolean {
  if (b.byteLength < 2) return false;
  const v = new Uint8Array(b, 0, 2);
  return v[0] === 0x1f && v[1] === 0x8b;
}

/** A zstd frame starts with the four bytes 28 b5 2f fd. */
export function isZstd(b: ArrayBuffer): boolean {
  if (b.byteLength < 4) return false;
  const v = new Uint8Array(b, 0, 4);
  return v[0] === 0x28 && v[1] === 0xb5 && v[2] === 0x2f && v[3] === 0xfd;
}

async function gunzip(b: ArrayBuffer): Promise<ArrayBuffer> {
  const stream = new Blob([b]).stream().pipeThrough(new DecompressionStream('gzip'));
  return new Response(stream).arrayBuffer();
}

/** The tile as the map engine wants it: gzip and zstd packing taken off, anything else left as it is. */
export async function unpack(b: ArrayBuffer): Promise<ArrayBuffer> {
  if (isGzip(b)) return gunzip(b);
  if (isZstd(b)) {
    const out = decompress(new Uint8Array(b));
    return out.buffer.slice(out.byteOffset, out.byteOffset + out.byteLength) as ArrayBuffer;
  }
  return b;
}

/** A clear one-pixel picture, for a square of a picture map that the file does not hold. */
function clearPicture(): Promise<ArrayBuffer> {
  blank ??= new Promise<ArrayBuffer>((ok, bad) => {
    const c = document.createElement('canvas');
    c.width = 1;
    c.height = 1;
    c.toBlob((blob) => (blob ? blob.arrayBuffer().then(ok, bad) : bad(new Error('no picture'))), 'image/png');
  });
  return blank;
}

/** Make `cmaptile://<kind>/<z>/<x>/<y>` addresses work. `base` is where the app serves the open map's tiles. */
export function registerTileProtocol(ml: typeof MapLibre, base: string): void {
  if (registered) return;
  registered = true;
  ml.addProtocol('cmaptile', async (params, abort) => {
    const rest = params.url.slice('cmaptile://'.length);
    const kind = rest.split('/')[0];
    const res = await fetch(base + 'tiles/' + rest, { signal: abort.signal });
    // A square the map does not hold (outside its area, or finer than it was cut): empty, not a fault.
    if (res.status === 404 || res.status === 204) return { data: kind === 'street' ? new ArrayBuffer(0) : await clearPicture() };
    if (!res.ok) throw new Error('The map file could not give a tile (' + res.status + ').');
    return { data: await unpack(await res.arrayBuffer()) };
  });
}
