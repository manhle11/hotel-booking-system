import { useTableSorting } from '../../hooks/use-table-sorting';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  App,
  Button,
  Form,
  Image,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Radio,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  Upload,
} from 'antd';
import { apiClient } from '../../lib/axios';
import type { ApiResponse, PageResponse } from '../../types/api';
import { roomTypeCatalogKeys, roomTypeCheckoutKeys } from '../room/api/room-type-query-keys';
import {
  BED_TYPE_LABELS,
  ROOM_VIEW_LABELS,
  type RoomCategory,
  type RoomTypeBed,
  type RoomViewType,
} from '../room/types/room-configuration';

export interface ManagedRoomType {
  id: string;
  code: string;
  name: string;
  description: string;
  shortDescription: string;
  basePrice: number;
  maxGuests: number;
  capacityAdults: number;
  capacityChildren: number;
  bedType: string | null;
  areaM2: number | null;
  status: 'ACTIVE' | 'INACTIVE';
  images: string[];
  amenityIds: string[];
  category: RoomCategory | null;
  beds: RoomTypeBed[];
  bedConfigurationStatus: 'CONFIGURED' | 'UNCONFIGURED';
  viewType?: RoomViewType | null;
  hasBalcony?: boolean | null;
  highFloor?: boolean | null;
}

type RoomTypeFormValues = Omit<
  ManagedRoomType,
  'id' | 'bedType' | 'category' | 'bedConfigurationStatus'
> & { categoryId?: string };

const bedOptions = (['SINGLE', 'DOUBLE', 'QUEEN', 'KING'] as const).map((value) => ({
  value,
  label: `${BED_TYPE_LABELS[value]} (${value})`,
}));

const endpoint = '/management/room-types';
export function RoomTypeManagementPage() {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);
  const sorting = useTableSorting<ManagedRoomType>(() => setPage(0));
  const [editing, setEditing] = useState<ManagedRoomType | null | undefined>();
  const [uploading, setUploading] = useState(false);
  const [form] = Form.useForm<RoomTypeFormValues>();
  const images = Form.useWatch('images', form) ?? [];
  const beds: RoomTypeBed[] = Form.useWatch('beds', form) ?? [];
  const query = useQuery({
    queryKey: ['managed-room-types', page, sorting.sort],
    queryFn: async () =>
      (
        await apiClient.get<ApiResponse<PageResponse<ManagedRoomType>>>(endpoint, {
          params: { page, size: 20, ...(sorting.sort ? { sort: sorting.sort } : {}) },
        })
      ).data.data,
  });
  const amenities = useQuery({
    queryKey: ['amenities'],
    queryFn: async () =>
      (await apiClient.get<ApiResponse<{ id: string; name: string }[]>>(`${endpoint}/amenities`))
        .data.data,
  });
  const categories = useQuery({
    queryKey: ['managed-room-categories'],
    queryFn: async () =>
      (await apiClient.get<ApiResponse<RoomCategory[]>>('/management/room-categories')).data.data,
    enabled: editing !== undefined,
  });
  const refresh = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['managed-room-types'] }),
      queryClient.invalidateQueries({ queryKey: ['room-types'] }),
      queryClient.invalidateQueries({ queryKey: ['managed-room-type-options'] }),
      queryClient.invalidateQueries({ queryKey: ['available-room-types'] }),
      queryClient.invalidateQueries({ queryKey: roomTypeCatalogKeys.all }),
      queryClient.invalidateQueries({ queryKey: roomTypeCheckoutKeys.all }),
    ]);
  };
  const save = useMutation({
    mutationFn: async (values: RoomTypeFormValues) => {
      const payload = {
        ...values,
        // An empty legacy configuration stays unknown until the manager explicitly adds beds.
        beds: editing && !values.beds?.length ? undefined : values.beds,
      };
      return editing
        ? apiClient.put(`${endpoint}/${editing.id}`, payload)
        : apiClient.post(endpoint, payload);
    },
    onSuccess: async () => {
      setEditing(undefined);
      await refresh();
      message.success('Đã lưu loại phòng');
    },
    onError: (error) => message.error(error.message),
  });
  const remove = useMutation({
    mutationFn: (id: string) => apiClient.delete(`${endpoint}/${id}`),
    onSuccess: async () => {
      await refresh();
      message.success('Đã ngừng kinh doanh loại phòng');
    },
    onError: (error) => message.error(error.message),
  });
  const open = (record: ManagedRoomType | null) => {
    form.resetFields();
    form.setFieldsValue(
      record
        ? {
            code: record.code,
            name: record.name,
            description: record.description,
            shortDescription: record.shortDescription,
            basePrice: record.basePrice,
            maxGuests: record.maxGuests,
            capacityAdults: record.capacityAdults,
            capacityChildren: record.capacityChildren,
            areaM2: record.areaM2,
            status: record.status,
            images: record.images,
            amenityIds: record.amenityIds,
            categoryId: record.category?.id,
            beds: record.beds?.map((bed) => ({ ...bed })) ?? [],
            viewType: record.viewType ?? undefined,
            hasBalcony: record.hasBalcony ?? undefined,
            highFloor: record.highFloor ?? undefined,
          }
        : {
            code: '',
            name: '',
            basePrice: 0,
            maxGuests: 2,
            capacityAdults: 2,
            capacityChildren: 0,
            status: 'ACTIVE',
            images: [],
            amenityIds: [],
            beds: [{ quantity: 1 }],
            hasBalcony: false,
            highFloor: false,
          },
    );
    setEditing(record);
  };
  return (
    <Space orientation="vertical" size="large" style={{ width: '100%' }}>
      <Space style={{ justifyContent: 'space-between', width: '100%' }}>
        <Typography.Title level={2}>Quản lý loại phòng</Typography.Title>
        <Button type="primary" onClick={() => open(null)}>
          Thêm loại phòng
        </Button>
      </Space>
      {query.isError && (
        <Alert
          type="error"
          title={query.error.message}
          action={<Button onClick={() => query.refetch()}>Thử lại</Button>}
        />
      )}
      <Table<ManagedRoomType>
        onChange={sorting.onChange}
        rowKey="id"
        loading={query.isLoading}
        dataSource={query.data?.content}
        scroll={{ x: 800 }}
        pagination={{
          current: page + 1,
          pageSize: 20,
          total: query.data?.totalElements,
          showSizeChanger: false,
          onChange: (value) => setPage(value - 1),
        }}
        columns={[
          {
            title: 'Ảnh',
            render: (_, r) =>
              r.images[0] ? (
                <Image width={80} height={55} style={{ objectFit: 'cover' }} src={r.images[0]} />
              ) : (
                'Chưa có ảnh'
              ),
          },
          { title: 'Mã', ...sorting.column('code'), dataIndex: 'code' },
          { title: 'Tên loại phòng', ...sorting.column('name'), dataIndex: 'name' },
          { title: 'Nhóm phòng', render: (_, room) => room.category?.name ?? 'Chưa phân nhóm' },
          {
            title: 'Giường',
            render: (_, room) =>
              room.beds?.length
                ? room.beds
                    .map((bed) => `${bed.quantity} ${BED_TYPE_LABELS[bed.bedType]}`)
                    .join(' + ')
                : room.bedType || 'Chưa xác minh',
          },
          {
            title: 'Giá / đêm',
            ...sorting.column('basePrice'),
            dataIndex: 'basePrice',
            render: (v) => `${Number(v).toLocaleString('vi-VN')} ₫`,
          },
          { title: 'Số khách', ...sorting.column('maxGuests'), dataIndex: 'maxGuests' },
          {
            title: 'Trạng thái',
            ...sorting.column('status'),
            dataIndex: 'status',
            render: (v) => (
              <Tag color={v === 'ACTIVE' ? 'green' : 'default'}>
                {v === 'ACTIVE' ? 'Đang kinh doanh' : 'Ngừng kinh doanh'}
              </Tag>
            ),
          },
          {
            title: 'Thao tác',
            render: (_, r) => (
              <Space>
                <Button onClick={() => open(r)}>Chi tiết / Sửa</Button>
                <Link to={`/management/reviews?roomTypeId=${r.id}`}>
                  <Button>Đánh giá</Button>
                </Link>
                <Popconfirm
                  title="Ngừng kinh doanh loại phòng này?"
                  description="Các phòng vật lý và lịch sử đặt phòng được giữ nguyên."
                  onConfirm={() => remove.mutateAsync(r.id)}
                >
                  <Button danger disabled={r.status === 'INACTIVE'} loading={remove.isPending}>
                    Ngừng kinh doanh
                  </Button>
                </Popconfirm>
              </Space>
            ),
          },
        ]}
      />
      <Modal
        title={editing ? 'Chi tiết loại phòng' : 'Thêm loại phòng'}
        open={editing !== undefined}
        onCancel={() => setEditing(undefined)}
        onOk={() => form.submit()}
        confirmLoading={save.isPending}
        okButtonProps={{ disabled: uploading }}
        okText="Lưu"
        cancelText="Hủy"
        width={760}
        forceRender
      >
        <Form form={form} layout="vertical" onFinish={(values) => save.mutate(values)}>
          <Form.Item
            name="code"
            label="Mã loại phòng"
            rules={[
              {
                required: true,
                pattern: /^[A-Z0-9_-]{1,50}$/,
                message: 'Dùng chữ hoa, số, dấu gạch ngang hoặc gạch dưới (tối đa 50 ký tự)',
              },
            ]}
          >
            <Input maxLength={50} />
          </Form.Item>
          <Form.Item
            name="name"
            label="Tên loại phòng"
            rules={[{ required: true, whitespace: true }]}
          >
            <Input maxLength={150} />
          </Form.Item>
          {categories.isError && (
            <Alert
              type="error"
              title="Không tải được nhóm phòng"
              action={<Button onClick={() => categories.refetch()}>Thử lại</Button>}
              style={{ marginBottom: 16 }}
            />
          )}
          <Form.Item
            name="categoryId"
            label="Nhóm phòng"
            rules={[{ required: !editing, message: 'Vui lòng chọn nhóm phòng.' }]}
          >
            <Select
              showSearch
              optionFilterProp="label"
              placeholder="Chọn nhóm phòng"
              loading={categories.isLoading}
              options={categories.data?.map((category) => ({
                value: category.id,
                label: category.name,
              }))}
            />
          </Form.Item>
          <Form.Item name="shortDescription" label="Mô tả ngắn">
            <Input.TextArea maxLength={500} />
          </Form.Item>
          <Form.Item name="description" label="Mô tả chi tiết">
            <Input.TextArea rows={3} maxLength={10000} />
          </Form.Item>
          <Space wrap align="start">
            <Form.Item name="basePrice" label="Giá cơ bản (VND)" rules={[{ required: true }]}>
              <InputNumber min={0} precision={0} style={{ width: 180 }} />
            </Form.Item>
            <Form.Item name="maxGuests" label="Tối đa khách" rules={[{ required: true }]}>
              <InputNumber min={1} max={100} precision={0} />
            </Form.Item>
            <Form.Item name="capacityAdults" label="Người lớn" rules={[{ required: true }]}>
              <InputNumber min={1} max={100} precision={0} />
            </Form.Item>
            <Form.Item name="capacityChildren" label="Trẻ em" rules={[{ required: true }]}>
              <InputNumber min={0} max={100} precision={0} />
            </Form.Item>
            <Form.Item name="areaM2" label="Diện tích (m²)">
              <InputNumber min={0.01} max={9999.99} precision={2} />
            </Form.Item>
          </Space>
          <Typography.Paragraph strong>Đặc điểm phòng</Typography.Paragraph>
          <Form.Item name="viewType" label="Hướng nhìn">
            <Select
              placeholder="Chọn hướng nhìn"
              options={Object.entries(ROOM_VIEW_LABELS).map(([value, label]) => ({ value, label }))}
            />
          </Form.Item>
          <Space wrap align="start">
            <Form.Item name="hasBalcony" label="Ban công riêng">
              <Radio.Group
                aria-label="Ban công riêng"
                options={[
                  { label: 'Có', value: true },
                  { label: 'Không', value: false },
                ]}
              />
            </Form.Item>
            <Form.Item name="highFloor" label="Tầng cao">
              <Radio.Group
                aria-label="Tầng cao"
                options={[
                  { label: 'Có', value: true },
                  { label: 'Không', value: false },
                ]}
              />
            </Form.Item>
          </Space>
          <div>
            <Typography.Text strong>Cấu hình giường</Typography.Text>
          </div>
          <Typography.Paragraph type="secondary">
            Chọn loại giường và số lượng. Loại phòng đã có đặt phòng sẽ giữ nguyên cấu hình giường.
          </Typography.Paragraph>
          {editing && !editing.beds?.length && (
            <Alert
              type="info"
              title="Chưa xác minh cấu hình giường"
              description={
                <>
                  <div>Thông tin hiện có: {editing.bedType || 'Chưa có thông tin giường'}.</div>
                  <div>
                    Chỉ thêm cấu hình sau khi xác minh thực tế. Có thể giữ nguyên để cập nhật thông
                    tin khác.
                  </div>
                </>
              }
              style={{ marginBottom: 16 }}
            />
          )}
          <Form.List
            name="beds"
            rules={[
              {
                validator: async (_, value: RoomTypeBed[] | undefined) => {
                  if ((!editing || editing.beds?.length) && !value?.length) {
                    throw new Error('Vui lòng thêm ít nhất một loại giường.');
                  }
                  const kinds = value?.map((bed) => bed?.bedType).filter(Boolean) ?? [];
                  if (new Set(kinds).size !== kinds.length) {
                    throw new Error('Mỗi loại giường chỉ được chọn một lần.');
                  }
                },
              },
            ]}
          >
            {(fields, { add, remove }, { errors }) => (
              <>
                {fields.map(({ key, name, ...restField }) => (
                  <div
                    key={key}
                    style={{ display: 'flex', flexWrap: 'wrap', gap: 12, alignItems: 'flex-start' }}
                  >
                    <Form.Item
                      {...restField}
                      name={[name, 'bedType']}
                      label="Loại giường"
                      rules={[{ required: true, message: 'Vui lòng chọn loại giường.' }]}
                      style={{ flex: '1 1 240px', minWidth: 0 }}
                    >
                      <Select
                        aria-label={`Loại giường ${name + 1}`}
                        placeholder="Chọn loại giường"
                        options={bedOptions.map((option) => ({
                          ...option,
                          disabled: beds.some(
                            (bed, index) => index !== name && bed?.bedType === option.value,
                          ),
                        }))}
                      />
                    </Form.Item>
                    <Form.Item
                      {...restField}
                      name={[name, 'quantity']}
                      label="Số lượng"
                      rules={[
                        { required: true, message: 'Vui lòng nhập số lượng.' },
                        {
                          type: 'integer',
                          min: 1,
                          message: 'Số lượng phải là số nguyên lớn hơn 0.',
                        },
                      ]}
                    >
                      <InputNumber
                        min={1}
                        precision={0}
                        aria-label={`Số lượng giường ${name + 1}`}
                      />
                    </Form.Item>
                    <Button
                      danger
                      aria-label={`Bỏ loại giường ${name + 1}`}
                      onClick={() => remove(name)}
                      style={{ marginTop: 30 }}
                    >
                      Bỏ
                    </Button>
                  </div>
                ))}
                <Form.ErrorList errors={errors} />
                <Button
                  type="dashed"
                  onClick={() => add({ quantity: 1 })}
                  disabled={fields.length >= bedOptions.length}
                  style={{ marginBottom: 24 }}
                >
                  Thêm loại giường
                </Button>
              </>
            )}
          </Form.List>
          <Form.Item name="status" label="Trạng thái" rules={[{ required: true }]}>
            <Select
              options={[
                { value: 'ACTIVE', label: 'Đang kinh doanh' },
                { value: 'INACTIVE', label: 'Ngừng kinh doanh' },
              ]}
            />
          </Form.Item>
          <Form.Item name="amenityIds" label="Tiện nghi">
            <Select
              mode="multiple"
              loading={amenities.isLoading}
              options={amenities.data?.map((a) => ({ value: a.id, label: a.name }))}
            />
          </Form.Item>
          <Typography.Paragraph type="secondary">
            Tiện nghi có sẵn như Wi-Fi, TV, điều hòa được quản lý tại đây. Dịch vụ đưa đón, trang
            trí phòng và bữa ăn mua thêm được quản lý tại Dịch vụ bổ sung.
          </Typography.Paragraph>
          <Form.Item name="images" hidden>
            <Input />
          </Form.Item>
          <Typography.Paragraph>
            Ảnh JPEG / PNG, tối đa 5 MB mỗi ảnh. Ảnh đầu tiên là ảnh đại diện.
          </Typography.Paragraph>
          <Space wrap>
            {images.map((url: string, i: number) => (
              <Space orientation="vertical" key={`${url}-${i}`}>
                <Image src={url} width={100} height={75} style={{ objectFit: 'cover' }} />
                <Space>
                  <Button
                    size="small"
                    disabled={i === 0 || uploading}
                    onClick={() =>
                      form.setFieldValue('images', [
                        url,
                        ...images.filter((_: string, index: number) => index !== i),
                      ])
                    }
                  >
                    Đại diện
                  </Button>
                  <Button
                    size="small"
                    danger
                    disabled={uploading}
                    onClick={() =>
                      form.setFieldValue(
                        'images',
                        images.filter((_: string, index: number) => index !== i),
                      )
                    }
                  >
                    Bỏ
                  </Button>
                </Space>
              </Space>
            ))}
          </Space>
          <Upload
            accept="image/jpeg,image/png"
            showUploadList={false}
            disabled={uploading || images.length >= 20}
            beforeUpload={async (file) => {
              if (file.size > 5 * 1024 * 1024) {
                message.error('Ảnh vượt quá 5 MB');
                return false;
              }
              setUploading(true);
              try {
                const data = new FormData();
                data.append('file', file);
                const response = await apiClient.post<ApiResponse<{ url: string }>>(
                  '/media',
                  data,
                  { headers: { 'Content-Type': undefined }, timeout: 60000 },
                );
                form.setFieldValue('images', [
                  ...form.getFieldValue('images'),
                  response.data.data.url,
                ]);
              } catch (error) {
                message.error(error instanceof Error ? error.message : 'Tải ảnh thất bại');
              } finally {
                setUploading(false);
              }
              return false;
            }}
          >
            <Button loading={uploading} style={{ marginTop: 12 }}>
              Tải ảnh lên
            </Button>
          </Upload>
        </Form>
      </Modal>
    </Space>
  );
}
