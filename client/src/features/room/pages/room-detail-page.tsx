import { ArrowLeftOutlined } from '@ant-design/icons';
import { CalendarOutlined } from '@ant-design/icons';
import { Button, Flex, Result, Space, Typography } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';

import { MainFooter } from '../../../components/layout/MainFooter';
import { MainHeader } from '../../../components/layout/MainHeader';
import { BookingCard } from '../../../components/room/BookingCard';
import { RoomDetailSkeleton } from '../../../components/room/RoomDetailSkeleton';
import { RoomGallery } from '../../../components/room/RoomGallery';
import { RoomInfo } from '../../../components/room/RoomInfo';
import { roomTypeDetailOptions } from '../api/room-detail-api';
import { RoomReviewSummary, RoomReviewsSection } from '../../review/room-reviews';
import './room-detail-page.css';

export function RoomDetailPage() {
  const { roomTypeId } = useParams();
  const detailQuery = useQuery({
    ...roomTypeDetailOptions(roomTypeId ?? ''),
    enabled: Boolean(roomTypeId),
    retry: 1,
  });

  if (detailQuery.isLoading) {
    return (
      <>
        <MainHeader />
        <RoomDetailSkeleton />
      </>
    );
  }

  if (detailQuery.isError || !detailQuery.data) {
    return (
      <>
        <MainHeader />
        <Result
          status="404"
          title="Không tìm thấy loại phòng"
          subTitle="Loại phòng này không tồn tại hoặc hiện không còn kinh doanh."
          extra={
            <Space>
              <Button onClick={() => void detailQuery.refetch()}>Thử lại</Button>
              <Link to="/room-types">
                <Button type="primary">Xem các loại phòng</Button>
              </Link>
            </Space>
          }
        />
      </>
    );
  }

  const roomType = detailQuery.data;
  return (
    <Flex vertical className="room-detail-shell">
      <MainHeader />
      <main className="room-detail-page">
        <Space orientation="vertical" size="large" style={{ width: '100%' }}>
          <Link to="/room-types">
            <Button type="text" icon={<ArrowLeftOutlined />}>
              Quay lại danh sách loại phòng
            </Button>
          </Link>
          <div className="room-detail-intro">
            <div>
              <Typography.Title>{roomType.name}</Typography.Title>
              <RoomReviewSummary roomTypeId={roomType.id} />
              <Typography.Paragraph type="secondary" className="room-detail-lead">
                {roomType.shortDescription || roomType.description}
              </Typography.Paragraph>
            </div>
            <Button type="primary" size="large" href="#room-booking" icon={<CalendarOutlined />}>
              Chọn ngày đặt phòng
            </Button>
          </div>
          <RoomGallery images={roomType.images} roomName={roomType.name} />
          <div className="room-detail-content">
            <BookingCard key={roomType.id} roomType={roomType} />
            <div className="room-detail-info">
              <RoomInfo roomType={roomType} />
              <Link to="/hotel-map" className="room-detail-map-link">
                Xem sơ đồ khách sạn
              </Link>
            </div>
          </div>
          <RoomReviewsSection roomTypeId={roomType.id} />
        </Space>
      </main>
      <MainFooter />
    </Flex>
  );
}
