import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, App, Button, Form, Input, Modal, Rate, Skeleton } from 'antd';
import {
  CheckCircleFilled,
  EditOutlined,
  SafetyCertificateOutlined,
  StarFilled,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import { useState } from 'react';
import { useAuth } from '../authentication/providers/auth-context';
import { createReview, getReviewEligibility, type ReviewEligibility } from './api';
import './reviews.css';

const ratingLabels = [
  'Rất không hài lòng',
  'Chưa hài lòng',
  'Bình thường',
  'Hài lòng',
  'Rất hài lòng',
];
interface Props {
  bookingId: string;
  roomTypeName: string;
}

export function ReviewAction({ bookingId, roomTypeName }: Props) {
  const { isAuthenticated, session } = useAuth();
  const [open, setOpen] = useState(false);
  const [form] = Form.useForm<{ rating: number; comment?: string }>();
  const rating = Form.useWatch('rating', form) as number | undefined;
  const client = useQueryClient();
  const { message } = App.useApp();
  const queryKey = ['review-eligibility', session?.user?.id, bookingId];
  const eligibility = useQuery({
    queryKey,
    queryFn: () => getReviewEligibility(bookingId),
    enabled: open && isAuthenticated,
    retry: false,
  });
  const submit = useMutation({
    mutationFn: (values: { rating: number; comment?: string }) => createReview(bookingId, values),
    onSuccess: async (review) => {
      client.setQueryData<ReviewEligibility>(queryKey, (old) => ({
        canReview: false,
        reasonCode: 'REVIEW_ALREADY_EXISTS',
        message: 'Bạn đã đánh giá hạng phòng này trong lần lưu trú này.',
        deadline: old?.deadline ?? null,
        review,
      }));
      await Promise.all([
        client.invalidateQueries({ queryKey: ['room-reviews'] }),
        client.invalidateQueries({ queryKey: ['review-summary'] }),
        client.invalidateQueries({ queryKey: ['managed-reviews'] }),
        client.invalidateQueries({ queryKey: ['managed-review-summary'] }),
      ]);
      form.resetFields();
      void message.success('Cảm ơn bạn đã chia sẻ trải nghiệm!');
    },
    onError: (error: Error) => {
      void message.error(error.message);
      void eligibility.refetch();
    },
  });
  const data = eligibility.data;
  if (!isAuthenticated) return null;
  return (
    <>
      <Button className="review-write-button" icon={<EditOutlined />} onClick={() => setOpen(true)}>
        {data?.review ? 'Xem đánh giá của bạn' : 'Đánh giá hạng phòng'}
      </Button>
      <Modal
        className="review-compose"
        title={`Đánh giá ${roomTypeName}`}
        open={open}
        width={560}
        onCancel={() => !submit.isPending && setOpen(false)}
        mask={{ closable: !submit.isPending }}
        closable={!submit.isPending}
        footer={data?.canReview ? undefined : null}
        okText="Gửi đánh giá"
        cancelText="Để sau"
        confirmLoading={submit.isPending}
        okButtonProps={{ disabled: eligibility.isFetching, size: 'large' }}
        cancelButtonProps={{ size: 'large' }}
        onOk={() => form.submit()}
      >
        <div className="review-compose__intro">
          <span className="review-compose__icon">
            <StarFilled />
          </span>
          <div>
            <span className="review-eyebrow">SAU MỖI CHUYẾN ĐI</span>
            <h3>Trải nghiệm của bạn rất đáng giá</h3>
            <p>Chia sẻ cảm nhận để những vị khách tiếp theo có lựa chọn phù hợp hơn.</p>
          </div>
        </div>
        {eligibility.isLoading ? (
          <Skeleton active />
        ) : eligibility.isError ? (
          <Alert
            type="error"
            title={eligibility.error.message}
            action={<Button onClick={() => void eligibility.refetch()}>Thử lại</Button>}
          />
        ) : data?.review ? (
          <div className="review-submitted">
            <CheckCircleFilled className="review-submitted__check" />
            <h3>Cảm ơn bạn đã đánh giá</h3>
            <Rate disabled value={data.review.rating} />
            <p className="review-body">
              {data.review.comment || 'Bạn đã gửi đánh giá bằng số sao.'}
            </p>
            <span className="review-muted">
              Đã gửi ngày {dayjs(data.review.createdAt).format('DD/MM/YYYY')}
            </span>
          </div>
        ) : data?.canReview ? (
          <Form form={form} layout="vertical" onFinish={(values) => submit.mutate(values)}>
            <div className="review-rating-input">
              <Form.Item
                name="rating"
                label="Mức độ hài lòng"
                rules={[
                  { required: true, message: 'Vui lòng chọn từ 1 đến 5 sao' },
                  { type: 'number', min: 1, max: 5, message: 'Vui lòng chọn từ 1 đến 5 sao' },
                ]}
              >
                <Rate aria-label="Số sao đánh giá" tooltips={ratingLabels} />
              </Form.Item>
              <span className="review-rating-input__label" aria-live="polite">
                {rating ? ratingLabels[rating - 1] : 'Chạm vào ngôi sao để chấm điểm'}
              </span>
            </div>
            <Form.Item
              name="comment"
              label="Điều gì khiến bạn nhớ nhất?"
              extra="Nhận xét không bắt buộc · Tối đa 1.000 ký tự"
              rules={[{ max: 1000, message: 'Nhận xét tối đa 1.000 ký tự' }]}
            >
              <Input.TextArea
                rows={4}
                maxLength={1000}
                showCount
                placeholder="Không gian, sự thoải mái hay một điều khách sạn có thể làm tốt hơn…"
              />
            </Form.Item>
            <div className="review-compose__note">
              <SafetyCertificateOutlined />
              <span>
                Đánh giá gắn với lần lưu trú của tài khoản bạn.
                {data.deadline && (
                  <> Có thể gửi đến {dayjs(data.deadline).format('HH:mm DD/MM/YYYY')}.</>
                )}
              </span>
            </div>
          </Form>
        ) : data ? (
          <Alert type="info" showIcon title={data.message} />
        ) : null}
      </Modal>
    </>
  );
}
