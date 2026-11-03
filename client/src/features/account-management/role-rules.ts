import type { AccountRoles, AccountRoleValue } from './types';

export const roleRules = [{ required: true, message: 'Vui lòng chọn một vai trò.' }];

// The API represents customers with an empty staff-role list.
export function toAccountRoles(role: AccountRoleValue): AccountRoles {
  return role === 'GUEST' ? [] : [role];
}
