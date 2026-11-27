// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { apiClient } from '../../../lib/axios';
import { ArrivalPolicyNotice } from './arrival-policy-notice';

vi.mock('../../../lib/axios', () => ({ apiClient: { get: vi.fn() } }));
beforeEach(() => {
  window.matchMedia = vi.fn().mockReturnValue({
    matches: false,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
  });
});
afterEach(() => {
  cleanup();
  vi.resetAllMocks();
});
const policy = {
  bookingCode: 'BK-TEST',
  version: 'HANBAY-STANDARD-2026-11-V2',
  terms: 'Hủy/đổi theo giá đêm đầu gốc; tổng phí tối đa P mỗi phòng.',
  acceptedAt: null,
  firstNightAmount: 1_000_001,
  accruedFee: 500_000,
  freeCancellationUntil: '2026-11-13T07:00:00Z',
  halfFeeUntil: '2026-11-14T07:00:00Z',
  zone: 'Asia/Ho_Chi_Minh',
  legacy: false,
  settlementReviewRequired: false,
  fees: [],
};
function mount(onReady = vi.fn()) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <ArrivalPolicyNotice codes={['BK-TEST']} accessToken="secret" onReady={onReady} />
    </QueryClientProvider>,
  );
  return onReady;
}
describe('booking-specific accepted policy', () => {
  it('shows the original first night and rounded fee before allowing checkout', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ data: { data: policy } });
    const ready = mount();
    await screen.findByText('Căn cứ P — giá phòng đêm đầu gốc');
    expect(screen.getAllByText(/1\.000\.001/).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/500\.000/).length).toBeGreaterThan(0);
    await waitFor(() => expect(ready).toHaveBeenLastCalledWith(true));
    expect(apiClient.get).toHaveBeenCalledWith('/bookings/BK-TEST/booking-policy', {
      headers: { 'X-Booking-Access-Token': 'secret' },
    });
  });
  it('keeps checkout unavailable when policy retrieval fails', async () => {
    vi.mocked(apiClient.get).mockRejectedValue(new Error('Mất kết nối'));
    const ready = mount();
    await screen.findByText('Chưa tải được chính sách và căn cứ phí');
    expect(ready).not.toHaveBeenCalledWith(true);
  });
  it('lets the customer reload a failed policy and enables checkout only after recovery', async () => {
    vi.mocked(apiClient.get)
      .mockRejectedValueOnce(new Error('Đã xảy ra lỗi không mong muốn'))
      .mockResolvedValueOnce({ data: { data: policy } });
    const ready = mount();
    await screen.findByText('Chưa tải được chính sách và căn cứ phí');
    expect(ready).not.toHaveBeenCalledWith(true);

    fireEvent.click(screen.getByRole('button', { name: 'Thử tải lại' }));

    await screen.findByText('Căn cứ P — giá phòng đêm đầu gốc');
    await waitFor(() => expect(ready).toHaveBeenLastCalledWith(true));
    expect(screen.queryByText('Chưa tải được chính sách và căn cứ phí')).toBeNull();
    expect(apiClient.get).toHaveBeenCalledTimes(2);
    expect(apiClient.get).toHaveBeenLastCalledWith('/bookings/BK-TEST/booking-policy', {
      headers: { 'X-Booking-Access-Token': 'secret' },
    });
  });
  it('does not present the new fee schedule as accepted by a legacy booking', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({
      data: {
        data: {
          ...policy,
          legacy: true,
          terms: null,
          firstNightAmount: null,
          settlementReviewRequired: true,
        },
      },
    });
    mount();
    await screen.findByText(/Booking này giữ điều khoản cũ/);
    expect(screen.queryByText('Căn cứ P — giá phòng đêm đầu gốc')).toBeNull();
    expect(screen.getByText(/Phí và khoản hoàn đang chờ quản lý/)).toBeTruthy();
  });
});
