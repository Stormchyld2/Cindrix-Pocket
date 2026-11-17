// Unpacking a tile: gzip, zstd and plain all come out as the same bytes. Needs `npm install` first (fzstd).
// Node's own zstd writer (22.15 and later) makes the test data; without it the zstd check is skipped.
import test from 'node:test';
import assert from 'node:assert/strict';
import zlib from 'node:zlib';
import { unpack, isGzip, isZstd } from '../src/tiles.ts';

const sample = Buffer.from('a street tile, repeated: ' + 'road '.repeat(400));
const asBuffer = (b) => b.buffer.slice(b.byteOffset, b.byteOffset + b.byteLength);

test('a plain tile is left as it is', async () => {
  const out = Buffer.from(await unpack(asBuffer(sample)));
  assert.deepEqual(out, sample);
  assert.ok(!isGzip(asBuffer(sample)) && !isZstd(asBuffer(sample)));
});

test('a gzip tile is unpacked', async () => {
  const packed = zlib.gzipSync(sample);
  assert.ok(isGzip(asBuffer(packed)));
  assert.deepEqual(Buffer.from(await unpack(asBuffer(packed))), sample);
});

test('a zstd tile is unpacked', { skip: typeof zlib.zstdCompressSync !== 'function' }, async () => {
  const packed = zlib.zstdCompressSync(sample);
  assert.ok(isZstd(asBuffer(packed)));
  assert.deepEqual(Buffer.from(await unpack(asBuffer(packed))), sample);
});

test('short or empty answers do not break the check', async () => {
  assert.equal((await unpack(new ArrayBuffer(0))).byteLength, 0);
  assert.equal((await unpack(new Uint8Array([1, 2, 3]).buffer)).byteLength, 3);
});
