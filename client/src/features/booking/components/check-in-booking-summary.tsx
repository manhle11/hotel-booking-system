import { Alert, Descriptions, Tag } from 'antd';
import dayjs from 'dayjs';
import type { BookingStatus, CheckInBookingContext } from '../types/booking';
import { formatCurrency } from '../../room/pages/room-search-utils';

const statusLabels: Record<BookingStatus, string> = {
  PENDING: 'Chờ xác nhận',
  CONFIRMED: 'Đã xác nhận',
  CHECKED_IN: 'Đã nhận phòng',
  CHECKED_OUT: 'Đã trả phòng',
  CANCELLED: 'Đã hủy',
  EXPIRED: 'Đã hết hạn',
  NO_SHOW: 'Không đến',
};

export function CheckInBookingSummary({ booking }: { booking: CheckInBookingContext }) {
  return (
    <div className="check-in-page__booking-summary">
      <Descriptions title="Đối chiếu booking" bordered size="small" column={{ xs: 1, sm: 2 }}>
        <Descriptions.Item label="Booking">{booking.bookingCode}</Descriptions.Item>
        <Descriptions.Item label="Trạng thái">
          <Tag>{statusLabels[booking.bookingStatus]}</Tag>
        </Descriptions.Item>
        <Descriptions.Item label="Khách chính">
          {booking.primaryGuest?.fullName ?? 'Chưa có thông tin khách'}
        </Descriptions.Item>
        <Descriptions.Item label="Liên hệ">
          {[booking.primaryGuest?.phone, booking.primaryGuest?.email].filter(Boolean).join(' · ') ||
            'Chưa cập nhật'}
        </Descriptions.Item>
        <Descriptions.Item label="Hạng phòng">{booking.roomType.name}</Descriptions.Item>
        <Descriptions.Item label="Số khách">
          {booking.adultCount} người lớn · {booking.childCount} trẻ em
        </Descriptions.Item>
        <Descriptions.Item label="Ngày nhận">
          {dayjs(booking.checkInDate).format('DD/MM/YYYY')}
        </Descriptions.Item>
        <Descriptions.Item label="Ngày trả">
          {dayjs(booking.checkOutDate).format('DD/MM/YYYY')}
        </Descriptions.Item>
        <Descriptions.Item label="Tổng tiền">
          {formatCurrency(booking.totalAmount)}
        </Descriptions.Item>
        <Descriptions.Item label="Đã thanh toán">
          {formatCurrency(booking.paidAmount)}
        </Descriptions.Item>
        <Descriptions.Item label="Còn phải thu">
          {formatCurrency(booking.outstandingAmount)}
        </Descriptions.Item>
      </Descriptions>
      {!booking.primaryGuest && (
        <Alert
          type="warning"
          showIcon
          title="Cần đối chiếu thông tin khách trước khi bàn giao phòng."
        />
      )}
      {booking.outstandingAmount > 0 && booking.canCheckIn && (
        <Alert
          type="info"
          showIcon
          title="Booking đã xác nhận còn công nợ. Khoản còn lại cần được theo dõi và quyết toán."
        />
      )}
      {booking.bookingStatus === 'CONFIRMED' && !booking.canCheckIn && (
        <Alert
          type="warning"
          showIcon
          title="Booking nằm ngoài khung giờ tiếp nhận"
          description="Liên hệ khách và xử lý theo chính sách khách sạn. Booking vẫn được giữ để theo dõi."
        />
      )}
      {booking.bookingStatus !== 'CONFIRMED' && booking.bookingStatus !== 'CHECKED_IN' && (
        <Alert
          type="warning"
          showIcon
          title={`Booking ${statusLabels[booking.bookingStatus].toLowerCase()}, không thể nhận phòng.`}
        />
      )}
    </div>
  );
}
