import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, Button, Card, Select, Space, Table, Tabs, Tag, Typography } from 'antd';
import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { formatHotelDateTime } from '../../../utils/hotel-time';
import {
  listBookingChanges,
  type ChangeStatus,
  type BookingChangeRequest,
} from '../api/booking-change-api';
import { BookingChangeReview } from '../components/booking-change-review';
import { refreshArrivalsAfterCheckIn } from '../api/front-desk-query';
import { BookingChangeSettlementMonitor } from '../components/booking-change-settlement-monitor';

export function BookingChangeRequestsPage() {
  const client = useQueryClient();
  const [params, setParams] = useSearchParams();
  const [status, setStatus] = useState<ChangeStatus | ''>('PENDING');
  const [tab, setTab] = useState('requests');
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<string | null>(null);
  const id = selected ?? params.get('requestId');
  const query = useQuery({
    queryKey: ['booking-change-requests', status, page],
    queryFn: () => listBookingChanges(status, page),
    refetchInterval: 10000,
    enabled: tab === 'requests',
  });
  function updated() {
    setPage(0);
    void client.invalidateQueries({ queryKey: ['booking-change-requests'] });
    void client.invalidateQueries({ queryKey: ['booking-change-settlements'] });
    void refreshArrivalsAfterCheckIn(client);
    for (const key of [
      'arrival-handling',
      'booking-history',
      'booking-detail',
      'booking-order',
      'booking-order-history',
      'refund-requests',
      'room-map',
      'room-types',
      'dashboard-report',
    ])
      void client.invalidateQueries({ queryKey: [key] });
  }
  return (
    <Space orientation="vertical" size="large" style={{ width: '100%' }}>
      <div>
        <Typography.Title level={2}>Yêu cầu hủy / đổi lịch</Typography.Title>
        <Typography.Paragraph type="secondary">
          Khách xác nhận hủy được giải phóng phòng ngay; quản lý xử lý phần quyết toán cần xem xét.
          Đổi lịch giữ ngày cũ đến khi quản lý áp dụng báo giá khách đã chấp thuận.
        </Typography.Paragraph>
      </div>
      <Tabs
        activeKey={tab}
        onChange={setTab}
        items={[
          { key: 'requests', label: 'Xử lý yêu cầu' },
          { key: 'settlements', label: 'Giám sát thu / hoàn' },
        ]}
      />
      {tab === 'settlements' ? (
        <BookingChangeSettlementMonitor />
      ) : (
        <>
          <Space wrap>
            <Select
              aria-label="Trạng thái yêu cầu"
              value={status}
              style={{ width: 220 }}
              onChange={(value) => {
                setStatus(value);
                setPage(0);
              }}
              options={[
                { value: 'PENDING', label: 'Chờ xử lý' },
                { value: 'APPROVED', label: 'Đã duyệt' },
                { value: 'REJECTED', label: 'Đã từ chối / kết thúc' },
                { value: '', label: 'Tất cả' },
              ]}
            />
            <Button loading={query.isFetching} onClick={() => void query.refetch()}>
              Làm mới
            </Button>
          </Space>
          {query.isError && <Alert type="error" title={query.error.message} />}
          <Card>
            <Table<BookingChangeRequest>
              rowKey="id"
              loading={query.isLoading}
              dataSource={query.data?.content ?? []}
              scroll={{ x: 1000 }}
              pagination={{
                current: page + 1,
                pageSize: 20,
                total: query.data?.totalElements ?? 0,
                onChange: (value) => setPage(value - 1),
                showSizeChanger: false,
              }}
              columns={[
                {
                  title: 'Booking / đơn',
                  render: (_, row) => (
                    <>
                      {row.bookingCode}
                      <br />
                      {row.orderCode && (
                        <Typography.Text type="secondary">
                          Toàn đơn: {row.orderCode}
                        </Typography.Text>
                      )}
                    </>
                  ),
                },
                {
                  title: 'Yêu cầu',
                  render: (_, row) =>
                    row.type === 'CANCELLATION' ? 'Khách báo hủy' : 'Khách xin đổi lịch',
                },
                {
                  title: 'Kỳ ở đề nghị',
                  render: (_, row) =>
                    row.checkInDate ? `${row.checkInDate} → ${row.checkOutDate}` : '—',
                },
                { title: 'Người ghi nhận', dataIndex: 'requestedBy' },
                {
                  title: 'Yêu cầu lúc',
                  dataIndex: 'requestedAt',
                  render: (value) => formatHotelDateTime(value),
                },
                {
                  title: 'Trạng thái',
                  render: (_, row) => (
                    <Tag
                      color={
                        row.status === 'PENDING'
                          ? 'orange'
                          : row.status === 'APPROVED'
                            ? 'green'
                            : 'default'
                      }
                    >
                      {row.status === 'PENDING'
                        ? 'Chờ xử lý'
                        : row.status === 'APPROVED'
                          ? 'Đã duyệt'
                          : row.snapshot?.autoClosed
                            ? 'Đã kết thúc do hủy'
                            : 'Đã từ chối'}
                    </Tag>
                  ),
                },
                {
                  title: 'Thao tác',
                  render: (_, row) => (
                    <Button onClick={() => setSelected(row.id)}>Xem xét yêu cầu</Button>
                  ),
                },
              ]}
            />
          </Card>
        </>
      )}
      {id && (
        <BookingChangeReview
          key={id}
          id={id}
          onUpdated={updated}
          onClose={() => {
            setSelected(null);
            if (params.has('requestId')) {
              params.delete('requestId');
              setParams(params, { replace: true });
            }
          }}
        />
      )}
    </Space>
  );
}
