import dayjs from 'dayjs';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { countStayNights, formatHotelDateTime, hotelToday } from './hotel-time';

afterEach(() => vi.useRealTimers());

describe('hotel calendar time', () => {
  it('counts eight nights from November 5 at 14:00 to November 13 at 12:00', () => {
    expect(countStayNights(dayjs('2026-11-05T14:00:00'), dayjs('2026-11-13T12:00:00'))).toBe(8);
  });

  it('handles leap days and does not count the departure date as another night', () => {
    expect(countStayNights(dayjs('2028-02-28T23:00:00'), dayjs('2028-03-01T06:00:00'))).toBe(2);
    expect(countStayNights(dayjs('2026-11-05'), dayjs('2026-11-05'))).toBe(0);
    expect(countStayNights(null, dayjs('2026-11-05'))).toBe(0);
  });

  it('uses the hotel date when UTC is still on the previous date', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-11-04T17:30:00Z'));
    expect(hotelToday().format('YYYY-MM-DD')).toBe('2026-11-05');
  });

  it('displays persisted UTC arrival times in the hotel time zone', () => {
    expect(formatHotelDateTime('2026-11-04T19:00:00Z')).toBe('05/11/2026 02:00');
  });
});
