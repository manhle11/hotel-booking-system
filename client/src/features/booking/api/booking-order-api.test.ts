// @vitest-environment jsdom
/// <reference types="node" />
import { webcrypto } from 'node:crypto';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { apiClient } from '../../../lib/axios';
import {
  cancelBookingOrder,
  createBookingOrder,
  createOrderPayment,
  getBookingOrder,
  getOrderAccessToken,
} from './booking-order-api';
import type { CreateBookingOrderRequest } from '../types/booking-order';

vi.mock('../../../lib/axios', () => ({ apiClient: { get: vi.fn(), post: vi.fn() } }));

const request: CreateBookingOrderRequest = {
  checkInDate: '2026-11-10',
  checkOutDate: '2026-11-12',
  guestInfo: { fullName: 'Guest', email: 'guest@example.test', identityNumber: 'TEST' },
  rooms: [
    { roomTypeId: 'deluxe', adults: 2, children: 0, addOns: [] },
    { roomTypeId: 'suite', adults: 1, children: 1, addOns: [] },
  ],
};

beforeEach(() => {
  vi.clearAllMocks();
  sessionStorage.clear();
  vi.stubGlobal('crypto', webcrypto);
});

describe('order checkout', () => {
  it('reuses the idempotency key and access token after a lost response', async () => {
    vi.mocked(apiClient.post)
      .mockRejectedValueOnce(new Error('Connection lost'))
      .mockResolvedValueOnce({ data: { data: { orderId: 'order-1' } } });
    await expect(createBookingOrder(request)).rejects.toThrow('Connection lost');
    await createBookingOrder(request);
    const first = vi.mocked(apiClient.post).mock.calls[0][2]?.headers;
    const second = vi.mocked(apiClient.post).mock.calls[1][2]?.headers;
    expect(first).toEqual(second);
    expect(getOrderAccessToken('order-1')).toBe(first?.['X-Booking-Access-Token']);
    expect(sessionStorage.getItem('booking-order-attempt')).toBeNull();
  });

  it('starts a new checkout when the previous attempt failed and the selected rooms change', async () => {
    vi.mocked(apiClient.post).mockRejectedValue(new Error('Unavailable'));
    await expect(createBookingOrder(request)).rejects.toThrow();
    await expect(
      createBookingOrder({ ...request, rooms: request.rooms.slice(0, 1) }),
    ).rejects.toThrow();
    expect(vi.mocked(apiClient.post).mock.calls[0][2]?.headers?.['Idempotency-Key']).not.toBe(
      vi.mocked(apiClient.post).mock.calls[1][2]?.headers?.['Idempotency-Key'],
    );
  });

  it('allows an identical new booking after successful checkout instead of reopening a cancelled old order', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ data: { data: { orderId: 'order-1' } } });
    await createBookingOrder(request);
    await createBookingOrder(request);
    expect(vi.mocked(apiClient.post).mock.calls[0][2]?.headers?.['Idempotency-Key']).not.toBe(
      vi.mocked(apiClient.post).mock.calls[1][2]?.headers?.['Idempotency-Key'],
    );
  });

  it('uses the order access token for details, payment, and whole-order cancellation', async () => {
    sessionStorage.setItem('booking-order-access:order-1', 'private-order-token');
    vi.mocked(apiClient.get).mockResolvedValue({ data: { data: { orderId: 'order-1' } } });
    vi.mocked(apiClient.post).mockResolvedValue({
      data: { data: { orderId: 'order-1', payment: { paymentId: 'payment-1' } } },
    });
    await getBookingOrder('order-1');
    await createOrderPayment('order-1');
    await cancelBookingOrder('order-1', 'Changed plans');
    expect(apiClient.get).toHaveBeenCalledWith('/booking-orders/order-1', {
      headers: { 'X-Booking-Access-Token': 'private-order-token' },
    });
    expect(apiClient.post).toHaveBeenCalledWith(
      '/booking-orders/order-1/payments',
      {},
      { headers: { 'X-Booking-Access-Token': 'private-order-token' } },
    );
    expect(apiClient.post).toHaveBeenCalledWith(
      '/booking-orders/order-1/cancel',
      { reason: 'Changed plans', refundInformation: undefined },
      { headers: { 'X-Booking-Access-Token': 'private-order-token' } },
    );
  });
});
