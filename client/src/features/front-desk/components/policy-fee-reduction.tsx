import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, Button, Input, InputNumber, Space, Typography } from 'antd';
import { useState } from 'react';
import { apiClient } from '../../../lib/axios';
import { getArrivalHandling } from '../api/arrival-handling-api';

export function PolicyFeeReduction({ code }: { code: string }) {
  const client = useQueryClient();
  const query = useQuery({
    queryKey: ['arrival-handling', code],
    queryFn: () => getArrivalHandling(code),
  });
  const [fee, setFee] = useState<number | null>(null);
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);
  const current = query.data?.policyFeeTotal ?? 0;
  if (!current) return null;
  return (
    <Space orientation="vertical" style={{ width: '100%' }}>
      <Typography.Text>
        Quản lý giảm/miễn phí riêng phòng {code}. Phí đang ghi nhận:{' '}
        {current.toLocaleString('vi-VN')}đ.
      </Typography.Text>
      <InputNumber
        aria-label="Tổng phí sau giảm"
        min={0}
        max={current}
        precision={0}
        value={fee}
        onChange={setFee}
        placeholder="Tổng phí sau giảm"
      />
      <Input.TextArea
        aria-label="Lý do giảm phí"
        value={reason}
        onChange={(e) => setReason(e.target.value)}
        maxLength={500}
        placeholder="Lý do và căn cứ giảm/miễn; lưu bút toán điều chỉnh"
      />
      {failure && <Alert type="error" title={failure} />}
      <Button
        disabled={fee === null || fee >= current || !reason.trim()}
        loading={busy}
        onClick={async () => {
          setBusy(true);
          setFailure(null);
          try {
            await apiClient.post(`/bookings/${encodeURIComponent(code)}/policy-fee-reduction`, {
              feeAmount: fee,
              reason: reason.trim(),
            });
            await Promise.all(
              [
                'arrival-handling',
                'booking-policy',
                'arrival-settlement',
                'booking-order',
                'booking-history',
                'refund-requests',
              ].map((key) => client.invalidateQueries({ queryKey: [key] })),
            );
            setFee(null);
            setReason('');
          } catch (e) {
            setFailure(e instanceof Error ? e.message : 'Chưa điều chỉnh được phí.');
          } finally {
            setBusy(false);
          }
        }}
      >
        Ghi giảm phí và đối soát khoản hoàn thêm
      </Button>
    </Space>
  );
}
