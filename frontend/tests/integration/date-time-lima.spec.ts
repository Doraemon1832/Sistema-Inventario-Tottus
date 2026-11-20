import { expect, test } from '@playwright/test';
import { dateKeyInLima, formatDateTimeLima } from '../../src/app/utils/date-time-lima';

test('las fechas ISO con zona se convierten a Lima y LocalDateTime conserva su hora local', () => {
  expect(formatDateTimeLima('2026-11-18T15:30:00Z')).toContain('18/11/2026 10:30');
  expect(formatDateTimeLima('2026-11-18T10:30:00')).toBe('18/11/2026 10:30');
  expect(formatDateTimeLima('2026-11-18')).toBe('18/11/2026');
  expect(dateKeyInLima(new Date('2026-11-18T04:30:00Z'))).toBe('2026-11-17');
});
