import { useTableSorting } from '../../../hooks/use-table-sorting';
import {
  App,
  Alert,
  Descriptions,
  Button,
  Card,
  Empty,
  Form,
  Input,
  Modal,
  Pagination,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { useState } from 'react';

import { confirmRefund, getPendingRefunds } from '../api/refund-api';
import type { RefundRequest } from '../types/refund';

const PAGE_SIZE = 10;
const money = new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' });

export function RefundRequestsPage() {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);
  const sorting = useTableSorting<RefundRequest>(() => setPage(0));
  const [selected, setSelected] = useState<RefundRequest | null>(null);
  const [form] = Form.useForm<{ bankReference: string }>();
  const refundQuery = useQuery({
    queryKey: ['refund-requests', page, sorting.sort],
    queryFn: () => getPendingRefunds(page, PAGE_SIZE, sorting.sort),
  });
  const confirmMutation = useMutation({
    mutationFn: (bankReference: string) => confirmRefund(selected!.refundId, bankReference),
    onSuccess: async () => {
      void message.success('Đã xác nhận hoàn tiền cho khách.');
      setSelected(null);
      form.resetFields();
      await queryClient.invalidateQueries({ queryKey: ['refund-requests'] });
    },
    onError: (error: Error) => void message.error(error.message),
  });

  return (
    <Space orientation="vertical" size="large" style={{ width: '100%' }}>
      <div>
        <Typography.Title level={2}>Yêu cầu hoàn tiền</Typography.Title>
        <Typography.Text type="secondary">
          Xác nhận sau khi Manager đã chuyển đúng số tiền do hệ thống tính cho khách.
        </Typography.Text>
      </div>
      {refundQuery.isError && (
        <Alert
          type="error"
          title={refundQuery.error.message}
          action={<Button onClick={() => refundQuery.refetch()}>Thử lại</Button>}
        />
      )}
      <Card>
        <Table<RefundRequest>
          onChange={sorting.onChange}
          rowKey="refundId"
          loading={refundQuery.isLoading}
          dataSource={refundQuery.data?.content ?? []}
          pagination={false}
          scroll={{ x: 1100 }}
          locale={{ emptyText: <Empty description="Không có yêu cầu hoàn tiền đang chờ" /> }}
          columns={[
            { title: 'Mã đặt phòng', ...sorting.column('bookingCode'), dataIndex: 'bookingCode' },
            { title: 'Mã thanh toán', ...sorting.column('paymentCode'), dataIndex: 'paymentCode' },
            {
              title: 'Số tiền',
              ...sorting.column('amount'),
              dataIndex: 'amount',
              render: (value: number) => money.format(value),
            },
            {
              title: 'Tài khoản nhận hoàn',
              ...sorting.column('bankAccount'),
              render: (_, refund) =>
                refund.bankName && refund.accountNumber && refund.accountHolderName ? (
                  <Space orientation="vertical" size={0}>
                    <Typography.Text>{refund.bankName}</Typography.Text>
                    <Typography.Text copyable>{refund.accountNumber}</Typography.Text>
                    <Typography.Text>{refund.accountHolderName}</Typography.Text>
                  </Space>
                ) : (
                  <Tag color="warning">Chưa có thông tin ngân hàng</Tag>
                ),
            },
            { title: 'Lý do', ...sorting.column('reason'), dataIndex: 'reason' },
            {
              title: 'Yêu cầu lúc',
              ...sorting.column('requestedAt'),
              dataIndex: 'requestedAt',
              render: (value: string) => dayjs(value).format('DD/MM/YYYY HH:mm'),
            },
            {
              title: 'Trạng thái',
              ...sorting.column('status'),
              render: () => <Tag color="orange">Chờ hoàn tiền</Tag>,
            },
            {
              title: 'Thao tác',
              render: (_, refund) => (
                <Button
                  type="primary"
                  onClick={() => {
                    form.resetFields();
                    setSelected(refund);
                  }}
                >
                  Xác nhận đã hoàn
                </Button>
              ),
            },
          ]}
        />
        {refundQuery.data && refundQuery.data.totalElements > PAGE_SIZE && (
          <Pagination
            current={refundQuery.data.page + 1}
            pageSize={PAGE_SIZE}
            total={refundQuery.data.totalElements}
            showSizeChanger={false}
            align="center"
            onChange={(nextPage) => setPage(nextPage - 1)}
          />
        )}
      </Card>
      <Modal
        title="Xác nhận đã hoàn tiền"
        open={selected !== null}
        okText="Xác nhận"
        cancelText="Hủy"
        confirmLoading={confirmMutation.isPending}
        onCancel={() => setSelected(null)}
        onOk={() =>
          void form
            .validateFields()
            .then(({ bankReference }) => confirmMutation.mutate(bankReference))
        }
      >
        <Typography.Paragraph>
          Số tiền cần hoàn:{' '}
          <Typography.Text strong>{money.format(selected?.amount ?? 0)}</Typography.Text>
        </Typography.Paragraph>
        <Descriptions
          column={1}
          items={[
            { key: 'booking', label: 'Mã đặt phòng', children: selected?.bookingCode },
            {
              key: 'bank',
              label: 'Ngân hàng nhận',
              children: selected?.bankName || 'Chưa cung cấp',
            },
            {
              key: 'holder',
              label: 'Chủ tài khoản',
              children: selected?.accountHolderName || 'Chưa cung cấp',
            },
            {
              key: 'account',
              label: 'Số tài khoản',
              children: selected?.accountNumber ? (
                <Typography.Text copyable>{selected.accountNumber}</Typography.Text>
              ) : (
                'Chưa cung cấp'
              ),
            },
          ]}
        />
        {selected &&
          (!selected.bankName || !selected.accountNumber || !selected.accountHolderName) && (
            <Alert
              type="warning"
              showIcon
              title="Yêu cầu này chưa lưu đầy đủ tài khoản nhận hoàn. Liên hệ khách để xác minh người nhận trước khi chuyển tiền; chỉ xác nhận sau khi thực sự hoàn tiền."
            />
          )}
        <Form form={form} layout="vertical">
          <Form.Item
            name="bankReference"
            label="Mã tham chiếu ngân hàng"
            rules={[
              {
                required: true,
                whitespace: true,
                message: 'Vui lòng nhập mã tham chiếu ngân hàng',
              },
              { max: 120, message: 'Mã tham chiếu không được vượt quá 120 ký tự' },
            ]}
          >
            <Input placeholder="Nhập mã giao dịch chuyển khoản hoàn tiền" />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}
