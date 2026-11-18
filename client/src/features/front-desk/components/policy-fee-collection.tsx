import { Alert, Button, Checkbox, Input, InputNumber, Space, Typography } from 'antd';
import { useState } from 'react';
import { ApiClientError } from '../../../types/api';
import {
  collectPolicyFee,
  getArrivalHandling,
  type PolicyFeeCollectionCommand,
} from '../api/arrival-handling-api';
import type { ArrivalHandling } from '../types/arrival-handling';

export function PolicyFeeCollection({
  data,
  onCollected,
}: {
  data: ArrivalHandling;
  onCollected: (updated: ArrivalHandling) => Promise<void>;
}) {
  const [amount, setAmount] = useState<number | null>(data.outstandingAmount);
  const [note, setNote] = useState('');
  const [received, setReceived] = useState(false);
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);
  const [command, setCommand] = useState<PolicyFeeCollectionCommand | null>(null);
  if (
    data.settlementReviewRequired ||
    data.outstandingAmount <= 0 ||
    !(data.bookingStatus === 'NO_SHOW' || data.staffCancellation)
  )
    return null;

  async function submit() {
    if (busy || amount === null) return;
    const next = command ?? {
      requestId: crypto.randomUUID(),
      amount,
      expectedOutstandingAmount: data.outstandingAmount,
      note: note.trim(),
    };
    setCommand(next);
    setBusy(true);
    setFailure(null);
    try {
      const updated = await collectPolicyFee(data.bookingCode, next);
      await onCollected(updated);
      setCommand(null);
      setAmount(updated.outstandingAmount);
      setReceived(false);
      setNote('');
    } catch (error) {
      setFailure(error instanceof Error ? error.message : 'Chưa xác minh được kết quả thu tiền.');
      if (error instanceof ApiClientError && error.status === 409) {
        try {
          const latest = await getArrivalHandling(data.bookingCode);
          await onCollected(latest);
          setCommand(null);
          setAmount(latest.outstandingAmount);
          setReceived(false);
        } catch {
          // Preserve the receipt until its outcome can be verified.
        }
      }
    } finally {
      setBusy(false);
    }
  }
  return (
    <Space orientation="vertical" style={{ width: '100%' }}>
      <Typography.Text strong>
        Còn phải thu phí: {data.outstandingAmount.toLocaleString('vi-VN')}đ
      </Typography.Text>
      <InputNumber
        aria-label="Tiền mặt phí đã nhận"
        min={1}
        max={data.outstandingAmount}
        precision={0}
        value={amount}
        onChange={setAmount}
        disabled={busy || command !== null}
      />
      <Input.TextArea
        aria-label="Ghi chú thu phí"
        value={note}
        maxLength={500}
        onChange={(e) => setNote(e.target.value)}
        disabled={busy || command !== null}
        placeholder="Nội dung thu tiền, người nộp hoặc số phiếu thu"
      />
      <Checkbox
        checked={received}
        disabled={busy || command !== null}
        onChange={(e) => setReceived(e.target.checked)}
      >
        Đã nhận đủ số tiền mặt nhập ở trên
      </Checkbox>
      {failure && (
        <Alert
          type="error"
          title={failure}
          description={
            command
              ? 'Gửi lại cùng xác nhận để tra cứu kết quả; hệ thống không ghi thu trùng.'
              : 'Đã cập nhật công nợ. Kiểm tra lại số tiền thực nhận trước khi xác nhận.'
          }
        />
      )}
      <Button
        type="primary"
        loading={busy}
        disabled={
          !command && (!received || !note.trim() || !amount || amount > data.outstandingAmount)
        }
        onClick={() => void submit()}
      >
        {command ? 'Tra cứu / gửi lại xác nhận thu' : 'Ghi nhận đã thu tiền mặt'}
      </Button>
    </Space>
  );
}
