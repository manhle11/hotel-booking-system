import { ArrowRightOutlined, CalendarOutlined } from '@ant-design/icons';
import { Button, DatePicker, InputNumber, Typography } from 'antd';
import type { Dayjs } from 'dayjs';
import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';

import { StayPriceSummary } from '../../features/booking/components/stay-price-summary';
import { useStayPrice } from '../../features/booking/hooks/use-stay-price';
import { getStayPriceSelection } from '../../features/booking/utils/stay-price-selection';
import type { RoomTypeDetail } from '../../features/room/types/room-detail';
import { formatCurrency } from '../../features/room/pages/room-search-utils';
import { countStayNights } from '../../utils/hotel-time';
import { useStayPolicy } from '../../hooks/use-stay-policy';
import { StayPolicyNote } from './StayPolicyNote';

export function BookingCard({ roomType }: { roomType: RoomTypeDetail }) {
  const { disabledArrivalDate } = useStayPolicy();
  const navigate = useNavigate();
  const [dates, setDates] = useState<[Dayjs | null, Dayjs | null] | null>(null);
  const [adults, setAdults] = useState(Math.min(2, roomType.capacityAdults));
  const [children, setChildren] = useState(0);
  const nights = useMemo(() => countStayNights(dates?.[0], dates?.[1]), [dates]);
  const stayPriceSelection = getStayPriceSelection(roomType.id, dates);
  const stayPriceRequest = stayPriceSelection.request;
  const stayPriceQuery = useStayPrice(stayPriceRequest);
  const guestCount = adults + children;
  const validGuests =
    adults >= 1 &&
    adults <= roomType.capacityAdults &&
    children <= roomType.capacityChildren &&
    guestCount <= roomType.maxGuests;
  const canContinue = nights > 0 && validGuests && stayPriceQuery.isSuccess;

  const continueBooking = () => {
    if (!canContinue || !dates?.[0] || !dates[1]) return;
    const params = new URLSearchParams({
      roomTypeId: roomType.id,
      checkIn: dates[0].format('YYYY-MM-DD'),
      checkOut: dates[1].format('YYYY-MM-DD'),
      adults: String(adults),
      children: String(children),
    });
    navigate(`/bookings/new?${params.toString()}`);
  };

  return (
    <section
      id="room-booking"
      className="booking-card"
      aria-labelledby="room-booking-title"
      tabIndex={-1}
    >
      <header className="booking-card__header">
        <h2 id="room-booking-title">
          <CalendarOutlined aria-hidden /> Đặt phòng
        </h2>
        <div className="booking-card__price">
          <span>Giá cơ bản</span>
          <div>
            <strong>{formatCurrency(roomType.basePrice)}</strong> <span>/ đêm</span>
          </div>
        </div>
      </header>
      <div className="booking-card__body">
        <label className="booking-card__label" htmlFor="room-check-in">
          Ngày lưu trú
        </label>
        <DatePicker.RangePicker
          id={{ start: 'room-check-in', end: 'room-check-out' }}
          size="large"
          className="booking-card__control"
          format="DD/MM/YYYY"
          placeholder={['Nhận phòng', 'Trả phòng']}
          disabledDate={disabledArrivalDate}
          onChange={setDates}
        />
        <div className="booking-card__guests">
          <div>
            <label className="booking-card__label" htmlFor="room-adults">
              Người lớn
            </label>
            <InputNumber
              id="room-adults"
              size="large"
              className="booking-card__control"
              min={1}
              max={roomType.capacityAdults}
              value={adults}
              onChange={(value) => setAdults(value ?? 1)}
            />
          </div>
          <div>
            <label className="booking-card__label" htmlFor="room-children">
              Trẻ em
            </label>
            <InputNumber
              id="room-children"
              size="large"
              className="booking-card__control"
              min={0}
              max={roomType.capacityChildren}
              value={children}
              onChange={(value) => setChildren(value ?? 0)}
            />
          </div>
        </div>
        {!validGuests && (
          <Typography.Text type="danger">
            Tổng số khách không được vượt quá {roomType.maxGuests} người.
          </Typography.Text>
        )}
        <div className="booking-card__summary" aria-live="polite">
          <StayPriceSummary
            data={stayPriceQuery.data}
            error={stayPriceQuery.error}
            isLoading={stayPriceQuery.isLoading}
            validationMessage={stayPriceSelection.validationMessage}
            hasSelection={Boolean(stayPriceRequest)}
            onRetry={() => void stayPriceQuery.refetch()}
          />
        </div>
        <Button
          type="primary"
          size="large"
          block
          disabled={!canContinue}
          onClick={continueBooking}
          icon={<ArrowRightOutlined />}
          iconPlacement="end"
        >
          Tiếp tục đặt phòng
        </Button>
        <div className="booking-card__notes">
          <StayPolicyNote />
          <Typography.Text type="secondary">
            Phòng cụ thể sẽ được lễ tân sắp xếp khi nhận phòng.
          </Typography.Text>
        </div>
      </div>
    </section>
  );
}
