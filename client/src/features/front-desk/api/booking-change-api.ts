import { apiClient } from '../../../lib/axios';
import type { ApiResponse, PageResponse } from '../../../types/api';
import type { ContactChannel } from '../types/arrival-handling';

export type ChangeType = 'CANCELLATION' | 'RESCHEDULE';
export type ChangeStatus = 'PENDING' | 'APPROVED' | 'REJECTED';
export interface BookingChangeRequest {
  id: string;
  bookingCode: string;
  orderCode: string | null;
  type: ChangeType;
  status: ChangeStatus;
  reason: string;
  requestedBy: string;
  requestedAt: string;
  checkInDate: string | null;
  checkOutDate: string | null;
  decidedBy: string | null;
  decidedAt: string | null;
  decisionNote: string | null;
  consentAt?: string | null;
  snapshot: {
    oldTotalAmount?: number;
    newTotalAmount?: number;
    feeAmount?: number;
    refundAmount?: number;
    additionalAmount?: number;
    balanceDueAt?: string;
    autoClosed?: boolean;
    closureReason?: string;
  } | null;
}
export interface BookingChangeQuote {
  request: BookingChangeRequest;
  canApprove: boolean;
  pricingAvailable?: boolean;
  blockReason: string | null;
  requiresFeeDecision: boolean;
  policyDescription: string;
  quoteToken: string;
  oldTotalAmount: number;
  newTotalAmount: number;
  availableAmount: number;
  feeAmount: number | null;
  refundAmount: number | null;
  additionalAmount: number;
  rooms: {
    bookingCode: string;
    roomTypeName: string;
    oldCheckInDate: string;
    oldCheckOutDate: string;
    newCheckInDate: string | null;
    newCheckOutDate: string | null;
    oldTotalAmount: number;
    newTotalAmount: number;
    availableAmount: number;
    standardFee: number;
    nights: { stayDate: string; price: number }[];
    originalFirstNightAmount?: number | null;
    previousFee?: number;
    children?: number;
    services?: string[];
    feeTiming?: {
      checkInAt: string;
      freeUntil: string;
      halfFeeUntil: string;
      ratePercent: number;
    } | null;
  }[];
  zone: string;
  serverNow: string;
}
const base = '/front-desk/booking-change-requests';

export interface BookingChangeSettlement {
  requestId: string;
  bookingCode: string;
  orderCode: string | null;
  roomCount: number;
  appliedAt: string;
  stayStatus: 'CANCELLED' | 'CONFIRMED' | 'MIXED';
  checkInDate: string;
  checkOutDate: string;
  obligationAmount: number;
  receivedAmount: number;
  availableAmount: number;
  outstandingAmount: number;
  collectionStatus: 'REVIEW_REQUIRED' | 'PAID' | 'OVERDUE' | 'PARTIALLY_PAID' | 'UNPAID';
  balanceDueAt: string | null;
  overdue: boolean;
  refundedAmount: number;
  pendingRefundAmount: number;
  unrequestedRefundAmount: number;
  refundStatus: 'REVIEW_REQUIRED' | 'PARTIALLY_REFUNDED' | 'PENDING' | 'REFUNDED' | 'NOT_REQUIRED';
  needsReview: boolean;
  additionalAtApproval: number | null;
  refundAtApproval: number | null;
  zone: string;
  checkedAt: string;
  rooms: {
    bookingCode: string;
    roomTypeName: string;
    bookingStatus: string;
    obligationAmount: number;
    availableAmount: number;
    outstandingAmount: number;
    balanceDueAt: string | null;
    needsReview: boolean;
  }[];
  refunds: {
    refundId: string;
    bookingCode: string;
    paymentCode: string;
    amount: number;
    status: string;
    requestedAt: string;
    refundedAt: string | null;
    bankReference: string | null;
    destinationProvided: boolean;
  }[];
}

export async function listBookingChangeSettlements(
  keyword = '',
  page = 0,
): Promise<PageResponse<BookingChangeSettlement>> {
  return (
    await apiClient.get<ApiResponse<PageResponse<BookingChangeSettlement>>>(`${base}/settlements`, {
      params: { keyword, page, size: 20 },
    })
  ).data.data;
}

export async function getBookingChangeSettlement(id: string): Promise<BookingChangeSettlement> {
  return (await apiClient.get<ApiResponse<BookingChangeSettlement>>(`${base}/${id}/settlement`))
    .data.data;
}
export async function createBookingChange(
  code: string,
  body: {
    type: ChangeType;
    checkInDate?: string;
    checkOutDate?: string;
    reason: string;
    contactChannel?: ContactChannel;
  },
): Promise<BookingChangeRequest> {
  return (
    await apiClient.post<ApiResponse<BookingChangeRequest>>(
      `/bookings/${encodeURIComponent(code)}/change-requests`,
      body,
    )
  ).data.data;
}
export async function getBookingChanges(code: string): Promise<BookingChangeRequest[]> {
  return (
    await apiClient.get<ApiResponse<BookingChangeRequest[]>>(
      `/bookings/${encodeURIComponent(code)}/change-requests`,
    )
  ).data.data;
}
export async function listBookingChanges(
  status: ChangeStatus | '',
  page: number,
): Promise<PageResponse<BookingChangeRequest>> {
  return (
    await apiClient.get<ApiResponse<PageResponse<BookingChangeRequest>>>(base, {
      params: { status: status || undefined, page, size: 20 },
    })
  ).data.data;
}
export async function getBookingChangeQuote(id: string): Promise<BookingChangeQuote> {
  return (await apiClient.get<ApiResponse<BookingChangeQuote>>(`${base}/${id}`)).data.data;
}
export async function approveBookingChange(
  id: string,
  body: {
    quoteToken: string;
    feeAmount?: number;
    note: string;
    customerAccepted?: boolean;
    servicesConfirmed?: boolean;
    childAges?: Record<string, number[]>;
    balanceDueAt?: string;
  },
): Promise<BookingChangeRequest> {
  return (await apiClient.post<ApiResponse<BookingChangeRequest>>(`${base}/${id}/approve`, body))
    .data.data;
}
export async function rejectBookingChange(id: string, note: string): Promise<BookingChangeRequest> {
  return (await apiClient.post<ApiResponse<BookingChangeRequest>>(`${base}/${id}/reject`, { note }))
    .data.data;
}

export function allocateBookingChangeFees(quote: BookingChangeQuote, fee: number) {
  const weight = quote.rooms.reduce((sum, room) => sum + BigInt(room.standardFee), 0n);
  const amounts = quote.rooms.map((room) =>
    weight === 0n ? 0n : (BigInt(fee) * BigInt(room.standardFee)) / weight,
  );
  let remainder = BigInt(fee) - amounts.reduce((sum, value) => sum + value, 0n);
  for (let i = 0; i < amounts.length && remainder > 0n; i++) {
    if (amounts[i] < BigInt(quote.rooms[i].standardFee)) {
      amounts[i]++;
      remainder--;
    }
  }
  return amounts.map(Number);
}

export function cancellationSettlement(quote: BookingChangeQuote, fee: number) {
  const amounts = allocateBookingChangeFees(quote, fee);
  return {
    refund: quote.rooms.reduce(
      (sum, room, i) =>
        sum +
        Math.max(
          room.availableAmount -
            amounts[i] -
            (quote.request.type === 'RESCHEDULE' ? room.newTotalAmount : 0),
          0,
        ),
      0,
    ),
    additional: quote.rooms.reduce(
      (sum, room, i) =>
        sum +
        Math.max(
          amounts[i] +
            (quote.request.type === 'RESCHEDULE' ? room.newTotalAmount : 0) -
            room.availableAmount,
          0,
        ),
      0,
    ),
  };
}

export async function changeRescheduleServices(
  id: string,
  dropAll: boolean,
  note: string,
): Promise<BookingChangeQuote> {
  return (
    await apiClient.post<ApiResponse<BookingChangeQuote>>(`${base}/${id}/services`, {
      dropAll,
      note,
    })
  ).data.data;
}

export async function allowRescheduleException(
  id: string,
  note: string,
): Promise<BookingChangeQuote> {
  return (
    await apiClient.post<ApiResponse<BookingChangeQuote>>(`${base}/${id}/exception`, { note })
  ).data.data;
}

export async function getStaffCancellationQuote(code: string): Promise<BookingChangeQuote> {
  return (
    await apiClient.get<ApiResponse<BookingChangeQuote>>(
      `/bookings/${encodeURIComponent(code)}/cancellation-quote`,
    )
  ).data.data;
}
