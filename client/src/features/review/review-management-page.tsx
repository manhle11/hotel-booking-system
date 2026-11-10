import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  App,
  Button,
  Card,
  Descriptions,
  Drawer,
  DatePicker,
  Empty,
  Form,
  Input,
  Modal,
  Popconfirm,
  Rate,
  Select,
  Space,
  Table,
  Tabs,
  Tooltip,
  Typography,
} from 'antd';
import {
  CheckCircleOutlined,
  ClearOutlined,
  ClockCircleOutlined,
  CommentOutlined,
  DeleteOutlined,
  ExclamationCircleOutlined,
  EyeOutlined,
  HomeOutlined,
  MessageOutlined,
  ReloadOutlined,
  SearchOutlined,
  StarFilled,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { apiClient } from '../../lib/axios';
import type { ApiResponse, PageResponse } from '../../types/api';
import {
  deleteReview,
  getManagedReviews,
  getManagedReviewSummary,
  replyReview,
  removeReviewReply,
  type ManagedReview,
  type ManagedReviewFilters,
} from './api';
import './review-management.css';

function ReviewStatus({ row }: { row: ManagedReview }) {
  if (row.deletedAt) {
    return (
      <span className="managed-review-status managed-review-status--deleted">
        <DeleteOutlined /> Đã xóa
      </span>
    );
  }
  return row.review.hotelReply ? (
    <span className="managed-review-status managed-review-status--replied">
      <CheckCircleOutlined /> Đã phản hồi
    </span>
  ) : (
    <span className="managed-review-status managed-review-status--waiting">
      <ClockCircleOutlined /> Chưa phản hồi
    </span>
  );
}

function GuestIdentity({ row }: { row: ManagedReview }) {
  return (
    <div className="managed-review-guest">
      <span className="managed-review-guest__avatar" aria-hidden="true">
        {row.guestName.trim().charAt(0)}
      </span>
      <div className="managed-review-guest__info">
        <strong>{row.guestName}</strong>
        <span className="managed-review-guest__reference">{row.orderCode || row.bookingCode}</span>
        <span className="managed-review-guest__room">
          <HomeOutlined /> {row.review.roomTypeName}
        </span>
      </div>
    </div>
  );
}

function ReviewContent({ row, expanded = false }: { row: ManagedReview; expanded?: boolean }) {
  return (
    <div className="managed-review-content">
      <div className="managed-review-content__heading">
        <span
          className={`managed-review-score${row.review.rating <= 2 ? ' managed-review-score--low' : ''}`}
        >
          <strong>
            {row.review.rating}
            <span>/5</span>
          </strong>
          <Rate disabled value={row.review.rating} aria-label={`${row.review.rating} trên 5 sao`} />
        </span>
        <time
          dateTime={row.review.createdAt}
          title={dayjs(row.review.createdAt).format('HH:mm DD/MM/YYYY')}
        >
          {dayjs(row.review.createdAt).format('DD/MM/YYYY')}
          <span> · {dayjs(row.review.createdAt).format('HH:mm')}</span>
        </time>
      </div>
      <p
        className={`managed-review-comment${expanded ? ' managed-review-comment--full' : ''}${!row.review.comment ? ' managed-review-comment--empty' : ''}`}
      >
        {row.review.comment || 'Khách chỉ chấm sao, không để lại nhận xét.'}
      </p>
    </div>
  );
}

export function ReviewManagementPage() {
  const [params, setParams] = useSearchParams();
  const roomTypeId = params.get('roomTypeId') || undefined;
  const ratingValue = Number(params.get('rating'));
  const rating =
    Number.isInteger(ratingValue) && ratingValue >= 1 && ratingValue <= 5 ? ratingValue : undefined;
  const [page, setPage] = useState(0);
  const deleted = params.get('view') === 'deleted';
  const replied =
    params.get('replied') === 'true' ? true : params.get('replied') === 'false' ? false : undefined;
  const search = params.get('search')?.trim() || undefined;
  const readDate = (key: string) => {
    const value = params.get(key);
    const date = value ? dayjs(value) : null;
    return value &&
      /^\d{4}-\d{2}-\d{2}$/.test(value) &&
      date?.isValid() &&
      date.format('YYYY-MM-DD') === value
      ? value
      : undefined;
  };
  const fromDate = readDate('fromDate');
  const toDate = readDate('toDate');
  const filters: ManagedReviewFilters = {
    roomTypeId,
    rating,
    deleted,
    replied,
    search,
    fromDate,
    toDate,
  };
  const [selected, setSelected] = useState<ManagedReview>();
  const [deleting, setDeleting] = useState<ManagedReview>();
  const [replying, setReplying] = useState<ManagedReview>();
  const [deleteForm] = Form.useForm<{ reason: string }>();
  const [replyForm] = Form.useForm<{ reply: string }>();
  const { message } = App.useApp();
  const client = useQueryClient();
  const query = useQuery({
    queryKey: ['managed-reviews', page, filters],
    queryFn: async () => {
      const result = await getManagedReviews(page, filters);
      if (result.page !== page) setPage((current) => (current === page ? result.page : current));
      return result;
    },
  });
  const summary = useQuery({
    queryKey: ['managed-review-summary', filters],
    queryFn: () => getManagedReviewSummary(filters),
  });
  const types = useQuery({
    queryKey: ['review-room-type-options'],
    queryFn: async () => {
      const result: { id: string; name: string }[] = [];
      let current = 0;
      let last = false;
      while (!last) {
        const response = await apiClient.get<
          ApiResponse<PageResponse<{ id: string; name: string }>>
        >('/management/room-types', { params: { page: current, size: 100 } });
        result.push(...response.data.data.content);
        last = response.data.data.last;
        current++;
      }
      return result;
    },
  });
  const refresh = async () => {
    await Promise.all([
      client.invalidateQueries({ queryKey: ['managed-reviews'] }),
      client.invalidateQueries({ queryKey: ['managed-review-summary'] }),
      client.invalidateQueries({ queryKey: ['room-reviews'] }),
      client.invalidateQueries({ queryKey: ['review-summary'] }),
      client.invalidateQueries({ queryKey: ['review-eligibility'] }),
    ]);
    setSelected(undefined);
  };
  const remove = useMutation({
    mutationFn: (values: { reason: string }) =>
      deleteReview({ id: deleting!.review.id, reason: values.reason.trim() }),
    onSuccess: async () => {
      setDeleting(undefined);
      deleteForm.resetFields();
      await refresh();
      void message.success('Đã xóa đánh giá và lưu lý do. Điểm trung bình đã được cập nhật.');
    },
    onError: (error: Error) => void message.error(error.message),
  });
  const saveReply = useMutation({
    mutationFn: (values: { reply: string }) =>
      replyReview(replying!.review.id, values.reply.trim()),
    onSuccess: async () => {
      setReplying(undefined);
      await refresh();
      void message.success('Đã lưu phản hồi của khách sạn.');
    },
    onError: (error: Error) => void message.error(error.message),
  });
  const removeReply = useMutation({
    mutationFn: removeReviewReply,
    onSuccess: async () => {
      await refresh();
      void message.success('Đã gỡ phản hồi của khách sạn.');
    },
    onError: (error: Error) => void message.error(error.message),
  });
  const openReply = (row: ManagedReview) => {
    setReplying(row);
    replyForm.setFieldsValue({ reply: row.review.hotelReply ?? '' });
  };
  const changeFilters = (values: Record<string, string | undefined>) => {
    setParams((current) => {
      const next = new URLSearchParams(current);
      Object.entries(values).forEach(([key, value]) => {
        if (value) next.set(key, value);
        else next.delete(key);
      });
      return next;
    });
    setPage(0);
  };
  const hasFilters = Boolean(
    roomTypeId || rating || replied !== undefined || search || fromDate || toDate,
  );
  const selectedTypeName = types.data?.find((type) => type.id === roomTypeId)?.name;

  return (
    <div className="review-management">
      <header className="review-management__hero">
        <div>
          <span className="review-management__eyebrow">
            <CommentOutlined /> TRẢI NGHIỆM KHÁCH HÀNG
          </span>
          <h1>Đánh giá hạng phòng</h1>
          <p>Theo dõi trải nghiệm lưu trú và chăm sóc từng phản hồi của khách.</p>
        </div>
        <Button
          icon={<ReloadOutlined aria-hidden="true" />}
          loading={query.isFetching || summary.isFetching}
          onClick={() => {
            void query.refetch();
            void summary.refetch();
            void types.refetch();
          }}
        >
          Làm mới
        </Button>
      </header>

      <div className="review-management__overview" aria-label="Tổng quan kết quả đánh giá">
        <div className="review-management__metric review-management__total">
          <span className="review-management__metric-icon">
            <MessageOutlined />
          </span>
          <div>
            <span className="review-management__metric-label">
              {deleted ? 'Kết quả trong lịch sử' : 'Đánh giá phù hợp'}
            </span>
            <strong>{summary.data?.totalReviews ?? '—'}</strong>
            <span className="review-management__metric-note">
              {selectedTypeName || (roomTypeId ? 'Hạng phòng đã chọn' : 'Tất cả hạng phòng')}
              {rating ? ` · ${rating} sao` : ''}
            </span>
          </div>
        </div>
        <div className="review-management__metric">
          <span
            className={`review-management__metric-icon review-management__metric-icon--${deleted ? 'green' : 'amber'}`}
          >
            {deleted ? <CheckCircleOutlined /> : <ClockCircleOutlined />}
          </span>
          <div>
            <span className="review-management__metric-label">
              {deleted ? 'Có phản hồi trước khi xóa' : 'Chưa phản hồi'}
            </span>
            <strong>
              {summary.data
                ? deleted
                  ? summary.data.repliedReviews
                  : summary.data.unrepliedReviews
                : '—'}
            </strong>
            <span className="review-management__metric-note">Trên toàn bộ kết quả đang lọc</span>
          </div>
        </div>
        <div className="review-management__metric">
          <span className="review-management__metric-icon review-management__metric-icon--rose">
            <ExclamationCircleOutlined />
          </span>
          <div>
            <span className="review-management__metric-label">Đánh giá 1–2 sao</span>
            <strong>{summary.data?.lowRatingReviews ?? '—'}</strong>
            <span className="review-management__metric-note">Trên toàn bộ kết quả đang lọc</span>
          </div>
        </div>
      </div>

      <section className="review-management__panel" aria-label="Danh sách đánh giá">
        <div className="review-management__panel-heading">
          <Tabs
            className="review-management__tabs"
            activeKey={deleted ? 'deleted' : 'active'}
            onChange={(value) =>
              changeFilters({ view: value === 'deleted' ? 'deleted' : undefined })
            }
            items={[
              {
                key: 'active',
                label: 'Đang hiển thị',
                icon: <MessageOutlined aria-hidden="true" />,
              },
              {
                key: 'deleted',
                label: 'Lịch sử đã xóa',
                icon: <DeleteOutlined aria-hidden="true" />,
              },
            ]}
          />
          <span className="review-management__sort-note">
            <ClockCircleOutlined /> {deleted ? 'Xóa gần nhất trước' : 'Mới nhất trước'}
          </span>
        </div>
        <div className="review-management__filters">
          <div className="review-management__extended-filters">
            <div>
              <label className="review-management__filter-label" htmlFor="review-management-search">
                Tìm đánh giá
              </label>
              <Input.Search
                id="review-management-search"
                key={search ?? ''}
                defaultValue={search}
                aria-label="Tìm khách hoặc mã đặt phòng"
                placeholder="Tên khách, mã đơn hoặc mã đặt phòng"
                prefix={<SearchOutlined aria-hidden="true" />}
                enterButton="Tìm"
                allowClear
                maxLength={100}
                onSearch={(value) => changeFilters({ search: value.trim() || undefined })}
              />
            </div>
            <div>
              <span className="review-management__filter-label">Trạng thái phản hồi</span>
              <Select
                aria-label="Lọc trạng thái phản hồi"
                value={replied === undefined ? 'all' : replied ? 'replied' : 'unreplied'}
                options={[
                  { value: 'all', label: 'Tất cả trạng thái' },
                  { value: 'unreplied', label: 'Chưa phản hồi' },
                  { value: 'replied', label: 'Đã phản hồi' },
                ]}
                onChange={(value) =>
                  changeFilters({
                    replied: value === 'all' ? undefined : String(value === 'replied'),
                  })
                }
              />
            </div>
            <div>
              <span className="review-management__filter-label">Ngày đánh giá</span>
              <DatePicker.RangePicker
                classNames={{ popup: { root: 'review-management-date-popup' } }}
                aria-label="Lọc ngày đánh giá"
                format="DD/MM/YYYY"
                placeholder={['Từ ngày', 'Đến ngày']}
                allowEmpty={[true, true]}
                value={[fromDate ? dayjs(fromDate) : null, toDate ? dayjs(toDate) : null]}
                onChange={(dates) =>
                  changeFilters({
                    fromDate: dates?.[0]?.format('YYYY-MM-DD'),
                    toDate: dates?.[1]?.format('YYYY-MM-DD'),
                  })
                }
              />
            </div>
          </div>
          <div className="review-management__type-filter">
            <span className="review-management__filter-label">Hạng phòng</span>
            <Select
              aria-label="Lọc theo hạng phòng"
              placeholder="Tất cả hạng phòng"
              allowClear
              showSearch
              optionFilterProp="label"
              value={roomTypeId}
              loading={types.isLoading}
              options={types.data?.map((type) => ({ value: type.id, label: type.name }))}
              onChange={(value?: string) => changeFilters({ roomTypeId: value })}
            />
          </div>
          <div className="review-management__rating-filter">
            <span className="review-management__filter-label">Mức đánh giá</span>
            <div
              className="review-management__rating-options"
              role="group"
              aria-label="Lọc theo số sao"
            >
              {[0, 5, 4, 3, 2, 1].map((value) => (
                <button
                  type="button"
                  key={value}
                  aria-label={value ? `Lọc ${value} sao` : 'Tất cả số sao'}
                  aria-pressed={(rating ?? 0) === value}
                  onClick={() => changeFilters({ rating: value ? String(value) : undefined })}
                >
                  {value || 'Tất cả'}
                  {value > 0 && <StarFilled />}
                </button>
              ))}
            </div>
          </div>
          {hasFilters && (
            <Button
              type="text"
              className="review-management__clear"
              icon={<ClearOutlined aria-hidden="true" />}
              onClick={() =>
                changeFilters({
                  roomTypeId: undefined,
                  rating: undefined,
                  replied: undefined,
                  search: undefined,
                  fromDate: undefined,
                  toDate: undefined,
                })
              }
            >
              Xóa bộ lọc
            </Button>
          )}
        </div>
        <div className="review-management__list-caption">
          <span>
            {deleted
              ? 'Các đánh giá đã gỡ được lưu lại cùng lý do và người xử lý.'
              : 'Đánh giá từ khách đã hoàn tất lưu trú.'}
          </span>
          <span>{query.data ? `${query.data.totalElements} kết quả` : 'Đang tải…'}</span>
        </div>
        {types.isError && (
          <Alert
            type="warning"
            title="Không tải được danh sách hạng phòng"
            action={<Button onClick={() => void types.refetch()}>Thử lại</Button>}
          />
        )}
        {summary.isError && (
          <Alert
            type="warning"
            title="Không tải được thống kê đánh giá"
            action={<Button onClick={() => void summary.refetch()}>Tải lại thống kê</Button>}
          />
        )}
        {query.isError && (
          <Alert
            type="error"
            title={query.error.message}
            action={<Button onClick={() => void query.refetch()}>Thử lại</Button>}
          />
        )}
        <Table<ManagedReview>
          className="review-management__table"
          rowKey={(row) => row.review.id}
          loading={query.isLoading}
          dataSource={query.data?.content ?? []}
          rowClassName={(row) =>
            row.deletedAt
              ? 'managed-review-row--deleted'
              : row.review.rating <= 2
                ? 'managed-review-row--attention'
                : ''
          }
          scroll={{ x: 920 }}
          locale={{
            emptyText: query.isError ? (
              'Không thể tải đánh giá'
            ) : (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description={
                  <>
                    <strong>Chưa có đánh giá phù hợp</strong>
                    <p>Thử thay đổi hạng phòng hoặc mức đánh giá để xem thêm kết quả.</p>
                  </>
                }
              />
            ),
          }}
          pagination={{
            current: query.data?.page !== undefined ? query.data.page + 1 : page + 1,
            pageSize: 20,
            total: query.data?.totalElements,
            showSizeChanger: false,
            showTotal: (total, range) => `${range[0]}–${range[1]} / ${total} đánh giá`,
            onChange: (value) => setPage(value - 1),
          }}
          columns={[
            {
              title: 'Khách & hạng phòng',
              width: 235,
              render: (_, row) => <GuestIdentity row={row} />,
            },
            {
              title: 'Nội dung đánh giá',
              render: (_, row) => (
                <div className="managed-review-cell">
                  <ReviewContent row={row} />
                  {row.review.hotelReply && (
                    <div className="managed-review-reply-preview">
                      <CommentOutlined />
                      <span>Khách sạn: {row.review.hotelReply}</span>
                    </div>
                  )}
                  {row.deletedAt && (
                    <div className="managed-review-deletion-preview">
                      <span>Lý do xóa</span>
                      <p>{row.deleteReason || 'Dữ liệu cũ chưa có lý do'}</p>
                    </div>
                  )}
                </div>
              ),
            },
            {
              title: 'Trạng thái',
              width: 160,
              render: (_, row) => (
                <div className="managed-review-state">
                  <ReviewStatus row={row} />
                  {row.deletedAt ? (
                    <div className="managed-review-state__audit">
                      <span>{row.deletedByName}</span>
                      <time dateTime={row.deletedAt}>
                        {dayjs(row.deletedAt).format('HH:mm DD/MM/YYYY')}
                      </time>
                    </div>
                  ) : row.review.rating <= 2 ? (
                    <span className="managed-review-attention">
                      <ExclamationCircleOutlined /> Cần chú ý
                    </span>
                  ) : null}
                </div>
              ),
            },
            {
              title: 'Thao tác',
              width: 180,
              render: (_, row) => (
                <div className="managed-review-actions">
                  {!deleted && (
                    <Button
                      className={row.review.hotelReply ? '' : 'managed-review-actions__reply'}
                      icon={<CommentOutlined aria-hidden="true" />}
                      onClick={() => openReply(row)}
                    >
                      {row.review.hotelReply ? 'Sửa phản hồi' : 'Phản hồi'}
                    </Button>
                  )}
                  <div className="managed-review-actions__secondary">
                    <Button
                      type="text"
                      size="small"
                      icon={<EyeOutlined aria-hidden="true" />}
                      onClick={() => setSelected(row)}
                    >
                      Chi tiết lưu trú
                    </Button>
                    {!deleted && (
                      <Tooltip title="Xóa đánh giá">
                        <Button
                          type="text"
                          size="small"
                          className="managed-review-actions__delete"
                          danger
                          aria-label="Xóa"
                          icon={<DeleteOutlined aria-hidden="true" />}
                          disabled={remove.isPending}
                          onClick={() => {
                            setDeleting(row);
                            deleteForm.resetFields();
                          }}
                        />
                      </Tooltip>
                    )}
                  </div>
                </div>
              ),
            },
          ]}
        />
      </section>
      <Drawer
        title="Chi tiết đánh giá và lần lưu trú"
        className="managed-review-drawer"
        open={Boolean(selected)}
        onClose={() => setSelected(undefined)}
        size="large"
        footer={
          selected && !selected.deletedAt ? (
            <div className="managed-review-drawer__footer">
              <Button
                danger
                icon={<DeleteOutlined />}
                onClick={() => {
                  setDeleting(selected);
                  deleteForm.resetFields();
                }}
              >
                Xóa đánh giá
              </Button>
              <Button type="primary" icon={<CommentOutlined />} onClick={() => openReply(selected)}>
                {selected.review.hotelReply ? 'Sửa phản hồi' : 'Phản hồi khách'}
              </Button>
            </div>
          ) : null
        }
      >
        {selected && (
          <Space orientation="vertical" size="large" style={{ width: '100%' }}>
            <div className="managed-review-drawer__review">
              <div className="managed-review-drawer__identity">
                <GuestIdentity row={selected} />
                <ReviewStatus row={selected} />
              </div>
              <ReviewContent row={selected} expanded />
            </div>
            {selected.review.hotelReply && (
              <section className="managed-review-drawer__reply">
                <h3>
                  <CommentOutlined /> Phản hồi của khách sạn
                </h3>
                <p>{selected.review.hotelReply}</p>
                <span>
                  {selected.repliedByName || 'Khách sạn'} ·{' '}
                  {selected.review.repliedAt
                    ? dayjs(selected.review.repliedAt).format('HH:mm DD/MM/YYYY')
                    : 'Chưa có thời gian phản hồi'}
                </span>
              </section>
            )}
            {selected.deletedAt && (
              <Alert
                type="warning"
                showIcon
                title={`Đã xóa bởi ${selected.deletedByName}`}
                description={
                  <>
                    <span>{dayjs(selected.deletedAt).format('HH:mm DD/MM/YYYY')}</span>
                    <p>{selected.deleteReason || 'Dữ liệu cũ chưa có lý do'}</p>
                  </>
                }
              />
            )}
            <h3 className="managed-review-drawer__section-title">
              <HomeOutlined /> Thông tin lưu trú
            </h3>
            <Descriptions
              column={1}
              bordered
              size="small"
              items={[
                {
                  key: 'order',
                  label: 'Mã đơn',
                  children: selected.orderCode || 'Đặt phòng đơn lẻ',
                },
                { key: 'code', label: 'Booking dùng đánh giá', children: selected.bookingCode },
                {
                  key: 'stays',
                  label: 'Số phòng trong đánh giá',
                  children: `${selected.stays.length} phòng`,
                },
              ]}
            />
            {selected.stays.map((stay) => (
              <Card
                className="managed-review-stay"
                size="small"
                title={stay.bookingCode}
                key={stay.bookingCode}
              >
                <Descriptions
                  column={1}
                  items={[
                    {
                      key: 'room',
                      label: 'Phòng được phân',
                      children: stay.roomNumber || 'Chưa có phòng',
                    },
                    {
                      key: 'dates',
                      label: 'Kỳ lưu trú',
                      children: `${dayjs(stay.checkInDate).format('DD/MM/YYYY')} – ${dayjs(stay.checkOutDate).format('DD/MM/YYYY')}`,
                    },
                    {
                      key: 'in',
                      label: 'Nhận phòng thực tế',
                      children: stay.checkedInAt
                        ? dayjs(stay.checkedInAt).format('HH:mm DD/MM/YYYY')
                        : 'Chưa nhận',
                    },
                    {
                      key: 'out',
                      label: 'Trả phòng thực tế',
                      children: stay.checkedOutAt
                        ? dayjs(stay.checkedOutAt).format('HH:mm DD/MM/YYYY')
                        : 'Chưa trả',
                    },
                  ]}
                />
              </Card>
            ))}
          </Space>
        )}
      </Drawer>
      <Modal
        title="Xóa đánh giá này?"
        className="managed-review-modal"
        open={Boolean(deleting)}
        onCancel={() => !remove.isPending && setDeleting(undefined)}
        okText="Xóa đánh giá"
        cancelText="Giữ lại"
        okButtonProps={{ danger: true }}
        confirmLoading={remove.isPending}
        onOk={() => deleteForm.submit()}
      >
        <Typography.Paragraph>
          Đánh giá sẽ bị gỡ khỏi trang hạng phòng và điểm trung bình. Nội dung và lý do xóa được giữ
          trong lịch sử.
        </Typography.Paragraph>
        {deleting && (
          <div className="managed-review-modal__context">
            <GuestIdentity row={deleting} />
            <ReviewContent row={deleting} />
          </div>
        )}
        <Form form={deleteForm} layout="vertical" onFinish={(values) => remove.mutate(values)}>
          <Form.Item
            name="reason"
            label="Lý do xóa"
            rules={[
              { required: true, whitespace: true, message: 'Vui lòng nhập lý do xóa' },
              { max: 500 },
            ]}
          >
            <Input.TextArea maxLength={500} rows={3} showCount />
          </Form.Item>
        </Form>
      </Modal>
      <Modal
        title="Phản hồi từ HANBAY Hotel"
        className="managed-review-modal"
        open={Boolean(replying)}
        onCancel={() => !saveReply.isPending && setReplying(undefined)}
        okText="Lưu phản hồi"
        cancelText="Đóng"
        confirmLoading={saveReply.isPending}
        onOk={() => replyForm.submit()}
      >
        {replying && (
          <div className="managed-review-modal__context">
            <GuestIdentity row={replying} />
            <ReviewContent row={replying} expanded />
          </div>
        )}
        <Form form={replyForm} layout="vertical" onFinish={(values) => saveReply.mutate(values)}>
          <Form.Item
            name="reply"
            label="Nội dung phản hồi"
            extra="Phản hồi sẽ hiển thị công khai cùng đánh giá trên trang hạng phòng."
            rules={[
              { required: true, whitespace: true, message: 'Nhập nội dung phản hồi' },
              { max: 1000 },
            ]}
          >
            <Input.TextArea rows={5} maxLength={1000} showCount />
          </Form.Item>
        </Form>
        {replying?.review.hotelReply && (
          <Popconfirm
            title="Gỡ phản hồi của khách sạn?"
            okText="Gỡ phản hồi"
            cancelText="Giữ lại"
            onConfirm={async () => {
              try {
                await removeReply.mutateAsync(replying.review.id);
                setReplying(undefined);
              } catch {
                // The mutation displays the error and keeps the editor open for retry.
              }
            }}
          >
            <Button danger loading={removeReply.isPending}>
              Gỡ phản hồi
            </Button>
          </Popconfirm>
        )}
      </Modal>
    </div>
  );
}
