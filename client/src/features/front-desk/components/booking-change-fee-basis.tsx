import { Tag, Typography } from 'antd';
import dayjs from 'dayjs';
import type { BookingChangeQuote } from '../api/booking-change-api';
import { formatHotelDateTime } from '../../../utils/hotel-time';

type Room = BookingChangeQuote['rooms'][number];

const money = (value: number) =>
  new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(value);

function FirstNightFeeTable({ rooms, rate }: { rooms: Room[]; rate: number }) {
  if (rooms.some((room) => room.originalFirstNightAmount == null)) return null;
  const previousFees = rooms.some((room) => (room.previousFee ?? 0) > 0);
  const oneNightStay = rooms.every(
    (room) =>
      room.oldCheckOutDate === dayjs(room.oldCheckInDate).add(1, 'day').format('YYYY-MM-DD') &&
      room.oldTotalAmount === room.originalFirstNightAmount &&
      (room.previousFee ?? 0) === 0,
  );
  return (
    <>
      <div className="booking-change-review__fee-table-scroll">
        <table className="booking-change-review__fee-table">
          <caption>Phí tính trên đêm đầu từng phòng</caption>
          <thead>
            <tr>
              <th scope="col">Phòng</th>
              <th scope="col">Căn cứ đêm đầu gốc (P)</th>
              <th scope="col">
                {previousFees ? 'Phí chuẩn sau đối chiếu phí cũ' : 'Phí theo mốc giờ'}
              </th>
            </tr>
          </thead>
          <tbody>
            {rooms.map((room) => (
              <tr key={room.bookingCode}>
                <th scope="row">
                  {room.roomTypeName}
                  <small>{room.bookingCode}</small>
                </th>
                <td>
                  {rate}% × {money(room.originalFirstNightAmount!)}
                </td>
                <td>{money(room.standardFee)}</td>
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr>
              <th scope="row" colSpan={2}>
                Tổng phí theo chính sách của {rooms.length} phòng
              </th>
              <td>{money(rooms.reduce((sum, room) => sum + room.standardFee, 0))}</td>
            </tr>
          </tfoot>
        </table>
      </div>
      <Typography.Text type="secondary">
        Giá đêm đầu gốc đã trừ giảm giá; các đêm tiếp theo và dịch vụ được tính riêng.
        {previousFees && ' Phí chuẩn giữ mức đã phát sinh nếu cao hơn phí theo mốc giờ.'}
      </Typography.Text>
      {oneNightStay && (
        <div>
          Các phòng trên chỉ đặt 1 đêm, nên tiền phòng đêm đầu cũng là tiền phòng cả kỳ trước khi
          đổi.
        </div>
      )}
    </>
  );
}

function feeRule(room: Room, cancellation: boolean) {
  if (!cancellation && room.newCheckInDate === room.oldCheckInDate && room.newCheckOutDate) {
    if (room.newCheckOutDate > room.oldCheckOutDate) return 'EXTENSION';
    if (room.newCheckOutDate < room.oldCheckOutDate) return 'SHORTENING';
  }
  return 'FIRST_NIGHT';
}

export function BookingChangeFeeBasis({ data }: { data: BookingChangeQuote }) {
  const groups = new Map<string, Room[]>();
  for (const room of data.rooms) {
    if (!room.feeTiming) continue;
    const key = `${JSON.stringify(room.feeTiming)}:${feeRule(room, data.request.type === 'CANCELLATION')}`;
    groups.set(key, [...(groups.get(key) ?? []), room]);
  }
  if (groups.size === 0) return null;
  const time = (value: string) => formatHotelDateTime(value, data.zone);
  const hints = new Set(
    [...groups.values()].map(([room]) => {
      const rule = feeRule(room, data.request.type === 'CANCELLATION');
      if (rule === 'EXTENSION') return 'Không thêm phí đổi';
      return `${room.feeTiming!.ratePercent}% ${rule === 'SHORTENING' ? 'đêm đầu bị bỏ' : 'đêm đầu mỗi phòng'}`;
    }),
  );
  const hint = data.rooms.some((room) => (room.previousFee ?? 0) > 0)
    ? 'Đã đối chiếu phí trước đây'
    : hints.size === 1
      ? [...hints][0]
      : 'Theo từng phòng';

  return (
    <details className="booking-change-review__fee-disclosure">
      <summary>
        Cách tính phí
        <span className="booking-change-review__fee-hint">{hint}</span>
      </summary>
      <section className="booking-change-review__fee-basis" aria-label="Căn cứ tính phí">
        <div>
          Tiếp nhận yêu cầu: {time(data.request.requestedAt)} · giờ khách sạn ({data.zone}).
        </div>
        {[...groups.entries()].map(([key, rooms]) => {
          const room = rooms[0];
          const timing = room.feeTiming!;
          const rule = feeRule(room, data.request.type === 'CANCELLATION');
          const minutes = dayjs(timing.checkInAt).diff(dayjs(data.request.requestedAt), 'minute');
          return (
            <div className="booking-change-review__fee-window" key={key}>
              <Typography.Text type="secondary">
                {rooms.map((item) => item.bookingCode).join(' · ')}
              </Typography.Text>
              <div>
                <Tag color="blue">
                  {rule === 'EXTENSION' ? 'Không thêm phí đổi' : `Mốc phí ${timing.ratePercent}%`}
                </Tag>
                Nhận phòng kỳ hiện tại: {time(timing.checkInAt)}
                {minutes >= 0 && ` (còn ${Math.floor(minutes / 60)} giờ ${minutes % 60} phút).`}
              </div>
              {rule === 'EXTENSION' ? (
                <div>
                  Giữ ngày nhận và thêm đêm: giữ phí trước đây, không phát sinh thêm phí đổi.
                </div>
              ) : (
                <>
                  <div>
                    {rule === 'SHORTENING'
                      ? 'Tỷ lệ áp dụng trên giá đêm đầu bị bỏ, có giới hạn theo phần bỏ và đêm đầu gốc.'
                      : 'Phí hủy / đổi lịch = tỷ lệ theo mốc giờ × giá phòng đêm đầu tiên đã chốt của từng phòng.'}
                  </div>
                  {rule === 'FIRST_NIGHT' && (data.pricingAvailable ?? data.canApprove) && (
                    <FirstNightFeeTable rooms={rooms} rate={timing.ratePercent} />
                  )}
                  <ul>
                    <li>0%: tiếp nhận đến {time(timing.freeUntil)} (còn ít nhất 48 giờ).</li>
                    <li>
                      50%: sau {time(timing.freeUntil)} đến {time(timing.halfFeeUntil)} (còn từ 24
                      đến dưới 48 giờ).
                    </li>
                    <li>
                      100%: sau {time(timing.halfFeeUntil)} (còn dưới 24 giờ hoặc đã qua giờ nhận).
                    </li>
                  </ul>
                </>
              )}
              {rooms.some((item) => (item.previousFee ?? 0) > 0) && (
                <div>Phí đã phát sinh trước đây được giữ; chỉ thu bổ sung phần phí còn thiếu.</div>
              )}
            </div>
          );
        })}
        <Typography.Text type="secondary">
          Tính theo thời điểm gửi yêu cầu và kỳ ở hiện tại trước khi đổi. Hai ngày trên lịch có thể
          chưa đủ 48 giờ. Tiền tăng đêm và chênh lệch giá kỳ mới được tính riêng với phí đổi.
        </Typography.Text>
      </section>
    </details>
  );
}
