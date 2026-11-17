// The copied map files must keep working here. Run with `node --test` (Node 22 reads the .ts files directly).
import test from 'node:test';
import assert from 'node:assert/strict';
import { toMgrs } from '../src/lib/coords.ts';
import { readOverlays } from '../src/lib/overlays.ts';

test('a position is written as a military grid reference', () => {
  assert.match(toMgrs(39.485, -80.139, 4), /^17S [A-Z]{2} \d{4} \d{4}$/);
});

test('the person\'s own pins come out cleaned', () => {
  const o = readOverlays({ pins: [{ label: 'Camp', lat: 39.48, lon: -80.13 }, { label: 'Bad', lat: 999, lon: 0 }], notes: 'hi' });
  assert.equal(o.pins.length, 1);
  assert.equal(o.pins[0].label, 'Camp');
});
