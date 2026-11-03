import type { ReactNode } from 'react';
import { Navigate } from 'react-router-dom';

import { useAuth } from '../features/authentication/providers/auth-context';
import type { UserRole } from '../types/common';
import { hasRouteRole } from './route-access';

interface RoleRouteProps {
  allowedRoles: UserRole[];
  children: ReactNode;
}

export function RoleRoute({ allowedRoles, children }: RoleRouteProps) {
  const { roles } = useAuth();
  const isAllowed = hasRouteRole(roles, allowedRoles);

  return isAllowed ? children : <Navigate to="/forbidden" replace />;
}
