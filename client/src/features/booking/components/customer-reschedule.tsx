import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, App, Button, Card, DatePicker, Input, Space, Spin, Tag, Typography } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { useEffect, useState } from 'react';
import { countStayNights, formatHotelDateTime } from '../../../utils/hotel-time';
import { apiClient } from '../../../lib/axios';
import { getBookingPolicy } from '../api/booking-policy-api';
import type { ApiResponse } from '../../../types/api';
import type {
  BookingChangeQuote,
  BookingChangeRequest,
} from '../../front-desk/api/booking-change-api';
import { BookingChangeQuoteSummary } from '../../front-desk/components/booking-change-quote-summary';

export function CustomerReschedule({
  code,
  accessToken,
  roomCount = 1,
  currentCheckInDate,
  currentCheckOutDate,
}: {
  code: string;
  accessToken?: string;
  roomCount?: number;
  currentCheckInDate?: string;
  currentCheckOutDate?: string;
}) {
  const { message } = App.useApp();
  const client = useQueryClient();
  const base = `/bookings/${encodeURIComponent(code)}/customer-change-requests`;
  const config = { headers: accessToken ? { 'X-Booking-Access-Token': accessToken } : undefined };
  const [dates, setDates] = useState<[Dayjs, Dayjs] | null>(null);
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);
  const query = useQuery({
    queryKey: ['customer-change-requests', code, accessToken],
    queryFn: async () =>
      (await apiClient.get<ApiResponse<BookingChangeRequest[]>>(base, config)).data.data,
    retry: false,
    refetchInterval: (q) => (q.state.data?.some((r) => r.status === 'PENDING') ? 10000 : false),
  });
  const latestDecision = query.data?.find((r) => r.status !== 'PENDING');
  useEffect(() => {
    if (!latestDecision?.id) return;
    for (const key of [
      'booking-order',
      'booking-order-history',
      'booking-history',
      'booking-detail',
      'booking-cancellation',
      'booking-policy',
      'reschedule-policy',
      'arrival-settlement',
    ]) {
      void client.invalidateQueries({ queryKey: [key] });
    }
  }, [client, latestDecision?.id, latestDecision?.status]);
  const policy = useQuery({
    queryKey: ['reschedule-policy', code, accessToken],
    queryFn: () => getBookingPolicy(code, accessToken),
    retry: false,
  });
  const pending = query.data?.find((r) => r.status === 'PENDING');
  const quoteQuery = useQuery({
    queryKey: ['customer-change-quote', code, accessToken, pending?.id],
    queryFn: async () =>
      (await apiClient.get<ApiResponse<BookingChangeQuote>>(`${base}/${pending!.id}/quote`, config))
        .data.data,
    enabled: !!pending,
    retry: false,
    refetchInterval: pending ? 10000 : false,
    refetchOnWindowFocus: false,
  });
  const quote = quoteQuery.data;
  const quoteAvailable =
    !!quote && (quote.pricingAvailable ?? quote.canApprove) && quote.feeAmount !== null;
  const oldNights =
    currentCheckInDate && currentCheckOutDate
      ? countStayNights(dayjs(currentCheckInDate), dayjs(currentCheckOutDate))
      : null;
  const selectedNights = dates ? countStayNights(dates[0], dates[1]) : 0;

  async function submit() {
    if (!dates || !selectedNights || !reason.trim() || busy) return;
    setBusy(true);
    setFailure(null);
    try {
      await apiClient.post(
        base,
        {
          type: 'RESCHEDULE',
          reason: reason.trim(),
          checkInDate: dates[0].format('YYYY-MM-DD'),
          checkOutDate: dates[1].format('YYYY-MM-DD'),
        },
        config,
      );
      await query.refetch();
      void message.success(
        'Đã gửi đề nghị. Ngày cũ vẫn giữ; xem báo giá thu thêm hoặc hoàn ở bên dưới.',
      );
    } catch (error) {
      setFailure(error instanceof Error ? error.message : 'Chưa gửi được yêu cầu.');
      await query.refetch();
    } finally {
      setBusy(false);
    }
  }

  async function consent() {
    if (
      !pending ||
      !quote ||
      !quote.canApprove ||
      !quoteAvailable ||
      quoteQuery.isFetching ||
      quoteQuery.isError ||
      busy
    )
      return;
    setBusy(true);
    setFailure(null);
    try {
      await apiClient.post(
        `${base}/${pending.id}/consent`,
        { quoteToken: quote.quoteToken },
        config,
      );
      await query.refetch();
      await quoteQuery.refetch();
      void message.success('Đã chấp thuận báo giá; khách sạn sẽ kiểm tra và áp dụng.');
    } catch (error) {
      setFailure(
        error instanceof Error
          ? error.message
          : 'Báo giá có thể đã thay đổi; xem lại trước khi xác nhận.',
      );
      await quoteQuery.refetch();
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card
      title={roomCount > 1 ? `Đề nghị đổi lịch toàn bộ ${roomCount} phòng` : 'Đề nghị đổi lịch'}
      style={{ marginTop: 24 }}
    >
      <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
        {!pending && (
          <Typography.Paragraph style={{ marginBottom: 0 }}>
            {policy.data?.legacy
              ? 'Booking giữ điều khoản cũ. Khách sạn xác nhận giá, phí và điều kiện đổi của booking này.'
              : 'Gói tiêu chuẩn: đổi cùng hạng, cùng số đêm một lần trong 90 ngày từ ngày nhận gốc. Tăng/giảm số đêm được báo giá trước và cần quản lý xét ngoại lệ. Phí sát ngày vẫn theo điều khoản đã chấp thuận.'}
          </Typography.Paragraph>
        )}
        {(failure || query.isError || (pending && quoteQuery.isError)) && (
          <Alert
            showIcon
            type="error"
            title={failure ?? query.error?.message ?? quoteQuery.error?.message}
          />
        )}
        {latestDecision?.snapshot?.balanceDueAt && (
          <Alert
            showIcon
            type="info"
            title="Hạn thu chênh lệch đã thỏa thuận"
            description={formatHotelDateTime(latestDecision.snapshot.balanceDueAt)}
          />
        )}
        {pending ? (
          <>
            <div className="customer-reschedule__status">
              <div>
                <Tag color="gold">Chờ duyệt</Tag>
                <Typography.Text strong>
                  {dayjs(pending.checkInDate).format('DD/MM/YYYY')} →{' '}
                  {dayjs(pending.checkOutDate).format('DD/MM/YYYY')}
                </Typography.Text>
              </div>
              <Typography.Text type="secondary">
                {pending.consentAt
                  ? 'Bạn đã đồng ý báo giá. Chờ khách sạn xác nhận.'
                  : 'Ngày cũ được giữ đến khi khách sạn duyệt.'}
              </Typography.Text>
            </div>
            {quoteQuery.isLoading && <Spin aria-label="Đang tính báo giá đổi lịch" />}
            {quote && !quoteQuery.isError && (
              <>
                <BookingChangeQuoteSummary data={quote} fee={quote.feeAmount} customer />
                {!quote.canApprove && (
                  <div className="customer-reschedule__notice" role="status">
                    <Typography.Text strong>
                      {quoteAvailable
                        ? 'Đã có báo giá dự kiến, chờ khách sạn xác nhận'
                        : 'Chưa tính được báo giá'}
                    </Typography.Text>
                    {quote.blockReason && <span>{quote.blockReason}</span>}
                  </div>
                )}
              </>
            )}
            <Space wrap>
              {quote && !quoteQuery.isError && (
                <Button
                  type="primary"
                  disabled={!quote.canApprove || !quoteAvailable || quoteQuery.isFetching}
                  loading={busy}
                  onClick={() => void consent()}
                >
                  Tôi đồng ý báo giá này
                </Button>
              )}
              <Button loading={quoteQuery.isFetching} onClick={() => void quoteQuery.refetch()}>
                Cập nhật báo giá đổi lịch
              </Button>
            </Space>
          </>
        ) : (
          <>
            {currentCheckInDate && currentCheckOutDate && (
              <Typography.Text>
                Kỳ hiện tại: {dayjs(currentCheckInDate).format('DD/MM/YYYY')} →{' '}
                {dayjs(currentCheckOutDate).format('DD/MM/YYYY')} · {oldNights} đêm
              </Typography.Text>
            )}
            <DatePicker.RangePicker
              aria-label="Kỳ ở mới"
              value={dates}
              onChange={(value) => setDates(value?.[0] && value[1] ? [value[0], value[1]] : null)}
              format="DD/MM/YYYY"
            />
            {dates && (
              <Typography.Text strong>
                Kỳ đề nghị: {selectedNights} đêm
                {oldNights !== null && selectedNights !== oldNights
                  ? ` · ${selectedNights > oldNights ? 'Tăng' : 'Giảm'} ${Math.abs(selectedNights - oldNights)} đêm`
                  : ''}
              </Typography.Text>
            )}
            <Input.TextArea
              aria-label="Lý do đổi lịch"
              maxLength={500}
              value={reason}
              onChange={(event) => setReason(event.target.value)}
              placeholder="Lý do và nội dung đề nghị đổi lịch"
            />
            <Button
              disabled={
                !dates || !selectedNights || !reason.trim() || query.isLoading || query.isError
              }
              loading={busy}
              onClick={() => void submit()}
            >
              Gửi đề nghị đổi lịch
            </Button>
          </>
        )}
      </Space>
    </Card>
  );
}
