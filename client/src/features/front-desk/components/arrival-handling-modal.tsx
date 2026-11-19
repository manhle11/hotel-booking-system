import { useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  App,
  Button,
  Checkbox,
  Collapse,
  DatePicker,
  Descriptions,
  Input,
  InputNumber,
  Modal,
  Radio,
  Select,
  Space,
  Spin,
  Tag,
  Table,
  Typography,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { formatHotelDateTime } from '../../../utils/hotel-time';
import { hasRouteRole, ROUTE_ROLES } from '../../../routes/route-access';
import { useAuth } from '../../authentication/providers/auth-context';
import {
  getArrivalHandling,
  holdArrival,
  markNoShow,
  releaseArrivalHold,
  updateArrivalEstimate,
  recordContactResult,
  decideArrivalHold,
} from '../api/arrival-handling-api';
import { refreshArrivalsAfterCheckIn } from '../api/front-desk-query';
import type { ArrivalHandling, ContactChannel, ContactOutcome } from '../types/arrival-handling';
import type { TodayArrival } from '../types/today-arrivals';
import { PolicyFeeReduction } from './policy-fee-reduction';
import { PolicyFeeCollection } from './policy-fee-collection';
import { BookingChangeRequestForm } from './booking-change-request-form';
import { getBookingChanges, type ChangeType } from '../api/booking-change-api';
import { channelLabels, outcomeLabels, nextArrivalAction } from '../arrival-presentation';

const money = (n: number) =>
  new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(n);
export type ArrivalHandlingSubject = Pick<
  TodayArrival,
  'bookingCode' | 'primaryGuest' | 'roomType' | 'adultCount' | 'childCount'
>;
type Action =
  'summary' | 'estimate' | 'contact' | 'request-hold' | 'decide-hold' | 'release' | 'settlement';
const titles: Record<Action, string> = {
  summary: 'Theo dõi khách đến',
  estimate: 'Cập nhật giờ khách đến',
  contact: 'Ghi nhận liên hệ',
  'request-hold': 'Xin nhận sau hạn',
  'decide-hold': 'Xem xét nhận sau hạn',
  release: 'Kết thúc hạn nhận muộn',
  settlement: 'Quyết toán khách không đến',
};
export function ArrivalHandlingModal({
  arrival,
  onClose,
}: {
  arrival: ArrivalHandlingSubject;
  onClose: () => void;
}) {
  const { roles } = useAuth();
  const manager = hasRouteRole(roles, ROUTE_ROLES.management);
  const reception = hasRouteRole(roles, ROUTE_ROLES.reception);
  const client = useQueryClient();
  const { message } = App.useApp();
  const key = ['arrival-handling', arrival.bookingCode];
  const query = useQuery({
    queryKey: key,
    queryFn: () => getArrivalHandling(arrival.bookingCode),
    retry: false,
    staleTime: 0,
  });
  const changes = useQuery({
    queryKey: ['booking-changes', arrival.bookingCode],
    queryFn: () => getBookingChanges(arrival.bookingCode),
    retry: false,
  });
  const [action, setAction] = useState<Action>('summary');
  const [scopeMode, setScopeMode] = useState<'ROOM' | 'SELECTED' | null>(null);
  const [selectedCodes, setSelectedCodes] = useState<string[]>([arrival.bookingCode]);
  const [changeType, setChangeType] = useState<ChangeType | null>(null);
  const [expected, setExpected] = useState<Dayjs | null>(null);
  const [until, setUntil] = useState<Dayjs | null>(null);
  const [note, setNote] = useState('');
  const [reason, setReason] = useState('');
  const [channel, setChannel] = useState<ContactChannel>('PHONE');
  const [outcome, setOutcome] = useState<ContactOutcome | null>(null);
  const [feeMode, setFeeMode] = useState<'standard' | 'reduce' | 'waive'>('standard');
  const [fee, setFee] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);
  const [uncertain, setUncertain] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);
  const data = query.data;
  const progress = data?.progress;
  const pending = changes.data?.find((r) => r.status === 'PENDING');
  const canEdit = data?.bookingStatus === 'CONFIRMED';
  const closed = data?.bookingStatus === 'NO_SHOW';
  const chosenFee = feeMode === 'waive' ? 0 : feeMode === 'reduce' ? fee : (data?.standardFee ?? 0);
  const available =
    data?.availableAmount ??
    (data ? data.refundAmount + data.feeAmount - data.outstandingAmount : 0);
  const validFee =
    chosenFee !== null &&
    chosenFee >= 0 &&
    chosenFee <= (data?.standardFee ?? 0) &&
    (feeMode !== 'reduce' || (chosenFee > 0 && chosenFee < (data?.standardFee ?? 0)));
  const needsFeeReason = feeMode !== 'standard' || !!data?.legacyPolicy;
  const time = (value: string) => formatHotelDateTime(value, data?.zone);
  const iso = (value: Dayjs) =>
    dayjs.tz(value.format('YYYY-MM-DD HH:mm:ss'), data!.zone).toISOString();
  const deadline = progress?.receptionDeadline ?? data?.cutoff;
  const orderRooms = data?.orderRooms ?? [];
  const grouped = orderRooms.length > 1;
  const scopeAction =
    ['estimate', 'contact', 'request-hold', 'decide-hold', 'release'].includes(action) &&
    !(action === 'contact' && (outcome === 'CANCELLATION' || outcome === 'RESCHEDULE'));
  const selectableRooms = orderRooms.filter(
    (r) =>
      r.canManageArrival &&
      (action !== 'decide-hold' ||
        (!!r.holdRequestedUntil &&
          r.holdRequestedUntil !== r.holdUntil &&
          r.holdRequestedUntil === data?.holdRequestedUntil)) &&
      (action !== 'release' || (!!r.holdUntil && dayjs(r.holdUntil).isAfter(data?.serverNow))),
  );
  const chosenCodes = scopeMode === 'SELECTED' ? selectedCodes : [arrival.bookingCode];
  const selectionValid =
    chosenCodes.length > 0 &&
    chosenCodes.includes(arrival.bookingCode) &&
    (!grouped || chosenCodes.every((code) => selectableRooms.some((r) => r.bookingCode === code)));
  const scopeReady = !scopeAction || !grouped || (scopeMode !== null && selectionValid);
  const scopeCodes = scopeAction && grouped ? chosenCodes : undefined;
  const scopeBody = scopeCodes ? { bookingCodes: scopeCodes } : {};
  const scopeArgs: [] | [string[]] = scopeCodes ? [scopeCodes] : [];
  const scopeDescription =
    chosenCodes.length > 1
      ? `${chosenCodes.length} phòng đã chọn trong đơn ${data?.orderCode}`
      : `riêng phòng ${arrival.bookingCode}`;
  const expectedAfterDeadline =
    expected &&
    (grouped
      ? orderRooms
          .filter((r) => chosenCodes.includes(r.bookingCode))
          .some((r) => !dayjs(iso(expected)).isBefore(r.receptionDeadline))
      : deadline && !dayjs(iso(expected)).isBefore(deadline));
  function begin(next: Action) {
    setAction(next);
    setScopeMode(grouped ? null : 'ROOM');
    setSelectedCodes([arrival.bookingCode]);
    setNote('');
    setReason('');
    setFailure(null);
    setFeeMode('standard');
    setFee(null);
    setUntil(null);
    setExpected(progress?.expectedAt ? dayjs(progress.expectedAt).tz(data?.zone) : null);
    setChannel(progress?.lastContactChannel ?? 'PHONE');
    setOutcome(
      next === 'settlement'
        ? progress?.lastContactOutcome === 'NO_ANSWER'
          ? 'NO_ANSWER'
          : null
        : null,
    );
  }
  async function changed(result: ArrivalHandling) {
    client.setQueryData(key, result);
    await refreshArrivalsAfterCheckIn(client);
    void client.invalidateQueries({
      queryKey: ['arrival-handling'],
      predicate: (query) => query.queryKey[1] !== arrival.bookingCode,
    });
    for (const prefix of [
      'room-map',
      'room-types',
      'booking-history',
      'booking-detail',
      'booking-order',
      'booking-order-history',
      'refund-requests',
      'dashboard-report',
    ])
      void client.invalidateQueries({ queryKey: [prefix] });
  }
  async function verify() {
    setBusy(true);
    try {
      await changed(await getArrivalHandling(arrival.bookingCode));
      await changes.refetch();
      setUncertain(false);
      setFailure(null);
      setFee(null);
      setFeeMode('standard');
      setAction('summary');
    } catch {
      setUncertain(true);
      setFailure('Chưa xác minh được kết quả. Tra cứu lại trước khi tiếp tục.');
    } finally {
      setBusy(false);
    }
  }
  async function act(command: () => Promise<ArrivalHandling>, success: string) {
    if (busy || uncertain) return;
    if (!scopeReady) return;
    setBusy(true);
    setFailure(null);
    try {
      await changed(await command());
      setAction('summary');
      void message.success(success);
    } catch (error) {
      setFailure(error instanceof Error ? error.message : 'Không thực hiện được thao tác.');
      setUncertain(true);
      try {
        const verified = await getArrivalHandling(arrival.bookingCode);
        await changed(verified);
        setUncertain(false);
        setFee(null);
        setFeeMode('standard');
        if (verified.bookingStatus !== 'CONFIRMED') {
          setAction('summary');
          setFailure(null);
        }
      } catch {
        setFailure('Chưa xác minh được kết quả. Tra cứu lại trước khi tiếp tục.');
      }
    } finally {
      setBusy(false);
    }
  }
  const disabled = busy || uncertain || !scopeReady;
  const contactInputs = (settlement = false) => (
    <>
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
          gap: 12,
        }}
      >
        <Space orientation="vertical" size={4}>
          <Typography.Text strong>Kênh liên hệ</Typography.Text>
          <Select
            aria-label="Kênh liên hệ"
            style={{ width: '100%' }}
            value={channel}
            disabled={disabled}
            onChange={setChannel}
            options={Object.entries(channelLabels).map(([value, label]) => ({ value, label }))}
          />
        </Space>
        <Space orientation="vertical" size={4}>
          <Typography.Text strong>Kết quả liên hệ</Typography.Text>
          <Select
            aria-label="Kết quả liên hệ"
            style={{ width: '100%' }}
            placeholder="Chọn kết quả"
            value={outcome}
            disabled={disabled}
            onChange={setOutcome}
            options={Object.entries(outcomeLabels)
              .filter(([value]) => !settlement || value === 'NO_ANSWER' || value === 'UNDECIDED')
              .map(([value, label]) => ({ value, label }))}
          />
        </Space>
      </div>
      <Input.TextArea
        aria-label="Ghi chú liên hệ"
        rows={2}
        maxLength={500}
        value={note}
        disabled={disabled}
        onChange={(e) => setNote(e.target.value)}
        placeholder={
          settlement
            ? 'Chi tiết liên hệ làm căn cứ xác nhận không đến (bắt buộc)'
            : 'Chi tiết bổ sung (nếu có)'
        }
      />
    </>
  );
  const dateField = (label: string, value: Dayjs | null, update: (v: Dayjs | null) => void) => (
    <>
      <Typography.Text strong>{label}</Typography.Text>
      <DatePicker
        aria-label={label}
        showTime={{ format: 'HH:mm' }}
        format="DD/MM/YYYY HH:mm"
        value={value}
        onChange={update}
        disabled={disabled}
      />
      <Typography.Text type="secondary">Giờ khách sạn · {data?.zone}</Typography.Text>
    </>
  );
  const replacement = `/bookings/new?roomTypeId=${encodeURIComponent(arrival.roomType.id)}&checkIn=${data ? dayjs(data.serverNow).tz(data.zone).format('YYYY-MM-DD') : ''}&checkOut=${data ? dayjs(data.serverNow).tz(data.zone).add(1, 'day').format('YYYY-MM-DD') : ''}&adults=${arrival.adultCount}&children=${arrival.childCount}&replacementFor=${encodeURIComponent(arrival.bookingCode)}`;
  return (
    <Modal
      open
      centered
      styles={{ body: { maxHeight: 'calc(100dvh - 220px)', overflowY: 'auto' } }}
      width={760}
      title={`${titles[action]} · ${arrival.bookingCode}`}
      footer={
        <Space>
          {action !== 'summary' && (
            <Button disabled={busy || uncertain} onClick={() => begin('summary')}>
              Quay lại thông tin khách
            </Button>
          )}
          <Button disabled={busy} onClick={onClose}>
            Đóng
          </Button>
        </Space>
      }
      onCancel={() => {
        if (!busy) onClose();
      }}
      mask={{ closable: !busy }}
    >
      <Space
        orientation="vertical"
        size={action === 'settlement' ? 'small' : 'middle'}
        style={{ width: '100%' }}
      >
        {query.isLoading && <Spin />}
        {(query.isError || failure) && (
          <Alert type="error" title={failure ?? query.error?.message} />
        )}
        <Button
          loading={busy}
          aria-label={uncertain ? 'Tra cứu lại kết quả' : 'Cập nhật tình trạng'}
          onClick={() => void verify()}
        >
          {uncertain ? 'Tra cứu lại kết quả' : 'Cập nhật tình trạng'}
        </Button>
        {data && (
          <>
            {scopeAction && grouped && (
              <Space orientation="vertical" style={{ width: '100%' }}>
                <Typography.Text strong>Phạm vi áp dụng trong đơn {data.orderCode}</Typography.Text>
                <Radio.Group
                  aria-label="Phạm vi xử lý khách"
                  value={scopeMode}
                  disabled={busy || uncertain}
                  onChange={(e) => {
                    setScopeMode(e.target.value);
                    setSelectedCodes(
                      e.target.value === 'SELECTED'
                        ? selectableRooms.map((r) => r.bookingCode)
                        : [arrival.bookingCode],
                    );
                  }}
                  options={[
                    { value: 'ROOM', label: `Chỉ phòng ${arrival.bookingCode}` },
                    { value: 'SELECTED', label: 'Các phòng cùng đơn đã chọn' },
                  ]}
                />
                {scopeMode === 'SELECTED' && (
                  <>
                    <Checkbox.Group
                      aria-label="Các phòng áp dụng"
                      value={selectedCodes}
                      onChange={(values) => setSelectedCodes(values.map(String))}
                      options={orderRooms.map((r) => ({
                        value: r.bookingCode,
                        label: `${r.bookingCode} · ${r.roomTypeName}${!r.canManageArrival ? ' · đã nhận/kết thúc' : !selectableRooms.includes(r) ? ' · khác đề nghị/hạn cần xử lý' : ''}`,
                        disabled:
                          busy ||
                          uncertain ||
                          r.bookingCode === arrival.bookingCode ||
                          !selectableRooms.includes(r),
                      }))}
                    />
                    <Typography.Text type="secondary">
                      Phòng đã nhận, đã hủy hoặc đã đóng không đến không nằm trong nhóm cập nhật.
                      Booking đang mở luôn thuộc phạm vi đã chọn.
                    </Typography.Text>
                  </>
                )}
                <Alert
                  type={scopeReady ? 'info' : 'warning'}
                  title={
                    scopeMode === null
                      ? 'Chọn phạm vi trước khi nhập và lưu'
                      : `Sẽ áp dụng ${scopeDescription}`
                  }
                  description={
                    scopeMode === 'SELECTED'
                      ? 'Chỉ các booking được đánh dấu ở trên sẽ thay đổi.'
                      : 'Các phòng còn lại giữ trạng thái và giờ đến riêng.'
                  }
                />
              </Space>
            )}
            {action === 'settlement' && (
              <Alert
                type="info"
                title={`Phạm vi: riêng phòng ${arrival.bookingCode}`}
                description="Quyết toán không đến và nhận phòng xét theo từng phòng thực tế."
              />
            )}
            {action === 'summary' ? (
              <>
                <Space wrap>
                  <Tag
                    color={
                      canEdit
                        ? progress?.group === 'ACTION_REQUIRED'
                          ? 'orange'
                          : 'blue'
                        : 'green'
                    }
                  >
                    {canEdit
                      ? progress?.group === 'ACTION_REQUIRED'
                        ? 'Cần xử lý'
                        : 'Chờ khách đến'
                      : closed
                        ? 'Đã đóng không đến'
                        : data.bookingStatus === 'CANCELLED'
                          ? 'Đã hủy'
                          : 'Đã nhận / trả phòng'}
                  </Tag>
                  <Typography.Text strong>Đã thanh toán {money(data.paidAmount)}</Typography.Text>
                </Space>
                <Descriptions bordered column={1} size="small">
                  <Descriptions.Item label="Khách">
                    {arrival.primaryGuest?.fullName ?? 'Chưa có thông tin'} ·{' '}
                    {arrival.primaryGuest?.phone ?? arrival.primaryGuest?.email}
                  </Descriptions.Item>
                  <Descriptions.Item label="Khách hẹn đến">
                    {progress?.expectedAt ? time(progress.expectedAt) : 'Chưa ghi nhận giờ hẹn'}
                  </Descriptions.Item>
                  <Descriptions.Item label="Hạn tiếp nhận">
                    Trước {time(deadline!)}
                  </Descriptions.Item>
                  <Descriptions.Item label="Trả phòng đã đặt">
                    {time(data.departureDeadline)}
                  </Descriptions.Item>
                </Descriptions>
                {grouped && (
                  <Collapse
                    items={[
                      {
                        key: 'order-rooms',
                        label: `Cùng đơn ${data.orderCode} · ${orderRooms.length} phòng`,
                        children: (
                          <Table
                            size="small"
                            pagination={false}
                            rowKey="bookingCode"
                            dataSource={orderRooms}
                            scroll={{ x: 600 }}
                            columns={[
                              {
                                title: 'Booking / hạng phòng',
                                render: (_, r) => (
                                  <>
                                    {r.bookingCode}
                                    <br />
                                    {r.roomTypeName}
                                  </>
                                ),
                              },
                              {
                                title: 'Tình trạng',
                                render: (_, r) =>
                                  r.bookingStatus === 'CONFIRMED'
                                    ? 'Chưa nhận phòng'
                                    : r.bookingStatus === 'CHECKED_IN'
                                      ? 'Đã nhận phòng'
                                      : r.bookingStatus === 'NO_SHOW'
                                        ? 'Không đến'
                                        : r.bookingStatus === 'CANCELLED'
                                          ? 'Đã hủy'
                                          : r.bookingStatus === 'CHECKED_OUT'
                                            ? 'Đã trả phòng'
                                            : 'Chờ xác nhận',
                              },
                              {
                                title: 'Giờ hẹn',
                                render: (_, r) => (r.expectedAt ? time(r.expectedAt) : 'Chưa hẹn'),
                              },
                              {
                                title: 'Hạn tiếp nhận',
                                render: (_, r) => time(r.receptionDeadline),
                              },
                            ]}
                          />
                        ),
                      },
                    ]}
                  />
                )}
                {data.orderCode && (
                  <Alert
                    type="info"
                    title="Mỗi phòng có trạng thái nhận khách riêng"
                    description="Giờ đến, liên hệ và nhận muộn: chọn một hoặc nhiều phòng cùng đơn. Nhận phòng / không đến: từng phòng. Hủy / đổi lịch: hiện áp dụng toàn đơn và có bước xác nhận riêng."
                  />
                )}
                {manager && (closed || data.bookingStatus === 'CANCELLED') && (
                  <PolicyFeeReduction code={arrival.bookingCode} />
                )}
                {(manager || reception) && (
                  <PolicyFeeCollection data={data} onCollected={changed} />
                )}
                {closed ? (
                  <Alert
                    type="success"
                    title="Đã xác nhận khách không đến; giữ chỗ đã được giải phóng"
                    description={`Phí ${money(data.feeAmount)} · Khoản hoàn ${money(data.refundAmount)}. Khoản chờ hoàn chưa phải đã chuyển tiền.`}
                  />
                ) : canEdit ? (
                  <>
                    <Typography.Text>{nextArrivalAction(progress ?? undefined)}</Typography.Text>
                    <Typography.Text type="secondary">
                      Đến muộn được tiếp nhận: phụ thu 0đ; giữ ngày trả và tiền kỳ ở đã đặt.
                    </Typography.Text>
                    {pending || progress?.changeRequestId ? (
                      <Alert
                        type="info"
                        title="Có yêu cầu hủy / đổi lịch chờ quản lý"
                        description={
                          <Link
                            to={`/front-desk/booking-requests?requestId=${pending?.id ?? progress?.changeRequestId}`}
                          >
                            Xem xét yêu cầu
                          </Link>
                        }
                      />
                    ) : null}
                    <Space wrap>
                      <Button disabled={disabled} onClick={() => begin('estimate')}>
                        Cập nhật giờ đến
                      </Button>
                      <Button disabled={disabled} onClick={() => begin('contact')}>
                        Ghi liên hệ
                      </Button>
                      {reception && progress?.canReceive && (
                        <Link
                          to={`/front-desk/check-in?bookingCode=${encodeURIComponent(arrival.bookingCode)}`}
                        >
                          <Button type="primary">Nhận phòng</Button>
                        </Link>
                      )}
                    </Space>
                    <Space wrap>
                      {deadline &&
                        dayjs(data.departureDeadline).isAfter(deadline) &&
                        !pending &&
                        !progress?.changeRequestId && (
                          <Button disabled={disabled} onClick={() => begin('request-hold')}>
                            Xin nhận sau hạn
                          </Button>
                        )}
                      {progress?.holdPending &&
                        (manager ? (
                          <Button disabled={disabled} onClick={() => begin('decide-hold')}>
                            Duyệt / từ chối nhận muộn
                          </Button>
                        ) : (
                          <Tag color="orange">Chờ quản lý duyệt nhận sau hạn</Tag>
                        ))}
                      {manager &&
                        data.holdUntil &&
                        dayjs(data.holdUntil).isAfter(data.serverNow) && (
                          <Button disabled={disabled} onClick={() => begin('release')}>
                            Kết thúc hạn nhận muộn
                          </Button>
                        )}
                    </Space>
                    <Space wrap>
                      <Button disabled={disabled} onClick={() => setChangeType('CANCELLATION')}>
                        Khách báo hủy
                      </Button>
                      {!pending && !progress?.changeRequestId && (
                        <Button disabled={disabled} onClick={() => setChangeType('RESCHEDULE')}>
                          Khách xin đổi lịch
                        </Button>
                      )}
                    </Space>
                    {data.canMarkNoShow && !pending && !progress?.changeRequestId ? (
                      <Button danger disabled={disabled} onClick={() => begin('settlement')}>
                        Xử lý không đến
                      </Button>
                    ) : (
                      <Typography.Text type="secondary">
                        {data.blockReason?.includes('Chưa đến cutoff')
                          ? 'Chưa đến thời điểm xử lý không đến. Khách vẫn được tiếp nhận trước hạn trên.'
                          : data.blockReason}
                      </Typography.Text>
                    )}
                  </>
                ) : (
                  <Alert type="info" title="Booking đã được xử lý; không còn chờ nhận phòng." />
                )}
                {closed && manager && (
                  <Link to={replacement}>
                    <Button>Đặt kỳ ở mới cho khách</Button>
                  </Link>
                )}
                {progress?.lastContactAt && (
                  <Descriptions column={1} size="small" title="Liên hệ gần nhất">
                    <Descriptions.Item label="Thời điểm">
                      {time(progress.lastContactAt)}
                    </Descriptions.Item>
                    <Descriptions.Item label="Kết quả">
                      {progress.lastContactChannel && channelLabels[progress.lastContactChannel]} ·{' '}
                      {progress.lastContactOutcome && outcomeLabels[progress.lastContactOutcome]}
                    </Descriptions.Item>
                    {progress.lastContactNote && (
                      <Descriptions.Item label="Ghi chú">
                        {progress.lastContactNote}
                      </Descriptions.Item>
                    )}
                  </Descriptions>
                )}
                <Collapse
                  items={[
                    {
                      key: 'history',
                      label: 'Lịch sử xử lý',
                      children: data.history.length
                        ? data.history.map((event, index) => (
                            <Typography.Paragraph key={index}>
                              {event.at ? time(event.at) : 'Vừa cập nhật'} ·{' '}
                              {event.actor ?? 'Nhân viên'}: {event.note}
                            </Typography.Paragraph>
                          ))
                        : 'Chưa có thao tác.',
                    },
                  ]}
                />
              </>
            ) : action === 'estimate' ? (
              <>
                <Alert
                  type={expectedAfterDeadline ? 'warning' : 'info'}
                  title={
                    expectedAfterDeadline
                      ? 'Giờ hẹn vượt hạn tiếp nhận của phòng đã chọn'
                      : 'Giờ hẹn không thay đổi hạn tiếp nhận'
                  }
                  description={`Hạn của phòng đang mở: trước ${time(deadline!)}. Lưu giờ hẹn chưa cho phép nhận sau hạn; cần quản lý duyệt đúng các phòng sẽ đến muộn.`}
                />
                {dateField('Giờ khách dự kiến đến', expected, setExpected)}
                <Input.TextArea
                  aria-label="Ghi chú giờ đến"
                  value={note}
                  onChange={(e) => setNote(e.target.value)}
                  maxLength={500}
                  rows={2}
                  disabled={disabled}
                  placeholder="Ghi chú (nếu có)"
                />
                <Button
                  type="primary"
                  disabled={disabled || !expected}
                  onClick={() =>
                    void act(
                      () =>
                        updateArrivalEstimate(
                          arrival.bookingCode,
                          iso(expected!),
                          note.trim(),
                          ...scopeArgs,
                        ),
                      'Đã cập nhật giờ khách hẹn đến.',
                    )
                  }
                >
                  Lưu giờ đến
                </Button>
                {expectedAfterDeadline && (
                  <Button disabled={disabled} onClick={() => setAction('request-hold')}>
                    Chuyển sang xin nhận sau hạn
                  </Button>
                )}
              </>
            ) : action === 'contact' ? (
              <>
                {contactInputs()}
                {outcome === 'CANCELLATION' || outcome === 'RESCHEDULE' ? (
                  <Alert
                    type="info"
                    title="Chuyển sang yêu cầu của khách"
                    description={
                      outcome === 'CANCELLATION'
                        ? 'Khách xác nhận hủy: hiện áp dụng toàn đơn, giải phóng phòng ngay; phần quyết toán thiếu căn cứ chuyển quản lý. Xem rõ phạm vi ở bước tiếp theo.'
                        : 'Đổi lịch hiện áp dụng toàn đơn; ngày cũ được giữ đến khi quản lý duyệt báo giá khách đã chấp thuận. Xem phạm vi ở bước tiếp theo.'
                    }
                    action={
                      <Button onClick={() => setChangeType(outcome)}>
                        Tiếp tục yêu cầu {outcome === 'CANCELLATION' ? 'hủy' : 'đổi lịch'}
                      </Button>
                    }
                  />
                ) : (
                  <Button
                    type="primary"
                    disabled={disabled || !outcome}
                    onClick={() =>
                      void act(
                        () =>
                          recordContactResult(arrival.bookingCode, {
                            channel,
                            outcome: outcome!,
                            note: note.trim(),
                            ...scopeBody,
                          }),
                        'Đã ghi nhận liên hệ.',
                      )
                    }
                  >
                    Lưu liên hệ
                  </Button>
                )}
              </>
            ) : action === 'request-hold' ? (
              <>
                <Alert
                  type="info"
                  title={`Hạn hiện tại: trước ${time(deadline!)}`}
                  description="Đề nghị hạn mới sau giờ khách hẹn, tối đa giờ trả phòng gốc. Lễ tân gửi đề nghị; quản lý có thể chấp thuận ngay."
                />
                {dateField('Giờ khách dự kiến đến', expected, setExpected)}
                {dateField('Hạn tiếp nhận đề nghị', until, setUntil)}
                <Input.TextArea
                  aria-label="Lý do nhận sau hạn"
                  value={note}
                  onChange={(e) => setNote(e.target.value)}
                  maxLength={500}
                  rows={2}
                  disabled={disabled}
                  placeholder="Lý do và nội dung đã trao đổi với khách"
                />
                <Button
                  type="primary"
                  disabled={disabled || !expected || !until || !note.trim()}
                  onClick={() =>
                    void act(
                      () =>
                        holdArrival(
                          arrival.bookingCode,
                          iso(until!),
                          note.trim(),
                          iso(expected!),
                          ...scopeArgs,
                        ),
                      manager ? 'Đã chấp thuận nhận sau hạn.' : 'Đã gửi đề nghị quản lý duyệt.',
                    )
                  }
                >
                  {manager ? 'Chấp thuận nhận muộn' : 'Gửi đề nghị quản lý'}
                </Button>
              </>
            ) : action === 'decide-hold' ? (
              <>
                <Descriptions column={1} bordered>
                  <Descriptions.Item label="Khách hẹn đến">
                    {progress?.expectedAt ? time(progress.expectedAt) : 'Chưa ghi nhận'}
                  </Descriptions.Item>
                  <Descriptions.Item label="Hạn đề nghị">
                    {data.holdRequestedUntil && time(data.holdRequestedUntil)}
                  </Descriptions.Item>
                  <Descriptions.Item label="Trả phòng gốc">
                    {time(data.departureDeadline)}
                  </Descriptions.Item>
                </Descriptions>
                <Typography.Text>
                  Chấp thuận cho nhận trước hạn đề nghị; phụ thu đến muộn 0đ, giữ tiền kỳ ở và ngày
                  trả.
                </Typography.Text>
                <Input.TextArea
                  aria-label="Lý do quyết định nhận muộn"
                  value={note}
                  onChange={(e) => setNote(e.target.value)}
                  maxLength={500}
                  rows={2}
                  disabled={disabled}
                  placeholder="Lý do chấp thuận hoặc từ chối"
                />
                <Space>
                  <Button
                    danger
                    disabled={disabled || !note.trim()}
                    onClick={() =>
                      void act(
                        () =>
                          decideArrivalHold(arrival.bookingCode, {
                            requestedUntil: data.holdRequestedUntil!,
                            approve: false,
                            note: note.trim(),
                            ...scopeBody,
                          }),
                        'Đã từ chối đề nghị; hạn tiếp nhận cũ giữ nguyên.',
                      )
                    }
                  >
                    Từ chối nhận sau hạn
                  </Button>
                  <Button
                    type="primary"
                    disabled={
                      disabled ||
                      !note.trim() ||
                      !dayjs(data.holdRequestedUntil).isAfter(data.serverNow)
                    }
                    onClick={() =>
                      void act(
                        () =>
                          decideArrivalHold(arrival.bookingCode, {
                            requestedUntil: data.holdRequestedUntil!,
                            approve: true,
                            note: note.trim(),
                            ...scopeBody,
                          }),
                        'Đã chấp thuận nhận muộn.',
                      )
                    }
                  >
                    Duyệt nhận muộn
                  </Button>
                </Space>
              </>
            ) : action === 'release' ? (
              <>
                <Alert
                  type="warning"
                  title="Kết thúc ngoại lệ nhận muộn"
                  description="Quyền nhận phòng trở về hạn mặc định. Ghi rõ lý do và việc đã thông báo cho khách; thao tác không tự đóng no-show."
                />
                <Input.TextArea
                  aria-label="Lý do kết thúc nhận muộn"
                  value={note}
                  onChange={(e) => setNote(e.target.value)}
                  maxLength={500}
                  rows={2}
                  disabled={disabled}
                />
                <Button
                  danger
                  disabled={disabled || !note.trim()}
                  onClick={() =>
                    void act(
                      () => releaseArrivalHold(arrival.bookingCode, note.trim(), ...scopeArgs),
                      'Đã kết thúc ngoại lệ nhận muộn.',
                    )
                  }
                >
                  Xác nhận kết thúc ngoại lệ
                </Button>
              </>
            ) : (
              <>
                {data.legacyPolicy && (
                  <Alert
                    type="warning"
                    title="Booking chưa ghi nhận điều khoản no-show; quản lý phải xem xét và xác nhận."
                    description="Đối chiếu thỏa thuận với khách và ghi căn cứ quyết định."
                  />
                )}
                <Descriptions column={1} bordered size="small">
                  <Descriptions.Item label="Tiền đã nhận">
                    {money(data.receivedAmount ?? data.paidAmount)}
                  </Descriptions.Item>
                  <Descriptions.Item label="Tiền còn bù trừ">{money(available)}</Descriptions.Item>
                  <Descriptions.Item label="Phí chuẩn">
                    100% tiền phòng đêm đầu · {money(data.standardFee)}
                  </Descriptions.Item>
                </Descriptions>
                {manager ? (
                  <>
                    <Typography.Text strong>Cách tính phí được duyệt</Typography.Text>
                    <Radio.Group
                      aria-label="Cách tính phí no-show"
                      value={feeMode}
                      disabled={disabled}
                      onChange={(e) => {
                        setFeeMode(e.target.value);
                        setFee(null);
                      }}
                      options={[
                        { value: 'standard', label: 'Áp dụng phí chuẩn' },
                        { value: 'reduce', label: 'Giảm phí' },
                        { value: 'waive', label: 'Miễn phí' },
                      ]}
                    />
                    {feeMode === 'reduce' && (
                      <InputNumber
                        aria-label="Phí no-show được duyệt"
                        min={1}
                        max={Math.max(data.standardFee - 1, 0)}
                        precision={0}
                        value={fee}
                        disabled={disabled}
                        onChange={setFee}
                        placeholder="Nhập mức phí sau giảm"
                      />
                    )}
                  </>
                ) : (
                  <Typography.Text>
                    Lễ tân áp dụng phí chuẩn {money(data.standardFee)}.
                  </Typography.Text>
                )}
                <Descriptions column={1} size="small">
                  <Descriptions.Item label="Phí thực tế được duyệt">
                    {chosenFee === null ? 'Chưa chọn mức giảm' : money(chosenFee)}
                  </Descriptions.Item>
                  <Descriptions.Item label="Phải hoàn">
                    {chosenFee === null ? '—' : money(Math.max(available - chosenFee, 0))}
                  </Descriptions.Item>
                  <Descriptions.Item label="Còn phải thu">
                    {chosenFee === null ? '—' : money(Math.max(chosenFee - available, 0))}
                  </Descriptions.Item>
                </Descriptions>
                {contactInputs(true)}
                {needsFeeReason && (
                  <Input.TextArea
                    aria-label="Lý do xử lý"
                    rows={2}
                    value={reason}
                    maxLength={500}
                    disabled={disabled}
                    onChange={(e) => setReason(e.target.value)}
                    placeholder={
                      data.legacyPolicy && feeMode === 'standard'
                        ? 'Căn cứ áp dụng phí với booking chưa lưu chấp thuận chính sách'
                        : 'Lý do miễn / giảm phí (bắt buộc)'
                    }
                  />
                )}
                <Alert
                  type="warning"
                  title="Sau xác nhận: đóng quyền nhận phòng và giải phóng giữ chỗ"
                  description="Ghi phí và tạo khoản hoàn cần xử lý riêng; chưa tự chuyển tiền hoàn cho khách."
                />
                {!data.canMarkNoShow && <Alert type="error" title={data.blockReason} />}
                <Button
                  danger
                  type="primary"
                  disabled={
                    disabled ||
                    !data.canMarkNoShow ||
                    !!pending ||
                    !!progress?.changeRequestId ||
                    (data.legacyPolicy && !manager) ||
                    !validFee ||
                    (needsFeeReason && !reason.trim()) ||
                    !note.trim() ||
                    !outcome
                  }
                  onClick={() =>
                    void act(
                      () =>
                        markNoShow(arrival.bookingCode, {
                          previewToken: data.previewToken,
                          feeAmount: chosenFee!,
                          reason: needsFeeReason ? reason.trim() : note.trim(),
                          contactNote: note.trim(),
                          channel,
                          outcome: outcome!,
                        }),
                      'Đã đóng booking không đến và ghi quyết toán.',
                    )
                  }
                >
                  Xác nhận không đến
                </Button>
              </>
            )}
          </>
        )}
        {changeType && (
          <BookingChangeRequestForm
            key={changeType}
            code={arrival.bookingCode}
            type={changeType}
            roomCount={data?.roomCount}
            contactChannel={action === 'contact' ? channel : undefined}
            initialReason={
              action === 'contact'
                ? `${channelLabels[channel]}: ${outcome ? outcomeLabels[outcome] : ''}${note.trim() ? ' · ' + note.trim() : ''}`
                : ''
            }
            onClose={() => setChangeType(null)}
            onSaved={() => {
              void changes.refetch();
              void query.refetch();
              void refreshArrivalsAfterCheckIn(client);
              setAction('summary');
            }}
          />
        )}
      </Space>
    </Modal>
  );
}
