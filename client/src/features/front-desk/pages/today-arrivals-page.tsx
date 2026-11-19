import { ReloadOutlined, TeamOutlined, WarningOutlined } from '@ant-design/icons';
import {
  Alert,
  Button,
  Card,
  DatePicker,
  Empty,
  Input,
  Select,
  Space,
  Table,
  Tag,
  Tabs,
  Typography,
  type TableColumnsType,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../../authentication/providers/auth-context';
import { hasRouteRole, ROUTE_ROLES } from '../../../routes/route-access';
import { useArrivals } from '../api/front-desk-query';
import type { TodayArrival, ArrivalView } from '../types/today-arrivals';
import type { ArrivalReason } from '../types/arrival-handling';
import {
  ArrivalHandlingModal,
  type ArrivalHandlingSubject,
} from '../components/arrival-handling-modal';
import { getCheckInBookingContext } from '../../booking/api/booking-api';
import { formatHotelDateTime, hotelToday, HOTEL_TIME_ZONE } from '../../../utils/hotel-time';
import {
  nextArrivalAction,
  reasonLabels,
  outcomeLabels,
  channelLabels,
} from '../arrival-presentation';
import './today-arrivals-page.css';

const { Paragraph, Text, Title } = Typography;
const PAGE_SIZE = 20;
const statuses: Record<string, string> = {
  CONFIRMED: 'Chờ khách đến',
  CHECKED_IN: 'Đã nhận phòng',
  CHECKED_OUT: 'Đã trả phòng',
  NO_SHOW: 'Đã đóng không đến',
  CANCELLED: 'Đã hủy theo yêu cầu',
};
export function TodayArrivalsPage() {
  const { roles } = useAuth();
  const canCheckIn = hasRouteRole(roles, ROUTE_ROLES.reception);
  const [view, setView] = useState<ArrivalView>('waiting');
  const [reason, setReason] = useState<ArrivalReason | undefined>();
  const [page, setPage] = useState(0);
  const [resolvedRange, setResolvedRange] = useState<[Dayjs, Dayjs] | null>(null);
  const [selectedArrival, setSelectedArrival] = useState<ArrivalHandlingSubject | null>(null);
  const [lookupBusy, setLookupBusy] = useState(false);
  const [lookupError, setLookupError] = useState<string | null>(null);
  async function lookup(code: string) {
    if (!code.trim() || lookupBusy) return;
    setLookupBusy(true);
    setLookupError(null);
    try {
      setSelectedArrival(await getCheckInBookingContext(code.trim()));
    } catch (error) {
      setLookupError(error instanceof Error ? error.message : 'Không tra cứu được booking.');
    } finally {
      setLookupBusy(false);
    }
  }
  const arrivalsQuery = useArrivals(
    {
      page,
      size: PAGE_SIZE,
      ...(reason ? { reason } : {}),
      ...(view === 'resolved' && resolvedRange
        ? {
            resolvedFrom: resolvedRange[0].format('YYYY-MM-DD'),
            resolvedTo: resolvedRange[1].format('YYYY-MM-DD'),
          }
        : {}),
    },
    view,
  );
  const hotelZone = arrivalsQuery.data?.content[0]?.zone ?? HOTEL_TIME_ZONE;
  const serverNow = arrivalsQuery.data?.content[0]?.serverNow;
  const today = serverNow
    ? dayjs(dayjs(serverNow).tz(hotelZone).format('YYYY-MM-DD'))
    : hotelToday(hotelZone);
  function changeResolvedRange(range: [Dayjs, Dayjs]) {
    setResolvedRange(range);
    setPage(0);
  }
  if (
    arrivalsQuery.isSuccess &&
    arrivalsQuery.data &&
    !arrivalsQuery.isPlaceholderData &&
    arrivalsQuery.data.page === page &&
    page > Math.max(0, arrivalsQuery.data.totalPages - 1)
  )
    setPage(Math.max(0, arrivalsQuery.data.totalPages - 1));
  const columns: TableColumnsType<TodayArrival> = [
    {
      title: 'Booking / khách',
      key: 'guest',
      fixed: 'left',
      width: 220,
      render: (_, b) => (
        <Space orientation="vertical" size={0}>
          <Text code>{b.bookingCode}</Text>
          {b.orderCode && (
            <Text type="secondary">
              Đơn {b.orderCode} · phòng {b.orderRoomNumber ?? '—'}/{b.roomCount}
            </Text>
          )}
          {b.primaryGuest ? (
            <>
              <Text strong>{b.primaryGuest.fullName}</Text>
              <Text type="secondary">
                {b.primaryGuest.phone ?? b.primaryGuest.email ?? 'Chưa có liên hệ'}
              </Text>
            </>
          ) : (
            <Tag icon={<WarningOutlined />} color="warning">
              Thiếu thông tin khách
            </Tag>
          )}
        </Space>
      ),
    },
    {
      title: 'Phòng / kỳ ở',
      key: 'stay',
      render: (_, b) => (
        <Space orientation="vertical" size={0}>
          <Text strong>{b.roomType.name}</Text>
          <Text>Nhận: {dayjs(b.checkInDate).format('DD/MM/YYYY')}</Text>
          <Text>Trả: {dayjs(b.checkOutDate).format('DD/MM/YYYY')}</Text>
          <Text type="secondary">
            {b.adultCount} người lớn · {b.childCount} trẻ em
          </Text>
        </Space>
      ),
    },
    {
      title: 'Giờ khách hẹn',
      key: 'eta',
      render: (_, b) =>
        b.progress?.expectedAt ? (
          formatHotelDateTime(b.progress.expectedAt, b.zone)
        ) : (
          <Text type="secondary">Chưa có giờ hẹn</Text>
        ),
    },
    {
      title: 'Hạn tiếp nhận',
      key: 'deadline',
      render: (_, b) =>
        b.progress ? (
          <Space orientation="vertical" size={0}>
            <Text>Trước {formatHotelDateTime(b.progress.receptionDeadline, b.zone)}</Text>
            {b.progress.holdPending && <Tag color="orange">Chờ duyệt nhận sau hạn</Tag>}
          </Space>
        ) : (
          '—'
        ),
    },
    {
      title: 'Liên hệ gần nhất',
      key: 'contact',
      render: (_, b) =>
        b.progress?.lastContactAt ? (
          <Space orientation="vertical" size={0}>
            <Text>{formatHotelDateTime(b.progress.lastContactAt, b.zone)}</Text>
            <Text>
              {b.progress.lastContactChannel && channelLabels[b.progress.lastContactChannel]} ·{' '}
              {b.progress.lastContactOutcome && outcomeLabels[b.progress.lastContactOutcome]}
            </Text>
            {b.progress.lastContactNote && (
              <Text
                type="secondary"
                ellipsis
                style={{ maxWidth: 220 }}
                title={b.progress.lastContactNote}
              >
                {b.progress.lastContactNote}
              </Text>
            )}
          </Space>
        ) : (
          <Text type="secondary">Chưa ghi nhận</Text>
        ),
    },
    {
      title: 'Tình trạng / việc tiếp theo',
      key: 'action',
      render: (_, b) => (
        <Space orientation="vertical" size={4}>
          <Tag
            color={
              b.progress?.group === 'ACTION_REQUIRED'
                ? 'orange'
                : b.progress?.group === 'RESOLVED'
                  ? 'green'
                  : 'blue'
            }
          >
            {statuses[b.bookingStatus] ?? b.bookingStatus}
          </Tag>
          {b.progress?.reasons.map((r) => (
            <Text key={r} type="warning">
              {reasonLabels[r]}
            </Text>
          ))}
          <Text>{nextArrivalAction(b.progress)}</Text>
        </Space>
      ),
    },
    {
      title: 'Thao tác',
      key: 'buttons',
      fixed: 'right',
      width: 150,
      render: (_, b) => (
        <Space wrap>
          {canCheckIn && b.progress?.canReceive && (
            <Link to={`/front-desk/check-in?bookingCode=${encodeURIComponent(b.bookingCode)}`}>
              <Button type="primary">Nhận phòng</Button>
            </Link>
          )}
          <Button onClick={() => setSelectedArrival(b)}>
            {view === 'resolved' ? 'Xem kết quả' : 'Xử lý khách'}
          </Button>
        </Space>
      ),
    },
  ];
  if (view === 'resolved')
    columns.splice(2, 0, {
      title: 'Thời điểm xử lý',
      key: 'resolvedAt',
      width: 180,
      render: (_, b) => (b.resolvedAt ? formatHotelDateTime(b.resolvedAt, b.zone) : '—'),
    });
  return (
    <main className="today-arrivals-page">
      <div className="today-arrivals-page__header">
        <div>
          <Title level={2}>Theo dõi khách đến</Title>
          <Paragraph type="secondary">
            Giờ khách hẹn để theo dõi liên hệ; hạn tiếp nhận quyết định quyền nhận phòng. Ưu tiên
            các booking trong nhóm cần xử lý.
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
      <Input.Search
        aria-label="Tra cứu xử lý khách chưa đến theo mã booking"
        placeholder="Tra cứu mã booking, kể cả booking đã xử lý"
        enterButton="Tra cứu xử lý"
        loading={lookupBusy}
        onSearch={(code) => void lookup(code)}
        style={{ marginBottom: 16 }}
      />
      {lookupError && <Alert type="error" title={lookupError} />}
      <Tabs
        activeKey={view}
        onChange={(key) => {
          setView(key as ArrivalView);
          setPage(0);
          setReason(undefined);
        }}
        items={[
          { key: 'waiting', label: 'Chờ nhận phòng' },
          { key: 'action', label: 'Cần xử lý' },
          { key: 'resolved', label: 'Đã xử lý' },
        ]}
      />
      <Paragraph type="secondary">
        {view === 'resolved'
          ? 'Tra cứu theo ngày nhận phòng, đóng không đến hoặc hủy booking, theo giờ khách sạn. Kết quả mới nhất hiển thị trước.'
          : 'Bao gồm khách đến hôm nay, booking chưa đến từ ngày trước và các yêu cầu đang chờ duyệt.'}
      </Paragraph>
      {view === 'resolved' && (
        <Space wrap>
          <Text strong>Ngày xử lý:</Text>
          <DatePicker.RangePicker
            value={resolvedRange ?? [today, today]}
            format="DD/MM/YYYY"
            placeholder={['Từ ngày xử lý', 'Đến ngày xử lý']}
            allowClear={false}
            onChange={(dates) => {
              if (dates?.[0] && dates[1]) changeResolvedRange([dates[0], dates[1]]);
            }}
          />
          <Button onClick={() => changeResolvedRange([today, today])}>Hôm nay</Button>
          <Button
            onClick={() =>
              changeResolvedRange([today.subtract(1, 'day'), today.subtract(1, 'day')])
            }
          >
            Hôm qua
          </Button>
          <Button onClick={() => changeResolvedRange([today.subtract(6, 'day'), today])}>
            7 ngày qua
          </Button>
          <Button onClick={() => changeResolvedRange([today.startOf('month'), today])}>
            Tháng này
          </Button>
        </Space>
      )}
      {view === 'action' && (
        <Select
          aria-label="Lý do cần xử lý"
          value={reason ?? 'ALL'}
          style={{ width: 300, marginBottom: 16 }}
          onChange={(value) => {
            setReason(value === 'ALL' ? undefined : (value as ArrivalReason));
            setPage(0);
          }}
          options={[
            { value: 'ALL', label: 'Tất cả lý do' },
            ...Object.entries(reasonLabels).map(([value, label]) => ({ value, label })),
          ]}
        />
      )}
      <Card>
        {!arrivalsQuery.isLoading && !arrivalsQuery.isError && (
          <div className="today-arrivals-page__summary">
            <TeamOutlined />
            <Text>
              Tổng cộng <Text strong>{arrivalsQuery.data?.totalElements ?? 0}</Text> booking trong
              nhóm
            </Text>
          </div>
        )}
        {arrivalsQuery.isError ? (
          <Alert
            type="error"
            showIcon
            title="Không thể tải danh sách khách đến"
            description={arrivalsQuery.error.message}
            action={
              <Button aria-label="Thử tải lại" onClick={() => void arrivalsQuery.refetch()}>
                Thử lại
              </Button>
            }
          />
        ) : !arrivalsQuery.isLoading && arrivalsQuery.data?.totalElements === 0 ? (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Không có booking trong nhóm này">
            <Button onClick={() => void arrivalsQuery.refetch()}>Làm mới</Button>
          </Empty>
        ) : (
          <Table<TodayArrival>
            columns={columns}
            dataSource={arrivalsQuery.data?.content ?? []}
            rowKey="bookingId"
            loading={arrivalsQuery.isLoading || arrivalsQuery.isPlaceholderData}
            pagination={{
              current: page + 1,
              pageSize: PAGE_SIZE,
              total: arrivalsQuery.data?.totalElements ?? 0,
              showSizeChanger: false,
              hideOnSinglePage: true,
              onChange: (next) => setPage(next - 1),
            }}
            scroll={{ x: 1400 }}
          />
        )}
      </Card>
      {selectedArrival && (
        <ArrivalHandlingModal
          key={selectedArrival.bookingCode}
          arrival={selectedArrival}
          onClose={() => setSelectedArrival(null)}
        />
      )}
    </main>
  );
}
