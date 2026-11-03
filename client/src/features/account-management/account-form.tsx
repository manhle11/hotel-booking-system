import { Alert, Form, Input, Modal, Select } from 'antd';
import type { FormInstance } from 'antd';
import type { Account, AccountRole, AccountRoleValue } from './types';
import { ROLE_LABELS } from './types';
import { roleRules } from './role-rules';

export interface AccountFormValues {
  fullName: string;
  email: string;
  phone?: string;
  password: string;
  role: AccountRoleValue;
}

export function AccountForm({
  account,
  roles,
  loading,
  onCancel,
  onSubmit,
}: {
  account: Account | null;
  roles: AccountRole[];
  loading: boolean;
  onCancel: () => void;
  onSubmit: (values: AccountFormValues, form: FormInstance<AccountFormValues>) => void;
}) {
  const [form] = Form.useForm<AccountFormValues>();
  return (
    <Modal
      open
      title={account ? 'Chỉnh sửa tài khoản' : 'Tạo tài khoản'}
      okText="Lưu tài khoản"
      cancelText="Hủy"
      confirmLoading={loading}
      closable={!loading}
      mask={{ closable: !loading }}
      cancelButtonProps={{ disabled: loading }}
      onCancel={onCancel}
      onOk={() => form.submit()}
    >
      <Form
        name="account-details"
        form={form}
        layout="vertical"
        initialValues={account ?? { role: 'GUEST' }}
        onFinish={(values) => onSubmit(values, form)}
      >
        <Form.Item
          name="fullName"
          label="Họ và tên"
          rules={[
            {
              required: true,
              whitespace: true,
              min: 2,
              max: 100,
              message: 'Nhập họ tên từ 2 đến 100 ký tự.',
            },
          ]}
        >
          <Input maxLength={100} autoComplete="name" />
        </Form.Item>
        <Form.Item
          name="email"
          label="Email"
          rules={[{ required: true, type: 'email', max: 255, message: 'Nhập email hợp lệ.' }]}
        >
          <Input disabled={!!account} maxLength={255} autoComplete="email" />
        </Form.Item>
        <Form.Item
          name="phone"
          label="Số điện thoại"
          rules={[
            {
              pattern: /^\+?[0-9]{8,15}$/,
              message: 'Nhập từ 8 đến 15 chữ số, có thể bắt đầu bằng +.',
            },
          ]}
        >
          <Input maxLength={16} autoComplete="tel" />
        </Form.Item>
        {!account && (
          <>
            <Form.Item
              name="password"
              label="Mật khẩu ban đầu"
              rules={[
                { required: true, min: 8, max: 72, message: 'Mật khẩu phải có từ 8 đến 72 ký tự.' },
                {
                  validator: (_, value: string) =>
                    value && new TextEncoder().encode(value).length > 72
                      ? Promise.reject(new Error('Mật khẩu tối đa 72 byte.'))
                      : Promise.resolve(),
                },
              ]}
            >
              <Input.Password autoComplete="new-password" />
            </Form.Item>
            <Form.Item
              name="role"
              label="Vai trò"
              rules={roleRules}
              extra="Mỗi tài khoản chỉ có một vai trò."
            >
              <Select
                options={[
                  { value: 'GUEST', label: 'Khách hàng' },
                  ...roles.map((role) => ({ value: role.code, label: ROLE_LABELS[role.code] })),
                ]}
              />
            </Form.Item>
            <Alert
              type="info"
              showIcon
              title="Quản trị viên chỉ quản lý tài khoản và phân quyền."
            />
          </>
        )}
      </Form>
    </Modal>
  );
}
