/** Backend timestamps (timestamptz, e.g. "2026-11-16T12:00:00.123456+00:00") to epoch ms. */
export function toMillis(iso: string | null | undefined): number | null {
  if (!iso) return null;
  const ms = Date.parse(iso);
  return Number.isNaN(ms) ? null : ms;
}

export function elapsedSeconds(fromIso: string | null | undefined, now: number): number {
  const from = toMillis(fromIso);
  if (from === null) return 0;
  return Math.max(0, Math.floor((now - from) / 1000));
}

export function formatDuration(totalSeconds: number): string {
  if (totalSeconds < 60) return `${totalSeconds}s`;
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  if (minutes < 60) return `${minutes}m ${seconds.toString().padStart(2, '0')}s`;
  const hours = Math.floor(minutes / 60);
  return `${hours}h ${(minutes % 60).toString().padStart(2, '0')}m`;
}

export function formatClock(iso: string | null | undefined): string {
  const ms = toMillis(iso);
  if (ms === null) return '';
  return new Date(ms).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

export function orderLabel(orderNumber: number): string {
  return `#${orderNumber.toString().padStart(3, '0')}`;
}
