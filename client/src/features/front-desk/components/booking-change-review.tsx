import { useQuery } from '@tanstack/react-query';
import {
  Alert,
  App,
  Button,
  Checkbox,
  Collapse,
  DatePicker,
  Input,
  InputNumber,
  Modal,
  Space,
  Spin,
  Tag,
  Typography,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { useEffect, useState } from 'react';
import {
  allowRescheduleException,
  approveBookingChange,
  cancellationSettlement,
  changeRescheduleServices,
  getBookingChangeQuote,
  rejectBookingChange,
  type BookingChangeRequest,
} from '../api/booking-change-api';
import { useAuth } from '../../authentication/providers/auth-context';
import { hasRouteRole, ROUTE_ROLES } from '../../../routes/route-access';
import { formatHotelDateTime } from '../../../utils/hotel-time';
import {
  BookingChangeQuoteSummary,
  BookingChangeStayComparison,
} from './booking-change-quote-summary';
import { PolicyFeeReduction } from './policy-fee-reduction';
import './booking-change-review.css';

const money = (n: number) =>
  new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(n);

function parseChildAges(value: string) {
  const parts = value.split(',').map((part) => part.trim());
  return parts.length &&
    parts.every((part) => /^\d+$/.test(part) && Number.isSafeInteger(Number(part)))
    ? parts.map(Number)
    : [];
}

export function BookingChangeReview({
  id,
  onClose,
  onUpdated,
}: {
  id: string;
  onClose: () => void;
  onUpdated: (result: BookingChangeRequest) => void;
}) {
  const { roles } = useAuth();
  const manager = hasRouteRole(roles, ROUTE_ROLES.management);
  const { message } = App.useApp();
  const query = useQuery({
    queryKey: ['booking-change-quote', id],
    queryFn: () => getBookingChangeQuote(id),
    retry: false,
    staleTime: 0,
    refetchOnWindowFocus: false,
  });
  const [note, setNote] = useState('');
  const [balanceDueAt, setBalanceDueAt] = useState<Dayjs | null>(null);
  const [acceptedQuote, setAcceptedQuote] = useState<string | null>(null);
  const [servicesQuote, setServicesQuote] = useState<string | null>(null);
  const [childAges, setChildAges] = useState<Record<string, string>>({});
  const [feeOverride, setFeeOverride] = useState<{ token: string; amount: number | null } | null>(
    null,
  );
  const [busy, setBusy] = useState(false);
  const [uncertain, setUncertain] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);
  const [clockNow, setClockNow] = useState(() => Date.now());
  const data = query.data;
  const request = data?.request;
  const cancellation = request?.type === 'CANCELLATION';
  const pending = request?.status === 'PENDING';
  const locked = busy || uncertain || query.isFetching;
  const customerAccepted = !!data && acceptedQuote === data.quoteToken;
  const servicesConfirmed = !!data && servicesQuote === data.quoteToken;
  const chosenFee =
    feeOverride && feeOverride.token === data?.quoteToken
      ? feeOverride.amount
      : (data?.feeAmount ?? null);
  const maxFee = data?.rooms.reduce((sum, room) => sum + room.standardFee, 0) ?? 0;
  const validFee =
    chosenFee !== null && Number.isSafeInteger(chosenFee) && chosenFee >= 0 && chosenFee <= maxFee;
  const projected =
    data && data.canApprove && validFee ? cancellationSettlement(data, chosenFee) : null;
  const hasServices = data?.rooms.some((room) => !!room.services?.length) ?? false;
  const roomsWithChildren = data?.rooms.filter((room) => (room.children ?? 0) > 0) ?? [];
  const needsDeadline = !cancellation && (projected?.additional ?? 0) > 0;
  const canRequestException =
    !cancellation && !data?.canApprove && /số đêm|một lần|90 ngày/.test(data?.blockReason ?? '');

  useEffect(() => {
    if (!needsDeadline) return;
    const timer = window.setInterval(() => setClockNow(Date.now()), 30_000);
    return () => window.clearInterval(timer);
  }, [needsDeadline]);

  function resetConfirmation() {
    setFeeOverride(null);
    setAcceptedQuote(null);
    setServicesQuote(null);
    setBalanceDueAt(null);
    setChildAges({});
  }

  function hotelDeadline() {
    return data && balanceDueAt?.isValid() ? balanceDueAt.tz(data.zone, true) : null;
  }

  function approvalProblem(currentTime: number) {
    if (!manager || !data || !pending || locked || query.isError) return 'Chưa thể duyệt yêu cầu.';
    if (!data.canApprove) return 'Cần xử lý điều kiện đang chặn trước khi duyệt.';
    if (data.pricingAvailable === false) return 'Cần cập nhật báo giá hợp lệ trước khi duyệt.';
    if (chosenFee === null) return 'Chọn rõ mức phí được duyệt, kể cả 0 đ.';
    if (!Number.isSafeInteger(chosenFee) || chosenFee < 0 || chosenFee > maxFee)
      return 'Mức phí phải nằm trong giới hạn đang hiển thị.';
    if (!note.trim()) return 'Nhập căn cứ duyệt hoặc lý do từ chối.';
    if (!cancellation) {
      if (!customerAccepted) return 'Đối chiếu và xác nhận khách chấp thuận báo giá này.';
      if (hasServices && !servicesConfirmed) return 'Xác nhận các dịch vụ chuyển được sang kỳ mới.';
      if (
        roomsWithChildren.some(
          (room) => parseChildAges(childAges[room.bookingCode] ?? '').length !== room.children,
        )
      ) {
        return 'Nhập đủ tuổi trẻ em tại ngày nhận mới cho từng phòng.';
      }
      if (needsDeadline) {
        const now = dayjs(data.serverNow).add(
          Math.max(currentTime - query.dataUpdatedAt, 0),
          'millisecond',
        );
        if (!hotelDeadline()?.isAfter(now))
          return 'Chọn hạn thu đã thỏa thuận ở tương lai, theo giờ khách sạn.';
      }
    }
    return null;
  }
  const problem = approvalProblem(clockNow);

  async function refreshQuote() {
    const result = await query.refetch();
    resetConfirmation();
    if (result.isSuccess && result.data) {
      setUncertain(false);
      if (result.data.request.status !== 'PENDING') onUpdated(result.data.request);
      return result.data;
    }
    setUncertain(true);
    setFailure('Chưa xác minh được kết quả; tra cứu lại trước khi thực hiện tiếp.');
    return null;
  }

  async function verify() {
    setBusy(true);
    setFailure(null);
    try {
      await refreshQuote();
    } finally {
      setBusy(false);
    }
  }

  async function updateQuote(action: () => Promise<unknown>) {
    if (locked || !note.trim()) return;
    setBusy(true);
    setFailure(null);
    try {
      await action();
      await refreshQuote();
    } catch (error) {
      setFailure(error instanceof Error ? error.message : 'Chưa cập nhật được báo giá.');
      setUncertain(true);
      await refreshQuote();
    } finally {
      setBusy(false);
    }
  }

  async function decide(approve: boolean) {
    if (
      !data ||
      !manager ||
      !pending ||
      locked ||
      !note.trim() ||
      (approve && approvalProblem(Date.now()))
    )
      return;
    setBusy(true);
    setFailure(null);
    try {
      const result = approve
        ? await approveBookingChange(id, {
            quoteToken: data.quoteToken,
            ...(chosenFee !== null ? { feeAmount: chosenFee } : {}),
            customerAccepted,
            ...(needsDeadline ? { balanceDueAt: hotelDeadline()?.toISOString() } : {}),
            servicesConfirmed,
            childAges: Object.fromEntries(
              roomsWithChildren.map((room) => [
                room.bookingCode,
                parseChildAges(childAges[room.bookingCode] ?? ''),
              ]),
            ),
            note: note.trim(),
          })
        : await rejectBookingChange(id, note.trim());
      onUpdated(result);
      void message.success(approve ? 'Đã duyệt và áp dụng yêu cầu.' : 'Đã từ chối yêu cầu.');
      await query.refetch();
    } catch (error) {
      setFailure(error instanceof Error ? error.message : 'Không xử lý được yêu cầu.');
      setUncertain(true);
      const verified = await refreshQuote();
      if (verified && verified.request.status !== 'PENDING') setFailure(null);
    } finally {
      setBusy(false);
    }
  }

  const feeEditor = data && (
    <div className="booking-change-review__detail">
      <label htmlFor={`change-fee-${id}`}>
        {data.requiresFeeDecision
          ? 'Phí cần quản lý xác định theo điều khoản và bằng chứng'
          : 'Tổng phí được duyệt sau điều chỉnh'}
      </label>
      <InputNumber
        id={`change-fee-${id}`}
        aria-label="Tổng phí được duyệt"
        min={0}
        max={maxFee}
        precision={0}
        value={chosenFee}
        disabled={locked}
        onChange={(amount) => {
          setFeeOverride({ token: data.quoteToken, amount });
          setAcceptedQuote(null);
        }}
        suffix="đ"
        controls={false}
        placeholder="Nhập rõ số tiền, kể cả 0"
        style={{ width: '100%', maxWidth: 320 }}
      />
      <Typography.Text type="secondary">
        Tối đa {money(maxFee)}. Số tiền này là tổng phí sau xử lý, đã gồm phí trước đây. Ghi căn cứ
        miễn/giảm ở phần xác nhận.
      </Typography.Text>
      {feeOverride?.token === data.quoteToken && !data.requiresFeeDecision && (
        <Button
          onClick={() => {
            setFeeOverride(null);
            setAcceptedQuote(null);
          }}
          disabled={locked}
        >
          Dùng mức phí theo chính sách
        </Button>
      )}
    </div>
  );

  return (
    <Modal
      open
      width={900}
      className="booking-change-review"
      title={cancellation ? 'Xem xét quyết toán hủy phòng' : 'Xem xét thay đổi kỳ lưu trú'}
      styles={{ body: { maxHeight: 'calc(100dvh - 240px)', overflowY: 'auto', paddingRight: 8 } }}
      onCancel={() => {
        if (!busy) onClose();
      }}
      mask={{ closable: !busy }}
      footer={
        <div className="booking-change-review__footer">
          {pending && manager && (
            <Typography.Text type="secondary" role="status">
              {problem ?? 'Đã đủ thông tin để duyệt. Tồn và giá được kiểm tra lại khi áp dụng.'}
            </Typography.Text>
          )}
          <Space wrap>
            <Button disabled={busy} onClick={onClose}>
              Đóng
            </Button>
            {pending && manager && (
              <>
                <Button danger disabled={locked || !note.trim()} onClick={() => void decide(false)}>
                  Từ chối yêu cầu
                </Button>
                <Button
                  type="primary"
                  aria-label="Duyệt và áp dụng"
                  loading={busy}
                  disabled={!!problem}
                  onClick={() => void decide(true)}
                >
                  Duyệt và áp dụng
                </Button>
              </>
            )}
          </Space>
        </div>
      }
    >
      <div className="booking-change-review__content">
        {query.isLoading && <Spin />}
        {(query.isError || failure) && (
          <Alert showIcon type="error" title={failure ?? query.error?.message} />
        )}
        <div className="booking-change-review__toolbar">
          <div>
            {request && (
              <Typography.Text strong>
                {cancellation ? 'Khách báo hủy' : 'Khách xin đổi lịch'}
              </Typography.Text>
            )}
            {request && (
              <Tag color={pending ? 'orange' : request.status === 'APPROVED' ? 'green' : 'default'}>
                {pending
                  ? 'Chờ xử lý'
                  : request.status === 'APPROVED'
                    ? 'Đã duyệt'
                    : request.snapshot?.autoClosed
                      ? 'Đã kết thúc do hủy'
                      : 'Đã từ chối'}
              </Tag>
            )}
          </div>
          <Button
            aria-label={uncertain ? 'Tra cứu lại kết quả' : 'Tra cứu lại yêu cầu'}
            loading={busy || query.isFetching}
            onClick={() => void verify()}
          >
            {uncertain ? 'Tra cứu lại kết quả' : 'Cập nhật báo giá'}
          </Button>
        </div>
        {data && request && (
          <>
            <div className="booking-change-review__context">
              <Typography.Text strong>
                {request.orderCode
                  ? `Toàn đơn ${request.orderCode} · ${data.rooms.length} phòng`
                  : request.bookingCode}
              </Typography.Text>
              <Typography.Text type="secondary">
                {request.requestedBy} · {formatHotelDateTime(request.requestedAt, data.zone)}
              </Typography.Text>
              <Typography.Paragraph>{request.reason}</Typography.Paragraph>
            </div>
            {!pending ? (
              <>
                <Alert
                  showIcon
                  type={request.status === 'APPROVED' ? 'success' : 'info'}
                  title={
                    request.status === 'APPROVED'
                      ? 'Yêu cầu đã được duyệt'
                      : request.snapshot?.autoClosed
                        ? 'Yêu cầu đổi lịch đã kết thúc do booking / đơn đã hủy'
                        : 'Yêu cầu đã bị từ chối'
                  }
                  description={
                    <Space orientation="vertical">
                      <span>{request.decisionNote}</span>
                      {request.snapshot && (
                        <>
                          <span>
                            Tổng phí còn hiệu lực: {money(request.snapshot.feeAmount ?? 0)}
                          </span>
                          <span>
                            Khoản hoàn được tạo: {money(request.snapshot.refundAmount ?? 0)}
                          </span>
                          <span>
                            Còn phải thu khi áp dụng:{' '}
                            {money(request.snapshot.additionalAmount ?? 0)}
                          </span>
                          {request.snapshot.balanceDueAt && (
                            <span>
                              Hạn thu đã hẹn:{' '}
                              {formatHotelDateTime(request.snapshot.balanceDueAt, data.zone)}
                            </span>
                          )}
                          <span>
                            Đối chiếu trạng thái thanh toán và hoàn tiền trước khi xác nhận đã
                            thu/hoàn.
                          </span>
                        </>
                      )}
                    </Space>
                  }
                />
                {request.status === 'APPROVED' && manager && (
                  <Collapse
                    items={[
                      {
                        key: 'reduce',
                        label: 'Điều chỉnh giảm phí sau quyết toán',
                        children: <PolicyFeeReduction code={request.bookingCode} />,
                      },
                    ]}
                  />
                )}
              </>
            ) : (
              <>
                {!data.canApprove && (
                  <Alert
                    showIcon
                    type="warning"
                    title="Chưa thể áp dụng yêu cầu"
                    description={data.blockReason}
                  />
                )}
                <section
                  aria-labelledby={`change-stay-${id}`}
                  className="booking-change-review__section"
                >
                  <Typography.Title id={`change-stay-${id}`} level={5}>
                    1. {cancellation ? 'Phạm vi hủy' : 'Kỳ ở hiện tại và đề nghị'}
                  </Typography.Title>
                  <BookingChangeStayComparison data={data} />
                  {!cancellation && (
                    <Typography.Paragraph
                      type="secondary"
                      className="booking-change-review__quote-pending"
                    >
                      Ngày cũ được giữ đến khi duyệt. Đổi khác số đêm được báo giá trước, cần quản
                      lý xét ngoại lệ và khách chấp thuận.
                    </Typography.Paragraph>
                  )}
                </section>
                <section
                  aria-labelledby={`change-price-${id}`}
                  className="booking-change-review__section"
                >
                  <Typography.Title id={`change-price-${id}`} level={5}>
                    2. Báo giá và thu / hoàn
                  </Typography.Title>
                  {manager && data.requiresFeeDecision && (
                    <Alert
                      showIcon
                      type="warning"
                      title="Cần xác định phí theo điều khoản đã chấp thuận"
                      description={feeEditor}
                    />
                  )}
                  <BookingChangeQuoteSummary data={data} fee={chosenFee} />
                  {manager && !data.requiresFeeDecision && data.canApprove && (
                    <Collapse
                      ghost
                      items={[
                        { key: 'fee', label: 'Điều chỉnh hoặc miễn phí', children: feeEditor },
                      ]}
                    />
                  )}
                </section>
                {manager ? (
                  <section
                    aria-labelledby={`change-confirm-${id}`}
                    className="booking-change-review__section"
                  >
                    <Typography.Title id={`change-confirm-${id}`} level={5}>
                      3. Xác nhận và quyết định
                    </Typography.Title>
                    <div className="booking-change-review__detail">
                      {!cancellation && data.canApprove && (
                        <>
                          <Checkbox
                            disabled={locked}
                            checked={customerAccepted}
                            onChange={(event) =>
                              setAcceptedQuote(event.target.checked ? data.quoteToken : null)
                            }
                          >
                            Đã đối chiếu khách chấp thuận kỳ mới, tổng tiền và khoản thu / hoàn.
                          </Checkbox>
                          {request.consentAt && (
                            <Typography.Text type="secondary">
                              Khách đã phản hồi báo giá lúc{' '}
                              {formatHotelDateTime(request.consentAt, data.zone)}. Đối chiếu lại
                              trước khi duyệt.
                            </Typography.Text>
                          )}
                          {hasServices && (
                            <Checkbox
                              disabled={locked}
                              checked={servicesConfirmed}
                              onChange={(event) =>
                                setServicesQuote(event.target.checked ? data.quoteToken : null)
                              }
                            >
                              Đã xác nhận chuyển được các dịch vụ sang kỳ mới theo báo giá.
                            </Checkbox>
                          )}
                          {needsDeadline && (
                            <div className="booking-change-review__field">
                              <label htmlFor={`change-deadline-${id}`}>
                                Hạn thu số tiền còn thiếu · giờ khách sạn
                              </label>
                              <DatePicker
                                id={`change-deadline-${id}`}
                                aria-label="Hạn thu chênh lệch"
                                showTime={{ format: 'HH:mm' }}
                                needConfirm={false}
                                value={balanceDueAt}
                                onChange={setBalanceDueAt}
                                format="DD/MM/YYYY HH:mm"
                                placeholder="Chọn hạn thu đã thỏa thuận"
                                disabled={locked}
                                style={{ width: '100%', maxWidth: 320 }}
                              />
                              <Typography.Text type="secondary">
                                Booking giữ CONFIRMED; theo dõi khoản thiếu theo hạn đã hẹn.
                              </Typography.Text>
                            </div>
                          )}
                          {(hasServices || roomsWithChildren.length > 0) && (
                            <div className="booking-change-review__detail">
                              {data.rooms
                                .filter((room) => room.services?.length || room.children)
                                .map((room) => (
                                  <div
                                    className="booking-change-review__field"
                                    key={room.bookingCode}
                                  >
                                    <Typography.Text strong>
                                      {room.bookingCode} · {room.roomTypeName}
                                    </Typography.Text>
                                    {room.services?.length ? (
                                      <Typography.Text>{room.services.join('; ')}</Typography.Text>
                                    ) : null}
                                    {!!room.children && (
                                      <>
                                        <label htmlFor={`change-ages-${room.bookingCode}`}>
                                          Tuổi của {room.children} trẻ em tại ngày nhận mới
                                        </label>
                                        <Input
                                          id={`change-ages-${room.bookingCode}`}
                                          aria-label={`Tuổi trẻ em ${room.bookingCode}`}
                                          placeholder="Ví dụ: 0, 5"
                                          disabled={locked}
                                          value={childAges[room.bookingCode] ?? ''}
                                          onChange={(event) =>
                                            setChildAges((values) => ({
                                              ...values,
                                              [room.bookingCode]: event.target.value,
                                            }))
                                          }
                                        />
                                      </>
                                    )}
                                  </div>
                                ))}
                            </div>
                          )}
                        </>
                      )}
                      <div className="booking-change-review__field">
                        <label htmlFor={`change-note-${id}`}>Căn cứ duyệt / lý do từ chối</label>
                        <Input.TextArea
                          id={`change-note-${id}`}
                          aria-label="Lý do quyết định"
                          rows={3}
                          maxLength={500}
                          showCount
                          value={note}
                          disabled={locked}
                          onChange={(event) => setNote(event.target.value)}
                          placeholder="Kênh, thời điểm và nội dung khách đồng ý; ghi căn cứ nếu miễn/giảm phí hoặc từ chối."
                        />
                      </div>
                      {(canRequestException || hasServices) && !cancellation && (
                        <Collapse
                          ghost
                          items={[
                            {
                              key: 'exception',
                              label: 'Xử lý ngoại lệ và dịch vụ',
                              children: (
                                <div className="booking-change-review__detail">
                                  <Typography.Text type="secondary">
                                    Nhập căn cứ ở trên trước khi thao tác. Báo giá thay đổi cần được
                                    khách chấp thuận lại.
                                  </Typography.Text>
                                  {canRequestException && (
                                    <Button
                                      disabled={locked || !note.trim()}
                                      onClick={() =>
                                        void updateQuote(() =>
                                          allowRescheduleException(id, note.trim()),
                                        )
                                      }
                                    >
                                      {data.blockReason?.includes('số đêm')
                                        ? 'Xét ngoại lệ thay đổi số đêm và báo giá lại'
                                        : 'Quản lý xét ngoại lệ giới hạn một lần / 90 ngày'}
                                    </Button>
                                  )}
                                  {hasServices && (
                                    <Button
                                      disabled={locked || !note.trim()}
                                      onClick={() =>
                                        void updateQuote(() =>
                                          changeRescheduleServices(id, true, note.trim()),
                                        )
                                      }
                                    >
                                      Bỏ toàn bộ dịch vụ chưa dùng và báo giá lại
                                    </Button>
                                  )}
                                </div>
                              ),
                            },
                          ]}
                        />
                      )}
                    </div>
                  </section>
                ) : (
                  <Alert
                    showIcon
                    type="info"
                    title="Chờ quản lý xem xét; lễ tân không có quyền duyệt hoặc từ chối."
                  />
                )}
              </>
            )}
          </>
        )}
      </div>
    </Modal>
  );
}
