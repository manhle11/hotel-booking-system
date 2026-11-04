import type {
  AddOnSelection,
  BookingPaymentStatus,
  BookingStatus,
  CreatePaymentRequestResponse,
  GuestInfo,
  PaymentRefundStatus,
} from './booking';

export type BookingOrderStatus = 'PENDING' | 'CONFIRMED' | 'CANCELLED' | 'EXPIRED';

export interface OrderRoomSelection {
  roomTypeId: string;
  adults: number;
  children: number;
  addOns: AddOnSelection[];
}

export interface CreateBookingOrderRequest {
  checkInDate: string;
  checkOutDate: string;
  guestInfo: GuestInfo;
  rooms: OrderRoomSelection[];
}

export interface BookingOrderRoom {
  bookingId: string;
  bookingCode: string;
  roomTypeId: string;
  roomTypeName: string;
  adults: number;
  children: number;
  totalAmount: number;
  paidAmount: number;
  status: BookingStatus;
  roomNumber: string | null;
}

export interface BookingOrder {
  orderId: string;
  orderCode: string;
  status: BookingOrderStatus;
  checkInDate: string;
  checkOutDate: string;
  contactName: string;
  contactEmail: string;
  totalAmount: number;
  paidAmount: number;
  currency: string;
  expiresAt: string;
  createdAt: string;
  rooms: BookingOrderRoom[];
  payment: CreatePaymentRequestResponse | null;
  paymentStatus: BookingPaymentStatus | null;
  refundAmount: number;
  refundStatus: PaymentRefundStatus | null;
  refundInformationRequired: boolean;
}
