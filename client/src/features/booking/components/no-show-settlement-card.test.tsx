// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App } from 'antd';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { getArrivalSettlement } from '../../front-desk/api/arrival-handling-api';
import { createPaymentRequest } from '../api/booking-api';
import type { CreatePaymentRequestResponse } from '../types/booking';
import type { ArrivalHandling } from '../../front-desk/types/arrival-handling';
import { getPaymentResultToken } from '../utils/payment-result-storage';
import { NoShowSettlementCard } from './no-show-settlement-card';

vi.mock('../../front-desk/api/arrival-handling-api', () => ({
  getArrivalSettlement: vi.fn(),
  supplyArrivalRefundInformation: vi.fn(),
}));
vi.mock('../api/booking-api', () => ({ createPaymentRequest: vi.fn() }));
let client: QueryClient;
beforeEach(() => {
  client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  window.matchMedia = vi.fn().mockReturnValue({
    matches: false,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
  });
  vi.mocked(getArrivalSettlement).mockResolvedValue({
    bookingId: 'b1',
    bookingCode: 'BK-FEE',
    bookingStatus: 'NO_SHOW',
    originalTotalAmount: 300000,
    outstandingAmount: 500000,
    feeAmount: 800000,
    refunds: [],
    refundAmount: 0,
  } as unknown as ArrivalHandling);
  vi.mocked(createPaymentRequest).mockResolvedValue({
    paymentId: 'fee-payment',
  } as CreatePaymentRequestResponse);
});
afterEach(() => {
  cleanup();
  client.clear();
  vi.resetAllMocks();
});

it('opens the fee QR with the booking access token without restoring the booking', async () => {
  render(
    <QueryClientProvider client={client}>
      <App>
        <MemoryRouter>
          <Routes>
            <Route
              path="/"
              element={<NoShowSettlementCard bookingCode="BK-FEE" accessToken="fee-token" />}
            />
            <Route path="/payments/:id/result" element={<div>QR thu phí</div>} />
          </Routes>
        </MemoryRouter>
      </App>
    </QueryClientProvider>,
  );
  fireEvent.click(await screen.findByRole('button', { name: 'Thanh toán phí còn thiếu' }));
  await screen.findByText('QR thu phí');
  await waitFor(() =>
    expect(createPaymentRequest).toHaveBeenCalledExactlyOnceWith('b1', 'fee-token'),
  );
  expect(getPaymentResultToken('fee-payment')).toBe('fee-token');
});
