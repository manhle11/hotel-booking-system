import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, App, Button, DatePicker, Input, Modal, Space, Typography } from 'antd';
import type { Dayjs } from 'dayjs';
import type { ContactChannel } from '../types/arrival-handling';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import {
  createBookingChange,
  getStaffCancellationQuote,
  getBookingChanges,
  type BookingChangeRequest,
  type ChangeType,
} from '../api/booking-change-api';

export function BookingChangeRequestForm({
  code,
  type,
  roomCount = 1,
  onClose,
  onSaved,
  initialReason = '',
  contactChannel,
}: {
  code: string;
  type: ChangeType;
  roomCount?: number;
  onClose: () => void;
  onSaved: () => void;
  initialReason?: string;
  contactChannel?: ContactChannel;
}) {
  const client = useQueryClient();
  const preview = useQuery({
    queryKey: ['staff-cancellation-quote', code],
    queryFn: () => getStaffCancellationQuote(code),
    enabled: type === 'CANCELLATION',
    retry: false,
  });
  const { message } = App.useApp();
  const [reason, setReason] = useState(initialReason.slice(0, 500));
  const [dates, setDates] = useState<[Dayjs, Dayjs] | null>(null);
  const [saved, setSaved] = useState<BookingChangeRequest | null>(null);
  const [failure, setFailure] = useState<string | null>(null);
  const mutation = useMutation({
    retry: false,
    mutationFn: () =>
      createBookingChange(code, {
        type,
        reason: reason.trim(),
        ...(contactChannel ? { contactChannel } : {}),
        ...(type === 'RESCHEDULE' && dates
          ? {
              checkInDate: dates[0].format('YYYY-MM-DD'),
              checkOutDate: dates[1].format('YYYY-MM-DD'),
            }
          : {}),
      }),
    onSuccess: (result) => {
      setSaved(result);
      onSaved();
      void client.invalidateQueries({ queryKey: ['booking-change-requests'] });
      void message.success(
        type === 'CANCELLATION'
          ? 'Đã hủy và giải phóng phòng; xem quyết toán.'
          : 'Đã ghi nhận yêu cầu; quản lý sẽ xem xét.',
      );
    },
    onError: async (error: Error) => {
      setFailure(error.message);
      try {
        const existing = (await getBookingChanges(code)).find(
          (r) =>
            r.status === 'PENDING' &&
            r.type === type &&
            r.reason === reason.trim() &&
            (type !== 'RESCHEDULE' ||
              (r.checkInDate === dates?.[0].format('YYYY-MM-DD') &&
                r.checkOutDate === dates?.[1].format('YYYY-MM-DD'))),
        );
        if (existing) {
          setSaved(existing);
          onSaved();
          setFailure(null);
        }
      } catch {
        setFailure(
          'Chưa tra cứu được kết quả. Thử lại với cùng thông tin sẽ không tạo trùng yêu cầu.',
        );
      }
    },
  });
  return (
    <Modal
      open
      title={type === 'CANCELLATION' ? 'Khách chủ động báo hủy' : 'Khách yêu cầu đổi lịch'}
      onCancel={() => {
        if (!mutation.isPending) onClose();
      }}
      footer={
        saved ? (
          <Button onClick={onClose}>Đóng</Button>
        ) : (
          <Space>
            <Button onClick={onClose} disabled={mutation.isPending}>
              Quay lại
            </Button>
            <Button
              type="primary"
              loading={mutation.isPending}
              disabled={!reason.trim() || (type === 'RESCHEDULE' && !dates)}
              onClick={() => mutation.mutate()}
            >
              {type === 'CANCELLATION'
                ? 'Khách xác nhận hủy, giải phóng phòng'
                : 'Gửi yêu cầu quản lý'}
            </Button>
          </Space>
        )
      }
    >
      {saved ? (
        <Space orientation="vertical">
          <Alert
            type="success"
            title="Đã ghi nhận yêu cầu của khách"
            description={
              type === 'CANCELLATION'
                ? 'Booking đã hủy và phòng đã giải phóng. Khoản hoàn cần đối soát; booking cũ có thể cần quản lý xác định phí.'
                : 'Booking vẫn giữ ngày cũ trong khi chờ quyết định, chưa bảo đảm phòng kỳ mới. Yêu cầu không kéo dài hạn tiếp nhận.'
            }
          />
          <Link to={`/front-desk/booking-requests?requestId=${encodeURIComponent(saved.id)}`}>
            Xem yêu cầu hủy / đổi lịch
          </Link>
        </Space>
      ) : (
        <Space orientation="vertical" style={{ width: '100%' }}>
          {type === 'CANCELLATION' && preview.data && (
            <Alert
              type="info"
              title="Điều kiện hủy hiện tại — chưa hủy booking"
              description={
                preview.data.requiresFeeDecision
                  ? 'Phí chưa đủ căn cứ, chuyển quản lý đối soát theo điều khoản cũ. Khách xác nhận hủy vẫn giải phóng phòng ngay.'
                  : `Tổng phí còn hiệu lực: ${(preview.data.feeAmount ?? 0).toLocaleString('vi-VN')}đ. Hoàn dự kiến: ${(preview.data.refundAmount ?? 0).toLocaleString('vi-VN')}đ. Mức phí tính lại tại thời điểm khách xác nhận.`
              }
            />
          )}
          {roomCount > 1 && (
            <Alert
              type="warning"
              title={`Yêu cầu áp dụng toàn bộ ${roomCount} phòng trong đơn`}
              description="Chỉ duyệt khi tất cả phòng đã xác nhận và chưa nhận phòng."
            />
          )}
          <Alert
            type="info"
            title={
              type === 'CANCELLATION'
                ? 'Ghi nhận ngay khi khách báo hủy'
                : 'Quản lý kiểm tra kỳ ở mới trước khi duyệt'
            }
            description={
              type === 'CANCELLATION'
                ? 'Khách xác nhận hủy sẽ giải phóng phòng ngay. Phí theo điều khoản đã chấp thuận; booking cũ thiếu căn cứ chuyển quản lý đối soát.'
                : 'Khi duyệt sẽ giữ mã booking, cập nhật ngày và giá kỳ mới; ghi phần thu thêm hoặc hoàn chênh lệch. Chưa duyệt thì ngày ở cũ vẫn giữ nguyên.'
            }
          />
          {type === 'RESCHEDULE' && (
            <>
              <Typography.Text>Kỳ ở khách đề nghị</Typography.Text>
              <DatePicker.RangePicker
                aria-label="Kỳ ở đề nghị"
                value={dates}
                onChange={(values) =>
                  setDates(values?.[0] && values[1] ? [values[0], values[1]] : null)
                }
                format="DD/MM/YYYY"
              />
              <Typography.Text type="secondary">
                Hệ thống kiểm tra ngày theo giờ khách sạn khi gửi và khi duyệt.
              </Typography.Text>
            </>
          )}
          <Typography.Text>Lý do / yêu cầu khách đã xác nhận</Typography.Text>
          <Input.TextArea
            aria-label="Yêu cầu khách"
            rows={3}
            maxLength={500}
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder="Ví dụ: khách gọi điện xác nhận muốn hủy / đổi sang ngày…"
          />
          {failure && <Alert type="error" title={failure} />}
        </Space>
      )}
    </Modal>
  );
}
