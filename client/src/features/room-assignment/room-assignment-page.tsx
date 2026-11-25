import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  App,
  Button,
  Card,
  Checkbox,
  DatePicker,
  Descriptions,
  Drawer,
  Empty,
  Input,
  Modal,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  Typography,
} from 'antd';
import { CalendarOutlined, HistoryOutlined, LockOutlined, ReloadOutlined } from '@ant-design/icons';
import { Link } from 'react-router-dom';
import dayjs from 'dayjs';
import { useAuth } from '../authentication/providers/auth-context';
import { hasRouteRole, ROUTE_ROLES } from '../../routes/route-access';
import { ROUTE_PATHS } from '../../routes/routePaths';
import { formatHotelDateTime, hotelToday } from '../../utils/hotel-time';
import { invalidateRoomMaps, roomStatuses } from '../room-map/api';
import {
  applyAssignment,
  getAssignmentPlan,
  assignBooking,
  getAssignmentCalendar,
  getAssignmentHistory,
  getLatestAssignment,
  suggestAssignment,
  type AssignmentBooking,
  type AssignmentHistory,
  type AssignmentPlan,
} from './api';
import './room-assignment-page.css';

const { Title, Text, Paragraph } = Typography;
const allocationLabels: Record<string, string> = {
  TENTATIVE: 'Dự kiến',
  LOCKED: 'Đã khóa',
  OCCUPIED: 'Đang ở',
};
const historyLabels: Record<string, string> = {
  ROOM_ASSIGNED: 'Gán phòng',
  ROOM_LOCKED: 'Khóa phòng',
  ROOM_UNLOCKED: 'Mở khóa phòng',
  PLAN_APPLIED: 'Áp dụng phương án',
  BOOKING_UPDATED: 'Cập nhật booking',
};
const errorMessage = (error: unknown) =>
  error instanceof Error ? error.message : 'Không thực hiện được thao tác. Vui lòng thử lại.';

function frozen(booking: AssignmentBooking, today: string) {
  return (
    !!booking.roomId &&
    (booking.status === 'CHECKED_IN' ||
      booking.allocationStatus === 'LOCKED' ||
      booking.start <= dayjs(today).add(1, 'day').format('YYYY-MM-DD'))
  );
}

function historyDescription(item: AssignmentHistory) {
  try {
    const details = JSON.parse(item.details);
    return (
      details.bookingCode ??
      details.after?.bookingCode ??
      details.before?.bookingCode ??
      'Lịch phòng'
    );
  } catch {
    return 'Lịch phòng';
  }
}

function historyRoomChange(
  item: AssignmentHistory,
  rooms: ReadonlyMap<string, { roomNumber: string }>,
) {
  try {
    const detail = JSON.parse(item.details);
    if (!('fromRoom' in detail) && !('toRoom' in detail)) return null;
    const label = (number: string | undefined, id: string | null) => {
      if (!id) return 'Chưa gán';
      const name = number || rooms.get(id)?.roomNumber;
      return name ? `Phòng ${name}` : 'Phòng đã xóa';
    };
    const before = label(detail.fromRoomNumber, detail.fromRoom);
    const after = label(detail.toRoomNumber, detail.toRoom);
    return before === after ? after : `${before} → ${after}`;
  } catch {
    return null;
  }
}

export function RoomAssignmentPage() {
  const { roles } = useAuth();
  const manager = hasRouteRole(roles, ROUTE_ROLES.management);
  const receptionist = hasRouteRole(roles, ROUTE_ROLES.reception);
  const { message } = App.useApp();
  const client = useQueryClient();
  const [from, setFrom] = useState(() => hotelToday().format('YYYY-MM-DD'));
  const [days, setDays] = useState(60);
  const [typeId, setTypeId] = useState<string>();
  const [draft, setDraft] = useState<AssignmentPlan>();
  const [confirmApply, setConfirmApply] = useState(false);
  const [historyOpen, setHistoryOpen] = useState(false);
  const [editor, setEditor] = useState<{ booking: AssignmentBooking; version: string }>();
  const [chosenRoom, setChosenRoom] = useState<string>();
  const [locked, setLocked] = useState(false);
  const [reason, setReason] = useState('');
  const [tick, setTick] = useState(() => Date.now());
  useEffect(() => {
    const timer = window.setInterval(() => setTick(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);
  const to = dayjs(from).add(days, 'day').format('YYYY-MM-DD');
  const calendar = useQuery({
    queryKey: ['room-assignment-calendar', from, to],
    queryFn: () => getAssignmentCalendar(from, to),
    refetchInterval: 15_000,
    refetchOnWindowFocus: 'always',
  });
  const data = calendar.data;
  const types = [...new Map(data?.rooms.map((r) => [r.roomTypeId, r.roomTypeName])).entries()];
  const selectedType = typeId ?? types[0]?.[0];
  const rooms = data?.rooms.filter((r) => r.roomTypeId === selectedType) ?? [];
  const bookings = data?.bookings.filter((b) => b.roomTypeId === selectedType) ?? [];
  const unassigned = bookings.filter((b) => b.status === 'CONFIRMED' && !b.roomId);
  const pending = bookings.filter((b) => b.status === 'PENDING').length;
  const latest = useQuery({
    queryKey: ['room-assignment-latest', selectedType, from, to],
    queryFn: () => getLatestAssignment(selectedType!, from, to),
    enabled: !!selectedType,
    refetchInterval: 30_000,
  });
  const history = useQuery({
    queryKey: ['room-assignment-history', selectedType],
    queryFn: () => getAssignmentHistory(selectedType!),
    enabled: historyOpen && !!selectedType,
  });
  const plan = draft ?? latest.data ?? undefined;
  const serverTime = data
    ? Date.parse(data.serverNow) + Math.max(0, tick - calendar.dataUpdatedAt)
    : tick;
  const stalePlan =
    !!plan &&
    (plan.sourceVersion !== data?.version ||
      serverTime >= Date.parse(plan.validUntil) ||
      plan.roomTypeId !== selectedType ||
      plan.from !== from ||
      plan.to !== to);
  const feasible = !!plan && ['OPTIMAL', 'FEASIBLE'].includes(plan.status);
  const canApply =
    feasible && !stalePlan && !plan?.applied && !!plan?.changes.length && calendar.isSuccess;
  const editorStale = !!editor && editor.version !== data?.version;
  const dates = Array.from({ length: days }, (_, i) => dayjs(from).add(i, 'day'));

  async function refresh() {
    await Promise.all([
      ...[
        'room-assignment-calendar',
        'room-assignment-latest',
        'room-assignment-history',
        'check-in-context',
        'suggested-room',
        'front-desk',
      ].map((key) => client.invalidateQueries({ queryKey: [key] })),
      invalidateRoomMaps(client),
    ]);
  }
  const suggest = useMutation({
    mutationFn: () => suggestAssignment(selectedType!, from, to),
    onSuccess: setDraft,
    onError: (e) => message.error(errorMessage(e)),
  });
  const apply = useMutation({
    mutationFn: (value: AssignmentPlan) => applyAssignment(value),
    onSuccess: async () => {
      setConfirmApply(false);
      setDraft(undefined);
      await refresh();
      message.success('Đã áp dụng lịch gán phòng.');
    },
    onError: async (e, value) => {
      setConfirmApply(false);
      const saved = await getAssignmentPlan(value.id).catch(() => undefined);
      if (saved?.applied) {
        setDraft(saved);
        await refresh();
        message.success('Phương án đã được áp dụng. Lịch phòng đã được cập nhật.');
        return;
      }
      await calendar.refetch();
      message.error(errorMessage(e));
    },
  });
  const assign = useMutation({
    mutationFn: () =>
      assignBooking(editor!.booking.id, {
        roomId: chosenRoom ?? null,
        locked,
        expectedVersion: editor!.version,
        reason,
        from,
        to,
      }),
    onSuccess: async () => {
      setEditor(undefined);
      setDraft(undefined);
      await refresh();
      message.success('Đã cập nhật gán phòng.');
    },
    onError: async (e) => {
      await calendar.refetch();
      message.error(errorMessage(e));
    },
  });

  function openBooking(booking: AssignmentBooking) {
    if (!data) return;
    setEditor({ booking, version: data.version });
    setChosenRoom(booking.roomId ?? undefined);
    setLocked(booking.allocationStatus === 'LOCKED');
    setReason('');
  }
  function changeScope() {
    setDraft(undefined);
    setConfirmApply(false);
    setEditor(undefined);
  }
  const editing = editor?.booking;
  const override =
    !!editing &&
    ((frozen(editing, data?.today ?? from) && editing.roomId !== (chosenRoom ?? null)) ||
      (editing.allocationStatus === 'LOCKED' && !locked));
  const editable = !!editing && editing.status === 'CONFIRMED';
  const needsManager = !!editing && frozen(editing, data?.today ?? from) && !manager;
  const roomById = new Map(data?.rooms.map((r) => [r.id, r]));

  return (
    <main className="assignment-page">
      <div className="assignment-heading">
        <div>
          <Title level={2}>Lịch gán phòng</Title>
          <Paragraph type="secondary">
            Xếp phòng liên tục cho kỳ ở, xem trước thay đổi rồi chủ động áp dụng.
          </Paragraph>
        </div>
        <Space wrap>
          <Link to={ROUTE_PATHS.roomMap}>
            <Button icon={<CalendarOutlined />}>Sơ đồ phòng</Button>
          </Link>
          <Button
            aria-label="Lịch sử gán phòng"
            icon={<HistoryOutlined />}
            disabled={!selectedType}
            onClick={() => setHistoryOpen(true)}
          >
            Lịch sử
          </Button>
        </Space>
      </div>
      <Card className="assignment-toolbar">
        <Space wrap size="middle">
          <label>
            Loại phòng{' '}
            <Select
              aria-label="Loại phòng"
              showSearch
              optionFilterProp="label"
              value={selectedType}
              onChange={(value) => {
                setTypeId(value);
                changeScope();
              }}
              options={types.map(([value, label]) => ({ value, label }))}
              style={{ minWidth: 220 }}
            />
          </label>
          <label>
            Từ ngày{' '}
            <DatePicker
              aria-label="Ngày bắt đầu lịch"
              allowClear={false}
              value={dayjs(from)}
              format="DD/MM/YYYY"
              onChange={(date) => {
                if (date) {
                  setFrom(date.format('YYYY-MM-DD'));
                  changeScope();
                }
              }}
            />
          </label>
          <label>
            Khoảng xem{' '}
            <Select
              aria-label="Khoảng xem"
              value={days}
              onChange={(value) => {
                setDays(value);
                changeScope();
              }}
              options={[30, 60, 90].map((value) => ({ value, label: `${value} ngày` }))}
            />
          </label>
          <Button
            aria-label="Làm mới lịch gán phòng"
            icon={<ReloadOutlined />}
            loading={calendar.isFetching}
            onClick={() => calendar.refetch()}
          >
            Làm mới
          </Button>
          <Button
            type="primary"
            loading={suggest.isPending}
            disabled={!selectedType || !calendar.isSuccess || apply.isPending || assign.isPending}
            onClick={() => suggest.mutate()}
          >
            Gợi ý xếp phòng
          </Button>
        </Space>
        <div className="assignment-legend">
          <Tag color="blue">Dự kiến</Tag>
          <Tag color="gold" icon={<LockOutlined />}>
            Đã khóa
          </Tag>
          <Tag color="green">Đang ở</Tag>
          <Text type="secondary">
            Giữ nguyên các gán trong hôm nay và ngày mai. Ngày trả phòng không chiếm thêm đêm.
          </Text>
        </div>
      </Card>
      {calendar.isError && (
        <Alert
          type="error"
          title="Không tải được lịch phòng"
          description={errorMessage(calendar.error)}
          action={<Button onClick={() => calendar.refetch()}>Thử lại</Button>}
        />
      )}
      {data?.warnings.map((w) => (
        <Alert key={w} type="warning" showIcon title={w} />
      ))}
      {calendar.isLoading ? (
        <Spin tip="Đang tải lịch phòng…">
          <div style={{ minHeight: 200 }} />
        </Spin>
      ) : (
        data && (
          <div className="assignment-workspace">
            <Card
              title={
                <Space>
                  <span>Phòng theo ngày</span>
                  <Tag>{rooms.length} phòng</Tag>
                </Space>
              }
              className="assignment-calendar-card"
            >
              {!rooms.length ? (
                <Empty description="Loại phòng chưa có phòng vật lý." />
              ) : (
                <div
                  className="assignment-calendar-scroll"
                  tabIndex={0}
                  aria-label="Lịch phòng, cuộn ngang để xem các ngày tiếp theo"
                >
                  <div
                    className="assignment-calendar"
                    style={{ width: `calc(var(--assignment-room-column) + ${days * 52}px)` }}
                    role="table"
                    aria-label="Lịch gán phòng theo ngày"
                  >
                    <div className="assignment-calendar-row assignment-calendar-header" role="row">
                      <div className="assignment-room-label" role="columnheader">
                        Phòng
                      </div>
                      <div
                        className="assignment-day-grid"
                        style={{ gridTemplateColumns: `repeat(${days}, 52px)` }}
                      >
                        {dates.map((date) => (
                          <div
                            role="columnheader"
                            className={
                              date.format('YYYY-MM-DD') === data.today ? 'assignment-today' : ''
                            }
                            key={date.format('YYYY-MM-DD')}
                          >
                            <strong>{date.format('DD/MM')}</strong>
                            <span>{['CN', 'T2', 'T3', 'T4', 'T5', 'T6', 'T7'][date.day()]}</span>
                          </div>
                        ))}
                      </div>
                    </div>
                    {rooms.map((room) => (
                      <div className="assignment-calendar-row" role="row" key={room.id}>
                        <div className="assignment-room-label" role="rowheader">
                          <strong>{room.roomNumber}</strong>
                          <span>Tầng {room.floor}</span>
                          <Tag>{roomStatuses[room.status]}</Tag>
                          {bookings.some((b) => b.roomId === room.id && b.overdue) && (
                            <Tag color="red">Quá giờ trả phòng</Tag>
                          )}
                        </div>
                        <div
                          className={`assignment-day-grid assignment-room-track ${['MAINTENANCE', 'OUT_OF_SERVICE'].includes(room.status) ? 'assignment-room-blocked' : ''}`}
                          style={{ gridTemplateColumns: `repeat(${days}, 52px)` }}
                          role="cell"
                        >
                          {['MAINTENANCE', 'OUT_OF_SERVICE'].includes(room.status) && (
                            <span className="assignment-block-label">
                              {roomStatuses[room.status]} · Chưa có ngày mở lại
                            </span>
                          )}
                          {bookings
                            .filter((b) => b.roomId === room.id && b.status !== 'PENDING')
                            .map((b) => {
                              const left = Math.max(0, dayjs(b.start).diff(dayjs(from), 'day'));
                              const right = Math.min(days, dayjs(b.end).diff(dayjs(from), 'day'));
                              if (right <= left) return null;
                              return (
                                <button
                                  key={b.id}
                                  className={`assignment-booking assignment-booking--${b.allocationStatus?.toLowerCase() ?? 'tentative'}`}
                                  style={{
                                    gridColumn: `${left + 1} / ${right + 1}`,
                                    gridRow:
                                      b.overdue ||
                                      !bookings.some(
                                        (other) => other.roomId === room.id && other.overdue,
                                      )
                                        ? 1
                                        : 2,
                                  }}
                                  onClick={() => openBooking(b)}
                                  aria-label={`Booking ${b.bookingCode}, phòng ${room.roomNumber}`}
                                  title={`${b.bookingCode} · ${b.guestName} · ${dayjs(b.start).format('DD/MM')} đến trước ${dayjs(b.scheduledEnd ?? b.end).format('DD/MM')}${b.overdue ? ' · Đang ở quá hạn, chưa giải phóng phòng' : ''}`}
                                >
                                  <strong>{b.bookingCode}</strong>
                                  <span>{b.guestName || 'Chưa có tên khách'}</span>
                                  <small>
                                    {allocationLabels[b.allocationStatus ?? 'TENTATIVE']}
                                    {b.overdue ? ' · Quá hạn trả phòng' : ''}
                                    {frozen(b, data.today) && b.allocationStatus === 'TENTATIVE'
                                      ? ' · Giữ ổn định'
                                      : ''}
                                  </small>
                                </button>
                              );
                            })}
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </Card>
            <Card
              className="assignment-unassigned"
              title={
                <Space>
                  <span>Chưa gán phòng</span>
                  <Tag color={unassigned.length ? 'orange' : 'default'}>{unassigned.length}</Tag>
                </Space>
              }
            >
              {!!pending && (
                <Paragraph type="secondary">
                  {pending} booking đang chờ thanh toán còn hạn đã được tính khi kiểm tra khả năng
                  xếp.
                </Paragraph>
              )}
              {!unassigned.length ? (
                <Empty
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                  description="Các booking đã xác nhận đều có phòng dự kiến."
                />
              ) : (
                <div className="assignment-unassigned-list">
                  {unassigned.map((b) => (
                    <button
                      key={b.id}
                      onClick={() => openBooking(b)}
                      className="assignment-unassigned-item"
                    >
                      <strong>{b.bookingCode}</strong>
                      <span>{b.guestName || 'Chưa có tên khách'}</span>
                      <span>
                        {dayjs(b.start).format('DD/MM')} – {dayjs(b.end).format('DD/MM/YYYY')}
                      </span>
                      {b.orderRoomNumber && <Tag>Phòng {b.orderRoomNumber} trong đơn</Tag>}
                    </button>
                  ))}
                </div>
              )}
            </Card>
          </div>
        )
      )}
      {plan && (
        <Card
          title="Phương án đề xuất"
          extra={
            <Tag color={feasible ? 'blue' : 'orange'}>
              {plan.applied
                ? 'Đã áp dụng'
                : plan.status === 'OPTIMAL'
                  ? 'Đã tối ưu theo tiêu chí'
                  : feasible
                    ? 'Có phương án hợp lệ'
                    : plan.status === 'INFEASIBLE'
                      ? 'Không xếp được với ràng buộc hiện tại'
                      : 'Chưa có kết luận'}
            </Tag>
          }
        >
          {stalePlan && (
            <Alert
              showIcon
              type="warning"
              title="Lịch đã cập nhật hoặc gợi ý hết hạn. Vui lòng lấy gợi ý mới."
            />
          )}
          {plan.warnings.map((w) => (
            <Alert key={w} type="warning" title={w} />
          ))}
          <div className="assignment-metrics">
            <div>
              <span>Khoảng trống 1 đêm</span>
              <strong>
                {plan.before.orphanOne} → {plan.after?.orphanOne ?? '—'}
              </strong>
            </div>
            <div>
              <span>Khoảng trống 2 đêm</span>
              <strong>
                {plan.before.orphanTwo} → {plan.after?.orphanTwo ?? '—'}
              </strong>
            </div>
            <div>
              <span>Booking đổi phòng</span>
              <strong>{plan.after?.changed ?? '—'}</strong>
            </div>
            <div>
              <span>Booking chưa gán</span>
              <strong>
                {plan.before.unassigned} → {plan.after?.unassigned ?? '—'}
              </strong>
            </div>
          </div>
          <Table
            rowKey="bookingId"
            size="small"
            dataSource={plan.changes}
            pagination={{ pageSize: 10 }}
            scroll={{ x: 760 }}
            locale={{
              emptyText: feasible
                ? 'Lịch hiện tại đã phù hợp; không có thay đổi cần áp dụng.'
                : 'Giữ lịch hiện tại. Xem lại các phòng cố định hoặc thử tính lại.',
            }}
            columns={[
              { title: 'Booking', dataIndex: 'bookingCode' },
              {
                title: 'Kỳ ở',
                render: (_, b) =>
                  `${dayjs(b.start).format('DD/MM')} – ${dayjs(b.end).format('DD/MM/YYYY')}`,
              },
              { title: 'Phòng trước', render: (_, b) => b.fromRoomNumber ?? 'Chưa gán' },
              { title: 'Phòng đề xuất', dataIndex: 'toRoomNumber' },
              { title: 'Lý do', dataIndex: 'reason' },
            ]}
          />
          <div className="assignment-plan-footer">
            <Text type="secondary">
              Gợi ý có hiệu lực đến {formatHotelDateTime(plan.validUntil)}. Chưa thay đổi lịch cho
              đến khi áp dụng.
            </Text>
            <Button
              type="primary"
              disabled={!canApply || suggest.isPending || apply.isPending}
              onClick={() => setConfirmApply(true)}
            >
              Áp dụng phương án
            </Button>
          </div>
        </Card>
      )}
      <Modal
        title="Áp dụng lịch gán phòng?"
        open={confirmApply}
        onCancel={() => !apply.isPending && setConfirmApply(false)}
        onOk={() => plan && apply.mutate(plan)}
        confirmLoading={apply.isPending}
        okButtonProps={{ disabled: !canApply }}
        okText="Xác nhận áp dụng"
        cancelText="Xem lại"
      >
        <Paragraph>
          Phương án sẽ cập nhật {plan?.changes.length ?? 0} booking. Các phòng đã khóa và khách đang
          ở được giữ nguyên. Hệ thống kiểm tra lại toàn bộ lịch trước khi lưu.
        </Paragraph>
      </Modal>
      <Modal
        title={editing ? `Gán phòng · ${editing.bookingCode}` : 'Gán phòng'}
        open={!!editor}
        onCancel={() => !assign.isPending && setEditor(undefined)}
        footer={
          editable ? (
            <Space>
              <Button onClick={() => setEditor(undefined)} disabled={assign.isPending}>
                Đóng
              </Button>
              <Button
                type="primary"
                loading={assign.isPending}
                disabled={editorStale || (override && (!manager || !reason.trim()))}
                onClick={() => assign.mutate()}
              >
                Lưu gán phòng
              </Button>
            </Space>
          ) : null
        }
      >
        {editing && (
          <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
            <Descriptions
              column={1}
              size="small"
              items={[
                {
                  key: 'guest',
                  label: 'Khách',
                  children: editing.guestName || 'Chưa có thông tin',
                },
                {
                  key: 'dates',
                  label: 'Kỳ ở',
                  children: `${dayjs(editing.start).format('DD/MM/YYYY')} – ${dayjs(editing.end).format('DD/MM/YYYY')}`,
                },
                {
                  key: 'state',
                  label: 'Gán phòng',
                  children: allocationLabels[editing.allocationStatus ?? ''] ?? 'Chưa gán',
                },
                ...(editing.orderRoomNumber
                  ? [
                      {
                        key: 'order',
                        label: 'Đơn nhiều phòng',
                        children: `Phòng thứ ${editing.orderRoomNumber}`,
                      },
                    ]
                  : []),
              ]}
            />
            {editorStale && (
              <Alert
                type="warning"
                title="Lịch đã cập nhật. Đóng hộp thoại và mở lại booking trước khi lưu."
              />
            )}
            {editable ? (
              <>
                {needsManager && (
                  <Alert
                    type="info"
                    title="Phòng đã cố định. Quản lý cần duyệt nếu muốn đổi phòng hoặc mở khóa."
                  />
                )}
                <label className="assignment-editor-label">
                  Phòng cho toàn bộ kỳ ở
                  <Select
                    aria-label="Phòng cho toàn bộ kỳ ở"
                    allowClear
                    placeholder="Chưa gán phòng"
                    value={chosenRoom}
                    disabled={needsManager || editorStale}
                    onChange={(value) => {
                      setChosenRoom(value);
                      if (!value) setLocked(false);
                    }}
                    options={rooms
                      .filter((r) => !['MAINTENANCE', 'OUT_OF_SERVICE'].includes(r.status))
                      .map((r) => ({
                        value: r.id,
                        label: `${r.roomNumber} · Tầng ${r.floor} · ${roomStatuses[r.status]}`,
                      }))}
                  />
                </label>
                <Checkbox
                  checked={locked}
                  disabled={
                    !chosenRoom ||
                    (editing.allocationStatus === 'LOCKED' && !manager) ||
                    editorStale
                  }
                  onChange={(event) => setLocked(event.target.checked)}
                >
                  Khóa phòng cụ thể cho booking này
                </Checkbox>
                <label className="assignment-editor-label">
                  Lý do {override ? '(bắt buộc)' : '(nếu có)'}
                  <Input.TextArea
                    aria-label="Lý do thay đổi gán phòng"
                    maxLength={500}
                    showCount
                    value={reason}
                    onChange={(e) => setReason(e.target.value)}
                    rows={3}
                  />
                </label>
                {chosenRoom && roomById.get(chosenRoom)?.status !== 'AVAILABLE' && (
                  <Alert
                    type="warning"
                    title="Phòng chưa sẵn sàng ở thời điểm hiện tại. Cần kiểm tra lại khi nhận khách."
                  />
                )}
                {receptionist && editing.start <= (data?.today ?? from) && (
                  <Link
                    to={`${ROUTE_PATHS.frontDeskCheckIn}?bookingCode=${encodeURIComponent(editing.bookingCode)}`}
                  >
                    <Button>Đi đến nhận phòng</Button>
                  </Link>
                )}
              </>
            ) : (
              <Alert type="info" title="Khách đang ở. Chức năng này giữ nguyên phòng thực tế." />
            )}
          </Space>
        )}
      </Modal>
      <Drawer
        title="Lịch sử gán phòng"
        open={historyOpen}
        onClose={() => setHistoryOpen(false)}
        size="large"
      >
        {history.isError ? (
          <Alert
            type="error"
            title={errorMessage(history.error)}
            action={<Button onClick={() => history.refetch()}>Thử lại</Button>}
          />
        ) : (
          <Spin spinning={history.isLoading}>
            <div className="assignment-history">
              {history.data?.length ? (
                history.data.map((item) => (
                  <article key={item.id}>
                    <Text strong>{historyLabels[item.action] ?? 'Cập nhật lịch'}</Text>
                    <Paragraph>{historyDescription(item)}</Paragraph>
                    <Paragraph>{historyRoomChange(item, roomById)}</Paragraph>
                    <Text type="secondary">
                      {formatHotelDateTime(item.createdAt)} · {item.actorName ?? 'Hệ thống'}
                    </Text>
                    {item.reason && <Paragraph>{item.reason}</Paragraph>}
                  </article>
                ))
              ) : (
                <Empty description="Chưa có thao tác gán phòng." />
              )}
            </div>
          </Spin>
        )}
      </Drawer>
    </main>
  );
}
