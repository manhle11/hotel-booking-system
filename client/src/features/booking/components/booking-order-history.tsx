import { keepPreviousData, useQuery } from '@tanstack/react-query';
import {
  Alert,
  Button,
  Card,
  Empty,
  Flex,
  Pagination,
  Skeleton,
  Space,
  Tag,
  Typography,
} from 'antd';
import dayjs from 'dayjs';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { getMyBookingOrders } from '../api/booking-order-api';
import { ORDER_STATUS } from '../utils/booking-order-presentation';

export function BookingOrderHistorySection() {
  const [page, setPage] = useState(0);
  const query = useQuery({
    queryKey: ['booking-order-history', page],
    queryFn: () => getMyBookingOrders(page),
    placeholderData: keepPreviousData,
  });
  return (
    <section aria-label="Các đơn đặt phòng" style={{ marginBottom: 28 }}>
      <Typography.Title level={4}>Đơn đặt phòng</Typography.Title>
      {query.isLoading ? (
        <Skeleton active />
      ) : query.isError ? (
        <Alert
          type="error"
          title="Không tải được lịch sử đơn"
          action={<Button onClick={() => void query.refetch()}>Thử lại</Button>}
        />
      ) : !query.data?.content.length ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Bạn chưa có đơn đặt phòng mới" />
      ) : (
        <Space orientation="vertical" size={16} style={{ width: '100%' }}>
          {query.data.content.map((order) => (
            <Card key={order.orderId}>
              <Flex justify="space-between" align="start" gap={16} wrap>
                <div>
                  <Typography.Text strong style={{ overflowWrap: 'anywhere' }}>
                    {order.orderCode}
                  </Typography.Text>
                  <Typography.Paragraph style={{ marginTop: 8 }}>
                    {order.rooms.length} phòng · {dayjs(order.checkInDate).format('DD/MM/YYYY')} →{' '}
                    {dayjs(order.checkOutDate).format('DD/MM/YYYY')}
                  </Typography.Paragraph>
                  <Typography.Paragraph type="secondary">
                    {order.rooms.map((room) => room.roomTypeName).join(' · ')}
                  </Typography.Paragraph>
                  <Tag color={ORDER_STATUS[order.status].color}>
                    {ORDER_STATUS[order.status].label}
                  </Tag>
                </div>
                <Space orientation="vertical" align="end">
                  <Typography.Text strong>
                    {new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(
                      order.totalAmount,
                    )}
                  </Typography.Text>
                  <Link to={`/booking-orders/${order.orderId}`}>
                    <Button type="primary">
                      {order.status === 'PENDING' ? 'Xem đơn / thanh toán' : 'Xem chi tiết'}
                    </Button>
                  </Link>
                </Space>
              </Flex>
            </Card>
          ))}
          <Pagination
            current={page + 1}
            pageSize={6}
            total={query.data.totalElements}
            showSizeChanger={false}
            onChange={(value) => setPage(value - 1)}
          />
        </Space>
      )}
    </section>
  );
}
