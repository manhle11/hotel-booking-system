import { useQuery } from '@tanstack/react-query';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Input,
  Modal,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import {
  getBookingChangeSettlement,
  listBookingChangeSettlements,
  type BookingChangeSettlement,
} from '../api/booking-change-api';
import { formatHotelDateTime } from '../../../utils/hotel-time';
import { useAuth } from '../../authentication/providers/auth-context';
import { hasRouteRole, ROUTE_ROLES } from '../../../routes/route-access';
import { ROUTE_PATHS } from '../../../routes/routePaths';

const money = (amount: number) =>
  new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(amount);
const collectionLabels = {
  REVIEW_REQUIRED: ['Cần đối soát', 'orange'],
  PAID: ['Đã thanh toán đủ', 'green'],
  OVERDUE: ['Quá hạn thu', 'red'],
  PARTIALLY_PAID: ['Còn thiếu tiền', 'orange'],
  UNPAID: ['Chưa thanh toán', 'orange'],
};
const refundLabels = {
  REVIEW_REQUIRED: ['Cần kiểm tra hoàn', 'red'],
  PARTIALLY_REFUNDED: ['Đã hoàn một phần', 'orange'],
  PENDING: ['Chờ hoàn tiền', 'orange'],
  REFUNDED: ['Đã hoàn tiền', 'green'],
  NOT_REQUIRED: ['Không có khoản hoàn', 'default'],
};
const stayLabels: Record<string, string> = {
  CONFIRMED: 'Đã xác nhận',
  CANCELLED: 'Đã hủy',
  CHECKED_IN: 'Đang lưu trú',
  CHECKED_OUT: 'Đã trả phòng',
  NO_SHOW: 'Không đến',
  MIXED: 'Xem từng phòng',
};
const refundStateLabels: Record<string, string> = {
  PENDING: 'Chờ hoàn',
  PROCESSING: 'Đang xử lý',
  SUCCESS: 'Đã hoàn',
  FAILED: 'Hoàn thất bại',
  CANCELLED: 'Đã hủy khoản hoàn',
};

export function BookingChangeSettlementMonitor() {
  const [keyword, setKeyword] = useState('');
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<string | null>(null);
  const query = useQuery({
    queryKey: ['booking-change-settlements', keyword, page],
    queryFn: () => listBookingChangeSettlements(keyword, page),
    refetchInterval: 10000,
  });
  return (
    <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
      <Typography.Paragraph type="secondary">
        Theo dõi thu / hoàn hiện tại của booking hoặc toàn đơn đã đổi lịch. Mỗi đơn hiện một dòng
        theo lần đổi đã áp dụng gần nhất; thanh toán và hoàn tiền được cập nhật từ giao dịch thực
        tế.
      </Typography.Paragraph>
      <Space wrap>
        <Input.Search
          aria-label="Tìm booking hoặc đơn đổi lịch"
          placeholder="Nhập mã booking hoặc mã đơn"
          allowClear
          maxLength={100}
          onSearch={(value) => {
            setKeyword(value.trim());
            setPage(0);
          }}
          style={{ width: 320, maxWidth: '100%' }}
        />
        <Button
          aria-label="Làm mới thu / hoàn"
          loading={query.isFetching}
          onClick={() => void query.refetch()}
        >
          Làm mới thu / hoàn
        </Button>
      </Space>
      {query.isError && <Alert showIcon type="error" title={query.error.message} />}
      <Card>
        <Table<BookingChangeSettlement>
          rowKey="requestId"
          loading={query.isLoading}
          dataSource={query.data?.content ?? []}
          scroll={{ x: 1400 }}
          locale={{ emptyText: 'Chưa có yêu cầu đổi lịch đã áp dụng' }}
          pagination={{
            current: page + 1,
            pageSize: 20,
            total: query.data?.totalElements ?? 0,
            showSizeChanger: false,
            onChange: (value) => setPage(value - 1),
          }}
          columns={[
            {
              title: 'Booking / đơn',
              width: 260,
              render: (_, row) => (
                <Space orientation="vertical" size={0}>
                  <Typography.Text strong>{row.orderCode ?? row.bookingCode}</Typography.Text>
                  <Typography.Text type="secondary">
                    {row.roomCount} phòng · Đổi lúc {formatHotelDateTime(row.appliedAt, row.zone)}
                  </Typography.Text>
                  <Tag>{stayLabels[row.stayStatus]}</Tag>
                </Space>
              ),
            },
            {
              title: 'Tổng phải trả hiện tại',
              align: 'right',
              render: (_, row) => (row.needsReview ? 'Chờ đối soát' : money(row.obligationAmount)),
            },
            {
              title: 'Khách thanh toán',
              render: (_, row) => (
                <Space orientation="vertical" size={4}>
                  <Tag color={collectionLabels[row.collectionStatus][1]}>
                    {collectionLabels[row.collectionStatus][0]}
                  </Tag>
                  <span>
                    {row.needsReview
                      ? 'Chưa xác định khoản thiếu'
                      : `Còn thiếu: ${money(row.outstandingAmount)}`}
                  </span>
                  {row.outstandingAmount > 0 && (
                    <Typography.Text type={row.overdue ? 'danger' : 'secondary'}>
                      {row.balanceDueAt
                        ? `Hạn thu: ${formatHotelDateTime(row.balanceDueAt, row.zone)}`
                        : 'Chưa có hạn thu'}
                    </Typography.Text>
                  )}
                </Space>
              ),
            },
            {
              title: 'Khách sạn hoàn tiền',
              render: (_, row) => (
                <Space orientation="vertical" size={4}>
                  <Tag color={refundLabels[row.refundStatus][1]}>
                    {refundLabels[row.refundStatus][0]}
                  </Tag>
                  <span>Đã hoàn: {money(row.refundedAmount)}</span>
                  <span>Chờ hoàn: {money(row.pendingRefundAmount)}</span>
                  {row.unrequestedRefundAmount > 0 && (
                    <Typography.Text type="danger">
                      Cần đối soát thêm {money(row.unrequestedRefundAmount)}
                    </Typography.Text>
                  )}
                </Space>
              ),
            },
            {
              title: 'Thao tác',
              render: (_, row) => (
                <Button onClick={() => setSelected(row.requestId)}>Chi tiết thu / hoàn</Button>
              ),
            },
          ]}
        />
      </Card>
      {selected && <SettlementDetail id={selected} onClose={() => setSelected(null)} />}
    </Space>
  );
}

function SettlementDetail({ id, onClose }: { id: string; onClose: () => void }) {
  const { roles } = useAuth();
  const manager = hasRouteRole(roles, ROUTE_ROLES.management);
  const query = useQuery({
    queryKey: ['booking-change-settlement', id],
    queryFn: () => getBookingChangeSettlement(id),
    refetchInterval: 10000,
  });
  const data = query.data;
  return (
    <Modal
      open
      width={1000}
      title="Giám sát thanh toán và hoàn tiền sau đổi lịch"
      onCancel={onClose}
      footer={<Button onClick={onClose}>Đóng</Button>}
      styles={{ body: { maxHeight: '70dvh', overflowY: 'auto' } }}
    >
      <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
        <Button
          aria-label="Cập nhật thu / hoàn"
          loading={query.isFetching}
          onClick={() => void query.refetch()}
        >
          Cập nhật thu / hoàn
        </Button>
        {query.isError && <Alert showIcon type="error" title={query.error.message} />}
        {data && (
          <>
            <Typography.Text strong>
              {data.orderCode ?? data.bookingCode} · {data.roomCount} phòng
            </Typography.Text>
            {data.stayStatus === 'CANCELLED' && (
              <Alert
                showIcon
                type="warning"
                title="Booking / đơn đã hủy sau khi đổi lịch"
                description="Các khoản dưới đây theo quyết toán hiện tại sau hủy. Lịch sử đổi lịch được giữ để đối chiếu; không còn quyền nhận phòng theo kỳ đã đổi."
              />
            )}
            {data.needsReview && (
              <Alert
                showIcon
                type="warning"
                title="Cần quản lý đối soát nghĩa vụ và tiền đã nhận"
                description="Chưa thể kết luận đã thanh toán đủ từ các số tiền tạm ghi nhận."
              />
            )}
            <Space wrap>
              <Tag color={collectionLabels[data.collectionStatus][1]}>
                {collectionLabels[data.collectionStatus][0]}
              </Tag>
              <Tag color={refundLabels[data.refundStatus][1]}>
                {refundLabels[data.refundStatus][0]}
              </Tag>
            </Space>
            <Descriptions bordered size="small" column={{ xs: 1, sm: 2 }}>
              <Descriptions.Item label="Tổng nghĩa vụ hiện tại">
                {data.needsReview ? 'Chờ đối soát' : money(data.obligationAmount)}
              </Descriptions.Item>
              <Descriptions.Item label="Tổng thực nhận">
                {money(data.receivedAmount)}
              </Descriptions.Item>
              <Descriptions.Item label="Tiền còn được bù trừ">
                {money(data.availableAmount)}
              </Descriptions.Item>
              <Descriptions.Item label="Khách còn thiếu">
                {data.needsReview ? 'Chưa xác định' : money(data.outstandingAmount)}
              </Descriptions.Item>
              <Descriptions.Item label="Khách sạn đã hoàn">
                {money(data.refundedAmount)}
              </Descriptions.Item>
              <Descriptions.Item label="Khách sạn còn chờ hoàn">
                {money(data.pendingRefundAmount)}
              </Descriptions.Item>
              <Descriptions.Item label="Hạn thu hiện tại">
                {data.outstandingAmount > 0 && data.balanceDueAt
                  ? formatHotelDateTime(data.balanceDueAt, data.zone)
                  : '—'}
              </Descriptions.Item>
              <Descriptions.Item label="Số liệu cập nhật lúc">
                {formatHotelDateTime(data.checkedAt, data.zone)}
              </Descriptions.Item>
              <Descriptions.Item label="Thu thêm khi duyệt đổi">
                {data.additionalAtApproval === null ? '—' : money(data.additionalAtApproval)}
              </Descriptions.Item>
              <Descriptions.Item label="Khoản hoàn khi duyệt đổi">
                {data.refundAtApproval === null ? '—' : money(data.refundAtApproval)}
              </Descriptions.Item>
            </Descriptions>
            <Typography.Text type="secondary">
              Hai khoản tại thời điểm duyệt là lịch sử. Trạng thái thu / hoàn bên trên tính lại theo
              các giao dịch hiện tại, bao gồm quyết toán hủy sau đó.
            </Typography.Text>
            {data.overdue && (
              <Alert
                showIcon
                type="warning"
                title="Đã quá hạn thu khoản còn thiếu"
                description="Liên hệ khách và xử lý theo thỏa thuận. Quá hạn thu không tự hủy booking hoặc tạo phí no-show."
              />
            )}
            {data.unrequestedRefundAmount > 0 && (
              <Alert
                showIcon
                type="warning"
                title={`Có ${money(data.unrequestedRefundAmount)} tiền dư chưa có khoản hoàn hợp lệ`}
                description="Cần đối soát và tạo quyết toán phù hợp trước khi kết luận đã hoàn đủ."
              />
            )}
            <Typography.Title level={5}>Công nợ từng phòng</Typography.Title>
            <Table
              pagination={false}
              size="small"
              rowKey="bookingCode"
              scroll={{ x: 650 }}
              dataSource={data.rooms}
              columns={[
                {
                  title: 'Booking / phòng',
                  render: (_, room) => (
                    <>
                      {room.bookingCode}
                      <br />
                      {room.roomTypeName}
                    </>
                  ),
                },
                {
                  title: 'Trạng thái',
                  dataIndex: 'bookingStatus',
                  render: (value: string) => stayLabels[value] ?? value,
                },
                {
                  title: 'Phải trả',
                  align: 'right',
                  render: (_, room) =>
                    room.needsReview ? 'Chờ đối soát' : money(room.obligationAmount),
                },
                {
                  title: 'Tiền bù trừ',
                  align: 'right',
                  dataIndex: 'availableAmount',
                  render: money,
                },
                {
                  title: 'Còn thiếu',
                  align: 'right',
                  render: (_, room) =>
                    room.needsReview ? 'Chưa xác định' : money(room.outstandingAmount),
                },
              ]}
            />
            <Typography.Title level={5}>Theo dõi các khoản hoàn</Typography.Title>
            <Table
              pagination={false}
              size="small"
              rowKey="refundId"
              scroll={{ x: 850 }}
              dataSource={data.refunds}
              locale={{ emptyText: 'Không có khoản hoàn' }}
              columns={[
                {
                  title: 'Booking / thanh toán',
                  render: (_, refund) => (
                    <>
                      {refund.bookingCode}
                      <br />
                      <Typography.Text type="secondary">{refund.paymentCode}</Typography.Text>
                    </>
                  ),
                },
                { title: 'Số tiền', dataIndex: 'amount', align: 'right', render: money },
                {
                  title: 'Trạng thái',
                  render: (_, refund) => (
                    <Space orientation="vertical" size={0}>
                      <Tag color={refund.status === 'SUCCESS' ? 'green' : 'orange'}>
                        {refundStateLabels[refund.status] ?? refund.status}
                      </Tag>
                      {refund.status === 'PENDING' && !refund.destinationProvided && (
                        <Typography.Text type="warning">Chưa có tài khoản nhận</Typography.Text>
                      )}
                    </Space>
                  ),
                },
                {
                  title: 'Đã hoàn lúc',
                  render: (_, refund) =>
                    refund.refundedAt ? formatHotelDateTime(refund.refundedAt, data.zone) : '—',
                },
                {
                  title: 'Mã đối soát ngân hàng',
                  render: (_, refund) => refund.bankReference ?? '—',
                },
              ]}
            />
            {manager && (
              <Space wrap>
                <Link to={ROUTE_PATHS.refunds}>
                  <Button>Mở quản lý hoàn tiền</Button>
                </Link>
                <Link to={ROUTE_PATHS.payments}>
                  <Button>Đối soát thanh toán</Button>
                </Link>
              </Space>
            )}
          </>
        )}
      </Space>
    </Modal>
  );
}
