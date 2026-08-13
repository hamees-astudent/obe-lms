/**
 * Date/time conversion between the API and `<input type="date">` /
 * `<input type="datetime-local">` controls.
 *
 * The backend uses `java.time.LocalDateTime` / `java.time.LocalDate`, which
 * Jackson serialises **without** a zone or offset ("2026-08-13T09:00:00",
 * "2026-08-13"). Sending an offset — as `Date#toISOString()` does with its
 * trailing `Z` — makes Jackson reject the payload with a 400, so every datetime
 * that crosses the wire must stay zone-free. Everything below keeps values in
 * the viewer's local zone, which is also how the browser interprets an ISO
 * date-time string that carries no offset.
 */

/** `<input type="datetime-local">` value → API `LocalDateTime` string. */
export function toApiDateTime(value?: string | null): string | undefined {
  if (!value) return undefined;
  // "2026-08-13T09:00" → "2026-08-13T09:00:00"
  return value.length === 16 ? `${value}:00` : value;
}

/** API `LocalDateTime` string → `<input type="datetime-local">` value. */
export function toInputDateTime(iso?: string | null): string {
  if (!iso) return '';
  return iso.slice(0, 16);
}

/** API `LocalDate` string → `<input type="date">` value. */
export function toInputDate(iso?: string | null): string {
  if (!iso) return '';
  return iso.slice(0, 10);
}

/** Today as an API `LocalDate` string, in the viewer's zone (not UTC). */
export function todayApiDate(): string {
  const now = new Date();
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}

/** Formats an API date-time for display. */
export function formatDateTime(iso?: string | null): string {
  if (!iso) return '—';
  return new Date(iso).toLocaleString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

/** Formats an API date for display. */
export function formatDate(iso?: string | null): string {
  if (!iso) return '—';
  // Parse as local midnight so the displayed day never shifts by a zone offset.
  const [y, m, d] = iso.slice(0, 10).split('-').map(Number);
  return new Date(y, m - 1, d).toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  });
}
