import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { UserStatus } from '../profile/types/profile';
import {
  createAccount,
  getAccountRoles,
  getAccounts,
  updateAccount,
  updateAccountRoles,
  updateAccountStatus,
} from './api';
import type { AccountDetails, AccountFilters, AccountRoles } from './types';

export function useAccounts(filters: AccountFilters) {
  return useQuery({
    queryKey: ['account-management', 'accounts', filters],
    queryFn: () => getAccounts(filters),
  });
}
export function useAccountRoles() {
  return useQuery({ queryKey: ['account-management', 'roles'], queryFn: getAccountRoles });
}
export function useAccountMutations() {
  const client = useQueryClient();
  const onSuccess = () => client.invalidateQueries({ queryKey: ['account-management'] });
  return {
    create: useMutation({ mutationFn: createAccount, onSuccess }),
    update: useMutation({
      mutationFn: ({ id, payload }: { id: string; payload: AccountDetails }) =>
        updateAccount(id, payload),
      onSuccess,
    }),
    status: useMutation({
      mutationFn: ({ id, status }: { id: string; status: UserStatus }) =>
        updateAccountStatus(id, status),
      onSuccess,
    }),
    roles: useMutation({
      mutationFn: ({ id, roles }: { id: string; roles: AccountRoles }) =>
        updateAccountRoles(id, roles),
      onSuccess,
    }),
  };
}
