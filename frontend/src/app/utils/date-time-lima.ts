const naiveDateTime = /^(\d{4})-(\d{2})-(\d{2})(?:T| )(\d{2}):(\d{2})(?::(\d{2}))?/;

/**
 * API LocalDateTime values without an offset are historical/business wall times
 * stored in America/Lima. Values carrying Z or an offset represent instants and
 * are converted with the IANA time zone database.
 */
export function formatDateTimeLima(value: string | Date | null | undefined): string {
  if (value == null || value === '') return '—';
  if (typeof value === 'string' && !/(?:Z|[+-]\d{2}:?\d{2})$/i.test(value)) {
    const match = naiveDateTime.exec(value);
    if (match) return `${match[3]}/${match[2]}/${match[1]} ${match[4]}:${match[5]}`;
    const dateOnly = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
    if (dateOnly) return `${dateOnly[3]}/${dateOnly[2]}/${dateOnly[1]}`;
  }
  const date = value instanceof Date ? value : new Date(value);
  if (!Number.isFinite(date.getTime())) return '—';
  return new Intl.DateTimeFormat('es-PE', {
    timeZone: 'America/Lima', day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit', hourCycle: 'h23'
  }).format(date).replace(',', '');
}

export function dateKeyInLima(value: Date = new Date()): string {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/Lima', year: 'numeric', month: '2-digit', day: '2-digit'
  }).formatToParts(value);
  const part = (type: string) => parts.find(item => item.type === type)?.value ?? '';
  return `${part('year')}-${part('month')}-${part('day')}`;
}

export function shiftDateKeyInLima(key: string, days: number): string {
  const [year, month, day] = key.split('-').map(Number);
  return dateKeyInLima(new Date(Date.UTC(year, month - 1, day + days, 12)));
}

export function dateKeyForLimaDateTime(value: string): string {
  if (/(?:Z|[+-]\d{2}:?\d{2})$/i.test(value)) return dateKeyInLima(new Date(value));
  return value.slice(0, 10);
}
