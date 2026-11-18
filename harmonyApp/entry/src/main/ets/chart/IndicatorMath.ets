/** Numeric prices use the same fixed-point units as the broker's candles. */
export interface ChartCandle {
  timeMs: number;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number | null;
}

function validatePeriod(period: number): void {
  if (!Number.isInteger(period) || period < 1 || period > 1000) {
    throw new Error('指标周期必须是 1 到 1000 之间的整数');
  }
}

/** NaN values break a series. The next window starts afresh. */
export function sma(values: number[], period: number): number[] {
  validatePeriod(period);
  const out: number[] = new Array<number>(values.length).fill(Number.NaN);
  let sum: number = 0;
  let count: number = 0;
  for (let i: number = 0; i < values.length; i++) {
    const value: number = values[i];
    if (Number.isNaN(value)) { sum = 0; count = 0; continue; }
    sum += value;
    count += 1;
    if (count > period) { sum -= values[i - period]; }
    if (count >= period) { out[i] = sum / period; }
  }
  return out;
}

/** Seed with an SMA of the first complete window, matching the existing app. */
export function ema(values: number[], period: number): number[] {
  validatePeriod(period);
  const out: number[] = new Array<number>(values.length).fill(Number.NaN);
  const alpha: number = 2 / (period + 1);
  let previous: number = Number.NaN;
  let sum: number = 0;
  let count: number = 0;
  for (let i: number = 0; i < values.length; i++) {
    const value: number = values[i];
    if (Number.isNaN(value)) { previous = Number.NaN; sum = 0; count = 0; continue; }
    if (Number.isNaN(previous)) {
      sum += value;
      count += 1;
      if (count === period) { previous = sum / period; out[i] = previous; }
    } else {
      previous = alpha * value + (1 - alpha) * previous;
      out[i] = previous;
    }
  }
  return out;
}

export function bollinger(values: number[], period: number, width: number): number[][] {
  if (!Number.isFinite(width) || width < 0.1 || width > 10) { throw new Error('带宽倍数须在 0.1 到 10'); }
  const middle: number[] = sma(values, period);
  const upper: number[] = new Array<number>(values.length).fill(Number.NaN);
  const lower: number[] = new Array<number>(values.length).fill(Number.NaN);
  for (let i: number = 0; i < values.length; i++) {
    const mean: number = middle[i];
    if (Number.isNaN(mean)) { continue; }
    let squares: number = 0;
    for (let j: number = i - period + 1; j <= i; j++) { squares += (values[j] - mean) ** 2; }
    const delta: number = width * Math.sqrt(squares / period);
    upper[i] = mean + delta;
    lower[i] = mean - delta;
  }
  return [middle, upper, lower];
}

export function rsi(values: number[], period: number): number[] {
  validatePeriod(period);
  const out: number[] = new Array<number>(values.length).fill(Number.NaN);
  if (values.length <= period) { return out; }
  let gain: number = 0;
  let loss: number = 0;
  for (let i: number = 1; i <= period; i++) {
    const delta: number = values[i] - values[i - 1];
    if (delta > 0) { gain += delta; } else { loss -= delta; }
  }
  gain /= period;
  loss /= period;
  out[period] = gain + loss === 0 ? 50 : 100 * gain / (gain + loss);
  for (let i: number = period + 1; i < values.length; i++) {
    const delta: number = values[i] - values[i - 1];
    gain = (gain * (period - 1) + Math.max(delta, 0)) / period;
    loss = (loss * (period - 1) + Math.max(-delta, 0)) / period;
    out[i] = gain + loss === 0 ? 50 : 100 * gain / (gain + loss);
  }
  return out;
}

export function macd(values: number[], fast: number, slow: number, signal: number): number[][] {
  validatePeriod(fast); validatePeriod(slow); validatePeriod(signal);
  if (fast >= slow) { throw new Error('MACD 快线须小于慢线'); }
  const quick: number[] = ema(values, fast);
  const lazy: number[] = ema(values, slow);
  const dif: number[] = values.map((_value: number, i: number): number => quick[i] - lazy[i]);
  const dea: number[] = ema(dif, signal);
  const bars: number[] = dif.map((value: number, i: number): number => (value - dea[i]) * 2);
  return [dif, dea, bars];
}

export function kdj(candles: ChartCandle[], period: number, kSmooth: number, dSmooth: number): number[][] {
  validatePeriod(period); validatePeriod(kSmooth); validatePeriod(dSmooth);
  const k: number[] = new Array<number>(candles.length).fill(Number.NaN);
  const d: number[] = new Array<number>(candles.length).fill(Number.NaN);
  const j: number[] = new Array<number>(candles.length).fill(Number.NaN);
  let previousK: number = 50;
  let previousD: number = 50;
  for (let i: number = period - 1; i < candles.length; i++) {
    let high: number = Number.NEGATIVE_INFINITY;
    let low: number = Number.POSITIVE_INFINITY;
    for (let x: number = i - period + 1; x <= i; x++) {
      high = Math.max(high, candles[x].high);
      low = Math.min(low, candles[x].low);
    }
    const rsv: number = high === low ? 50 : (candles[i].close - low) / (high - low) * 100;
    previousK = ((kSmooth - 1) * previousK + rsv) / kSmooth;
    previousD = ((dSmooth - 1) * previousD + previousK) / dSmooth;
    k[i] = previousK; d[i] = previousD; j[i] = 3 * previousK - 2 * previousD;
  }
  return [k, d, j];
}

export function atr(candles: ChartCandle[], period: number): number[] {
  validatePeriod(period);
  const out: number[] = new Array<number>(candles.length).fill(Number.NaN);
  let previous: number = Number.NaN;
  let sum: number = 0;
  for (let i: number = 0; i < candles.length; i++) {
    const c: ChartCandle = candles[i];
    const range: number = i === 0 ? c.high - c.low : Math.max(c.high - c.low,
      Math.abs(c.high - candles[i - 1].close), Math.abs(c.low - candles[i - 1].close));
    if (Number.isNaN(previous)) {
      sum += range;
      if (i === period - 1) { previous = sum / period; out[i] = previous; }
    } else {
      previous = (previous * (period - 1) + range) / period;
      out[i] = previous;
    }
  }
  return out;
}
