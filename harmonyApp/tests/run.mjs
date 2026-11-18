import assert from 'node:assert/strict';
import { cp, mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const root = join(here, '../entry/src/main/ets/chart');
const tmp = await mkdtemp(join(tmpdir(), 'unitrading-harmony-'));
try {
  await cp(join(root, 'IndicatorMath.ets'), join(tmp, 'IndicatorMath.ts'));
  await cp(join(root, 'Timeframe.ets'), join(tmp, 'Timeframe.ts'));
  const m = await import(pathToFileURL(join(tmp, 'IndicatorMath.ts')));
  const t = await import(pathToFileURL(join(tmp, 'Timeframe.ts')));
  const nan = Number.NaN;
  const near = (actual, expected) => {
    assert.equal(actual.length, expected.length);
    expected.forEach((v, i) => Number.isNaN(v) ? assert.ok(Number.isNaN(actual[i]), `index ${i}`) : assert.ok(Math.abs(v - actual[i]) < 1e-8, `index ${i}: ${actual[i]} != ${v}`));
  };
  near(m.sma([1, 2, 3, 4, 5, 6], 3), [nan, nan, 2, 3, 4, 5]);
  near(m.ema([1, 2, 3, 4, 5, 6], 3), [nan, nan, 2, 3, 4, 5]);
  near(m.sma([1, 2, nan, 4, 5], 2), [nan, 1.5, nan, nan, 4.5]);
  near(m.ema([1, 2, nan, 4, 5], 2), [nan, 1.5, nan, nan, 4.5]);
  const bands = m.bollinger([1, 2, 3, 4, 5], 3, 2);
  const dev = Math.sqrt(2 / 3);
  near(bands[0], [nan, nan, 2, 3, 4]);
  near(bands[1], [nan, nan, 2 + 2 * dev, 3 + 2 * dev, 4 + 2 * dev]);
  near(bands[2], [nan, nan, 2 - 2 * dev, 3 - 2 * dev, 4 - 2 * dev]);
  near(m.rsi([10, 11, 10, 12, 11], 2), [nan, nan, 50, 100 * 1.25 / 1.5, 50]);
  near(m.rsi([5, 5, 5, 5], 2), [nan, nan, 50, 50]);
  const lines = m.macd(Array.from({ length: 50 }, (_, i) => i), 12, 26, 9);
  near([lines[0][49], lines[1][49], lines[2][49]], [7, 7, 0]);
  const candles = [[10, 8, 9], [12, 9, 11], [11, 7, 8]].map(([high, low, close], timeMs) => ({ timeMs, open: close, high, low, close, volume: null }));
  near(m.atr(candles, 2), [nan, 2.5, 3.25]);
  const rising = Array.from({ length: 6 }, (_, i) => ({ timeMs: i, open: i, high: i + 10, low: i, close: i + 10, volume: null }));
  const kdj = m.kdj(rising, 3, 3, 3);
  assert.ok(Math.abs(kdj[0][2] - 200 / 3) < 1e-8);
  assert.deepEqual(t.sortFavorites(['1h', '5m', '1s', '3m', '1m']), ['1s', '1m', '3m', '5m', '1h']);
  assert.equal(t.intervalSeconds('367d'), null);
  assert.throws(() => t.sortFavorites(['1m', 'bad']));
  assert.throws(() => m.ema([1], 0));
  assert.throws(() => m.macd([1], 4, 2, 1));
  console.log('PASS: indicators and interval ordering; native HarmonyOS build is not covered');
} finally {
  await rm(tmp, { recursive: true, force: true });
}
