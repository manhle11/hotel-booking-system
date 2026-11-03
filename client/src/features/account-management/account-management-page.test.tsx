// @vitest-environment jsdom

import { App } from 'antd';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { useAuth } from '../authentication/providers/auth-context';
import { useAccountMutations, useAccountRoles, useAccounts } from './hooks';
import { AccountManagementPage } from './account-management-page';
import type { Account } from './types';

vi.mock('../authentication/providers/auth-context', () => ({ useAuth: vi.fn() }));
vi.mock('./hooks', () => ({
  useAccountMutations: vi.fn(),
  useAccountRoles: vi.fn(),
  useAccounts: vi.fn(),
}));

const account: Account = {
  id: 'staff-id',
  fullName: 'Nguyễn Nhân Viên',
  email: 'staff@example.com',
  phone: '0900000000',
  roles: ['HOUSEKEEPER'],
  status: 'ACTIVE',
  createdAt: '2026-11-03T10:00:00Z',
  updatedAt: '2026-11-03T10:00:00Z',
};
const statusMutation = { mutateAsync: vi.fn(), isPending: false };
const rolesMutation = { mutate: vi.fn(), isPending: false };
const createMutation = { mutate: vi.fn(), isPending: false };
const updateMutation = { mutate: vi.fn(), isPending: false };

beforeEach(() => {
  vi.clearAllMocks();
  window.matchMedia = vi.fn().mockImplementation((query: string) => ({
    matches: false,
    media: query,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
  }));
  globalThis.ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  };
  vi.mocked(useAuth).mockReturnValue({
    session: { user: { id: 'admin-id' } },
    updateUser: vi.fn(),
  } as never);
  vi.mocked(useAccounts).mockReturnValue({
    data: { content: [account], page: 0, totalElements: 1 },
    isLoading: false,
    isError: false,
  } as never);
  vi.mocked(useAccountRoles).mockReturnValue({
    data: [
      { code: 'ADMIN', name: 'Quản trị viên' },
      { code: 'HOUSEKEEPER', name: 'Buồng phòng' },
    ],
    isError: false,
  } as never);
  vi.mocked(useAccountMutations).mockReturnValue({
    create: createMutation,
    update: updateMutation,
    status: statusMutation,
    roles: rolesMutation,
  } as never);
});
afterEach(cleanup);

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/admin/accounts?role=HOUSEKEEPER']}>
      <App>
        <AccountManagementPage />
      </App>
    </MemoryRouter>,
  );
}

describe('account administration', () => {
  it('renders accounts and applies the role filter from role management', () => {
    renderPage();
    expect(screen.getByText(account.email)).toBeTruthy();
    expect(useAccounts).toHaveBeenCalledWith(expect.objectContaining({ role: 'HOUSEKEEPER' }));
  });

  it('blocks an account only after confirmation', async () => {
    statusMutation.mutateAsync.mockResolvedValue({ ...account, status: 'LOCKED' });
    renderPage();
    fireEvent.click(screen.getByRole('button', { name: /Chặn$/ }));
    expect(statusMutation.mutateAsync).not.toHaveBeenCalled();
    const dialog = await screen.findByRole('dialog');
    fireEvent.click(within(dialog).getByRole('button', { name: 'Chặn tài khoản' }));
    await waitFor(() =>
      expect(statusMutation.mutateAsync).toHaveBeenCalledWith({ id: account.id, status: 'LOCKED' }),
    );
  });

  it('reactivates a blocked account after confirmation', async () => {
    vi.mocked(useAccounts).mockReturnValue({
      data: { content: [{ ...account, status: 'LOCKED' }], totalElements: 1 },
    } as never);
    renderPage();
    fireEvent.click(screen.getByRole('button', { name: /Kích hoạt/ }));
    const dialog = await screen.findByRole('dialog');
    fireEvent.click(within(dialog).getByRole('button', { name: 'Kích hoạt' }));
    await waitFor(() =>
      expect(statusMutation.mutateAsync).toHaveBeenCalledWith({ id: account.id, status: 'ACTIVE' }),
    );
  });

  it('disables self blocking and self role changes', () => {
    vi.mocked(useAuth).mockReturnValue({
      session: { user: { id: account.id } },
      updateUser: vi.fn(),
    } as never);
    renderPage();
    expect(screen.getByText('Chặn').closest('button')?.disabled).toBe(true);
    expect(screen.getByText('Phân quyền').closest('button')?.disabled).toBe(true);
  });

  it('submits role assignments using the account identifier', async () => {
    renderPage();
    fireEvent.click(screen.getByRole('button', { name: /Phân quyền/ }));
    const dialog = await screen.findByRole('dialog');
    fireEvent.click(within(dialog).getByRole('button', { name: 'Lưu vai trò' }));
    await waitFor(() =>
      expect(rolesMutation.mutate).toHaveBeenCalledWith(
        { id: account.id, roles: ['HOUSEKEEPER'] },
        expect.any(Object),
      ),
    );
  });

  it('replaces the existing role when a different role is selected', async () => {
    renderPage();
    fireEvent.click(screen.getByRole('button', { name: /Phân quyền/ }));
    const dialog = await screen.findByRole('dialog');
    fireEvent.mouseDown(within(dialog).getByRole('combobox'));
    fireEvent.click(
      await screen.findByText('Quản trị viên', { selector: '.ant-select-item-option-content' }),
    );
    fireEvent.click(within(dialog).getByRole('button', { name: 'Lưu vai trò' }));
    await waitFor(() =>
      expect(rolesMutation.mutate).toHaveBeenCalledWith(
        { id: account.id, roles: ['ADMIN'] },
        expect.any(Object),
      ),
    );
  });

  it('offers customer as a single role that removes staff permissions', async () => {
    renderPage();
    fireEvent.click(screen.getByRole('button', { name: /Phân quyền/ }));
    const dialog = await screen.findByRole('dialog');
    fireEvent.mouseDown(within(dialog).getByRole('combobox'));
    fireEvent.click(
      await screen.findByText('Khách hàng', { selector: '.ant-select-item-option-content' }),
    );
    fireEvent.click(within(dialog).getByRole('button', { name: 'Lưu vai trò' }));
    await waitFor(() =>
      expect(rolesMutation.mutate).toHaveBeenCalledWith(
        { id: account.id, roles: [] },
        expect.any(Object),
      ),
    );
  });

  it('creates a guest account with normalized fields', async () => {
    renderPage();
    fireEvent.click(screen.getByRole('button', { name: /Tạo tài khoản/ }));
    const dialog = await screen.findByRole('dialog');
    fireEvent.change(within(dialog).getByLabelText('Họ và tên'), {
      target: { value: '  Khách mới  ' },
    });
    fireEvent.change(within(dialog).getByLabelText('Email'), {
      target: { value: 'guest@example.com' },
    });
    fireEvent.change(within(dialog).getByLabelText('Mật khẩu ban đầu'), {
      target: { value: 'Password123' },
    });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Lưu tài khoản' }));
    await waitFor(() =>
      expect(createMutation.mutate).toHaveBeenCalledWith(
        {
          fullName: 'Khách mới',
          phone: null,
          email: 'guest@example.com',
          password: 'Password123',
          roles: [],
        },
        expect.any(Object),
      ),
    );
  });
});
