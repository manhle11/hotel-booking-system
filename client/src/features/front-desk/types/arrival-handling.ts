import type { BookingStatus, PaymentRefundStatus } from '../../booking/types/booking';

export type ContactChannel = 'PHONE' | 'EMAIL' | 'MESSAGE' | 'IN_PERSON' | 'OTHER';
export type ContactOutcome =
  'STILL_COMING' | 'NO_ANSWER' | 'CANCELLATION' | 'RESCHEDULE' | 'UNDECIDED';
export type ArrivalGroup = 'WAITING' | 'ACTION_REQUIRED' | 'RESOLVED';
export type ArrivalReason =
  'RECEPTION_EXPIRED' | 'HOLD_REQUEST' | 'CHANGE_REQUEST' | 'ETA_AFTER_DEADLINE' | 'PAST_ETA';
export interface ArrivalProgress {
  group: ArrivalGroup;
  reasons: ArrivalReason[];
  expectedAt: string | null;
  receptionDeadline: string;
  canReceive: boolean;
  holdPending: boolean;
  lastContactAt: string | null;
  lastContactChannel: ContactChannel | null;
  lastContactOutcome: ContactOutcome | null;
  lastContactNote: string | null;
  changeRequestId: string | null;
}

export interface ArrivalOrderRoom {
  bookingCode: string;
  orderRoomNumber: number | null;
  roomTypeName: string;
  bookingStatus: BookingStatus;
  expectedAt: string | null;
  receptionDeadline: string;
  departureDeadline: string;
  holdUntil: string | null;
  holdRequestedUntil: string | null;
  canManageArrival: boolean;
}

export interface ArrivalHandling {
  orderRooms?: ArrivalOrderRoom[];
  settlementReviewRequired?: boolean;
  policyFeeTotal?: number;
  balanceDueAt?: string | null;
  bookingId: string;
  bookingCode: string;
  bookingStatus: BookingStatus;
  policyVersion: string;
  staffCancellation?: boolean;
  stayRevision?: number;
  orderCode?: string | null;
  roomCount?: number;
  legacyPolicy: boolean;
  canMarkNoShow: boolean;
  blockReason: string | null;
  cutoff: string;
  departureDeadline: string;
  serverNow: string;
  zone: string;
  originalTotalAmount: number;
  paidAmount: number;
  receivedAmount?: number;
  availableAmount?: number;
  progress?: ArrivalProgress | null;
  standardFee: number;
  feeAmount: number;
  refundAmount: number;
  outstandingAmount: number;
  holdUntil: string | null;
  holdRequestedUntil: string | null;
  noShowAt: string | null;
  previewToken: string;
  history: { at: string | null; actor: string | null; note: string }[];
  refunds: {
    refundId: string;
    amount: number;
    status: PaymentRefundStatus;
    informationRequired: boolean;
  }[];
}
