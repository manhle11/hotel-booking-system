import { Navigate, createBrowserRouter, Outlet } from 'react-router-dom';

import { DashboardLayout } from '../layouts/dashboard-layout';
import { LoadingPage } from '../pages/loading-page';
import { authRoutes } from '../routes/authRoutes';
import { ProtectedRoute } from '../routes/protected-route';
import { GuestRoute } from '../routes/guest-route';
import { RoleRoute } from '../routes/role-route';
import { USER_ROLES } from '../types/common';
import { ROUTE_ROLES } from '../routes/route-access';
import { ROUTE_PATHS } from '../routes/routePaths';

const routeHydrationFallback = <LoadingPage />;

export const router = createBrowserRouter([
  {
    path: '/hotel-map',
    hydrateFallbackElement: routeHydrationFallback,
    lazy: async () => {
      const { PublicHotelMapPage } = await import('@features/room-map/public-hotel-map-page');
      return { Component: PublicHotelMapPage };
    },
  },
  {
    path: '/',
    hydrateFallbackElement: routeHydrationFallback,
    lazy: async () => {
      const { HomePage } = await import('@features/home/pages/home-page');
      return { Component: HomePage };
    },
  },
  {
    path: '/rooms/search',
    lazy: async () => {
      const { RoomSearchPage } = await import('@features/room');

      return { Component: RoomSearchPage };
    },
  },
  {
    path: '/login',
    hydrateFallbackElement: routeHydrationFallback,
    lazy: async () => {
      const { LoginPage } = await import('@features/authentication/pages/login-page');
      return { Component: LoginPage };
    },
  },
  {
    path: '/bookings/new',
    lazy: async () => {
      const { MultiRoomBookingPage } =
        await import('@features/booking/pages/multi-room-booking-page');
      return { Component: MultiRoomBookingPage };
    },
  },
  {
    path: '/booking-orders/:orderId',
    hydrateFallbackElement: routeHydrationFallback,
    lazy: async () => {
      const { BookingOrderPage } = await import('@features/booking/pages/booking-order-page');
      return { Component: BookingOrderPage };
    },
  },
  {
    path: '/payments/:paymentId/result',
    hydrateFallbackElement: routeHydrationFallback,
    lazy: async () => {
      const { PaymentResultPage } = await import('@features/booking');
      return { Component: PaymentResultPage };
    },
  },
  {
    path: '/room-types/:roomTypeId',
    lazy: async () => {
      const { RoomDetailPage } = await import('@features/room/pages/room-detail-page');

      return { Component: RoomDetailPage };
    },
  },
  {
    path: '/room-types',
    hydrateFallbackElement: routeHydrationFallback,
    lazy: async () => {
      const { RoomTypesPage } = await import('@features/room');
      return { Component: RoomTypesPage };
    },
  },

  {
    element: <ProtectedRoute />,
    children: [
      {
        path: ROUTE_PATHS.profile,
        hydrateFallbackElement: routeHydrationFallback,
        lazy: async () => {
          const { UserPage } = await import('@features/profile');
          return { Component: UserPage };
        },
      },
      {
        element: (
          <GuestRoute>
            <Outlet />
          </GuestRoute>
        ),
        children: [
          {
            path: ROUTE_PATHS.guestBookings,
            lazy: async () => {
              const { BookingListPage } = await import('@features/booking');
              return { Component: BookingListPage };
            },
          },
          {
            path: `${ROUTE_PATHS.guestBookings}/:bookingCode`,
            lazy: async () => {
              const { BookingListPage } = await import('@features/booking');
              return { Component: BookingListPage };
            },
          },
        ],
      },
      {
        path: '/forbidden',
        element: <h1>403 - Bạn không có quyền truy cập trang này</h1>,
      },
      {
        element: (
          <RoleRoute
            allowedRoles={[
              USER_ROLES.admin,
              USER_ROLES.manager,
              USER_ROLES.receptionist,
              USER_ROLES.housekeeper,
            ]}
          >
            <DashboardLayout />
          </RoleRoute>
        ),
        children: [
          {
            element: (
              <RoleRoute allowedRoles={ROUTE_ROLES.admin}>
                <Outlet />
              </RoleRoute>
            ),
            children: [
              {
                path: ROUTE_PATHS.adminAccounts,
                hydrateFallbackElement: routeHydrationFallback,
                lazy: async () => {
                  const { AccountManagementPage } = await import('@features/account-management');
                  return { Component: AccountManagementPage };
                },
              },
              {
                path: ROUTE_PATHS.adminRoles,
                hydrateFallbackElement: routeHydrationFallback,
                lazy: async () => {
                  const { RoleManagementPage } = await import('@features/account-management');
                  return { Component: RoleManagementPage };
                },
              },
            ],
          },
          {
            element: (
              <RoleRoute allowedRoles={ROUTE_ROLES.maintenance}>
                <Outlet />
              </RoleRoute>
            ),
            children: [
              {
                path: ROUTE_PATHS.roomMaintenance,
                element: <Navigate to={ROUTE_PATHS.maintenance} replace />,
              },
              {
                path: ROUTE_PATHS.maintenance,
                hydrateFallbackElement: routeHydrationFallback,
                lazy: async () => {
                  const { MaintenancePage } = await import('@features/maintenance');
                  return { Component: MaintenancePage };
                },
              },
            ],
          },
          {
            element: (
              <RoleRoute allowedRoles={ROUTE_ROLES.management}>
                <Outlet />
              </RoleRoute>
            ),
            children: [
              {
                path: ROUTE_PATHS.dashboard,
                hydrateFallbackElement: routeHydrationFallback,
                lazy: async () => {
                  const { DashboardPage } = await import('@features/dashboard');
                  return { Component: DashboardPage };
                },
              },
            ],
          },
          {
            element: (
              <RoleRoute allowedRoles={ROUTE_ROLES.reception}>
                <Outlet />
              </RoleRoute>
            ),
            children: [
              {
                path: ROUTE_PATHS.frontDeskCheckIn,
                lazy: async () => {
                  const { CheckInGuestPage } = await import('@features/booking');
                  return { Component: CheckInGuestPage };
                },
              },
            ],
          },
          {
            element: (
              <RoleRoute allowedRoles={ROUTE_ROLES.housekeeping}>
                <Outlet />
              </RoleRoute>
            ),
            children: [
              {
                path: ROUTE_PATHS.housekeeping,
                lazy: async () => {
                  const { HousekeepingPage } = await import('@features/housekeeping');
                  return { Component: HousekeepingPage };
                },
              },
            ],
          },
          {
            element: (
              <RoleRoute allowedRoles={ROUTE_ROLES.frontDesk}>
                <Outlet />
              </RoleRoute>
            ),
            children: [
              {
                path: ROUTE_PATHS.arrivals,
                hydrateFallbackElement: routeHydrationFallback,
                lazy: async () => {
                  const { TodayArrivalsPage } = await import('@features/front-desk');
                  return { Component: TodayArrivalsPage };
                },
              },
              {
                path: ROUTE_PATHS.roomMap,
                hydrateFallbackElement: routeHydrationFallback,
                lazy: async () => {
                  const { StaffRoomMapPage } =
                    await import('@features/room-map/staff-room-map-page');
                  return { Component: StaffRoomMapPage };
                },
              },
              {
                path: ROUTE_PATHS.departures,
                hydrateFallbackElement: routeHydrationFallback,
                lazy: async () => {
                  const { TodayDeparturesPage } = await import('@features/front-desk');
                  return { Component: TodayDeparturesPage };
                },
              },
            ],
          },
          {
            element: (
              <RoleRoute allowedRoles={ROUTE_ROLES.management}>
                <Outlet />
              </RoleRoute>
            ),
            children: [
              {
                path: '/management/rooms',
                lazy: async () => {
                  const { RoomManagementPage } =
                    await import('@features/inventory/room-management-page');
                  return { Component: RoomManagementPage };
                },
              },
              {
                path: ROUTE_PATHS.floorPlans,
                hydrateFallbackElement: routeHydrationFallback,
                lazy: async () => {
                  const { FloorPlanManagementPage } =
                    await import('@features/room-map/floor-plan-management-page');
                  return { Component: FloorPlanManagementPage };
                },
              },
              {
                path: '/management/room-types',
                lazy: async () => {
                  const { RoomTypeManagementPage } =
                    await import('@features/inventory/room-type-management-page');
                  return { Component: RoomTypeManagementPage };
                },
              },
              {
                path: ROUTE_PATHS.reviews,
                lazy: async () => {
                  const { ReviewManagementPage } =
                    await import('@features/review/review-management-page');
                  return { Component: ReviewManagementPage };
                },
              },
              {
                path: ROUTE_PATHS.addOnServices,
                lazy: async () => {
                  const { AddOnManagementPage } = await import('@features/addon-management');
                  return { Component: AddOnManagementPage };
                },
              },
              {
                path: ROUTE_PATHS.priceRules,
                lazy: async () => {
                  const { PriceRuleManagementPage } =
                    await import('@features/price-rule-management');
                  return { Component: PriceRuleManagementPage };
                },
              },
              {
                path: ROUTE_PATHS.payments,
                lazy: async () => {
                  const { PaymentMonitoringPage } = await import('@features/payment');
                  return { Component: PaymentMonitoringPage };
                },
              },
              {
                path: ROUTE_PATHS.webhookEvents,
                lazy: async () => {
                  const { PaymentMonitoringPage } = await import('@features/payment');
                  return { Component: PaymentMonitoringPage };
                },
              },
              {
                path: ROUTE_PATHS.refunds,
                element: (
                  <RoleRoute allowedRoles={ROUTE_ROLES.refunds}>
                    <Outlet />
                  </RoleRoute>
                ),
                children: [
                  {
                    index: true,
                    lazy: async () => {
                      const { RefundRequestsPage } = await import('@features/payment');
                      return { Component: RefundRequestsPage };
                    },
                  },
                ],
              },
            ],
          },
        ],
      },
    ],
  },
  ...authRoutes,
  {
    path: '*',
    element: <h1>404 - Không tìm thấy trang</h1>,
  },
]);
