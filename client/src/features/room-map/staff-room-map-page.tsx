import { useState } from 'react';
import { Alert, Button, Drawer, Empty, Input, Select, Space, Spin, Tabs, Typography } from 'antd';
import { Link } from 'react-router-dom';
import { useAuth } from '../authentication/providers/auth-context';
import { hasRouteRole, ROUTE_ROLES } from '../../routes/route-access';
import { roomStatuses, useStaffMap, type MapRoom, type RoomStatus } from './api';
import { HotelFloorMap } from './hotel-floor-map';
import { RoomMapDetails } from './room-map-details';
import { ROUTE_PATHS } from '../../routes/routePaths';

export function StaffRoomMapPage() {
  const query = useStaffMap();
  const { roles } = useAuth();
  const [selected, setSelected] = useState<MapRoom>();
  const [status, setStatus] = useState<RoomStatus>();
  const [type, setType] = useState<string>();
  const [search, setSearch] = useState('');
  const [booking, setBooking] = useState('');
  const currentSelected = query.data?.floors
    .flatMap((f) => f.rooms)
    .find((r) => r.key === selected?.key);
  const types = new Map(
    query.data?.floors.flatMap((f) => f.rooms.map((r) => [r.roomTypeId, r.roomTypeName] as const)),
  );
  return (
    <main className="staff-room-map-page">
      <Typography.Title level={2}>Sơ đồ phòng</Typography.Title>
      <Typography.Paragraph type="secondary">
        Trạng thái vận hành hiện tại. Chọn booking trong bước nhận phòng để kiểm tra phòng đủ điều
        kiện.
      </Typography.Paragraph>
      <Space wrap className="room-map-toolbar">
        <Link to={ROUTE_PATHS.roomAssignments}>
          <Button>Lịch gán phòng</Button>
        </Link>
        <Input
          aria-label="Tìm số phòng"
          placeholder="Tìm số phòng"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          style={{ width: 170 }}
        />
        <Select
          aria-label="Lọc trạng thái"
          placeholder="Tất cả trạng thái"
          allowClear
          value={status}
          onChange={setStatus}
          style={{ width: 180 }}
          options={Object.entries(roomStatuses).map(([value, label]) => ({ value, label }))}
        />
        <Select
          aria-label="Lọc hạng phòng"
          placeholder="Tất cả hạng phòng"
          allowClear
          value={type}
          onChange={setType}
          style={{ width: 210 }}
          options={[...types].map(([value, label]) => ({ value, label }))}
        />
        <Button loading={query.isFetching} onClick={() => query.refetch()}>
          Làm mới
        </Button>
      </Space>
      {hasRouteRole(roles, ROUTE_ROLES.reception) && (
        <Space wrap style={{ marginBottom: 18 }}>
          <Input
            aria-label="Booking cần nhận phòng"
            placeholder="Mã booking cần nhận phòng"
            value={booking}
            onChange={(e) => setBooking(e.target.value.toUpperCase())}
            maxLength={50}
            style={{ width: 270 }}
          />
          <Link to={`/front-desk/check-in?bookingCode=${encodeURIComponent(booking.trim())}`}>
            <Button type="primary" disabled={!booking.trim()}>
              Chọn phòng cho booking
            </Button>
          </Link>
        </Space>
      )}
      {query.data && (
        <Typography.Paragraph type="secondary">
          Cập nhật lúc{' '}
          {new Date(query.data.serverNow ?? query.dataUpdatedAt).toLocaleTimeString('vi-VN', {
            timeZone: 'Asia/Ho_Chi_Minh',
          })}{' '}
          · Tự làm mới mỗi 15 giây
        </Typography.Paragraph>
      )}
      {query.isError && (
        <Alert
          type="error"
          showIcon
          title="Không thể cập nhật sơ đồ"
          description="Dữ liệu đang hiển thị có thể đã cũ. Hãy kiểm tra kết nối và làm mới."
        />
      )}
      {query.isLoading && <Spin />}
      {query.data?.floors.length === 0 && <Empty description="Chưa có phòng" />}
      <Tabs
        onChange={() => setSelected(undefined)}
        items={query.data?.floors.map((floor) => {
          const matches = floor.rooms.filter(
            (r) =>
              (!status || r.operationalStatus === status) &&
              (!type || r.roomTypeId === type) &&
              r.roomNumber.toLowerCase().includes(search.toLowerCase()),
          );
          return {
            key: String(floor.floorNumber),
            label: floor.rooms.length
              ? `${floor.name} (${matches.length}/${floor.rooms.length})`
              : floor.name,
            children: (
              <div className="room-map-panel">
                {floor.rooms.length > 0 && (
                  <Typography.Paragraph>
                    {matches.length} phòng theo bộ lọc ·{' '}
                    {matches.filter((r) => r.operationalStatus === 'AVAILABLE').length} sẵn sàng
                  </Typography.Paragraph>
                )}
                <HotelFloorMap
                  floor={floor}
                  showStatus
                  onRoomClick={setSelected}
                  selectedKey={selected?.key}
                  visibleKeys={new Set(matches.map((r) => r.key))}
                />
              </div>
            ),
          };
        })}
      />
      <Drawer
        className="coastal-room-drawer"
        open={!!currentSelected}
        onClose={() => setSelected(undefined)}
        title={`Phòng ${currentSelected?.roomNumber ?? ''}`}
      >
        {currentSelected && <RoomMapDetails room={currentSelected} />}
        <Typography.Paragraph>
          {currentSelected?.operationalStatus && roomStatuses[currentSelected.operationalStatus]}
        </Typography.Paragraph>
        <Alert
          type="info"
          title="Phòng sẵn sàng vẫn cần kiểm tra đúng hạng, booking và lịch gán trước khi nhận khách."
        />
      </Drawer>
    </main>
  );
}
