import { apiClient } from '../../../lib/axios';
import type { ApiResponse, PageResponse } from '../../../types/api';
import type { RefundInformation } from '../../payment/types/refund';
import type { CancellationEligibility } from '../types/booking';
import type { BookingOrder, CreateBookingOrderRequest } from '../types/booking-order';
import { clearLatestPaymentResult, storePaymentResultToken } from '../utils/payment-result-storage';

const tokenKey = (id: string) => `booking-order-access:${id}`;

export function getOrderAccessToken(id: string): string | undefined {
  return sessionStorage.getItem(tokenKey(id)) ?? undefined;
}

function checkoutHeaders(id: string) {
  const token = getOrderAccessToken(id);
  return token ? { 'X-Booking-Access-Token': token } : undefined;
}

interface CheckoutAttempt {
  signature: string;
  key: string;
  token: string;
}

export async function createBookingOrder(
  request: CreateBookingOrderRequest,
): Promise<BookingOrder> {
  const digest = await crypto.subtle.digest(
    'SHA-256',
    new TextEncoder().encode(JSON.stringify(request)),
  );
  const signature = Array.from(new Uint8Array(digest), (value) =>
    value.toString(16).padStart(2, '0'),
  ).join('');
  let attempt: CheckoutAttempt | undefined;
  try {
    attempt = JSON.parse(sessionStorage.getItem('booking-order-attempt') ?? 'null') as
      CheckoutAttempt | undefined;
  } catch {
    sessionStorage.removeItem('booking-order-attempt');
  }
  if (!attempt || attempt.signature !== signature) {
    attempt = {
      signature,
      key: crypto.randomUUID(),
      token: `${crypto.randomUUID()}${crypto.randomUUID()}`.replaceAll('-', ''),
    };
    sessionStorage.setItem('booking-order-attempt', JSON.stringify(attempt));
  }
  const response = await apiClient.post<ApiResponse<BookingOrder>>('/booking-orders', request, {
    headers: { 'Idempotency-Key': attempt.key, 'X-Booking-Access-Token': attempt.token },
  });
  sessionStorage.setItem(tokenKey(response.data.data.orderId), attempt.token);
  sessionStorage.removeItem('booking-order-attempt');
  return response.data.data;
}

export async function getBookingOrder(id: string): Promise<BookingOrder> {
  const response = await apiClient.get<ApiResponse<BookingOrder>>(`/booking-orders/${id}`, {
    headers: checkoutHeaders(id),
  });
  const order = response.data.data;
  if (order.payment && order.status !== 'PENDING')
    clearLatestPaymentResult(order.payment.paymentId);
  return order;
}

export async function createOrderPayment(id: string): Promise<BookingOrder> {
  const response = await apiClient.post<ApiResponse<BookingOrder>>(
    `/booking-orders/${id}/payments`,
    {},
    { headers: checkoutHeaders(id) },
  );
  const token = getOrderAccessToken(id);
  if (token && response.data.data.payment)
    storePaymentResultToken(response.data.data.payment.paymentId, token);
  return response.data.data;
}

export async function getOrderCancellationEligibility(
  id: string,
): Promise<CancellationEligibility> {
  const response = await apiClient.get<ApiResponse<CancellationEligibility>>(
    `/booking-orders/${id}/cancellation-eligibility`,
    { headers: checkoutHeaders(id) },
  );
  return response.data.data;
}

export async function cancelBookingOrder(
  id: string,
  reason?: string,
  refundInformation?: RefundInformation,
): Promise<BookingOrder> {
  const response = await apiClient.post<ApiResponse<BookingOrder>>(
    `/booking-orders/${id}/cancel`,
    { reason, refundInformation },
    { headers: checkoutHeaders(id) },
  );
  const order = response.data.data;
  if (order.payment) clearLatestPaymentResult(order.payment.paymentId);
  return order;
}

export async function getMyBookingOrders(page = 0, size = 6): Promise<PageResponse<BookingOrder>> {
  const response = await apiClient.get<ApiResponse<PageResponse<BookingOrder>>>(
    '/booking-orders/my-orders',
    { params: { page, size } },
  );
  return response.data.data;
}

export async function supplyOrderRefundInformation(
  id: string,
  information: RefundInformation,
): Promise<BookingOrder> {
  const response = await apiClient.post<ApiResponse<BookingOrder>>(
    `/booking-orders/${id}/refund-information`,
    information,
    { headers: checkoutHeaders(id) },
  );
  return response.data.data;
}
