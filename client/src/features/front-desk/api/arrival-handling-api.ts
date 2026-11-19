import { apiClient } from '../../../lib/axios';
import type { ApiResponse } from '../../../types/api';
import type { RefundInformation } from '../../payment/types/refund';
import type { ArrivalHandling, ContactChannel, ContactOutcome } from '../types/arrival-handling';

const path = (code: string) => `/bookings/${encodeURIComponent(code)}`;
export async function updateArrivalEstimate(
  code: string,
  expectedAt: string,
  note: string,
  bookingCodes?: string[],
): Promise<ArrivalHandling> {
  return (
    await apiClient.post<ApiResponse<ArrivalHandling>>(`${path(code)}/arrival-estimate`, {
      expectedAt,
      note,
      ...(bookingCodes ? { bookingCodes } : {}),
    })
  ).data.data;
}
export async function recordContactResult(
  code: string,
  body: { channel: ContactChannel; outcome: ContactOutcome; note: string; bookingCodes?: string[] },
): Promise<ArrivalHandling> {
  return (
    await apiClient.post<ApiResponse<ArrivalHandling>>(`${path(code)}/arrival-contact-result`, body)
  ).data.data;
}
export async function decideArrivalHold(
  code: string,
  body: { requestedUntil: string; approve: boolean; note: string; bookingCodes?: string[] },
): Promise<ArrivalHandling> {
  return (
    await apiClient.post<ApiResponse<ArrivalHandling>>(`${path(code)}/arrival-hold/decision`, body)
  ).data.data;
}
export async function getArrivalHandling(code: string): Promise<ArrivalHandling> {
  return (await apiClient.get<ApiResponse<ArrivalHandling>>(`${path(code)}/arrival-handling`)).data
    .data;
}
export async function recordArrivalContact(code: string, note: string): Promise<ArrivalHandling> {
  return (
    await apiClient.post<ApiResponse<ArrivalHandling>>(`${path(code)}/arrival-contact`, { note })
  ).data.data;
}
export async function holdArrival(
  code: string,
  until: string,
  note: string,
  expectedAt?: string,
  bookingCodes?: string[],
): Promise<ArrivalHandling> {
  return (
    await apiClient.post<ApiResponse<ArrivalHandling>>(`${path(code)}/arrival-hold`, {
      until,
      note,
      ...(expectedAt ? { expectedAt } : {}),
      ...(bookingCodes ? { bookingCodes } : {}),
    })
  ).data.data;
}
export async function releaseArrivalHold(
  code: string,
  note: string,
  bookingCodes?: string[],
): Promise<ArrivalHandling> {
  return (
    await apiClient.post<ApiResponse<ArrivalHandling>>(`${path(code)}/arrival-hold/release`, {
      note,
      ...(bookingCodes ? { bookingCodes } : {}),
    })
  ).data.data;
}
export async function markNoShow(
  code: string,
  request: {
    previewToken: string;
    feeAmount: number;
    reason: string;
    contactNote: string;
    channel?: ContactChannel;
    outcome?: ContactOutcome;
  },
): Promise<ArrivalHandling> {
  return (await apiClient.post<ApiResponse<ArrivalHandling>>(`${path(code)}/no-show`, request)).data
    .data;
}
export async function getArrivalSettlement(code: string, token?: string): Promise<ArrivalHandling> {
  return (
    await apiClient.get<ApiResponse<ArrivalHandling>>(`${path(code)}/arrival-settlement`, {
      headers: token ? { 'X-Booking-Access-Token': token } : undefined,
    })
  ).data.data;
}

export interface PolicyFeeCollectionCommand {
  requestId: string;
  amount: number;
  expectedOutstandingAmount: number;
  note: string;
}

export async function collectPolicyFee(
  code: string,
  command: PolicyFeeCollectionCommand,
): Promise<ArrivalHandling> {
  return (
    await apiClient.post<ApiResponse<ArrivalHandling>>(
      `${path(code)}/policy-fee-collection`,
      command,
    )
  ).data.data;
}
export async function supplyArrivalRefundInformation(
  code: string,
  information: RefundInformation,
  token?: string,
): Promise<ArrivalHandling> {
  return (
    await apiClient.post<ApiResponse<ArrivalHandling>>(
      `${path(code)}/arrival-refund-information`,
      information,
      {
        headers: token ? { 'X-Booking-Access-Token': token } : undefined,
      },
    )
  ).data.data;
}
