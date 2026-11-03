import { describe, expect, it } from 'vitest';
import { USER_ROLES } from '../types/common';
import { getPostLoginRedirect } from '../features/authentication/utils/post-login-redirect';
import { canAccessPostLoginPath, hasRouteRole, ROUTE_ROLES } from './route-access';

describe('account administrator scope', () => {
  it.each(['/admin/accounts', '/admin/roles'])('reserves %s for admin', (pathname) => {
    expect(canAccessPostLoginPath(pathname, [USER_ROLES.admin])).toBe(true);
    for (const roles of [
      [],
      [USER_ROLES.manager],
      [USER_ROLES.receptionist],
      [USER_ROLES.housekeeper],
    ]) {
      expect(canAccessPostLoginPath(pathname, roles)).toBe(false);
    }
  });

  it.each([
    '/dashboard',
    '/front-desk/arrivals/today',
    '/front-desk/departures/today',
    '/front-desk/check-in',
    '/management/rooms',
    '/management/room-types',
    '/add-on-services',
    '/price-rules',
    '/payment-monitoring/payments',
    '/payment-monitoring/webhook-events',
    '/payment-monitoring/refunds',
    '/housekeeping',
    '/maintenance',
  ])('rejects old admin task %s, including old multi-role accounts', (pathname) => {
    for (const roles of [[USER_ROLES.admin], Object.values(USER_ROLES)]) {
      expect(canAccessPostLoginPath(pathname, roles)).toBe(false);
      expect(getPostLoginRedirect(roles, { pathname })).toBe('/admin/accounts');
    }
  });

  it('keeps personal profile and admin default destination', () => {
    expect(getPostLoginRedirect([USER_ROLES.admin])).toBe('/admin/accounts');
    expect(canAccessPostLoginPath('/account/profile', [USER_ROLES.admin])).toBe(true);
    expect(hasRouteRole([USER_ROLES.admin, USER_ROLES.manager], ROUTE_ROLES.management)).toBe(
      false,
    );
  });
});
