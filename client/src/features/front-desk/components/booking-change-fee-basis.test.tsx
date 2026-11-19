// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest';
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import type { BookingChangeQuote } from '../api/booking-change-api';
import { BookingChangeFeeBasis } from './booking-change-fee-basis';
import { BookingChangeQuoteSummary } from './booking-change-quote-summary';

function quote(): BookingChangeQuote {
  return {
    request: {
      id: 'change-1',
      bookingCode: 'BK-1',
      orderCode: 'ORD-1',
      type: 'RESCHEDULE',
      status: 'PENDING',
      reason: 'Đổi lịch và tăng đêm',
      requestedBy: 'Khách',
      requestedAt: '2026-11-18T10:18:00Z',
      checkInDate: '2026-11-26',
      checkOutDate: '2026-11-28',
      decidedBy: null,
      decidedAt: null,
      decisionNote: null,
      snapshot: null,
    },
    canApprove: false,
    pricingAvailable: true,
    blockReason: 'Chờ quản lý xét ngoại lệ tăng đêm',
    requiresFeeDecision: false,
    policyDescription: 'Tính phí tại lúc nhận yêu cầu',
    quoteToken: 'quote-1',
    oldTotalAmount: 6_050_000,
    newTotalAmount: 12_100_000,
    availableAmount: 6_050_000,
    feeAmount: 3_025_000,
    refundAmount: 0,
    additionalAmount: 9_075_000,
    rooms: [2_450_000, 3_600_000].map((price, index) => ({
      bookingCode: `BK-${index + 1}`,
      roomTypeName: 'Deluxe',
      oldCheckInDate: '2026-11-20',
      oldCheckOutDate: '2026-11-21',
      newCheckInDate: '2026-11-26',
      newCheckOutDate: '2026-11-28',
      oldTotalAmount: price,
      newTotalAmount: price * 2,
      availableAmount: price,
      standardFee: price / 2,
      originalFirstNightAmount: price,
      previousFee: 0,
      nights: [],
      feeTiming: {
        checkInAt: '2026-11-20T07:00:00Z',
        freeUntil: '2026-11-18T07:00:00Z',
        halfFeeUntil: '2026-11-19T07:00:00Z',
        ratePercent: 50,
      },
    })),
    zone: 'Asia/Ho_Chi_Minh',
    serverNow: '2026-11-19T12:00:00Z',
  };
}

afterEach(cleanup);

function renderFeeDetails(data: BookingChangeQuote) {
  render(<BookingChangeFeeBasis data={data} />);
  fireEvent.click(screen.getByText('Cách tính phí'));
}

describe('fee basis in booking change quotes', () => {
  it.each([true, false])(
    'explains the 44h42 window and separates the fee from extra nights (customer=%s)',
    (customer) => {
      const data = quote();
      render(<BookingChangeQuoteSummary data={data} fee={data.feeAmount} customer={customer} />);
      fireEvent.click(screen.getByText('Cách tính phí'));
      const basis = screen.getByRole('region', { name: 'Căn cứ tính phí' });
      expect(within(basis).getAllByText('Mốc phí 50%')).toHaveLength(1);
      expect(basis).toHaveTextContent('Tiếp nhận yêu cầu: 18/11/2026 17:18');
      expect(basis).toHaveTextContent('Nhận phòng kỳ hiện tại: 20/11/2026 14:00');
      expect(basis).toHaveTextContent('còn 44 giờ 42 phút');
      expect(basis).toHaveTextContent('0%: tiếp nhận đến 18/11/2026 14:00');
      expect(basis).toHaveTextContent('50%: sau 18/11/2026 14:00 đến 19/11/2026 14:00');
      expect(basis).toHaveTextContent('100%: sau 19/11/2026 14:00');
      const settlement = screen.getByLabelText('Kết quả thu hoàn');
      expect(settlement).toHaveTextContent(customer ? 'Bạn cần thanh toán thêm' : 'Còn phải thu');
      expect(settlement).toHaveTextContent(/9\.075\.000/);
      const amounts = screen.getByLabelText('Chi tiết báo giá');
      expect(
        within(amounts).getByText(customer ? 'Phí đổi lịch' : 'Phí phát sinh lần này')
          .parentElement,
      ).toHaveTextContent(/3\.025\.000/);
      if (!customer)
        expect(
          within(amounts).getByText('Chênh lệch giá kỳ ở, chưa gồm phí').parentElement,
        ).toHaveTextContent(/6\.050\.000/);
    },
  );

  it('keeps an accrued fee visible even when the current timing rate is zero', () => {
    const data = quote();
    for (const room of data.rooms) {
      room.previousFee = room.originalFirstNightAmount!;
      room.standardFee = room.previousFee;
      room.feeTiming!.ratePercent = 0;
    }
    data.request.requestedAt = '2026-11-17T07:00:00Z';
    renderFeeDetails(data);
    expect(screen.getByText('Mốc phí 0%')).toBeInTheDocument();
    expect(screen.getByText(/Phí đã phát sinh trước đây được giữ/)).toBeInTheDocument();
    expect(
      screen.getByRole('columnheader', { name: 'Phí chuẩn sau đối chiếu phí cũ' }),
    ).toBeInTheDocument();
  });

  it.each([1, 3, 7])('shows only the first-night base for a %s-night stay', (nights) => {
    const data = quote();
    for (const room of data.rooms) {
      room.oldCheckOutDate = `2026-11-${20 + nights}`;
      room.oldTotalAmount = room.originalFirstNightAmount! * nights;
    }
    data.oldTotalAmount = data.rooms.reduce((sum, room) => sum + room.oldTotalAmount, 0);
    renderFeeDetails(data);
    const table = screen.getByRole('table', { name: 'Phí tính trên đêm đầu từng phòng' });
    const first = within(table).getByRole('row', { name: /BK-1/ });
    expect(first).toHaveTextContent(/50% × 2\.450\.000/);
    expect(first).toHaveTextContent(/1\.225\.000/);
    const second = within(table).getByRole('row', { name: /BK-2/ });
    expect(second).toHaveTextContent(/50% × 3\.600\.000/);
    expect(second).toHaveTextContent(/1\.800\.000/);
    const total = within(table).getByRole('row', { name: /Tổng phí theo chính sách/ });
    expect(total).toHaveTextContent(/3\.025\.000/);
    if (nights === 1) {
      expect(screen.getByText(/Các phòng trên chỉ đặt 1 đêm/)).toBeInTheDocument();
    } else {
      expect(screen.queryByText(/Các phòng trên chỉ đặt 1 đêm/)).not.toBeInTheDocument();
    }
  });

  it('excludes service charges from the displayed first-night base', () => {
    const data = quote();
    for (const room of data.rooms) {
      room.oldTotalAmount += 900_000;
      room.services = ['Đưa đón × 1'];
    }
    renderFeeDetails(data);
    const table = screen.getByRole('table', { name: 'Phí tính trên đêm đầu từng phòng' });
    expect(table).toHaveTextContent(/50% × 2\.450\.000/);
    expect(table).toHaveTextContent(/3\.025\.000/);
    expect(table).not.toHaveTextContent(/3\.350\.000/);
    expect(screen.queryByText(/Các phòng trên chỉ đặt 1 đêm/)).not.toBeInTheDocument();
  });

  it('explains the extension exception without calling the time bracket a new fee', () => {
    const data = quote();
    for (const room of data.rooms) {
      room.newCheckInDate = room.oldCheckInDate;
      room.feeTiming!.ratePercent = 100;
    }
    renderFeeDetails(data);
    expect(
      within(screen.getByRole('region', { name: 'Căn cứ tính phí' })).getByText(
        'Không thêm phí đổi',
      ),
    ).toBeInTheDocument();
    expect(screen.queryByText('Mốc phí 100%')).not.toBeInTheDocument();
  });

  it('uses the first removed night as the basis when shortening without moving arrival', () => {
    const data = quote();
    for (const room of data.rooms) {
      room.oldCheckOutDate = '2026-11-23';
      room.newCheckInDate = room.oldCheckInDate;
      room.newCheckOutDate = '2026-11-21';
    }
    renderFeeDetails(data);
    expect(screen.getByText(/Tỷ lệ áp dụng trên giá đêm đầu bị bỏ/)).toBeInTheDocument();
  });

  it('does not invent a standard rate for legacy terms without fee timing', () => {
    const data = quote();
    data.rooms.forEach((room) => {
      room.feeTiming = null;
    });
    render(<BookingChangeFeeBasis data={data} />);
    expect(screen.queryByRole('region', { name: 'Căn cứ tính phí' })).not.toBeInTheDocument();
  });

  it('keeps the customer summary to four amounts and expands the fee explanation on demand', () => {
    const data = quote();
    render(<BookingChangeQuoteSummary data={data} fee={data.feeAmount} customer />);
    const amounts = screen.getByLabelText('Chi tiết báo giá');
    expect(amounts.children).toHaveLength(4);
    expect(amounts).toHaveTextContent('Giá kỳ mới');
    expect(amounts).toHaveTextContent('Phí đổi lịch');
    expect(amounts).toHaveTextContent('Tổng tiền sau đổi');
    expect(amounts).toHaveTextContent('Tiền đã trả được bù trừ');
    const summary = screen.getByText('Cách tính phí');
    const details = summary.closest('details')!;
    expect(details).not.toHaveAttribute('open');
    expect(details.querySelector('table')).not.toBeVisible();
    fireEvent.click(summary);
    expect(details).toHaveAttribute('open');
    expect(details.querySelector('table')).toBeVisible();
    expect(screen.getByLabelText('Kết quả thu hoàn')).toHaveTextContent(/9\.075\.000/);
  });
});
