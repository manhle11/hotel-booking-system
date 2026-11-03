import { apiClient } from '../../lib/axios';
import type { ApiResponse } from '../../types/api';
import type { UserStatus } from '../profile/types/profile';
import type {
  Account,
  AccountDetails,
  AccountFilters,
  AccountPage,
  AccountRole,
  AccountRoles,
  CreateAccount,
} from './types';

export async function getAccounts(params: AccountFilters): Promise<AccountPage> {
  return (await apiClient.get<ApiResponse<AccountPage>>('/admin/accounts', { params })).data.data;
}
export async function getAccountRoles(): Promise<AccountRole[]> {
  return (await apiClient.get<ApiResponse<AccountRole[]>>('/admin/roles')).data.data;
}
export async function createAccount(payload: CreateAccount): Promise<Account> {
  return (await apiClient.post<ApiResponse<Account>>('/admin/accounts', payload)).data.data;
}
export async function updateAccount(id: string, payload: AccountDetails): Promise<Account> {
  return (await apiClient.put<ApiResponse<Account>>(`/admin/accounts/${id}`, payload)).data.data;
}
export async function updateAccountStatus(id: string, status: UserStatus): Promise<Account> {
  return (await apiClient.patch<ApiResponse<Account>>(`/admin/accounts/${id}/status`, { status }))
    .data.data;
}
export async function updateAccountRoles(id: string, roles: AccountRoles): Promise<Account> {
  return (await apiClient.put<ApiResponse<Account>>(`/admin/accounts/${id}/roles`, { roles })).data
    .data;
}
