import { useState } from 'react';
import { Alert, Button, Drawer, Empty, Space, Spin, Tabs, Typography } from 'antd';
import { Link } from 'react-router-dom';
import { MainHeader } from '../../components/layout/MainHeader';
import { MainFooter } from '../../components/layout/MainFooter';
import { usePublicMap, type MapRoom } from './api';
import { HotelFloorMap } from './hotel-floor-map';
import { RoomMapDetails } from './room-map-details';

export function PublicHotelMapPage() {
  const query = usePublicMap();
  const [selected, setSelected] = useState<MapRoom>();
  return (
    <div className="hotel-map-page">
      <MainHeader />
      <main className="hotel-map-content">
        <header className="hotel-map-hero">
          <Typography.Title level={1}>Sơ đồ khách sạn</Typography.Title>
          <p>Khám phá bố trí các tầng và hạng phòng tại HANBAY.</p>
        </header>
        <Alert
          type="info"
          showIcon
          title="Đặt theo hạng phòng, lễ tân sắp xếp phòng cụ thể khi nhận phòng."
          description="Sơ đồ thể hiện vị trí. Số phòng còn bán được kiểm tra theo ngày lưu trú và số khách trong bước tìm phòng."
        />
        {query.isLoading && <Spin />}
        {query.isError && (
          <Alert
            type="error"
            title="Không thể tải sơ đồ"
            description={query.error.message}
            action={<Button onClick={() => query.refetch()}>Thử lại</Button>}
          />
        )}
        {query.data?.floors.length === 0 && (
          <Empty description="Khách sạn chưa cập nhật danh sách phòng" />
        )}
        <Tabs
          onChange={() => setSelected(undefined)}
          items={query.data?.floors.map((floor) => ({
            key: String(floor.floorNumber),
            label: floor.rooms.length ? `${floor.name} (${floor.rooms.length})` : floor.name,
            children: (
              <div className="room-map-panel">
                <HotelFloorMap
                  floor={floor}
                  onRoomClick={setSelected}
                  selectedKey={selected?.key}
                />
              </div>
            ),
          }))}
        />
        <Drawer
          className="coastal-room-drawer"
          open={!!selected}
          onClose={() => setSelected(undefined)}
          title={selected ? `Phòng ${selected.roomNumber}` : 'Thông tin phòng'}
        >
          {selected && (
            <Space orientation="vertical" size="large" style={{ width: '100%' }}>
              <RoomMapDetails room={selected} />
              {selected.bookable ? (
                <Space wrap>
                  <Link to={`/room-types/${selected.roomTypeId}`}>
                    <Button type="primary">Xem hạng phòng và đặt</Button>
                  </Link>
                  <Link to="/rooms/search">
                    <Button>Tìm phòng theo ngày</Button>
                  </Link>
                </Space>
              ) : (
                <Alert type="info" title="Hạng phòng hiện chưa mở bán" />
              )}
            </Space>
          )}
        </Drawer>
      </main>
      <MainFooter />
    </div>
  );
}
