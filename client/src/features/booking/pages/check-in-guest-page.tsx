import {
  CheckCircleOutlined,
  IdcardOutlined,
  LoginOutlined,
  NumberOutlined,
} from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  App,
  Button,
  Card,
  Col,
  Descriptions,
  Form,
  Input,
  Radio,
  Row,
  Space,
  Tag,
  Typography,
  Tabs,
} from 'antd';
import { useStayPolicy } from '../../../hooks/use-stay-policy';
import { formatHotelDateTime } from '../../../utils/hotel-time';
import { useEffect, useEffectEvent, useRef, useState } from 'react';
import { HotelFloorMap } from '../../room-map/hotel-floor-map';
import { invalidateRoomMaps, useStaffMap } from '../../room-map/api';
import { refreshArrivalsAfterCheckIn } from '../../front-desk/api/front-desk-query';

import {
  checkInGuest,
  getAvailablePhysicalRooms,
  getCheckInBookingContext,
} from '../api/booking-api';
import type { CheckInGuestResponse, CheckInBookingContext } from '../types/booking';
import { ApiClientError } from '../../../types/api';
import { CheckInBookingSummary } from '../components/check-in-booking-summary';
import { getCheckInErrorMessage } from '../utils/check-in-error-mapper';
import './check-in-guest-page.css';

interface CheckInFormValues {
  bookingCode: string;
  roomId: string;
  earlyArrivalReason?: string;
}

type CheckInResult = CheckInGuestResponse & { recovered?: boolean };

function needsVerification(error: unknown) {
  return (
    !(error instanceof ApiClientError) ||
    error.kind === 'network' ||
    (error.status !== undefined && error.status >= 500) ||
    error.code === 'BOOKING_ALREADY_CHECKED_IN'
  );
}

function recordedCheckIn(booking: CheckInBookingContext | undefined): CheckInResult | null {
  if (
    booking?.bookingStatus !== 'CHECKED_IN' ||
    !booking.checkedInAt ||
    booking.assignedRoom?.status !== 'OCCUPIED'
  )
    return null;
  return {
    bookingId: booking.bookingId,
    bookingCode: booking.bookingCode,
    bookingStatus: 'CHECKED_IN',
    room: { ...booking.assignedRoom, status: 'OCCUPIED' },
    checkedInAt: booking.checkedInAt,
    recovered: true,
  };
}

export function CheckInGuestPage() {
  const { policy } = useStayPolicy();
  const { message } = App.useApp();
  const [form] = Form.useForm<CheckInFormValues>();
  const [initialBookingCode] = useState(() =>
    (new URLSearchParams(window.location.search).get('bookingCode') ?? '').trim().toUpperCase(),
  );
  const [result, setResult] = useState<CheckInResult | null>(null);
  const [searchedBookingCode, setSearchedBookingCode] = useState(initialBookingCode);
  const [verificationRequired, setVerificationRequired] = useState(false);
  const completedBookingId = useRef<string | null>(null);
  const client = useQueryClient();
  const [showMap, setShowMap] = useState(false);
  const [mapFloorNumber, setMapFloorNumber] = useState<number>();
  const [online, setOnline] = useState(window.navigator.onLine !== false);
  const selectedRoomId = Form.useWatch('roomId', form);
  const bookingQuery = useQuery({
    queryKey: ['check-in-context', searchedBookingCode],
    queryFn: () => getCheckInBookingContext(searchedBookingCode),
    enabled: !!searchedBookingCode,
    retry: false,
    staleTime: 0,
    refetchInterval: 15_000,
    refetchOnWindowFocus: 'always',
    refetchOnReconnect: 'always',
  });
  const visibleResult = result ?? recordedCheckIn(bookingQuery.data);
  const mapQuery = useStaffMap(showMap && !!searchedBookingCode && !!bookingQuery.data?.canCheckIn);
  useEffect(() => {
    const update = () => setOnline(window.navigator.onLine !== false);
    window.addEventListener('online', update);
    window.addEventListener('offline', update);
    return () => {
      window.removeEventListener('online', update);
      window.removeEventListener('offline', update);
    };
  }, []);

  const roomsQuery = useQuery({
    queryKey: ['available-physical-rooms', searchedBookingCode],
    queryFn: () => getAvailablePhysicalRooms(searchedBookingCode),
    enabled: !!searchedBookingCode && bookingQuery.isSuccess && bookingQuery.data.canCheckIn,
    retry: false,
    refetchInterval: 15_000,
    refetchOnWindowFocus: 'always',
    refetchOnReconnect: 'always',
  });
  const eligible = new Set((roomsQuery.data ?? []).map((room) => room.roomId));
  const eligibleMapFloors = (mapQuery.data?.floors ?? []).flatMap((floor) => {
    const eligibleRooms = floor.rooms.filter((room) => !!room.roomId && eligible.has(room.roomId));
    return eligibleRooms.length
      ? [{ ...floor, eligibleRooms, visibleKeys: new Set(eligibleRooms.map((room) => room.key)) }]
      : [];
  });
  const activeMapFloor =
    eligibleMapFloors.find((floor) => floor.floorNumber === mapFloorNumber) ??
    eligibleMapFloors.find((floor) =>
      floor.eligibleRooms.some((room) => room.roomId === selectedRoomId),
    ) ??
    eligibleMapFloors[0];
  const eligibleMapTypes = [
    ...new Set(
      eligibleMapFloors.flatMap((floor) => floor.eligibleRooms.map((room) => room.roomTypeName)),
    ),
  ];
  const dataReady =
    online &&
    !verificationRequired &&
    !visibleResult &&
    bookingQuery.isSuccess &&
    bookingQuery.data.canCheckIn &&
    roomsQuery.isSuccess &&
    (!showMap || mapQuery.isSuccess);
  useEffect(() => {
    if (
      selectedRoomId &&
      roomsQuery.data &&
      !roomsQuery.data.some((room) => room.roomId === selectedRoomId)
    )
      form.setFieldValue('roomId', undefined);
  }, [roomsQuery.data, selectedRoomId, form]);

  const completeCheckIn = (data: CheckInResult) => {
    if (completedBookingId.current === data.bookingId) return;
    completedBookingId.current = data.bookingId;
    setResult(data);
    setSearchedBookingCode('');
    setVerificationRequired(false);
    setMapFloorNumber(undefined);
    form.setFieldsValue({ bookingCode: '', roomId: undefined, earlyArrivalReason: undefined });
    void invalidateRoomMaps(client);
    void refreshArrivalsAfterCheckIn(client);
    void Promise.all(
      ['today-departures', 'booking-history', 'manager-dashboard', 'check-in-context'].map((key) =>
        client.invalidateQueries({ queryKey: [key] }),
      ),
    );
    void message.success(
      data.recovered
        ? `Đã xác minh khách đang ở phòng ${data.room.roomNumber}`
        : `Check-in thành công, phòng ${data.room.roomNumber}`,
    );
  };
  const checkInMutation = useMutation({
    retry: false,
    mutationFn: async ({
      bookingCode,
      roomId,
      earlyArrivalReason,
    }: CheckInFormValues): Promise<CheckInResult> => {
      const code = bookingCode.trim().toUpperCase();
      const reason = earlyArrivalReason?.trim();
      try {
        return await (reason
          ? checkInGuest(code, roomId.trim(), reason)
          : checkInGuest(code, roomId.trim()));
      } catch (error) {
        if (needsVerification(error)) {
          try {
            const booking = await getCheckInBookingContext(code);
            client.setQueryData(['check-in-context', code], booking);
            const recorded = recordedCheckIn(booking);
            if (recorded) return recorded;
          } catch {
            /* Keep the original error until the receptionist can verify again. */
          }
        }
        throw error;
      }
    },
    onSuccess: completeCheckIn,
    onMutate: () => setResult(null),
    onError: (error) => {
      setVerificationRequired(needsVerification(error));
      form.setFieldValue('roomId', undefined);
      void invalidateRoomMaps(client);
    },
  });

  const completeAutomaticRecovery = useEffectEvent(() => {
    if (!verificationRequired || !bookingQuery.isSuccess) return;
    const recorded = recordedCheckIn(bookingQuery.data);
    const currentCode = (form.getFieldValue('bookingCode') ?? '').trim().toUpperCase();
    if (
      !recorded ||
      recorded.bookingCode !== searchedBookingCode ||
      recorded.bookingCode !== currentCode
    )
      return;
    checkInMutation.reset();
    completeCheckIn(recorded);
  });
  useEffect(() => {
    completeAutomaticRecovery();
  }, [verificationRequired, bookingQuery.isSuccess, bookingQuery.data, searchedBookingCode]);

  const resetForm = () => {
    form.resetFields();
    form.setFieldsValue({ bookingCode: '', roomId: undefined, earlyArrivalReason: undefined });
    checkInMutation.reset();
    setSearchedBookingCode('');
    setMapFloorNumber(undefined);
    setResult(null);
    setVerificationRequired(false);
  };

  const verifyResult = async () => {
    const response = await bookingQuery.refetch();
    if (
      !response.isSuccess ||
      response.data.bookingCode !== (form.getFieldValue('bookingCode') ?? '').trim().toUpperCase()
    )
      return;
    checkInMutation.reset();
    const recorded = recordedCheckIn(response.data);
    if (recorded) completeCheckIn(recorded);
    else {
      setVerificationRequired(false);
      void message.info(
        'Đã tra cứu trạng thái booking. Tìm lại phòng khả dụng trước khi xác nhận.',
      );
    }
  };

  const loadAvailableRooms = async () => {
    const { bookingCode } = await form.validateFields(['bookingCode']);
    form.setFieldValue('roomId', undefined);
    setResult(null);
    setVerificationRequired(false);
    checkInMutation.reset();
    const normalizedCode = bookingCode.trim().toUpperCase();
    setSearchedBookingCode(normalizedCode);
    setMapFloorNumber(undefined);
    if (normalizedCode === searchedBookingCode) {
      void bookingQuery.refetch();
      void roomsQuery.refetch();
    }
  };

  return (
    <main className="check-in-page">
      <header className="check-in-page__header">
        <Typography.Text className="check-in-page__eyebrow">FRONT DESK</Typography.Text>
        <Typography.Title level={2}>Check-in khách</Typography.Title>
        <Typography.Paragraph type="secondary">
          Nhập mã booking đã xác nhận và phòng vật lý còn trống để hoàn tất nhận phòng.
        </Typography.Paragraph>
      </header>

      <Row gutter={[24, 24]} align="top">
        <Col
          xs={24}
          lg={visibleResult ? 11 : showMap ? 24 : 16}
          xl={visibleResult ? 10 : showMap ? 24 : 14}
        >
          <Card title="Thông tin check-in" className="check-in-page__card">
            <Form
              form={form}
              layout="vertical"
              requiredMark="optional"
              disabled={checkInMutation.isPending}
              initialValues={{
                bookingCode: initialBookingCode,
              }}
              onFinish={(values) => {
                if (
                  !checkInMutation.isPending &&
                  dataReady &&
                  eligible.has(values.roomId) &&
                  values.bookingCode.trim().toUpperCase() === searchedBookingCode
                )
                  checkInMutation.mutate(values);
              }}
            >
              <Form.Item
                name="bookingCode"
                label="Mã booking"
                normalize={(value: string) => value.toUpperCase()}
                rules={[
                  { required: true, whitespace: true, message: 'Vui lòng nhập mã booking' },
                  { max: 50, message: 'Mã booking không được vượt quá 50 ký tự' },
                ]}
              >
                <Input
                  prefix={<IdcardOutlined />}
                  placeholder="Ví dụ: BK20260819000001"
                  autoComplete="off"
                  size="large"
                  onChange={() => {
                    setSearchedBookingCode('');
                    setMapFloorNumber(undefined);
                    form.setFieldValue('roomId', undefined);
                    form.setFieldValue('earlyArrivalReason', undefined);
                    setResult(null);
                    checkInMutation.reset();
                    setVerificationRequired(false);
                  }}
                />
              </Form.Item>

              <Button
                className="check-in-page__load-rooms"
                onClick={() => void loadAvailableRooms()}
                disabled={verificationRequired}
                loading={bookingQuery.isFetching || roomsQuery.isFetching}
              >
                Tìm phòng khả dụng
              </Button>

              {bookingQuery.isError && (
                <Alert
                  className="check-in-page__error"
                  type="error"
                  showIcon
                  title="Không thể tra cứu booking"
                  description={getCheckInErrorMessage(bookingQuery.error)}
                  action={<Button onClick={() => void bookingQuery.refetch()}>Thử lại</Button>}
                />
              )}
              {bookingQuery.data && <CheckInBookingSummary booking={bookingQuery.data} />}
              {verificationRequired && !visibleResult && (
                <Alert
                  className="check-in-page__error"
                  type="warning"
                  showIcon
                  title="Chưa xác định được kết quả nhận phòng"
                  description="Kiểm tra lại trạng thái booking trước khi gửi yêu cầu nhận phòng tiếp theo."
                  action={
                    <Button loading={bookingQuery.isFetching} onClick={() => void verifyResult()}>
                      Tra cứu lại kết quả
                    </Button>
                  }
                />
              )}

              <Button
                type="link"
                disabled={!searchedBookingCode}
                onClick={() => setShowMap((value) => !value)}
              >
                {showMap ? 'Ẩn sơ đồ' : 'Xem sơ đồ để chọn phòng'}
              </Button>
              {!online && (
                <Alert
                  type="warning"
                  showIcon
                  title="Mất kết nối. Hãy kết nối và tải lại phòng trước khi xác nhận."
                />
              )}
              {showMap && searchedBookingCode && (
                <>
                  {mapQuery.isError && (
                    <Alert
                      type="error"
                      title="Không thể cập nhật sơ đồ"
                      action={<Button onClick={() => mapQuery.refetch()}>Thử lại</Button>}
                    />
                  )}
                  {roomsQuery.isSuccess && mapQuery.isSuccess && eligibleMapFloors.length > 0 && (
                    <Alert
                      className="check-in-page__map-context"
                      type="info"
                      showIcon
                      title={`Chọn phòng cho ${searchedBookingCode} · ${eligibleMapTypes.join(', ')}`}
                      description={`${roomsQuery.data.length} phòng trong danh sách khả dụng của booking. Chỉ mở các tầng có phòng này; ô mờ không thể chọn.`}
                    />
                  )}
                  {eligibleMapFloors.length > 0 && (
                    <Tabs
                      activeKey={activeMapFloor ? String(activeMapFloor.floorNumber) : undefined}
                      onChange={(key) => setMapFloorNumber(Number(key))}
                      items={eligibleMapFloors.map((floor) => ({
                        key: String(floor.floorNumber),
                        label: `${floor.name} (${floor.eligibleRooms.length} có thể nhận)`,
                        children: (
                          <HotelFloorMap
                            floor={floor}
                            showStatus
                            selectedKey={selectedRoomId}
                            visibleKeys={floor.visibleKeys}
                            onRoomClick={(room) => {
                              if (dataReady && room.roomId && eligible.has(room.roomId))
                                form.setFieldValue('roomId', room.roomId);
                            }}
                            reason={(room) =>
                              !dataReady
                                ? 'Cần cập nhật dữ liệu'
                                : room.operationalStatus !== 'AVAILABLE'
                                  ? 'Không thể nhận'
                                  : !room.roomId || !eligible.has(room.roomId)
                                    ? 'Không thuộc danh sách khả dụng của booking'
                                    : undefined
                            }
                          />
                        ),
                      }))}
                    />
                  )}
                  {roomsQuery.isSuccess &&
                    roomsQuery.data.length > 0 &&
                    mapQuery.isSuccess &&
                    !eligibleMapFloors.length && (
                      <Alert
                        type="info"
                        title="Phòng khả dụng chưa xuất hiện trên sơ đồ"
                        description="Chọn phòng trong danh sách Phòng vật lý bên dưới."
                      />
                    )}
                  <Typography.Paragraph type="secondary">
                    Chọn ô chưa giữ phòng. Bấm xác nhận check-in để gán phòng và nhận khách.
                  </Typography.Paragraph>
                </>
              )}

              {roomsQuery.isError && (
                <Alert
                  className="check-in-page__error"
                  type="error"
                  showIcon
                  message="Không thể tải phòng khả dụng"
                  description={getCheckInErrorMessage(roomsQuery.error)}
                />
              )}

              {roomsQuery.isSuccess && roomsQuery.data.length === 0 && (
                <Alert
                  className="check-in-page__error"
                  type="warning"
                  showIcon
                  message="Không còn phòng khả dụng thuộc hạng phòng đã đặt"
                />
              )}

              <Form.Item
                name="roomId"
                label="Phòng vật lý"
                extra="Danh sách được lọc theo hạng phòng của booking và trạng thái AVAILABLE."
                rules={[{ required: true, message: 'Vui lòng chọn phòng' }]}
              >
                <Radio.Group
                  className="check-in-page__room-options"
                  disabled={!dataReady || !roomsQuery.data?.length}
                  onChange={(event) =>
                    setMapFloorNumber(
                      roomsQuery.data?.find((room) => room.roomId === event.target.value)?.floor,
                    )
                  }
                >
                  {(roomsQuery.data ?? []).map((room) => (
                    <Radio.Button key={room.roomId} value={room.roomId}>
                      <span className="check-in-page__room-label">
                        <NumberOutlined />
                        <span>
                          Phòng {room.roomNumber} · Tầng {room.floor}
                        </span>
                        <CheckCircleOutlined className="check-in-page__room-selected-icon" />
                      </span>
                    </Radio.Button>
                  ))}
                </Radio.Group>
              </Form.Item>

              <Form.Item
                name="earlyArrivalReason"
                label="Lý do duyệt nhận phòng sớm (nếu có)"
                extra={`Nhận trước ${policy.checkInTime.slice(0, 5)} cần lễ tân xác nhận phòng đã sẵn sàng và nhập lý do. Khách đến sau nửa đêm được xử lý theo booking gốc đến ${policy.lateArrivalCutoff.slice(0, 5)}.`}
                rules={[
                  {
                    required: !!bookingQuery.data?.earlyArrivalRequired,
                    whitespace: true,
                    message: 'Nhập lý do duyệt nhận phòng sớm',
                  },
                  { max: 500, message: 'Lý do không được vượt quá 500 ký tự' },
                ]}
              >
                <Input.TextArea rows={2} maxLength={500} />
              </Form.Item>

              {checkInMutation.isError && !visibleResult && (
                <Alert
                  className="check-in-page__error"
                  type="error"
                  showIcon
                  message="Không thể check-in"
                  description={getCheckInErrorMessage(checkInMutation.error)}
                />
              )}

              <Space wrap>
                <Button
                  type="primary"
                  htmlType="submit"
                  size="large"
                  icon={<LoginOutlined />}
                  loading={checkInMutation.isPending}
                  disabled={!dataReady || !eligible.has(selectedRoomId)}
                >
                  Xác nhận check-in
                </Button>
                <Button size="large" onClick={resetForm} disabled={checkInMutation.isPending}>
                  Làm mới
                </Button>
              </Space>
            </Form>
          </Card>
        </Col>

        {visibleResult && (
          <Col xs={24} lg={13} xl={14}>
            <Card className="check-in-page__result">
              <Space orientation="vertical" size="large" className="check-in-page__result-content">
                <CheckCircleOutlined className="check-in-page__success-icon" />
                <div className="check-in-page__result-heading">
                  <Typography.Title level={3}>Check-in thành công</Typography.Title>
                  <Typography.Text type="secondary">
                    {visibleResult.recovered
                      ? 'Đã tra cứu và xác nhận khách đã nhận phòng.'
                      : 'Có thể bàn giao chìa khóa phòng cho khách.'}
                  </Typography.Text>
                </div>
                <Descriptions bordered column={1} size="middle" className="check-in-page__details">
                  <Descriptions.Item label="Mã booking">
                    {visibleResult.bookingCode}
                  </Descriptions.Item>
                  <Descriptions.Item label="Số phòng">
                    <Typography.Text strong>{visibleResult.room.roomNumber}</Typography.Text>
                  </Descriptions.Item>
                  <Descriptions.Item label="Tầng">{visibleResult.room.floor}</Descriptions.Item>
                  <Descriptions.Item label="Booking">
                    <Tag color="blue">Đã nhận phòng</Tag>
                  </Descriptions.Item>
                  <Descriptions.Item label="Phòng">
                    <Tag color="green">Đang sử dụng</Tag>
                  </Descriptions.Item>
                  <Descriptions.Item label="Thời gian">
                    {formatHotelDateTime(visibleResult.checkedInAt, policy.zone)}
                  </Descriptions.Item>
                </Descriptions>
              </Space>
            </Card>
          </Col>
        )}
      </Row>
    </main>
  );
}
