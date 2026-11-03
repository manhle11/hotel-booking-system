import { Alert, Button, Card, Space, Table, Tag, Typography } from 'antd';
import { useNavigate } from 'react-router-dom';
import { ROUTE_PATHS } from '../../routes/routePaths';
import { useAccountRoles } from './hooks';
import type { AccountRole } from './types';
import { ROLE_LABELS } from './types';

export function RoleManagementPage() {
  const roles = useAccountRoles();
  const navigate = useNavigate();
  return (
    <Space orientation="vertical" size="large" style={{ width: '100%' }}>
      <div>
        <Typography.Title level={2}>Quản lý vai trò</Typography.Title>
        <Typography.Text type="secondary">
          Xem phạm vi quyền và quản lý các tài khoản được gán từng vai trò.
        </Typography.Text>
      </div>
      <Alert
        type="info"
        showIcon
        title="Mỗi tài khoản chỉ có một vai trò. Chọn “Quản lý tài khoản” để đổi vai trò; chọn Khách hàng để bỏ quyền nhân viên."
      />
      <Card>
        {roles.isError ? (
          <Alert
            type="error"
            showIcon
            title="Không tải được vai trò"
            action={<Button onClick={() => void roles.refetch()}>Thử lại</Button>}
          />
        ) : (
          <Table<AccountRole>
            rowKey="code"
            loading={roles.isLoading}
            dataSource={roles.data ?? []}
            pagination={false}
            scroll={{ x: 750 }}
            columns={[
              {
                title: 'Mã vai trò',
                dataIndex: 'code',
                render: (code: string) => <Tag color="blue">{code}</Tag>,
              },
              {
                title: 'Tên vai trò',
                dataIndex: 'code',
                key: 'name',
                render: (code: AccountRole['code']) => ROLE_LABELS[code],
              },
              {
                title: 'Phạm vi quyền',
                dataIndex: 'description',
                render: (description: string | null, item) =>
                  item.code === 'ADMIN'
                    ? 'Quản lý tài khoản, chặn/mở chặn và phân quyền tài khoản.'
                    : description || '—',
              },
              {
                title: 'Thao tác',
                key: 'actions',
                render: (_, item) => (
                  <Button
                    onClick={() => navigate(`${ROUTE_PATHS.adminAccounts}?role=${item.code}`)}
                  >
                    Quản lý tài khoản
                  </Button>
                ),
              },
            ]}
          />
        )}
      </Card>
    </Space>
  );
}
