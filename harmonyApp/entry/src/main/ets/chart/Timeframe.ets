const MAX_SECONDS: number = 366 * 86400;

/** Interpret favourite intervals in seconds to preserve ordering across units. */
export function intervalSeconds(label: string): number | null {
  const match: RegExpMatchArray | null = label.trim().match(/^(\d+)(s|m|h|d)$/i);
  if (match === null) { return null; }
  const count: number = Number(match[1]);
  const unit: string = match[2].toLowerCase();
  const multiplier: number = unit === 's' ? 1 : unit === 'm' ? 60 : unit === 'h' ? 3600 : 86400;
  const seconds: number = count * multiplier;
  if (!Number.isSafeInteger(seconds) || seconds < 1 || seconds > MAX_SECONDS) { return null; }
  return seconds;
}

export function sortFavorites(labels: string[]): string[] {
  const result: string[] = labels.slice();
  for (let i: number = 0; i < result.length; i++) {
    if (intervalSeconds(result[i]) === null) { throw new Error('周期格式无效'); }
  }
  return result.sort((a: string, b: string): number => (intervalSeconds(a) as number) - (intervalSeconds(b) as number));
}
