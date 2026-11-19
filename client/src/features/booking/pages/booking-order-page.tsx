import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  App,
  Button,
  Card,
  Checkbox,
  Col,
  Descriptions,
  Flex,
  Input,
  Modal,
  Row,
  Skeleton,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
} from 'antd';
import dayjs from 'dayjs';
import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { MainHeader } from '@components/layout/MainHeader';
import { MainFooter } from '@components/layout/MainFooter';
import { BookingSteps } from '@components/booking/BookingSteps';
import { RefundInformationForm } from '../../payment/components/refund-information-form';
import { ARRIVAL_POLICY_VERSION } from '../arrival-policy';
import { CustomerReschedule } from '../components/customer-reschedule';
import { NoShowSettlementCard } from '../components/no-show-settlement-card';
import { ArrivalPolicyNotice } from '../components/arrival-policy-notice';
import type { RefundInformation } from '../../payment/types/refund';
import {
  cancelBookingOrder,
  claimBookingOrder,
  createOrderPayment,
  getBookingOrder,
  getOrderCancellationEligibility,
  getOrderAccessToken,
  supplyOrderRefundInformation,
} from '../api/booking-order-api';
import type { BookingOrder, BookingOrderRoom } from '../types/booking-order';
import { ORDER_STATUS } from '../utils/booking-order-presentation';
import { PaymentInstructions } from './booking-payment-page';
import './create-booking-page.css';
import './booking-order.css';
import { ReviewAction } from '../../review/review-action';
import { useAuth } from '../../authentication/providers/auth-context';
import { createPaymentRequest } from '../api/booking-api';
import { storePaymentResultToken } from '../utils/payment-result-storage';

const ROOM_STATUS: Record<string, string> = {
  PENDING: 'Đang giữ',
  CONFIRMED: 'Đã xác nhận',
  CHECKED_IN: 'Đã nhận phòng',
  CHECKED_OUT: 'Đã trả phòng',
  CANCELLED: 'Đã hủy',
  EXPIRED: 'Hết hạn',
  NO_SHOW: 'Không đến',
};
const money = (value: number) =>
  new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(value);
const emptyRefund: RefundInformation = {
  method: 'BANK_TRANSFER',
  bankName: '',
  accountHolderName: '',
  accountNumber: '',
};

export function BookingOrderPage() {
  const navigate = useNavigate();
  const { orderId = '' } = useParams();
  const { message } = App.useApp();
  const client = useQueryClient();
  const { session, isAuthenticated } = useAuth();
  const accountId = session?.user?.id ?? null;
  const [accepted, setAccepted] = useState(false);
  const [policyReady, setPolicyReady] = useState(false);
  const [cancelOpen, setCancelOpen] = useState(false);
  const [reason, setReason] = useState('');
  const [refund, setRefund] = useState<RefundInformation>(emptyRefund);
  const queryKey = ['booking-order', orderId, accountId];
  const orderQuery = useQuery({
    queryKey,
    queryFn: () => getBookingOrder(orderId),
    retry: false,
    refetchInterval: (query) =>
      query.state.data?.status === 'PENDING' ||
      query.state.data?.paymentStatus === 'PENDING' ||
      query.state.data?.refundStatus === 'PENDING' ||
      query.state.data?.refundStatus === 'PROCESSING'
        ? 3000
        : false,
  });
  const onUpdated = (order: BookingOrder) => {
    client.setQueryData(queryKey, order);
    void client.invalidateQueries({ queryKey: ['booking-order-history'] });
  };
  const payment = useMutation({
    mutationFn: () => createOrderPayment(orderId, ARRIVAL_POLICY_VERSION),
    onSuccess: onUpdated,
    onError: (error: Error) => void message.error(error.message),
  });
  const claim = useMutation({
    mutationFn: () => claimBookingOrder(orderId),
    onSuccess: (updated) => {
      onUpdated(updated);
      void client.invalidateQueries({ queryKey: ['booking-history'] });
      void message.success('Đã lưu đơn vào lịch sử tài khoản.');
    },
    onError: (error: Error) => void message.error(error.message),
  });
  const balancePayment = useMutation({
    mutationFn: (room: BookingOrderRoom) =>
      createPaymentRequest(room.bookingId, getOrderAccessToken(orderId)),
    onSuccess: (result) => {
      const token = getOrderAccessToken(orderId);
      if (token) storePaymentResultToken(result.paymentId, token);
      navigate(`/payments/${result.paymentId}/result`);
    },
    onError: (error: Error) => void message.error(error.message),
  });
  const wholeBalancePayment = useMutation({
    mutationFn: () => createOrderPayment(orderId),
    onSuccess: (updated) => {
      onUpdated(updated);
      if (updated.payment) {
        const token = getOrderAccessToken(orderId);
        if (token) storePaymentResultToken(updated.payment.paymentId, token);
        navigate(`/payments/${updated.payment.paymentId}/result`);
      }
    },
    onError: (error: Error) => {
      void message.error(error.message);
      void orderQuery.refetch();
    },
  });
  const supplyRefund = useMutation({
    mutationFn: () => supplyOrderRefundInformation(orderId, refund),
    onSuccess: (updated) => {
      onUpdated(updated);
      void message.success('Đã lưu tài khoản nhận hoàn tiền.');
    },
    onError: (error: Error) => void message.error(error.message),
  });
  const eligibility = useQuery({
    queryKey: ['order-cancellation', orderId, accountId],
    queryFn: () => getOrderCancellationEligibility(orderId),
    enabled: cancelOpen,
    retry: false,
  });
  const cancel = useMutation({
    mutationFn: () =>
      cancelBookingOrder(
        orderId,
        reason.trim() || undefined,
        eligibility.data?.refundAmount ? refund : undefined,
      ),
    onSuccess: (order) => {
      onUpdated(order);
      setCancelOpen(false);
      void message.success('Đã hủy toàn bộ phòng trong đơn.');
    },
    onError: (error: Error) => void message.error(error.message),
  });
  const order = orderQuery.data;
  const pending = order?.status === 'PENDING' && dayjs(order.expiresAt).isAfter(dayjs());
  const wholeBalanceAmount =
    order?.rooms.reduce((sum, room) => sum + Math.max(room.totalAmount - room.paidAmount, 0), 0) ??
    0;
  const canPayWholeBalance =
    order?.status === 'CONFIRMED' &&
    wholeBalanceAmount > 0 &&
    !order.rooms.some(
      (room) =>
        room.totalAmount > room.paidAmount &&
        room.status !== 'CONFIRMED' &&
        room.status !== 'CHECKED_IN',
    );
  const validRefund =
    !eligibility.data?.refundAmount ||
    Boolean(
      refund.bankName.trim() &&
      refund.accountHolderName.trim() &&
      /^[0-9A-Za-z]{4,34}$/.test(refund.accountNumber),
    );

  return (
    <div className="booking-public-shell">
      <MainHeader />
      <main className="create-booking-page">
        <div className="booking-page-shell">
          <Link to="/account/bookings">Lịch sử đặt phòng</Link>
          <Typography.Title level={2}>Đơn đặt phòng của bạn</Typography.Title>
          {orderQuery.isLoading ? (
            <Skeleton active paragraph={{ rows: 10 }} />
          ) : !order ? (
            <Alert
              type="error"
              showIcon
              title="Không thể xem đơn đặt phòng"
              description="Vui lòng đăng nhập bằng tài khoản đã đặt phòng hoặc mở lại đơn trên trình duyệt đã sử dụng."
              action={
                <Space wrap>
                  <Button onClick={() => void orderQuery.refetch()}>Thử lại</Button>
                </Space>
              }
            />
          ) : (
            <>
              <BookingSteps current={2} style={{ marginBlock: 24 }} />
              {order.claimable && (
                <Alert
                  type="info"
                  showIcon
                  title="Đơn này chưa được lưu vào tài khoản"
                  description="Lưu đơn để xem lại trong lịch sử đặt phòng sau này."
                  action={
                    isAuthenticated ? (
                      <Button loading={claim.isPending} onClick={() => claim.mutate()}>
                        Lưu đơn vào lịch sử
                      </Button>
                    ) : (
                      <Link
                        to="/login"
                        state={{ from: { pathname: `/booking-orders/${orderId}` } }}
                      >
                        <Button>Đăng nhập để lưu đơn</Button>
                      </Link>
                    )
                  }
                  style={{ marginBottom: 24 }}
                />
              )}
              {orderQuery.isError && (
                <Alert
                  type="warning"
                  showIcon
                  title="Chưa cập nhật được trạng thái mới nhất"
                  action={<Button onClick={() => void orderQuery.refetch()}>Thử lại</Button>}
                  style={{ marginBottom: 16 }}
                />
              )}
              {order.status === 'CONFIRMED' && (
                <Alert
                  type="success"
                  showIcon
                  title={`Đã xác nhận ${order.rooms.length} phòng`}
                  description={`Tình hình từng phòng: ${order.rooms.filter((r) => r.status === 'CONFIRMED').length} chờ đến, ${order.rooms.filter((r) => r.status === 'CHECKED_IN').length} đang ở, ${order.rooms.filter((r) => r.status === 'CHECKED_OUT').length} đã trả, ${order.rooms.filter((r) => r.status === 'NO_SHOW').length} không đến.`}
                  style={{ marginBottom: 24 }}
                />
              )}
              {order.status === 'EXPIRED' && (
                <Alert
                  type="warning"
                  showIcon
                  title="Đơn đã hết thời gian thanh toán"
                  description="Các phòng đã được giải phóng. Nếu bạn đã chuyển tiền, hệ thống sẽ đối soát và xử lý hoàn tiền theo kết quả ghi nhận."
                  style={{ marginBottom: 24 }}
                />
              )}
              {order.status === 'CANCELLED' && (
                <Alert
                  type="info"
                  showIcon
                  title="Đã hủy toàn bộ phòng trong đơn"
                  style={{ marginBottom: 24 }}
                />
              )}
              {order.status === 'CANCELLED' && (
                <ArrivalPolicyNotice
                  codes={order.rooms.map((r) => r.bookingCode)}
                  accessToken={getOrderAccessToken(orderId)}
                />
              )}
              <Row gutter={[24, 24]}>
                <Col xs={24} xl={16}>
                  <Card title={`${order.rooms.length} phòng trong đơn`}>
                    <Descriptions column={1} size="small">
                      <Descriptions.Item label="Ngày lưu trú">
                        {dayjs(order.checkInDate).format('DD/MM/YYYY')} →{' '}
                        {dayjs(order.checkOutDate).format('DD/MM/YYYY')}
                      </Descriptions.Item>
                      <Descriptions.Item label="Người đặt">{order.contactName}</Descriptions.Item>
                      <Descriptions.Item label="Email">{order.contactEmail}</Descriptions.Item>
                    </Descriptions>
                    <Table<BookingOrderRoom>
                      rowKey="bookingId"
                      dataSource={order.rooms}
                      pagination={false}
                      scroll={{ x: 620 }}
                      columns={[
                        {
                          title: 'Phòng',
                          key: 'room',
                          render: (_, room, index) => (
                            <Space orientation="vertical" size={0}>
                              <strong>
                                Phòng {index + 1} · {room.roomTypeName}
                              </strong>
                              <Typography.Text type="secondary">{room.bookingCode}</Typography.Text>
                              {room.roomNumber && <span>Số phòng: {room.roomNumber}</span>}
                            </Space>
                          ),
                        },
                        {
                          title: 'Khách',
                          key: 'guests',
                          render: (_, room) =>
                            `${room.adults} người lớn${room.children ? `, ${room.children} trẻ em (${room.childAges ? room.childAges.map((age) => `${age} tuổi`).join(', ') : 'chưa có dữ liệu tuổi'})` : ''}`,
                        },
                        { title: 'Thành tiền', dataIndex: 'totalAmount', render: money },
                        {
                          title: 'Trạng thái',
                          dataIndex: 'status',
                          render: (status: string) => ROOM_STATUS[status] ?? status,
                        },
                        {
                          title: 'Thanh toán bổ sung',
                          render: (_, room) =>
                            room.status === 'CONFIRMED' && room.totalAmount > room.paidAmount ? (
                              <Button
                                disabled={wholeBalancePayment.isPending}
                                loading={
                                  balancePayment.isPending &&
                                  balancePayment.variables?.bookingId === room.bookingId
                                }
                                onClick={() => balancePayment.mutate(room)}
                              >
                                Thanh toán chênh lệch
                              </Button>
                            ) : (
                              '—'
                            ),
                        },
                      ]}
                    />
                  </Card>
                  {order.status === 'CONFIRMED' &&
                    order.rooms.every((room) => room.status === 'CONFIRMED') &&
                    order.rooms[0] && (
                      <CustomerReschedule
                        code={order.rooms[0].bookingCode}
                        accessToken={getOrderAccessToken(orderId)}
                        roomCount={order.rooms.length}
                        currentCheckInDate={order.checkInDate}
                        currentCheckOutDate={order.checkOutDate}
                      />
                    )}
                  {order.rooms
                    .filter(
                      (room) =>
                        room.status === 'NO_SHOW' ||
                        (room.status === 'CANCELLED' && room.staffCancellation),
                    )
                    .map((room) => (
                      <NoShowSettlementCard
                        key={room.bookingCode}
                        bookingCode={room.bookingCode}
                        accessToken={getOrderAccessToken(orderId)}
                        kind={room.status === 'NO_SHOW' ? 'no-show' : 'cancellation'}
                      />
                    ))}
                  {order.reviewOwner === true &&
                    order.rooms.some((room) => room.status === 'CHECKED_OUT') && (
                      <Card title="Đánh giá sau lưu trú" style={{ marginTop: 24 }}>
                        <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
                          {order.rooms
                            .filter(
                              (room, index, rooms) =>
                                room.status === 'CHECKED_OUT' &&
                                rooms.findIndex(
                                  (candidate) =>
                                    candidate.roomTypeId === room.roomTypeId &&
                                    candidate.status === 'CHECKED_OUT',
                                ) === index,
                            )
                            .map((room) => (
                              <Flex
                                key={room.roomTypeId}
                                justify="space-between"
                                align="center"
                                wrap
                                gap={12}
                              >
                                <Typography.Text strong>{room.roomTypeName}</Typography.Text>
                                <ReviewAction
                                  bookingId={room.bookingId}
                                  roomTypeName={room.roomTypeName}
                                />
                              </Flex>
                            ))}
                        </Space>
                      </Card>
                    )}
                  {pending && (
                    <Card title="Thanh toán chung cho toàn đơn" style={{ marginTop: 24 }}>
                      {order.paidAmount > 0 && (
                        <Alert
                          type="info"
                          showIcon
                          title={`Đã nhận ${money(order.paidAmount)}`}
                          description={`Còn cần thanh toán ${money(Math.max(0, order.totalAmount - order.paidAmount))}. Chỉ xác nhận phòng khi nhận đủ tổng tiền.`}
                          style={{ marginBottom: 16 }}
                        />
                      )}
                      {order.payment ? (
                        <PaymentInstructions
                          key={order.payment.paymentUrl}
                          payment={order.payment}
                          onCopy={async (value, success) => {
                            try {
                              await navigator.clipboard.writeText(value);
                              void message.success(success);
                            } catch {
                              void message.warning(
                                'Vui lòng sao chép thông tin chuyển khoản thủ công.',
                              );
                            }
                          }}
                        />
                      ) : (
                        <Flex vertical gap={16}>
                          <Typography.Paragraph>
                            Chuyển khoản một lần cho tất cả phòng trong đơn. Hệ thống tự động xác
                            nhận khi nhận đủ tiền trước hạn.
                          </Typography.Paragraph>
                          <ArrivalPolicyNotice
                            codes={order.rooms.map((r) => r.bookingCode)}
                            accessToken={getOrderAccessToken(orderId)}
                            onReady={setPolicyReady}
                          />
                          <Checkbox
                            checked={accepted}
                            onChange={(event) => setAccepted(event.target.checked)}
                          >
                            Tôi đồng ý với điều khoản đặt phòng, chính sách hủy/đổi lịch và chính
                            sách đến muộn/không đến của HANBAY HOTEL.
                          </Checkbox>
                          <Button
                            type="primary"
                            disabled={!policyReady || !accepted}
                            loading={payment.isPending}
                            onClick={() => payment.mutate()}
                          >
                            Tạo mã QR thanh toán {money(order.totalAmount)}
                          </Button>
                          {payment.isError && <Alert type="error" title={payment.error.message} />}
                        </Flex>
                      )}
                    </Card>
                  )}
                  {order.refundAmount > 0 && (
                    <Alert
                      type="info"
                      showIcon
                      title={`Hoàn tiền: ${money(order.refundAmount)}`}
                      description={
                        order.refundStatus === 'SUCCESS'
                          ? 'Khoản hoàn tiền gần nhất đã hoàn tất.'
                          : 'Yêu cầu hoàn tiền đang được khách sạn đối soát và xử lý.'
                      }
                      style={{ marginTop: 24 }}
                    />
                  )}
                  {order.refundInformationRequired && (
                    <Card title="Tài khoản nhận hoàn tiền" style={{ marginTop: 24 }}>
                      <Typography.Paragraph>
                        Vui lòng cung cấp tài khoản ngân hàng để khách sạn xử lý khoản hoàn tiền
                        đang chờ.
                      </Typography.Paragraph>
                      <RefundInformationForm
                        value={refund}
                        onChange={setRefund}
                        disabled={supplyRefund.isPending}
                      />
                      <Button
                        type="primary"
                        loading={supplyRefund.isPending}
                        disabled={
                          !refund.bankName.trim() ||
                          !refund.accountHolderName.trim() ||
                          !/^[0-9A-Za-z]{4,34}$/.test(refund.accountNumber)
                        }
                        onClick={() => supplyRefund.mutate()}
                      >
                        Gửi thông tin nhận hoàn tiền
                      </Button>
                    </Card>
                  )}
                </Col>
                <Col xs={24} xl={8}>
                  <Card title="Tóm tắt đơn" className="order-summary">
                    <Typography.Text type="secondary">Mã đơn chung</Typography.Text>
                    <Typography.Paragraph strong copyable style={{ overflowWrap: 'anywhere' }}>
                      {order.orderCode}
                    </Typography.Paragraph>
                    <Tag color={ORDER_STATUS[order.status].color}>
                      {ORDER_STATUS[order.status].label}
                    </Tag>
                    <Descriptions column={1} size="small" style={{ marginTop: 24 }}>
                      <Descriptions.Item label="Tiền đặt phòng ban đầu">
                        {money(order.bookedAmount)}
                      </Descriptions.Item>
                      <Descriptions.Item
                        label={
                          order.additionalChargeAmount < 0
                            ? 'Điều chỉnh giảm quyết toán'
                            : 'Phí phát sinh'
                        }
                      >
                        {money(order.additionalChargeAmount)}
                      </Descriptions.Item>
                      <Descriptions.Item label="Đã thanh toán">
                        {money(order.paidAmount)}
                      </Descriptions.Item>
                      {(order.status === 'PENDING' || order.status === 'CONFIRMED') && (
                        <Descriptions.Item label="Còn phải thanh toán">
                          {money(wholeBalanceAmount)}
                        </Descriptions.Item>
                      )}
                    </Descriptions>
                    <Statistic
                      title="Tổng tiền thực tế"
                      value={order.totalAmount}
                      formatter={(value) => money(Number(value))}
                      style={{ marginTop: 24 }}
                    />
                    {canPayWholeBalance && (
                      <Flex vertical gap={8} style={{ marginTop: 24 }}>
                        <Button
                          type="primary"
                          size="large"
                          block
                          loading={wholeBalancePayment.isPending}
                          disabled={balancePayment.isPending || orderQuery.isError}
                          onClick={() => wholeBalancePayment.mutate()}
                        >
                          Thanh toán toàn bộ {money(wholeBalanceAmount)}
                        </Button>
                        <Typography.Text type="secondary">
                          Chuyển khoản một lần cho toàn bộ khoản còn thiếu trong đơn.
                        </Typography.Text>
                      </Flex>
                    )}
                    {pending && (
                      <Statistic.Countdown
                        title="Thời gian giữ phòng còn lại"
                        value={dayjs(order.expiresAt).valueOf()}
                        format="mm:ss"
                        onFinish={() => void orderQuery.refetch()}
                        style={{ marginTop: 24 }}
                      />
                    )}
                    <Flex vertical gap={12} style={{ marginTop: 24 }}>
                      {(order.status === 'PENDING' || order.status === 'CONFIRMED') && (
                        <Button
                          danger
                          onClick={() => {
                            setCancelOpen(true);
                            void eligibility.refetch();
                          }}
                        >
                          Hủy toàn đơn
                        </Button>
                      )}
                      <Link to="/rooms/search">
                        <Button block>Đặt phòng khác</Button>
                      </Link>
                    </Flex>
                  </Card>
                </Col>
              </Row>
            </>
          )}
          <Modal
            title="Hủy toàn bộ phòng trong đơn"
            open={cancelOpen}
            onCancel={() => !cancel.isPending && setCancelOpen(false)}
            onOk={() => cancel.mutate()}
            confirmLoading={cancel.isPending}
            okText="Xác nhận hủy toàn đơn"
            cancelText="Giữ đơn"
            okButtonProps={{ danger: true, disabled: !eligibility.data?.canCancel || !validRefund }}
          >
            {eligibility.isFetching ? (
              <Skeleton active />
            ) : eligibility.isError ? (
              <Alert
                type="error"
                title="Không kiểm tra được điều kiện hủy"
                action={<Button onClick={() => void eligibility.refetch()}>Thử lại</Button>}
              />
            ) : (
              <>
                <Typography.Paragraph>
                  {eligibility.data?.canCancel
                    ? eligibility.data.policyDescription
                    : eligibility.data?.message}
                </Typography.Paragraph>
                {Boolean(eligibility.data?.refundAmount) && (
                  <>
                    <Typography.Paragraph strong>
                      Số tiền hoàn dự kiến: {money(eligibility.data!.refundAmount)}
                    </Typography.Paragraph>
                    <RefundInformationForm
                      value={refund}
                      onChange={setRefund}
                      disabled={cancel.isPending}
                    />
                  </>
                )}
                {eligibility.data?.canCancel && (
                  <Input.TextArea
                    aria-label="Lý do hủy đơn"
                    placeholder="Lý do hủy (không bắt buộc)"
                    value={reason}
                    maxLength={500}
                    disabled={cancel.isPending}
                    onChange={(event) => setReason(event.target.value)}
                  />
                )}
                {cancel.isError && (
                  <Alert type="error" title={cancel.error.message} style={{ marginTop: 12 }} />
                )}
              </>
            )}
          </Modal>
        </div>
      </main>
      <MainFooter />
    </div>
  );
}
