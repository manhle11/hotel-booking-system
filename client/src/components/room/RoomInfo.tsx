import { CheckCircleOutlined, TeamOutlined } from '@ant-design/icons';
import { Card, Col, Descriptions, Empty, Row, Space, Typography } from 'antd';

import type { RoomTypeDetail } from '../../features/room/types/room-detail';
import { BED_TYPE_LABELS, ROOM_VIEW_LABELS } from '../../features/room/types/room-configuration';

export function RoomInfo({ roomType }: { roomType: RoomTypeDetail }) {
  return (
    <Space orientation="vertical" size="large" style={{ width: '100%' }}>
      <Card title="Thông tin loại phòng">
        <Descriptions column={{ xs: 1, sm: 3 }}>
          {roomType.category && (
            <Descriptions.Item label="Nhóm phòng">{roomType.category.name}</Descriptions.Item>
          )}
          {roomType.beds?.length || roomType.bedType ? (
            <Descriptions.Item label="Giường">
              {roomType.beds?.length
                ? roomType.beds
                    .map((bed) => `${bed.quantity} ${BED_TYPE_LABELS[bed.bedType]}`)
                    .join(' + ')
                : roomType.bedType}
            </Descriptions.Item>
          ) : null}
          {roomType.areaM2 != null && (
            <Descriptions.Item label="Diện tích">{roomType.areaM2} m²</Descriptions.Item>
          )}
          <Descriptions.Item label="Sức chứa tối đa">
            <TeamOutlined /> {roomType.maxGuests} khách
          </Descriptions.Item>
          <Descriptions.Item label="Người lớn">{roomType.capacityAdults}</Descriptions.Item>
          <Descriptions.Item label="Trẻ em">{roomType.capacityChildren}</Descriptions.Item>
        </Descriptions>
        {(roomType.viewType || roomType.hasBalcony != null || roomType.highFloor != null) && (
          <>
            <Typography.Title level={4}>Đặc điểm phòng</Typography.Title>
            <Descriptions column={{ xs: 1, sm: 3 }}>
              {roomType.viewType && (
                <Descriptions.Item label="Hướng nhìn">
                  {ROOM_VIEW_LABELS[roomType.viewType]}
                </Descriptions.Item>
              )}
              {roomType.hasBalcony != null && (
                <Descriptions.Item label="Ban công riêng">
                  {roomType.hasBalcony ? 'Có' : 'Không'}
                </Descriptions.Item>
              )}
              {roomType.highFloor != null && (
                <Descriptions.Item label="Tầng cao">
                  {roomType.highFloor ? 'Có' : 'Không'}
                </Descriptions.Item>
              )}
            </Descriptions>
          </>
        )}
        <Typography.Title level={3}>Mô tả</Typography.Title>
        {roomType.description ? (
          roomType.description
            .split(/\n\s*\n/)
            .filter(Boolean)
            .map((text) => <Typography.Paragraph key={text}>{text}</Typography.Paragraph>)
        ) : (
          <Typography.Paragraph>Chưa có mô tả chi tiết.</Typography.Paragraph>
        )}
      </Card>
      <Card title="Tiện nghi nổi bật">
        {roomType.amenities.length === 0 ? (
          <Empty description="Chưa có thông tin tiện nghi" />
        ) : (
          <Row gutter={[16, 16]}>
            {roomType.amenities.map((amenity) => (
              <Col xs={24} sm={12} key={amenity.id}>
                <Space>
                  <CheckCircleOutlined className="amenity-icon" />
                  <Typography.Text>{amenity.name}</Typography.Text>
                </Space>
              </Col>
            ))}
          </Row>
        )}
      </Card>
    </Space>
  );
}
