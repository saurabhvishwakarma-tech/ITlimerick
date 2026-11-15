// All times come from the API in UTC and are shown in the event's own timezone (Europe/Dublin
// by default), so attendees see venue time even if their phone is set to another zone.

export function formatTime(iso: string, timeZone: string): string {
  return new Intl.DateTimeFormat('en-IE', { hour: '2-digit', minute: '2-digit', hour12: false, timeZone }).format(
    new Date(iso),
  );
}

export function formatDay(iso: string, timeZone: string): string {
  return new Intl.DateTimeFormat('en-IE', { weekday: 'long', day: 'numeric', month: 'long', timeZone }).format(
    new Date(iso),
  );
}

export function formatDateTime(iso: string, timeZone: string): string {
  return new Intl.DateTimeFormat('en-IE', {
    day: 'numeric',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
    timeZone,
  }).format(new Date(iso));
}

/** A key like "2027-02-18" for grouping sessions by day in the event timezone. */
export function dayKey(iso: string, timeZone: string): string {
  const p = parts(new Date(iso), timeZone);
  return `${p.year}-${p.month}-${p.day}`;
}

export function relativeTime(iso: string, now: number = Date.now()): string {
  const minutes = Math.round((now - new Date(iso).getTime()) / 60000);
  if (minutes < 1) return 'just now';
  if (minutes < 60) return `${minutes} min ago`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours} h ago`;
  const days = Math.round(hours / 24);
  return `${days} day${days === 1 ? '' : 's'} ago`;
}

function parts(date: Date, timeZone: string) {
  const out: Record<string, string> = {};
  for (const p of new Intl.DateTimeFormat('en-GB', {
    timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(date)) {
    out[p.type] = p.value;
  }
  return out as { year: string; month: string; day: string; hour: string; minute: string; second: string };
}

/** UTC ISO string -> value for an <input type="datetime-local">, in the event timezone. */
export function toLocalInput(iso: string | null | undefined, timeZone: string): string {
  if (!iso) return '';
  const p = parts(new Date(iso), timeZone);
  return `${p.year}-${p.month}-${p.day}T${p.hour}:${p.minute}`;
}

/** Value from an <input type="datetime-local"> (venue wall-clock time) -> UTC ISO string. */
export function fromLocalInput(value: string, timeZone: string): string | null {
  if (!value) return null;
  const [date, time] = value.split('T');
  const [y, m, d] = date.split('-').map(Number);
  const [hh, mm] = time.split(':').map(Number);
  const wallAsUtc = Date.UTC(y, m - 1, d, hh, mm);
  // Find the UTC instant whose wall-clock time in `timeZone` equals the input. Two passes handle DST edges.
  let guess = wallAsUtc;
  for (let i = 0; i < 2; i++) {
    const p = parts(new Date(guess), timeZone);
    const shownAsUtc = Date.UTC(+p.year, +p.month - 1, +p.day, +p.hour, +p.minute);
    guess += wallAsUtc - shownAsUtc;
  }
  return new Date(guess).toISOString();
}
