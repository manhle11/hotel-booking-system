import dayjs, { type Dayjs } from 'dayjs';
import utc from 'dayjs/plugin/utc';
import timezone from 'dayjs/plugin/timezone';

dayjs.extend(utc);
dayjs.extend(timezone);

export const HOTEL_TIME_ZONE = 'Asia/Ho_Chi_Minh';

export function countStayNights(
  checkIn: Dayjs | null | undefined,
  checkOut: Dayjs | null | undefined,
) {
  if (!checkIn?.isValid() || !checkOut?.isValid()) return 0;
  return Math.max(0, checkOut.startOf('day').diff(checkIn.startOf('day'), 'day'));
}

export function hotelToday(zone = HOTEL_TIME_ZONE) {
  return dayjs(dayjs().tz(zone).format('YYYY-MM-DD'));
}

export function formatHotelDateTime(value: string, zone = HOTEL_TIME_ZONE) {
  return dayjs(value).tz(zone).format('DD/MM/YYYY HH:mm');
}
