import type { Dayjs } from 'dayjs';

export interface BookingFormValues {
  roomTypeId: string;
  stayDates: [Dayjs, Dayjs];
  adults: number;
  children: number;
  childAges?: number[];
  fullName: string;
  email: string;
  phone?: string;
  identityNumber: string;
}
