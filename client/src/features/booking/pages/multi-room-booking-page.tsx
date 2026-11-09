import { ArrowLeftOutlined, HomeOutlined } from '@ant-design/icons';
import { useMutation, useQuery } from '@tanstack/react-query';
import {
  Alert,
  App,
  Button,
  Card,
  Col,
  Flex,
  Form,
  Input,
  InputNumber,
  Row,
  Skeleton,
  Space,
  Typography,
} from 'antd';
import { useEffect, useState } from 'react';
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom';
import dayjs from 'dayjs';
import { addOnUnitLabel } from '../types/addon-unit';
import { MealQuantityCalculator } from '../components/meal-quantity-calculator';
import { ChildPolicyNote } from '../../../components/room/ChildPolicyNote';
import { useHotelPolicy } from '../../../hooks/use-stay-policy';
import { MainHeader } from '@components/layout/MainHeader';
import { MainFooter } from '@components/layout/MainFooter';
import { BookingSteps } from '@components/booking/BookingSteps';
import { useAuth } from '@features/authentication/providers/auth-context';
import { searchAvailableRoomTypes } from '@features/room';
import { roomTypeCheckoutKeys } from '../../room/api/room-type-query-keys';
import { createBookingOrder } from '../api/booking-order-api';
import { getAddOns } from '../api/catalog-api';
import type { GuestInfo } from '../types/booking';
import type { CreateBookingOrderRequest, OrderRoomSelection } from '../types/booking-order';
import { changeRoomQuantity, MAX_ORDER_ROOMS } from '../utils/order-room-selection';
import { calculateMealQuantity, type MealQuantityInput } from '../utils/meal-quantity';
import './create-booking-page.css';
import './booking-order.css';

const money = (value: number) =>
  new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(value);

interface CheckoutRoom extends OrderRoomSelection {
  mealInputs?: Record<string, MealQuantityInput>;
}

export function MultiRoomBookingPage() {
  const [params] = useSearchParams();
  const policy = useHotelPolicy();
  const navigate = useNavigate();
  const { message } = App.useApp();
  const { session } = useAuth();
  const checkIn = params.get('checkIn') ?? '';
  const checkOut = params.get('checkOut') ?? '';
  const [form] = Form.useForm<GuestInfo>();
  useEffect(() => {
    if (!session?.user) return;
    const current = form.getFieldsValue(['fullName', 'email', 'phone']);
    form.setFieldsValue({
      fullName: current.fullName || session.user.fullName,
      email: current.email || session.user.email,
      phone: current.phone || session.user.phone || undefined,
    });
  }, [form, session?.user]);
  const [rooms, setRooms] = useState<CheckoutRoom[]>(() => {
    const roomTypeId = params.get('roomTypeId');
    return roomTypeId
      ? [
          {
            roomTypeId,
            adults: Number(params.get('adults')) || 2,
            children: Number(params.get('children')) || 0,
            addOns: [],
          },
        ]
      : [];
  });
  const availability = useQuery({
    queryKey: roomTypeCheckoutKeys.available(checkIn, checkOut),
    queryFn: () =>
      searchAvailableRoomTypes({ checkIn, checkOut, adults: 1, children: 0, page: 0, size: 100 }),
    enabled: Boolean(checkIn && checkOut),
  });
  const addOns = useQuery({ queryKey: ['booking', 'add-ons'], queryFn: getAddOns });
  const create = useMutation({
    mutationFn: createBookingOrder,
    onSuccess: (order) => navigate(`/booking-orders/${order.orderId}`),
    onError: (error: Error) => {
      void message.error(error.message);
      void availability.refetch();
    },
  });
  const types = availability.data?.content ?? [];
  const nights = dayjs(checkOut).diff(dayjs(checkIn), 'day');
  const invalidMeals = rooms.some((room) =>
    Object.values(room.mealInputs ?? {}).some(
      (input) => calculateMealQuantity(input, nights).error,
    ),
  );
  const total = rooms.reduce(
    (sum, room) =>
      sum +
      (types.find((type) => type.id === room.roomTypeId)?.roomTotal ?? 0) +
      room.addOns.reduce(
        (price, item) =>
          price +
          item.quantity * (addOns.data?.find((a) => a.id === item.addOnServiceId)?.price ?? 0),
        0,
      ),
    0,
  );
  const invalidRoom = rooms.some((room) => {
    const type = types.find((item) => item.id === room.roomTypeId);
    return (
      !type ||
      room.adults < 1 ||
      room.children < 0 ||
      (room.children > 0 &&
        ((room.childAges?.length ?? 0) !== room.children ||
          Array.from({ length: room.children }, (_, i) => room.childAges?.[i]).some(
            (age) =>
              age === undefined ||
              !Number.isInteger(age) ||
              age < 0 ||
              age >= (policy.adultFromAge ?? 12),
          ))) ||
      room.adults > type.adultCapacity ||
      room.children > type.childCapacity ||
      room.adults + room.children > type.maxGuests ||
      rooms.filter((item) => item.roomTypeId === room.roomTypeId).length > type.availableRooms
    );
  });
  const updateRoom = (index: number, patch: Partial<CheckoutRoom>) =>
    setRooms((current) => current.map((room, i) => (i === index ? { ...room, ...patch } : room)));

  if (!checkIn || !checkOut) return <Navigate to="/rooms/search" replace />;

  return (
    <div className="booking-public-shell">
      <MainHeader />
      <main className="create-booking-page">
        <div className="booking-page-shell">
          <Link to={`/rooms/search?${params.toString()}`}>
            <ArrowLeftOutlined /> Thay đổi ngày lưu trú
          </Link>
          <Typography.Title level={2}>Đặt phòng cho kỳ nghỉ của bạn</Typography.Title>
          <Typography.Paragraph type="secondary">
            Chọn tối đa {MAX_ORDER_ROOMS} phòng cùng ngày nhận/trả và thanh toán chung một lần.
          </Typography.Paragraph>
          <BookingSteps current={1} paymentDisabled style={{ marginBlock: 24 }} />
          <Row gutter={[24, 24]}>
            <Col xs={24} xl={16}>
              <Card title="Chọn loại phòng và số lượng">
                <Typography.Paragraph>
                  {checkIn} → {checkOut}
                </Typography.Paragraph>
                {availability.isLoading ? (
                  <Skeleton active />
                ) : availability.isError ? (
                  <Alert
                    type="error"
                    showIcon
                    title="Không tải được phòng trống"
                    action={<Button onClick={() => void availability.refetch()}>Thử lại</Button>}
                  />
                ) : !types.length ? (
                  <Alert type="info" title="Không còn phòng trống trong kỳ lưu trú này" />
                ) : (
                  types.map((type) => {
                    const quantity = rooms.filter((room) => room.roomTypeId === type.id).length;
                    return (
                      <div className="order-room-type" key={type.id}>
                        {type.imageUrl ? (
                          <img src={type.imageUrl} alt={type.name} />
                        ) : (
                          <HomeOutlined className="order-room-type__placeholder" />
                        )}
                        <div className="order-room-type__description">
                          <Typography.Text strong>{type.name}</Typography.Text>
                          <Typography.Text type="secondary">
                            Tối đa {type.maxGuests} khách/phòng · Còn {type.availableRooms} phòng
                          </Typography.Text>
                          <Typography.Text>
                            {money(type.roomTotal)} / phòng / {type.totalNights} đêm
                          </Typography.Text>
                        </div>
                        <InputNumber
                          aria-label={`Số phòng ${type.name}`}
                          min={0}
                          max={Math.min(
                            type.availableRooms,
                            MAX_ORDER_ROOMS - rooms.length + quantity,
                          )}
                          value={quantity}
                          disabled={create.isPending}
                          onChange={(value) =>
                            setRooms((current) => changeRoomQuantity(current, type.id, value ?? 0))
                          }
                        />
                      </div>
                    );
                  })
                )}
              </Card>
              <Form
                form={form}
                layout="vertical"
                requiredMark={false}
                initialValues={{
                  fullName: session?.user?.fullName,
                  email: session?.user?.email,
                  phone: session?.user?.phone ?? undefined,
                }}
                onFinish={(guestInfo) => {
                  if (!rooms.length || invalidRoom || invalidMeals || create.isPending) return;
                  const request: CreateBookingOrderRequest = {
                    checkInDate: checkIn,
                    checkOutDate: checkOut,
                    rooms: rooms.map(({ roomTypeId, adults, children, childAges, addOns }) => ({
                      roomTypeId,
                      adults,
                      children,
                      addOns,
                      ...(childAges ? { childAges } : {}),
                    })),
                    guestInfo: {
                      fullName: guestInfo.fullName.trim(),
                      email: guestInfo.email.trim(),
                      phone: guestInfo.phone?.trim() || undefined,
                      identityNumber: guestInfo.identityNumber.trim(),
                    },
                  };
                  create.mutate(request);
                }}
              >
                {rooms.length > 0 && (
                  <Card title="Khách và dịch vụ theo từng phòng" style={{ marginTop: 24 }}>
                    {rooms.map((room, index) => {
                      const type = types.find((item) => item.id === room.roomTypeId);
                      return (
                        <div key={`${room.roomTypeId}-${index}`} className="order-guest-room">
                          <Typography.Title level={5}>
                            Phòng {index + 1} · {type?.name ?? 'Không còn khả dụng'}
                          </Typography.Title>
                          <Flex gap={20} wrap>
                            <Space>
                              <span>Người lớn</span>
                              <InputNumber
                                aria-label={`Người lớn phòng ${index + 1}`}
                                min={1}
                                max={type?.adultCapacity ?? 20}
                                precision={0}
                                value={room.adults}
                                disabled={create.isPending}
                                onChange={(value) => updateRoom(index, { adults: value ?? 1 })}
                              />
                            </Space>
                            <Space>
                              <span>Trẻ em</span>
                              <InputNumber
                                aria-label={`Trẻ em phòng ${index + 1}`}
                                min={0}
                                max={type?.childCapacity ?? 20}
                                precision={0}
                                value={room.children}
                                disabled={create.isPending}
                                onChange={(value) =>
                                  updateRoom(index, {
                                    children: value ?? 0,
                                    childAges: room.childAges?.slice(0, value ?? 0),
                                  })
                                }
                              />
                            </Space>
                          </Flex>
                          <ChildPolicyNote />
                          <Space wrap>
                            {Array.from(
                              { length: Math.min(room.children, 20) },
                              (_, childIndex) => (
                                <label key={childIndex}>
                                  Tuổi trẻ em {childIndex + 1} tại ngày nhận phòng{' '}
                                  <InputNumber
                                    aria-label={`Tuổi trẻ em ${childIndex + 1} phòng ${index + 1}`}
                                    precision={0}
                                    disabled={create.isPending}
                                    value={room.childAges?.[childIndex]}
                                    onChange={(value) => {
                                      const ages = [...(room.childAges ?? [])];
                                      ages[childIndex] = value ?? Number.NaN;
                                      updateRoom(index, { childAges: ages });
                                    }}
                                  />
                                </label>
                              ),
                            )}
                          </Space>
                          {type && (
                            <Typography.Paragraph type="secondary" style={{ marginTop: 8 }}>
                              Tổng số khách tối đa: {type.maxGuests} người. Mỗi phòng cần ít nhất
                              một người lớn.
                            </Typography.Paragraph>
                          )}
                          {addOns.data?.length ? (
                            <details>
                              <summary>Dịch vụ bổ sung cho phòng {index + 1}</summary>
                              {addOns.data.map((addOn) => (
                                <Flex
                                  key={addOn.id}
                                  justify="space-between"
                                  align="center"
                                  gap={12}
                                  style={{ marginTop: 12 }}
                                >
                                  <div style={{ flex: '1 1 240px', minWidth: 0 }}>
                                    {addOn.name} · {money(addOn.price)}/
                                    {addOnUnitLabel(addOn.quantityUnit)}
                                    <br />
                                    Tổng số {addOnUnitLabel(addOn.quantityUnit)} cho cả kỳ lưu trú
                                    {addOn.quantityUnit === 'SERVING' && (
                                      <MealQuantityCalculator
                                        nights={nights}
                                        value={room.mealInputs?.[addOn.id]}
                                        disabled={create.isPending}
                                        onChange={(input) => {
                                          const { quantity } = calculateMealQuantity(input, nights);
                                          updateRoom(index, {
                                            mealInputs: { ...room.mealInputs, [addOn.id]: input },
                                            addOns: [
                                              ...room.addOns.filter(
                                                (item) => item.addOnServiceId !== addOn.id,
                                              ),
                                              ...(quantity > 0
                                                ? [{ addOnServiceId: addOn.id, quantity }]
                                                : []),
                                            ],
                                          });
                                        }}
                                      />
                                    )}
                                  </div>
                                  <InputNumber
                                    aria-label={`${addOn.name} phòng ${index + 1}`}
                                    min={0}
                                    max={1000}
                                    precision={0}
                                    readOnly={addOn.quantityUnit === 'SERVING'}
                                    controls={addOn.quantityUnit !== 'SERVING'}
                                    value={
                                      room.addOns.find((item) => item.addOnServiceId === addOn.id)
                                        ?.quantity ?? 0
                                    }
                                    disabled={create.isPending}
                                    onChange={
                                      addOn.quantityUnit === 'SERVING'
                                        ? undefined
                                        : (value) =>
                                            updateRoom(index, {
                                              addOns: [
                                                ...room.addOns.filter(
                                                  (item) => item.addOnServiceId !== addOn.id,
                                                ),
                                                ...(value
                                                  ? [{ addOnServiceId: addOn.id, quantity: value }]
                                                  : []),
                                              ],
                                            })
                                    }
                                  />
                                </Flex>
                              ))}
                            </details>
                          ) : null}
                        </div>
                      );
                    })}
                    {addOns.isError && (
                      <Alert
                        type="warning"
                        title="Chưa tải được dịch vụ bổ sung"
                        action={<Button onClick={() => void addOns.refetch()}>Thử lại</Button>}
                      />
                    )}
                  </Card>
                )}
                <Card title="Thông tin người đặt" style={{ marginTop: 24 }}>
                  <Typography.Paragraph type="secondary">
                    Thông tin liên hệ dùng chung cho tất cả phòng trong đơn.
                  </Typography.Paragraph>
                  <Row gutter={16}>
                    <Col xs={24} md={12}>
                      <Form.Item
                        name="fullName"
                        label="Họ và tên"
                        rules={[
                          { required: true, whitespace: true, message: 'Vui lòng nhập họ tên.' },
                        ]}
                      >
                        <Input autoComplete="name" disabled={create.isPending} />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={12}>
                      <Form.Item
                        name="email"
                        label="Email"
                        rules={[
                          { required: true, type: 'email', message: 'Vui lòng nhập email hợp lệ.' },
                        ]}
                      >
                        <Input type="email" autoComplete="email" disabled={create.isPending} />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={12}>
                      <Form.Item
                        name="phone"
                        label="Số điện thoại"
                        rules={[
                          {
                            pattern: /^0\d{9}$/,
                            message: 'Số điện thoại cần gồm 10 chữ số, bắt đầu bằng 0.',
                          },
                        ]}
                      >
                        <Input autoComplete="tel" disabled={create.isPending} />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={12}>
                      <Form.Item
                        name="identityNumber"
                        label="CCCD / giấy tờ tùy thân"
                        rules={[
                          {
                            required: true,
                            whitespace: true,
                            message: 'Vui lòng nhập giấy tờ tùy thân.',
                          },
                        ]}
                      >
                        <Input disabled={create.isPending} />
                      </Form.Item>
                    </Col>
                  </Row>
                </Card>
              </Form>
            </Col>
            <Col xs={24} xl={8}>
              <Card title="Đơn của bạn" className="order-summary">
                <Typography.Paragraph>
                  {rooms.length} phòng · {checkIn} → {checkOut}
                </Typography.Paragraph>
                {types
                  .filter((type) => rooms.some((room) => room.roomTypeId === type.id))
                  .map((type) => (
                    <Flex justify="space-between" key={type.id} style={{ marginBottom: 12 }}>
                      <span>{type.name}</span>
                      <strong>
                        × {rooms.filter((room) => room.roomTypeId === type.id).length}
                      </strong>
                    </Flex>
                  ))}
                <Typography.Title level={3}>{money(total)}</Typography.Title>
                <Typography.Paragraph type="secondary">
                  Giá tạm tính gồm tiền phòng và dịch vụ đã chọn. Tổng tiền sẽ được kiểm tra lại
                  trước khi giữ phòng.
                </Typography.Paragraph>
                {(invalidRoom || invalidMeals) && (
                  <Alert
                    type="warning"
                    showIcon
                    title={
                      invalidMeals
                        ? 'Vui lòng kiểm tra số người và số ngày dùng bữa'
                        : 'Vui lòng kiểm tra sức chứa, tuổi trẻ em và số phòng còn trống'
                    }
                    style={{ marginBottom: 16 }}
                  />
                )}
                {create.isError && (
                  <Alert
                    type="error"
                    showIcon
                    title={create.error.message}
                    style={{ marginBottom: 16 }}
                  />
                )}
                <Button
                  type="primary"
                  size="large"
                  block
                  loading={create.isPending}
                  disabled={!rooms.length || invalidRoom || invalidMeals || availability.isFetching}
                  onClick={() => form.submit()}
                >
                  Giữ {rooms.length || ''} phòng và tiếp tục
                </Button>
                <Typography.Paragraph type="secondary" style={{ marginTop: 12, marginBottom: 0 }}>
                  Một mã đơn và một lần thanh toán cho tất cả phòng. Chỉ giữ phòng sau khi bạn tiếp
                  tục.
                </Typography.Paragraph>
              </Card>
            </Col>
          </Row>
        </div>
      </main>
      <MainFooter />
    </div>
  );
}
