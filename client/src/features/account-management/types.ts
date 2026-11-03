import type { PageResponse } from '../../types/api';
import type { UserRole } from '../../types/common';
import type { User, UserStatus } from '../profile/types/profile';

export type Account = User;
export type AccountPage = PageResponse<Account>;
export type AccountRoleValue = UserRole | 'GUEST';
export type AccountRoles = [] | [UserRole];
export interface AccountRole {
  code: UserRole;
  name: string;
  description: string | null;
}
export interface AccountFilters {
  page: number;
  size: number;
  search?: string;
  status?: UserStatus;
  role?: string;
}
export interface AccountDetails {
  fullName: string;
  phone: string | null;
}
export interface CreateAccount extends AccountDetails {
  email: string;
  password: string;
  roles: AccountRoles;
}
export const ROLE_LABELS: Record<UserRole, string> = {
  ADMIN: 'Quản trị viên',
  MANAGER: 'Quản lý khách sạn',
  RECEPTIONIST: 'Lễ tân',
  HOUSEKEEPER: 'Buồng phòng',
};
export const STATUS_LABELS: Record<UserStatus, string> = {
  ACTIVE: 'Đang hoạt động',
  LOCKED: 'Đã chặn',
  INACTIVE: 'Ngừng hoạt động',
};
