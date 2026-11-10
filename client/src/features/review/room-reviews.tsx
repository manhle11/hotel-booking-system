import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Pagination, Rate, Select, Skeleton } from 'antd';
import {
  CheckCircleFilled,
  CommentOutlined,
  SafetyCertificateOutlined,
  StarFilled,
  ArrowRightOutlined,
} from '@ant-design/icons';
import { Link } from 'react-router-dom';
import dayjs from 'dayjs';
import { useState } from 'react';
import { getReviewSummary, getRoomReviews } from './api';
import './reviews.css';

function useReviewSummary(roomTypeId: string) {
  return useQuery({
    queryKey: ['review-summary', roomTypeId],
    queryFn: () => getReviewSummary(roomTypeId),
    staleTime: 60_000,
  });
}
export function RoomReviewSummary({ roomTypeId }: { roomTypeId: string }) {
  const query = useReviewSummary(roomTypeId);
  return (
    <span className="review-inline-score" aria-label="Điểm đánh giá của khách">
      <StarFilled />
      <span>
        {query.isPending ? (
          'Đang tải đánh giá…'
        ) : query.isError ? (
          'Chưa tải được điểm đánh giá'
        ) : query.data.totalReviews ? (
          <>
            <strong>{query.data.averageRating?.toLocaleString('vi-VN')} / 5</strong>
            <span className="review-inline-score__divider">·</span>
            {query.data.totalReviews} lượt đánh giá
          </>
        ) : (
          'Chưa có đánh giá'
        )}
      </span>
    </span>
  );
}
export function RoomReviewsSection({ roomTypeId }: { roomTypeId: string }) {
  const [page, setPage] = useState(0);
  const [rating, setRating] = useState<number>();
  const summary = useReviewSummary(roomTypeId);
  const query = useQuery({
    queryKey: ['room-reviews', roomTypeId, page, rating],
    queryFn: async () => {
      const result = await getRoomReviews(roomTypeId, page, rating);
      if (result.page !== page) setPage((current) => (current === page ? result.page : current));
      return result;
    },
  });
  return (
    <section className="room-reviews" aria-label="Đánh giá từ khách lưu trú">
      <header className="room-reviews__header">
        <div>
          <span className="review-eyebrow">LẮNG NGHE KHÁCH HÀNG</span>
          <h2>Những trải nghiệm tại HANBAY</h2>
          <p>Cảm nhận chân thực từ những kỳ lưu trú đã hoàn tất.</p>
        </div>
        <span className="review-verified-note">
          <SafetyCertificateOutlined /> Đặt phòng đã xác thực
        </span>
      </header>
      <div className="room-reviews__overview">
        <div className="review-score-tile">
          <StarFilled />
          <div>
            <span className="review-score-tile__number">
              {summary.data?.averageRating?.toLocaleString('vi-VN', { minimumFractionDigits: 1 }) ??
                '—'}
            </span>
            <span className="review-score-tile__outof"> / 5</span>
          </div>
          <span>
            {summary.isPending
              ? 'Đang tải đánh giá…'
              : summary.isError
                ? 'Chưa tải được điểm đánh giá'
                : summary.data.totalReviews
                  ? `${summary.data.totalReviews} lượt đánh giá`
                  : 'Chưa có đánh giá'}
          </span>
        </div>
        <div className="room-reviews__context">
          <h3>Mỗi chia sẻ đều giúp chúng tôi tốt hơn</h3>
          <p>
            Chỉ tài khoản đã đặt phòng và trả phòng mới có thể gửi đánh giá. Điểm số được tổng hợp
            từ những đánh giá đang hiển thị.
          </p>
          <Link to="/account/bookings" className="review-text-link">
            Xem đặt phòng để đánh giá <ArrowRightOutlined />
          </Link>
        </div>
      </div>
      <div className="room-reviews__toolbar">
        <h3>{rating ? `Đánh giá ${rating} sao` : 'Đánh giá từ khách lưu trú'}</h3>
        <Select
          aria-label="Lọc đánh giá theo số sao"
          value={rating ?? 0}
          onChange={(value) => {
            setRating(value || undefined);
            setPage(0);
          }}
          options={[
            { value: 0, label: 'Tất cả số sao' },
            ...[5, 4, 3, 2, 1].map((value) => ({ value, label: `${value} sao` })),
          ]}
        />
      </div>
      {query.isLoading ? (
        <Skeleton active />
      ) : query.isError ? (
        <Alert
          type="error"
          title="Không tải được đánh giá"
          action={<Button onClick={() => void query.refetch()}>Thử lại</Button>}
        />
      ) : !query.data?.content.length ? (
        <div className="review-empty">
          <span>
            <CommentOutlined />
          </span>
          <h3>
            {rating ? 'Chưa có đánh giá với số sao này' : 'Chưa có chia sẻ cho hạng phòng này'}
          </h3>
          <p>
            {rating
              ? 'Bạn có thể chọn số sao khác để xem thêm nhận xét.'
              : 'Hạng phòng chưa có đánh giá. Hãy chia sẻ trải nghiệm sau khi trả phòng.'}
          </p>
          {rating && (
            <Button
              onClick={() => {
                setRating(undefined);
                setPage(0);
              }}
            >
              Xem tất cả đánh giá
            </Button>
          )}
        </div>
      ) : (
        <>
          <div className="room-reviews__list">
            {query.data.content.map((review) => (
              <article key={review.id} className="guest-review">
                <header className="guest-review__header">
                  <div className="guest-review__identity">
                    <span className="guest-review__avatar" aria-hidden="true">
                      {review.authorName.charAt(0)}
                    </span>
                    <div>
                      <strong>{review.authorName}</strong>
                      <span className="guest-review__verified">
                        <CheckCircleFilled /> Đã hoàn tất lưu trú
                      </span>
                    </div>
                  </div>
                  <time dateTime={review.createdAt}>
                    {dayjs(review.createdAt).format('DD/MM/YYYY')}
                  </time>
                </header>
                <div className="guest-review__rating">
                  <Rate disabled value={review.rating} style={{ fontSize: 17 }} />
                  <span>{review.rating}/5</span>
                </div>
                {review.comment && <p className="review-body">{review.comment}</p>}
                {review.hotelReply && (
                  <div className="hotel-review-reply">
                    <div>
                      <strong>Phản hồi từ HANBAY Hotel</strong>
                      {review.repliedAt && (
                        <time dateTime={review.repliedAt}>
                          {dayjs(review.repliedAt).format('DD/MM/YYYY')}
                        </time>
                      )}
                    </div>
                    <p className="review-body">{review.hotelReply}</p>
                  </div>
                )}
              </article>
            ))}
          </div>
          <Pagination
            current={page + 1}
            total={query.data.totalElements}
            pageSize={6}
            showSizeChanger={false}
            onChange={(value) => setPage(value - 1)}
            hideOnSinglePage
          />
        </>
      )}
    </section>
  );
}
