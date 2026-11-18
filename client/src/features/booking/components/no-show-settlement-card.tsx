import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, App, Button, Card, Descriptions, Space, Typography } from 'antd';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { createPaymentRequest } from '../api/booking-api';
import { storePaymentResultToken } from '../utils/payment-result-storage';
import { formatHotelDateTime } from '../../../utils/hotel-time';
import {
  getArrivalSettlement,
  supplyArrivalRefundInformation,
} from '../../front-desk/api/arrival-handling-api';
import { RefundInformationForm } from '../../payment/components/refund-information-form';
import type { RefundInformation } from '../../payment/types/refund';

const money = (n: number) =>
  new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(n);

export function NoShowSettlementCard({
  bookingCode,
  accessToken,
  kind = 'no-show',
}: {
  bookingCode: string;
  accessToken?: string;
  kind?: 'no-show' | 'cancellation' | 'reschedule';
}) {
  const client = useQueryClient();
  const { message } = App.useApp();
  const navigate = useNavigate();
  const key = ['arrival-settlement', bookingCode, accessToken];
  const query = useQuery({
    queryKey: key,
    queryFn: () => getArrivalSettlement(bookingCode, accessToken),
    retry: false,
    refetchInterval: (q) =>
      q.state.data?.outstandingAmount ||
      q.state.data?.refunds.some((r) => r.status === 'PENDING' || r.status === 'PROCESSING')
        ? 10000
        : false,
  });
  const pay = useMutation({
    mutationFn: () => createPaymentRequest(query.data!.bookingId, accessToken),
    onSuccess: (payment) => {
      if (accessToken) storePaymentResultToken(payment.paymentId, accessToken);
      navigate(`/payments/${payment.paymentId}/result`);
    },
    onError: (error: Error) => void message.error(error.message),
  });
  const [information, setInformation] = useState<RefundInformation>({
    method: 'BANK_TRANSFER',
    bankName: '',
    accountHolderName: '',
    accountNumber: '',
  });
  const save = useMutation({
    mutationFn: () => supplyArrivalRefundInformation(bookingCode, information, accessToken),
    onSuccess: (result) => {
      client.setQueryData(key, result);
      void message.success('Đã lưu tài khoản nhận hoàn tiền.');
    },
    onError: (error: Error) => void message.error(error.message),
  });
  const data = query.data;
  return (
    <Card
      title={
        kind === 'cancellation'
          ? 'Quyết toán khách chủ động hủy'
          : kind === 'reschedule'
            ? 'Hoàn chênh lệch đổi lịch'
            : 'Quyết toán khách không đến'
      }
      size="small"
      loading={query.isLoading}
    >
      <Space orientation="vertical" style={{ width: '100%' }}>
        {query.isError && (
          <Alert
            type="error"
            title={query.error.message}
            action={<Button onClick={() => void query.refetch()}>Thử lại</Button>}
          />
        )}
        {data && (
          <>
            {data.settlementReviewRequired && (
              <Alert
                type="warning"
                title="Booking đã hủy và giải phóng phòng"
                description="Phí và tiền hoàn đang chờ quản lý đối soát theo điều khoản cũ; chưa xác nhận khoản phải thu hoặc hoàn."
              />
            )}
            <Descriptions column={1} size="small">
              <Descriptions.Item
                label={kind === 'reschedule' ? 'Tổng tiền kỳ ở hiện tại' : 'Tổng tiền kỳ ở ban đầu'}
              >
                {money(data.originalTotalAmount)}
              </Descriptions.Item>
              {kind !== 'reschedule' && !data.settlementReviewRequired && (
                <Descriptions.Item
                  label={
                    kind === 'cancellation' ? 'Phí hủy được duyệt' : 'Phí không đến được duyệt'
                  }
                >
                  {money(data.feeAmount)}
                </Descriptions.Item>
              )}
              {kind === 'reschedule' && (
                <Descriptions.Item label="Tổng phí đổi còn hiệu lực">
                  {money(data.policyFeeTotal ?? 0)}
                </Descriptions.Item>
              )}
              {!data.settlementReviewRequired && (
                <Descriptions.Item label="Còn phải thu">
                  {money(
                    kind === 'reschedule'
                      ? Math.max(data.originalTotalAmount - (data.availableAmount ?? 0), 0)
                      : data.outstandingAmount,
                  )}
                </Descriptions.Item>
              )}
              {data.balanceDueAt && (
                <Descriptions.Item label="Hạn thu đã thỏa thuận">
                  {formatHotelDateTime(data.balanceDueAt, data.zone)}
                </Descriptions.Item>
              )}
              <Descriptions.Item label="Tổng tiền hoàn ghi nhận">
                {money(
                  kind === 'reschedule'
                    ? data.refunds
                        .filter((r) => r.status !== 'FAILED' && r.status !== 'CANCELLED')
                        .reduce((sum, r) => sum + r.amount, 0)
                    : data.refundAmount,
                )}
              </Descriptions.Item>
            </Descriptions>
            {kind !== 'reschedule' &&
              !data.settlementReviewRequired &&
              data.outstandingAmount > 0 &&
              (data.bookingStatus === 'NO_SHOW' || data.staffCancellation) && (
                <Button type="primary" loading={pay.isPending} onClick={() => pay.mutate()}>
                  Thanh toán phí còn thiếu
                </Button>
              )}
            {data.refunds.map((refund) => (
              <Typography.Text key={refund.refundId}>
                {money(refund.amount)} ·{' '}
                {refund.status === 'SUCCESS'
                  ? 'Đã hoàn'
                  : refund.status === 'PENDING' || refund.status === 'PROCESSING'
                    ? 'Đang chờ hoàn'
                    : 'Cần khách sạn kiểm tra'}
              </Typography.Text>
            ))}
            {data.refunds.some((r) => r.informationRequired) && (
              <>
                <Alert
                  type="info"
                  title="Bổ sung tài khoản để khách sạn hoàn tiền. Khoản chờ hoàn chưa được chuyển vào tài khoản."
                />
                <RefundInformationForm
                  value={information}
                  onChange={setInformation}
                  disabled={save.isPending}
                />
                <Button
                  type="primary"
                  loading={save.isPending}
                  disabled={
                    !information.bankName.trim() ||
                    !information.accountHolderName.trim() ||
                    information.accountNumber.length < 4
                  }
                  onClick={() => save.mutate()}
                >
                  Lưu tài khoản nhận hoàn
                </Button>
              </>
            )}
          </>
        )}
      </Space>
    </Card>
  );
}
