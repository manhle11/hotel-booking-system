// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { ApiClientError } from '../../../types/api';
import { collectPolicyFee, getArrivalHandling } from '../api/arrival-handling-api';
import type { ArrivalHandling } from '../types/arrival-handling';
import { PolicyFeeCollection } from './policy-fee-collection';

vi.mock('../api/arrival-handling-api', () => ({
  collectPolicyFee: vi.fn(),
  getArrivalHandling: vi.fn(),
}));
const data = {
  bookingCode: 'BK-FEE',
  bookingStatus: 'NO_SHOW',
  outstandingAmount: 500000,
  settlementReviewRequired: false,
} as ArrivalHandling;
beforeEach(() => {
  globalThis.ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  };
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

it('requires actual cash receipt and retries the same receipt after a lost response', async () => {
  const updated = { ...data, outstandingAmount: 0 };
  vi.mocked(collectPolicyFee)
    .mockRejectedValueOnce(new Error('Mất phản hồi'))
    .mockResolvedValueOnce(updated);
  const onCollected = vi.fn(async () => {});
  render(<PolicyFeeCollection data={data} onCollected={onCollected} />);
  const button = screen.getByRole('button', {
    name: 'Ghi nhận đã thu tiền mặt',
  }) as HTMLButtonElement;
  expect(button.disabled).toBe(true);
  fireEvent.change(screen.getByLabelText('Ghi chú thu phí'), {
    target: { value: 'Khách A nộp tiền mặt' },
  });
  fireEvent.click(screen.getByRole('checkbox'));
  fireEvent.click(button);
  await screen.findByText('Mất phản hồi');
  expect(onCollected).not.toHaveBeenCalled();
  expect((screen.getByLabelText('Tiền mặt phí đã nhận') as HTMLInputElement).disabled).toBe(true);
  fireEvent.click(screen.getByRole('button', { name: /Tra cứu \/ gửi lại xác nhận thu/ }));
  await waitFor(() => expect(onCollected).toHaveBeenCalledWith(updated));
  const calls = vi.mocked(collectPolicyFee).mock.calls;
  expect(calls).toHaveLength(2);
  expect(calls[1]).toEqual(calls[0]);
  expect(calls[0][1]).toMatchObject({
    amount: 500000,
    expectedOutstandingAmount: 500000,
    note: 'Khách A nộp tiền mặt',
  });
});

it('hides collection until legacy cancellation settlement has been approved', () => {
  render(
    <PolicyFeeCollection
      data={{ ...data, settlementReviewRequired: true }}
      onCollected={vi.fn()}
    />,
  );
  expect(screen.queryByRole('button')).toBeNull();
});

it('refreshes a rejected stale collection and requires confirmation of the new balance', async () => {
  vi.mocked(collectPolicyFee).mockRejectedValueOnce(
    new ApiClientError({ kind: 'api', status: 409, message: 'Công nợ đã thay đổi' }),
  );
  const latest = { ...data, outstandingAmount: 300000 };
  vi.mocked(getArrivalHandling).mockResolvedValueOnce(latest);
  const onCollected = vi.fn(async () => {});
  render(<PolicyFeeCollection data={data} onCollected={onCollected} />);
  fireEvent.change(screen.getByLabelText('Ghi chú thu phí'), { target: { value: 'Phiếu QA' } });
  fireEvent.click(screen.getByRole('checkbox'));
  fireEvent.click(screen.getByRole('button', { name: 'Ghi nhận đã thu tiền mặt' }));
  await waitFor(() => expect(onCollected).toHaveBeenCalledWith(latest));
  await waitFor(() =>
    expect((screen.getByLabelText('Tiền mặt phí đã nhận') as HTMLInputElement).disabled).toBe(
      false,
    ),
  );
  expect((screen.getByLabelText('Tiền mặt phí đã nhận') as HTMLInputElement).value).toBe('300000');
  expect((screen.getByRole('checkbox') as HTMLInputElement).checked).toBe(false);
  expect(
    (screen.getByRole('button', { name: /Ghi nhận đã thu tiền mặt/ }) as HTMLButtonElement)
      .disabled,
  ).toBe(true);
});
