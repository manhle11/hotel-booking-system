import { Collapse, Table, Tag, Typography } from 'antd';
import dayjs from 'dayjs';
import {
  allocateBookingChangeFees,
  cancellationSettlement,
  type BookingChangeQuote,
} from '../api/booking-change-api';
import { countStayNights } from '../../../utils/hotel-time';
import { BookingChangeFeeBasis } from './booking-change-fee-basis';
import './booking-change-review.css';

const money = (value: number) =>
  new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(value);

function StayRange({ start, end }: { start: string; end: string }) {
  return (
    <>
      <span>
        {dayjs(start).format('DD/MM/YYYY')} → {dayjs(end).format('DD/MM/YYYY')}
      </span>
      <Typography.Text type="secondary">
        {countStayNights(dayjs(start), dayjs(end))} đêm
      </Typography.Text>
    </>
  );
}

export function BookingChangeStayComparison({ data }: { data: BookingChangeQuote }) {
  const cancellation = data.request.type === 'CANCELLATION';
  return (
    <div className="booking-change-review__rooms">
      {data.rooms.map((room) => {
        const oldNights = countStayNights(dayjs(room.oldCheckInDate), dayjs(room.oldCheckOutDate));
        const newNights = countStayNights(dayjs(room.newCheckInDate), dayjs(room.newCheckOutDate));
        const difference = newNights - oldNights;
        return (
          <article className="booking-change-review__room" key={room.bookingCode}>
            <div className="booking-change-review__room-title">
              <Typography.Text strong>{room.bookingCode}</Typography.Text>
              <Typography.Text type="secondary">{room.roomTypeName}</Typography.Text>
              {!cancellation && room.newCheckInDate && room.newCheckOutDate && (
                <Tag color={difference === 0 ? 'blue' : 'orange'}>
                  {difference === 0
                    ? 'Giữ số đêm'
                    : `${difference > 0 ? 'Tăng' : 'Giảm'} ${Math.abs(difference)} đêm`}
                </Tag>
              )}
            </div>
            <div className="booking-change-review__stay-grid">
              <div className="booking-change-review__stay">
                <Typography.Text type="secondary">Kỳ ở hiện tại</Typography.Text>
                <StayRange start={room.oldCheckInDate} end={room.oldCheckOutDate} />
              </div>
              <div className="booking-change-review__stay booking-change-review__stay--new">
                <Typography.Text type="secondary">
                  {cancellation ? 'Sau khi hủy' : 'Kỳ ở đề nghị'}
                </Typography.Text>
                {cancellation ? (
                  <span>Giải phóng phần giữ chỗ chưa sử dụng</span>
                ) : room.newCheckInDate && room.newCheckOutDate ? (
                  <StayRange start={room.newCheckInDate} end={room.newCheckOutDate} />
                ) : (
                  <span>Chưa có ngày đề nghị</span>
                )}
              </div>
            </div>
          </article>
        );
      })}
    </div>
  );
}

export function BookingChangeQuoteSummary({
  data,
  fee,
  customer = false,
}: {
  data: BookingChangeQuote;
  fee: number | null;
  customer?: boolean;
}) {
  const cancellation = data.request.type === 'CANCELLATION';
  const maxFee = data.rooms.reduce((sum, room) => sum + room.standardFee, 0);
  const validFee = fee !== null && Number.isSafeInteger(fee) && fee >= 0 && fee <= maxFee;
  const pricingAvailable = data.pricingAvailable ?? data.canApprove;
  const quoteAvailable = pricingAvailable && validFee;
  const previousFee = data.rooms.reduce((sum, room) => sum + (room.previousFee ?? 0), 0);
  const feeDifference = fee === null ? null : fee - previousFee;
  const oldStayAmount = Math.max(data.oldTotalAmount - previousFee, 0);
  const projected = quoteAvailable ? cancellationSettlement(data, fee) : null;
  const allocated = validFee ? allocateBookingChangeFees(data, fee) : [];
  const shownAmount = (value: number) => (quoteAvailable ? money(value) : 'Chưa xác định');

  return (
    <div className={`booking-change-quote${customer ? ' booking-change-quote--customer' : ''}`}>
      {!customer && <BookingChangeFeeBasis data={data} />}
      <dl className="booking-change-review__amounts" aria-label="Chi tiết báo giá">
        {customer ? (
          <>
            {!cancellation && (
              <div>
                <dt>Giá kỳ mới (phòng + dịch vụ)</dt>
                <dd>{shownAmount(data.newTotalAmount)}</dd>
              </div>
            )}
            <div>
              <dt>{cancellation ? 'Phí hủy' : 'Phí đổi lịch'}</dt>
              <dd>{fee === null ? 'Chưa xác định' : shownAmount(fee)}</dd>
            </div>
            <div className="booking-change-review__total">
              <dt>Tổng tiền sau {cancellation ? 'hủy' : 'đổi'}</dt>
              <dd>
                {fee === null
                  ? 'Chưa xác định'
                  : shownAmount((cancellation ? 0 : data.newTotalAmount) + fee)}
              </dd>
            </div>
            <div>
              <dt>Tiền đã trả được bù trừ</dt>
              <dd>{money(data.availableAmount)}</dd>
            </div>
          </>
        ) : (
          <>
            <div>
              <dt>Tổng đang ghi nhận</dt>
              <dd>{money(data.oldTotalAmount)}</dd>
            </div>
            {!cancellation && (
              <>
                <div>
                  <dt>Giá kỳ mới (phòng + dịch vụ)</dt>
                  <dd>{shownAmount(data.newTotalAmount)}</dd>
                </div>
                <div className="booking-change-review__secondary-row">
                  <dt>Chênh lệch giá kỳ ở, chưa gồm phí</dt>
                  <dd>
                    {quoteAvailable
                      ? `${data.newTotalAmount > oldStayAmount ? '+' : ''}${money(data.newTotalAmount - oldStayAmount)}`
                      : 'Chưa xác định'}
                  </dd>
                </div>
              </>
            )}
            <div>
              <dt>Phí đã ghi trước đây</dt>
              <dd>{money(previousFee)}</dd>
            </div>
            <div>
              <dt>
                {feeDifference !== null && feeDifference < 0
                  ? 'Điều chỉnh giảm phí lần này'
                  : 'Phí phát sinh lần này'}
              </dt>
              <dd>{feeDifference === null ? 'Chưa xác định' : shownAmount(feeDifference)}</dd>
            </div>
            <div>
              <dt>Tổng phí còn hiệu lực sau xử lý</dt>
              <dd>{fee === null ? 'Chưa xác định' : shownAmount(fee)}</dd>
            </div>
            <div className="booking-change-review__total">
              <dt>Tổng nghĩa vụ sau {cancellation ? 'hủy' : 'đổi'}</dt>
              <dd>
                {fee === null
                  ? 'Chưa xác định'
                  : shownAmount((cancellation ? 0 : data.newTotalAmount) + fee)}
              </dd>
            </div>
            <div>
              <dt>Tiền khách còn được bù trừ</dt>
              <dd>{money(data.availableAmount)}</dd>
            </div>
          </>
        )}
      </dl>
      {projected ? (
        <div
          className="booking-change-review__settlement"
          aria-label="Kết quả thu hoàn"
          aria-live="polite"
        >
          {projected.additional > 0 && (
            <div>
              <span>{customer ? 'Bạn cần thanh toán thêm' : 'Còn phải thu'}</span>
              <strong>{money(projected.additional)}</strong>
            </div>
          )}
          {projected.refund > 0 && (
            <div>
              <span>{customer ? 'Bạn được hoàn' : 'Cần hoàn cho khách'}</span>
              <strong>{money(projected.refund)}</strong>
            </div>
          )}
          {projected.additional === 0 && projected.refund === 0 && (
            <div>
              <span>Không cần thu hoặc hoàn thêm</span>
              <strong>{money(0)}</strong>
            </div>
          )}
        </div>
      ) : (
        <Typography.Paragraph type="secondary" className="booking-change-review__quote-pending">
          {pricingAvailable
            ? customer
              ? 'Khách sạn cần xác định mức phí trước khi tính khoản thanh toán thêm hoặc hoàn.'
              : 'Chọn mức phí được duyệt để tính số tiền cần thu hoặc hoàn.'
            : 'Chưa có báo giá áp dụng. Xử lý điều kiện đang chặn trước khi chốt tiền với khách.'}
        </Typography.Paragraph>
      )}
      {projected && !data.canApprove && !customer && (
        <Typography.Paragraph type="secondary" className="booking-change-review__quote-pending">
          Số tiền dự kiến theo ngày đề nghị. Chờ khách sạn xác nhận điều kiện áp dụng; chưa thu thêm
          hoặc tạo khoản hoàn.
        </Typography.Paragraph>
      )}
      {projected && projected.refund > 0 && (
        <Typography.Paragraph type="secondary" className="booking-change-review__quote-pending">
          {customer
            ? 'Khoản hoàn sẽ được xử lý sau khi duyệt.'
            : 'Duyệt sẽ tạo khoản hoàn chờ xử lý; quản lý xác nhận đã hoàn sau khi đối soát.'}
        </Typography.Paragraph>
      )}
      {customer && <BookingChangeFeeBasis data={data} />}
      <Collapse
        ghost
        items={[
          {
            key: 'detail',
            label: customer
              ? 'Chi tiết kỳ ở và giá phòng'
              : 'Chi tiết giá từng đêm và căn cứ phí theo phòng',
            children: (
              <div className="booking-change-review__detail">
                {customer && (
                  <>
                    <BookingChangeStayComparison data={data} />
                    {data.rooms
                      .filter((room) => room.services?.length)
                      .map((room) => (
                        <Typography.Text key={room.bookingCode}>
                          {room.roomTypeName}: {room.services!.join('; ')}
                        </Typography.Text>
                      ))}
                  </>
                )}
                <Typography.Paragraph type="secondary">
                  {data.policyDescription}
                </Typography.Paragraph>
                <Typography.Paragraph type="secondary">
                  Giá đêm đầu gốc là căn cứ giới hạn phí, không phải giá bình quân kỳ ở. Tiền bù trừ
                  đã loại các khoản đã hoàn hoặc đang dành cho khoản hoàn khác.
                </Typography.Paragraph>
                <Table
                  size="small"
                  pagination={false}
                  rowKey="bookingCode"
                  scroll={{ x: 560 }}
                  dataSource={data.rooms.map((room, index) => ({
                    ...room,
                    approvedFee: allocated[index],
                  }))}
                  columns={[
                    { title: 'Booking / phòng', dataIndex: 'bookingCode' },
                    {
                      title: 'Giá đêm đầu gốc',
                      dataIndex: 'originalFirstNightAmount',
                      align: 'right',
                      render: (value: number | null | undefined) =>
                        value == null ? 'Theo điều khoản cũ' : money(value),
                    },
                    {
                      title: 'Phí trước đây',
                      dataIndex: 'previousFee',
                      align: 'right',
                      render: (value: number | undefined) => money(value ?? 0),
                    },
                    {
                      title: 'Phí sau xử lý',
                      dataIndex: 'approvedFee',
                      align: 'right',
                      render: (value: number | undefined) =>
                        quoteAvailable && value !== undefined ? money(value) : 'Chưa xác định',
                    },
                    {
                      title: 'Tiền bù trừ',
                      dataIndex: 'availableAmount',
                      align: 'right',
                      render: money,
                    },
                  ]}
                />
                {!cancellation &&
                  pricingAvailable &&
                  data.rooms.map(
                    (room) =>
                      room.nights.length > 0 && (
                        <div key={room.bookingCode}>
                          <Typography.Text strong>
                            {room.bookingCode} · {room.roomTypeName}
                          </Typography.Text>
                          <Table
                            size="small"
                            pagination={false}
                            rowKey="stayDate"
                            dataSource={room.nights}
                            columns={[
                              {
                                title: 'Đêm lưu trú',
                                dataIndex: 'stayDate',
                                render: (value: string) => dayjs(value).format('DD/MM/YYYY'),
                              },
                              {
                                title: 'Giá đã báo',
                                dataIndex: 'price',
                                align: 'right',
                                render: money,
                              },
                            ]}
                          />
                        </div>
                      ),
                  )}
              </div>
            ),
          },
        ]}
      />
    </div>
  );
}
