import { describe, expect, it } from 'vitest';
import { dayKey, formatTime, fromLocalInput, toLocalInput } from './time';

describe('event timezone helpers', () => {
  it('shows Dublin wall-clock time in winter (UTC+0) and summer (UTC+1)', () => {
    expect(formatTime('2027-02-18T09:30:00Z', 'Europe/Dublin')).toBe('09:30');
    expect(formatTime('2027-06-18T09:30:00Z', 'Europe/Dublin')).toBe('10:30');
  });

  it('round-trips datetime-local values through UTC', () => {
    const tz = 'Europe/Dublin';
    for (const value of ['2027-02-18T09:00', '2027-06-18T14:45', '2027-10-31T00:30']) {
      const iso = fromLocalInput(value, tz)!;
      expect(toLocalInput(iso, tz)).toBe(value);
    }
    expect(fromLocalInput('2027-06-18T10:30', tz)).toBe('2027-06-18T09:30:00.000Z');
  });

  it('groups by the event day, not the browser day', () => {
    expect(dayKey('2027-02-18T23:30:00Z', 'Europe/Dublin')).toBe('2027-02-18');
    expect(dayKey('2027-02-18T23:30:00Z', 'Asia/Tokyo')).toBe('2027-02-19');
  });

  it('handles empty values', () => {
    expect(fromLocalInput('', 'Europe/Dublin')).toBeNull();
    expect(toLocalInput(null, 'Europe/Dublin')).toBe('');
  });
});
