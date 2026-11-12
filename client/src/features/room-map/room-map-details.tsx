import { Typography } from 'antd';
import type { MapRoom } from './api';
import { roomAreaLabel } from './room-map-scale';
import { roomThemeClass } from './room-map-theme';
import { BED_TYPE_LABELS } from '../room/types/room-configuration';
import { roomPriceFormatter } from '../room/utils/room-presentation';

export function RoomMapDetails({ room }: { room: MapRoom }) {
  const info = room.typeInfo;
  const image = info?.images?.[0] || room.thumbnailUrl;
  const beds = info?.beds
    ?.map((bed) => `${BED_TYPE_LABELS[bed.bedType] ?? bed.bedType} × ${bed.quantity}`)
    .join(' · ');
  return (
    <div className="room-map-details">
      {image && (
        <img
          className="room-map-detail-image"
          src={image}
          alt={`Ảnh minh họa hạng ${room.roomTypeName}`}
        />
      )}
      {info?.category?.name && (
        <span className={`room-map-detail-category ${roomThemeClass(room)}`}>
          {info.category.name}
        </span>
      )}
      <Typography.Title level={4}>{room.roomTypeName}</Typography.Title>
      {info?.shortDescription && (
        <Typography.Paragraph type="secondary">{info.shortDescription}</Typography.Paragraph>
      )}
      <dl className="room-map-detail-facts">
        <div>
          <dt>Diện tích</dt>
          <dd>{roomAreaLabel(room.areaM2)}</dd>
        </div>
        <div>
          <dt>Giường</dt>
          <dd>{beds || info?.bedType || room.bedType || 'Chưa cập nhật'}</dd>
        </div>
        {typeof info?.maxGuests === 'number' && (
          <div>
            <dt>Sức chứa</dt>
            <dd>Tối đa {info.maxGuests} khách</dd>
          </div>
        )}
        {typeof info?.capacityAdults === 'number' && typeof info.capacityChildren === 'number' && (
          <div>
            <dt>Giới hạn khách</dt>
            <dd>
              {info.capacityAdults} người lớn · {info.capacityChildren} trẻ em
            </dd>
          </div>
        )}
      </dl>
      {typeof info?.basePrice === 'number' && Number.isFinite(info.basePrice) && (
        <div className="room-map-detail-price">
          <span>Giá cơ bản / đêm</span>
          <strong>{roomPriceFormatter.format(info.basePrice)}</strong>
          <small>Giá cho ngày lưu trú được kiểm tra ở bước tìm phòng.</small>
        </div>
      )}
      {!!info?.amenities?.length && (
        <div className="room-map-detail-amenities">
          <Typography.Text strong>Tiện nghi</Typography.Text>
          <ul>
            {info.amenities.map((amenity) => (
              <li key={amenity.id}>{amenity.name}</li>
            ))}
          </ul>
        </div>
      )}
      <Typography.Paragraph type="secondary" className="room-map-detail-note">
        Ảnh, giường, sức chứa và tiện nghi là thông tin của hạng phòng.
      </Typography.Paragraph>
    </div>
  );
}
