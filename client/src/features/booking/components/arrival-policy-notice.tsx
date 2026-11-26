import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Descriptions, Space, Typography } from 'antd';
import { useEffect } from 'react';
import { getBookingPolicy } from '../api/booking-policy-api';
import { formatHotelDateTime } from '../../../utils/hotel-time';

const money = (n: number) =>
  new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(n);

export function ArrivalPolicyNotice({
  codes,
  accessToken,
  onReady,
}: {
  codes: string[];
  accessToken?: string;
  onReady?: (ready: boolean) => void;
}) {
  const query = useQuery({
    queryKey: ['booking-policy', codes, accessToken],
    queryFn: () => Promise.all(codes.map((code) => getBookingPolicy(code, accessToken))),
    retry: false,
  });
  useEffect(() => {
    onReady?.(query.isSuccess);
  }, [onReady, query.isSuccess]);
  if (query.isError)
    return (
      <Alert
        type="error"
        title="Chưa tải được chính sách và căn cứ phí"
        description={query.error.message}
        action={
          <Button loading={query.isFetching} onClick={() => void query.refetch()}>
            Thử tải lại
          </Button>
        }
      />
    );
  if (!query.data) return <Alert type="info" title="Đang tải chính sách và căn cứ phí…" />;
  return (
    <Space orientation="vertical" style={{ width: '100%' }}>
      <Alert
        type="info"
        showIcon
        title="Chính sách hủy, đổi lịch, đến muộn và không đến"
        description={
          query.data[0]?.terms ??
          'Booking này giữ điều khoản cũ. Liên hệ khách sạn để đối soát; không tự áp chính sách mới.'
        }
      />
      {query.data.map((p) => (
        <Descriptions key={p.bookingCode} title={p.bookingCode} size="small" column={1}>
          {p.firstNightAmount !== null && (
            <>
              <Descriptions.Item label="Căn cứ P — giá phòng đêm đầu gốc">
                {money(p.firstNightAmount)}
              </Descriptions.Item>
              <Descriptions.Item label="Hủy/đổi miễn phí đến">
                {formatHotelDateTime(p.freeCancellationUntil, p.zone)}
              </Descriptions.Item>
              <Descriptions.Item label="Mức 50% đến">
                {formatHotelDateTime(p.halfFeeUntil, p.zone)} —{' '}
                {money(Math.floor(p.firstNightAmount / 2))}
              </Descriptions.Item>
              <Descriptions.Item label="Sau mốc 50% / không đến">
                {money(p.firstNightAmount)}
              </Descriptions.Item>
              <Descriptions.Item label="Tổng phí đã phát sinh">
                {money(p.accruedFee)}
              </Descriptions.Item>
            </>
          )}
          {p.settlementReviewRequired && (
            <Descriptions.Item label="Quyết toán">
              <Typography.Text type="warning">
                Booking đã hủy, phòng đã giải phóng. Phí và khoản hoàn đang chờ quản lý đối soát.
              </Typography.Text>
            </Descriptions.Item>
          )}
          {p.fees.map((f, i) => (
            <Descriptions.Item key={i} label={formatHotelDateTime(f.at, p.zone)}>
              {f.event}: {money(f.amount)}; tổng {money(f.total)}. {f.reason}
            </Descriptions.Item>
          ))}
        </Descriptions>
      ))}
    </Space>
  );
}
