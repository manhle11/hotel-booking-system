import {
  EditOutlined,
  LockOutlined,
  PlusOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
  UnlockOutlined,
} from '@ant-design/icons';
import {
  Alert,
  App,
  Button,
  Card,
  Empty,
  Flex,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import type { FormInstance, TableProps } from 'antd';
import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useAuth } from '../authentication/providers/auth-context';
import { ApiClientError } from '../../types/api';
import type { UserRole } from '../../types/common';
import type { UserStatus } from '../profile/types/profile';
import { AccountForm } from './account-form';
import { roleRules, toAccountRoles } from './role-rules';
import type { AccountFormValues } from './account-form';
import { useAccountMutations, useAccountRoles, useAccounts } from './hooks';
import type { Account, AccountRoleValue } from './types';
import { ROLE_LABELS, STATUS_LABELS } from './types';

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : 'Không thể thực hiện thao tác. Vui lòng thử lại.';
}

export function AccountManagementPage() {
  const { message, modal } = App.useApp();
  const { session, updateUser } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState<UserStatus>();
  const [editor, setEditor] = useState<Account | 'create' | null>(null);
  const [roleAccount, setRoleAccount] = useState<Account | null>(null);
  const [roleForm] = Form.useForm<{ role: AccountRoleValue }>();
  const role = searchParams.get('role') || undefined;
  const accounts = useAccounts({ page, size: 10, search: search || undefined, status, role });
  const roles = useAccountRoles();
  const mutations = useAccountMutations();

  const submitAccount = (values: AccountFormValues, form: FormInstance<AccountFormValues>) => {
    const onError = (error: unknown) => {
      if (error instanceof ApiClientError && error.data?.errors) {
        const fields: (keyof AccountFormValues)[] = [
          'fullName',
          'email',
          'phone',
          'password',
          'role',
        ];
        form.setFields(
          fields.flatMap((name) => {
            const errors = error.data?.errors?.[name === 'role' ? 'roles' : name];
            return errors ? [{ name, errors: Array.isArray(errors) ? errors : [errors] }] : [];
          }),
        );
      }
      void message.error(errorMessage(error));
    };
    const onSuccess = (account: Account) => {
      if (account.id === session?.user?.id) updateUser(account);
      setEditor(null);
      setPage(0);
      void message.success('Đã lưu tài khoản.');
    };
    const details = { fullName: values.fullName.trim(), phone: values.phone?.trim() || null };
    if (editor && editor !== 'create') {
      mutations.update.mutate({ id: editor.id, payload: details }, { onError, onSuccess });
    } else {
      mutations.create.mutate(
        {
          ...details,
          email: values.email.trim(),
          password: values.password,
          roles: toAccountRoles(values.role),
        },
        { onError, onSuccess },
      );
    }
  };

  const confirmStatus = (account: Account) => {
    const blocking = account.status === 'ACTIVE';
    modal.confirm({
      title: blocking ? 'Chặn tài khoản?' : 'Kích hoạt tài khoản?',
      content: blocking
        ? `${account.fullName || account.email} sẽ bị đăng xuất và không thể đăng nhập cho đến khi được mở chặn.`
        : `${account.fullName || account.email} sẽ có thể đăng nhập trở lại.`,
      okText: blocking ? 'Chặn tài khoản' : 'Kích hoạt',
      cancelText: 'Hủy',
      okButtonProps: { danger: blocking },
      onOk: async () => {
        try {
          await mutations.status.mutateAsync({
            id: account.id,
            status: blocking ? 'LOCKED' : 'ACTIVE',
          });
          setPage(0);
          void message.success(blocking ? 'Đã chặn tài khoản.' : 'Đã kích hoạt tài khoản.');
        } catch (error) {
          void message.error(errorMessage(error));
          throw error;
        }
      },
    });
  };

  const columns: TableProps<Account>['columns'] = [
    {
      title: 'Tài khoản',
      dataIndex: 'fullName',
      width: 250,
      render: (value, account) => (
        <Space orientation="vertical" size={0}>
          <Space>
            <Typography.Text strong>{value || '—'}</Typography.Text>
            {account.id === session?.user?.id && <Tag>Bạn</Tag>}
          </Space>
          <Typography.Text type="secondary" style={{ overflowWrap: 'anywhere' }}>
            {account.email || '—'}
          </Typography.Text>
        </Space>
      ),
    },
    { title: 'Số điện thoại', dataIndex: 'phone', width: 140, render: (value) => value || '—' },
    {
      title: 'Vai trò',
      dataIndex: 'roles',
      width: 180,
      render: (values: UserRole[]) => (
        <Space size={[0, 4]} wrap>
          {values.length ? (
            values.map((value) => (
              <Tag key={value} color={value === 'ADMIN' ? 'purple' : 'blue'}>
                {ROLE_LABELS[value]}
              </Tag>
            ))
          ) : (
            <Tag>Khách hàng</Tag>
          )}
        </Space>
      ),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      width: 150,
      render: (value: UserStatus) => (
        <Tag color={value === 'ACTIVE' ? 'green' : value === 'LOCKED' ? 'red' : 'default'}>
          {STATUS_LABELS[value]}
        </Tag>
      ),
    },
    {
      title: 'Thao tác',
      key: 'actions',
      width: 350,
      fixed: 'right',
      render: (_, account) => {
        const self = account.id === session?.user?.id;
        return (
          <Space>
            <Button icon={<EditOutlined />} onClick={() => setEditor(account)}>
              Sửa
            </Button>
            <Tooltip title={self ? 'Không thể tự thay đổi vai trò.' : undefined}>
              <Button
                icon={<SafetyCertificateOutlined />}
                disabled={self || !roles.data}
                onClick={() => {
                  roleForm.setFieldsValue({ role: account.roles[0] ?? 'GUEST' });
                  setRoleAccount(account);
                }}
              >
                Phân quyền
              </Button>
            </Tooltip>
            <Tooltip title={self ? 'Không thể tự chặn tài khoản.' : undefined}>
              <Button
                danger={account.status === 'ACTIVE'}
                icon={account.status === 'ACTIVE' ? <LockOutlined /> : <UnlockOutlined />}
                disabled={self}
                loading={
                  mutations.status.isPending && mutations.status.variables?.id === account.id
                }
                onClick={() => confirmStatus(account)}
              >
                {account.status === 'ACTIVE' ? 'Chặn' : 'Kích hoạt'}
              </Button>
            </Tooltip>
          </Space>
        );
      },
    },
  ];

  return (
    <Space orientation="vertical" size="large" style={{ width: '100%' }}>
      <Flex justify="space-between" align="start" gap="middle" wrap>
        <div>
          <Typography.Title level={2}>Quản lý tài khoản</Typography.Title>
          <Typography.Text type="secondary">
            Quản lý thông tin, trạng thái và vai trò của các tài khoản trong hệ thống.
          </Typography.Text>
        </div>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          disabled={!roles.data}
          onClick={() => setEditor('create')}
        >
          Tạo tài khoản
        </Button>
      </Flex>
      {roles.isError && (
        <Alert
          type="error"
          showIcon
          title="Không tải được danh sách vai trò"
          action={<Button onClick={() => void roles.refetch()}>Thử lại</Button>}
        />
      )}
      <Card>
        <Flex gap="middle" wrap style={{ marginBottom: 24 }}>
          <Input.Search
            aria-label="Tìm tài khoản"
            placeholder="Họ tên, email hoặc số điện thoại"
            allowClear
            maxLength={150}
            style={{ width: 340, maxWidth: '100%' }}
            onSearch={(value) => {
              setSearch(value.trim());
              setPage(0);
            }}
          />
          <Select
            aria-label="Lọc trạng thái tài khoản"
            placeholder="Tất cả trạng thái"
            allowClear
            style={{ width: 190 }}
            value={status}
            options={Object.entries(STATUS_LABELS).map(([value, label]) => ({ value, label }))}
            onChange={(value: UserStatus | undefined) => {
              setStatus(value);
              setPage(0);
            }}
          />
          <Select
            aria-label="Lọc vai trò tài khoản"
            placeholder="Tất cả vai trò"
            allowClear
            style={{ width: 200 }}
            value={role}
            options={[
              { value: 'GUEST', label: 'Khách hàng' },
              ...Object.entries(ROLE_LABELS).map(([value, label]) => ({ value, label })),
            ]}
            onChange={(value: string | undefined) => {
              setSearchParams(value ? { role: value } : {});
              setPage(0);
            }}
          />
          <Button icon={<ReloadOutlined />} onClick={() => void accounts.refetch()}>
            Tải lại
          </Button>
        </Flex>
        {accounts.isError ? (
          <Alert
            type="error"
            showIcon
            title="Không tải được danh sách tài khoản"
            description={errorMessage(accounts.error)}
            action={<Button onClick={() => void accounts.refetch()}>Thử lại</Button>}
          />
        ) : (
          <Table<Account>
            rowKey="id"
            columns={columns}
            dataSource={accounts.data?.content ?? []}
            loading={accounts.isLoading}
            scroll={{ x: 1070 }}
            locale={{ emptyText: <Empty description="Không có tài khoản phù hợp" /> }}
            pagination={{
              current: page + 1,
              pageSize: 10,
              total: accounts.data?.totalElements ?? 0,
              showSizeChanger: false,
              showTotal: (total) => `${total} tài khoản`,
              onChange: (value) => setPage(value - 1),
            }}
          />
        )}
      </Card>
      {editor && (
        <AccountForm
          account={editor === 'create' ? null : editor}
          roles={roles.data ?? []}
          loading={mutations.create.isPending || mutations.update.isPending}
          onCancel={() => setEditor(null)}
          onSubmit={submitAccount}
        />
      )}
      <Modal
        open={roleAccount !== null}
        title={`Phân quyền: ${roleAccount?.fullName || roleAccount?.email || ''}`}
        okText="Lưu vai trò"
        cancelText="Hủy"
        confirmLoading={mutations.roles.isPending}
        closable={!mutations.roles.isPending}
        mask={{ closable: !mutations.roles.isPending }}
        cancelButtonProps={{ disabled: mutations.roles.isPending }}
        onCancel={() => setRoleAccount(null)}
        onOk={() => roleForm.submit()}
      >
        <Form
          name="account-role"
          form={roleForm}
          layout="vertical"
          onFinish={({ role: selected }: { role: AccountRoleValue }) => {
            if (!roleAccount) return;
            mutations.roles.mutate(
              { id: roleAccount.id, roles: toAccountRoles(selected) },
              {
                onSuccess: () => {
                  setRoleAccount(null);
                  setPage(0);
                  void message.success('Đã cập nhật vai trò. Tài khoản cần đăng nhập lại.');
                },
                onError: (error) => void message.error(errorMessage(error)),
              },
            );
          }}
        >
          <Form.Item
            name="role"
            label="Vai trò tài khoản"
            rules={roleRules}
            extra="Chọn một vai trò duy nhất; chọn Khách hàng để bỏ quyền nhân viên."
          >
            <Select
              options={[
                { value: 'GUEST', label: 'Khách hàng' },
                ...(roles.data ?? []).map((item) => ({
                  value: item.code,
                  label: ROLE_LABELS[item.code],
                })),
              ]}
            />
          </Form.Item>
          <Alert
            type="info"
            showIcon
            title="Thay đổi vai trò sẽ đăng xuất tài khoản khỏi các phiên hiện tại."
          />
        </Form>
      </Modal>
    </Space>
  );
}
