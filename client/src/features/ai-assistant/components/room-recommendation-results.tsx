import { Button, Card, Image, Space, Tag, Typography } from 'antd';
import type { RoomRecommendationResult } from '../types/room-recommendation';

const money = (amount: number) =>
  new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(amount);
const bedLabel = { SINGLE: 'đơn', DOUBLE: 'đôi', QUEEN: 'Queen', KING: 'King' };

export function RoomRecommendationResults({
  result,
  onAsk,
  disabled,
}: {
  result: RoomRecommendationResult;
  onAsk: (message: string) => void;
  disabled?: boolean;
}) {
  if (result.status !== 'RECOMMENDED') return null;
  return (
    <section className="ai-recommendations" aria-label="Phòng được tư vấn">
      <Typography.Text type="secondary">
        {result.criteria.checkIn?.split('-').reverse().join('/')} –{' '}
        {result.criteria.checkOut?.split('-').reverse().join('/')} · {result.criteria.adults} người
        lớn{result.criteria.children ? `, ${result.criteria.children} trẻ em` : ''}
      </Typography.Text>
      {result.recommendations.slice(0, 3).map((item) => (
        <Card key={item.room.id} size="small" className="ai-room-card">
          <div className="ai-room-card__overview">
            {item.room.imageUrl && (
              <Image alt={item.room.name} src={item.room.imageUrl} preview={false} />
            )}
            <div>
              <Typography.Title level={5} style={{ margin: 0 }}>
                {item.room.name}
              </Typography.Title>
              <Typography.Text>
                {item.room.beds
                  .map((b) => `${b.quantity} giường ${bedLabel[b.bedType]}`)
                  .join(' + ') || 'Chưa xác minh giường'}
              </Typography.Text>
              <Typography.Paragraph strong style={{ marginBlock: 4 }}>
                {money(item.room.roomTotal)} / {item.room.totalNights} đêm
              </Typography.Paragraph>
              {item.room.areaM2 != null && (
                <Typography.Text type="secondary">{item.room.areaM2} m²</Typography.Text>
              )}
            </div>
          </div>
          <div className="ai-room-amenities">
            {item.amenities.slice(0, 4).map((a) => (
              <Tag key={a.id}>{a.name}</Tag>
            ))}
          </div>
          {item.reasons[0] && (
            <Typography.Paragraph className="ai-room-card__reason">
              {item.reasons[0]}
            </Typography.Paragraph>
          )}
          {item.unmetPreferences[0] && (
            <Typography.Paragraph type="secondary">{item.unmetPreferences[0]}</Typography.Paragraph>
          )}
          <Space wrap>
            <Button
              size="small"
              disabled={disabled}
              onClick={() => onAsk(`Cho tôi tìm hiểu phòng ${item.room.name}`)}
            >
              Tìm hiểu thêm
            </Button>
            <Button
              size="small"
              type="primary"
              disabled={disabled}
              onClick={() => onAsk(`Tôi chọn phòng ${item.room.name}`)}
            >
              Chọn phòng này
            </Button>
          </Space>
        </Card>
      ))}
      <Typography.Text type="secondary" className="ai-room-card__notice">
        Tổng tiền phòng cho kỳ lưu trú đã kiểm tra, chưa giữ chỗ.
      </Typography.Text>
    </section>
  );
}
