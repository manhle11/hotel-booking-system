import { ReloadOutlined, TeamOutlined, WarningOutlined } from '@ant-design/icons';
import {
  Alert,
  Button,
  Card,
  Empty,
  Space,
  Table,
  Tag,
  Tabs,
  Typography,
  type TableColumnsType,
} from 'antd';
import dayjs from 'dayjs';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../../authentication/providers/auth-context';
import { hasRouteRole, ROUTE_ROLES } from '../../../routes/route-access';

import { useArrivals } from '../api/front-desk-query';
import type { TodayArrival, ArrivalView } from '../types/today-arrivals';
import './today-arrivals-page.css';

const { Paragraph, Text, Title } = Typography;
const PAGE_SIZE = 20;
const formatDate = (value: string) => dayjs(value).format('DD/MM/YYYY');

const columns: TableColumnsType<TodayArrival> = [
  {
    title: 'Mã booking',
    dataIndex: 'bookingCode',
    render: (value: string) => <Text code>{value}</Text>,
  },
  {
    title: 'Khách chính',
    key: 'primaryGuest',
    render: (_value, arrival) =>
      arrival.primaryGuest ? (
        <Space orientation="vertical" size={0}>
          <Text strong>{arrival.primaryGuest.fullName}</Text>
          <Text type="secondary">{arrival.primaryGuest.phone ?? 'Chưa có số điện thoại'}</Text>
          {arrival.primaryGuest.email && <Text type="secondary">{arrival.primaryGuest.email}</Text>}
        </Space>
      ) : (
        <Tag icon={<WarningOutlined />} color="warning">
          Thiếu thông tin khách
        </Tag>
      ),
  },
  {
    title: 'Hạng phòng',
    key: 'roomType',
    render: (_value, arrival) => (
      <Space orientation="vertical" size={0}>
        <Text strong>{arrival.roomType.name}</Text>
        <Text type="secondary">{arrival.roomType.code}</Text>
      </Space>
    ),
  },
  {
    title: 'Lưu trú',
    key: 'stayDates',
    render: (_value, arrival) => (
      <Space orientation="vertical" size={0}>
        <Text>Nhận: {formatDate(arrival.checkInDate)}</Text>
        <Text>Trả: {formatDate(arrival.checkOutDate)}</Text>
      </Space>
    ),
  },
  {
    title: 'Số khách',
    key: 'guestCount',
    align: 'center',
    render: (_value, arrival) => (
      <Space orientation="vertical" size={0}>
        <Text>{arrival.adultCount} người lớn</Text>
        <Text type="secondary">{arrival.childCount} trẻ em</Text>
      </Space>
    ),
  },
  {
    title: 'Trạng thái',
    dataIndex: 'bookingStatus',
    render: () => <Tag color="success">Đã xác nhận</Tag>,
  },
];

export function TodayArrivalsPage() {
  const { roles } = useAuth();
  const canCheckIn = hasRouteRole(roles, ROUTE_ROLES.reception);
  const [view, setView] = useState<ArrivalView>('today');
  const [page, setPage] = useState(0);
  const arrivalsQuery = useArrivals({ page, size: PAGE_SIZE }, view);
  const arrivals = arrivalsQuery.data?.content ?? [];
  if (
    arrivalsQuery.isSuccess &&
    arrivalsQuery.data &&
    !arrivalsQuery.isPlaceholderData &&
    arrivalsQuery.data.page === page &&
    page > Math.max(0, arrivalsQuery.data.totalPages - 1)
  ) {
    setPage(Math.max(0, arrivalsQuery.data.totalPages - 1));
  }
  const pageColumns: TableColumnsType<TodayArrival> = [...columns];
  if (canCheckIn)
    pageColumns.push({
      title: 'Thao tác',
      key: 'actions',
      render: (_, arrival) => (
        <Link to={`/front-desk/check-in?bookingCode=${encodeURIComponent(arrival.bookingCode)}`}>
          <Button type={view === 'today' ? 'primary' : 'default'}>
            {view === 'today' ? 'Nhận phòng' : 'Xem booking'}
          </Button>
        </Link>
      ),
    });

  return (
    <main className="today-arrivals-page">
      <div className="today-arrivals-page__header">
        <div>
          <Title level={2}>
            {view === 'today' ? 'Khách đến hôm nay' : 'Khách chưa đến / quá giờ'}
          </Title>
          <Paragraph type="secondary">
            {view === 'today'
              ? 'Danh sách đã xác nhận dự kiến nhận phòng trong ngày, gồm khách đến muộn từ hôm trước còn trong khung giờ tiếp nhận.'
              : 'Theo dõi booking đã xác nhận nhưng khách chưa đến và đã hết khung giờ tiếp nhận.'}
          </Paragraph>
        </div>
        <Button
          icon={<ReloadOutlined />}
          loading={arrivalsQuery.isFetching}
          onClick={() => void arrivalsQuery.refetch()}
        >
          Làm mới
        </Button>
      </div>

      <Tabs
        activeKey={view}
        onChange={(key) => {
          setView(key as ArrivalView);
          setPage(0);
        }}
        items={[
          { key: 'today', label: 'Dự kiến hôm nay' },
          { key: 'overdue', label: 'Chưa đến / quá giờ' },
        ]}
      />
      {view === 'overdue' && (
        <Alert
          type="warning"
          showIcon
          title="Khách đã quá khung giờ nhận phòng và chưa được nhận"
          description="Booking vẫn ở trạng thái đã xác nhận. Liên hệ khách và xử lý theo chính sách khách sạn; hệ thống giữ booking để theo dõi."
        />
      )}

      <Card>
        {!arrivalsQuery.isLoading && !arrivalsQuery.isError && (
          <div className="today-arrivals-page__summary">
            <TeamOutlined />
            <Text>
              Tổng cộng <Text strong>{arrivalsQuery.data?.totalElements ?? 0}</Text> booking dự kiến
              đến{view === 'overdue' ? ' · đã quá giờ tiếp nhận' : ''}
            </Text>
          </div>
        )}

        {arrivalsQuery.isError && (
          <Alert
            showIcon
            type="error"
            title={
              view === 'today'
                ? 'Không thể tải danh sách khách đến hôm nay'
                : 'Không thể tải danh sách khách chưa đến / quá giờ'
            }
            description={arrivalsQuery.error.message}
            action={
              <Button onClick={() => void arrivalsQuery.refetch()} aria-label="Thử tải lại">
                Thử lại
              </Button>
            }
            className="today-arrivals-page__feedback"
          />
        )}

        {!arrivalsQuery.isError &&
          (!arrivalsQuery.isLoading && arrivalsQuery.data?.totalElements === 0 ? (
            <Empty
              description={
                view === 'today'
                  ? 'Hôm nay không có khách dự kiến nhận phòng'
                  : 'Không có booking chưa đến / quá giờ'
              }
              image={Empty.PRESENTED_IMAGE_SIMPLE}
            >
              <Button onClick={() => void arrivalsQuery.refetch()}>Làm mới</Button>
            </Empty>
          ) : (
            <Table<TodayArrival>
              columns={pageColumns}
              dataSource={arrivals}
              rowKey="bookingId"
              loading={arrivalsQuery.isLoading}
              pagination={{
                current: page + 1,
                pageSize: PAGE_SIZE,
                total: arrivalsQuery.data?.totalElements ?? 0,
                showSizeChanger: false,
                hideOnSinglePage: true,
                onChange: (nextPage) => setPage(nextPage - 1),
              }}
              scroll={{ x: 'max-content' }}
            />
          ))}
      </Card>
    </main>
  );
}
