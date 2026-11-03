// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App } from 'antd';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { apiClient } from '../../lib/axios';
import { DEFAULT_STAY_POLICY } from '../../hooks/use-stay-policy';
import { RoomManagementPage } from './room-management-page';
import { RoomTypeManagementPage } from './room-type-management-page';
import { MaintenancePage } from '../maintenance/pages/maintenance-page';
import { TodayDeparturesPage } from '../front-desk/pages/today-departures-page';
import { PaymentMonitoringPage } from '../payment/pages/payment-monitoring-page';
import { RefundRequestsPage } from '../payment/pages/refund-requests-page';
import { HousekeepingPage } from '../housekeeping/pages/housekeeping-page';

vi.mock('../../lib/axios', () => ({
  apiClient: { get: vi.fn(), post: vi.fn(), patch: vi.fn(), delete: vi.fn() },
}));
vi.mock('../authentication/providers/auth-context', () => ({
  useAuth: () => ({ roles: ['MANAGER'] }),
}));

let client: QueryClient;
type ListParams = {
  page?: number;
  size?: number;
  sort?: string;
  keyword?: string;
  status?: string;
};
beforeEach(() => {
  vi.clearAllMocks();
  globalThis.ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  };
  window.matchMedia = vi.fn().mockReturnValue({
    matches: false,
    addListener() {},
    removeListener() {},
    addEventListener() {},
    removeEventListener() {},
  });
  vi.mocked(apiClient.get).mockImplementation(async (url, config) => {
    const params = (config?.params ?? {}) as ListParams;
    const data =
      url === '/stay-policy'
        ? DEFAULT_STAY_POLICY
        : String(url).endsWith('/amenities')
          ? []
          : {
              content: [],
              page: params.page ?? 0,
              size: params.size ?? 20,
              totalElements: 41,
              totalPages: 3,
              first: !params.page,
              last: true,
            };
    return { data: { data } } as never;
  });
  client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } });
});
afterEach(() => {
  cleanup();
  client.clear();
});

const cases = [
  {
    Page: HousekeepingPage,
    path: '/housekeeping',
    endpoint: '/housekeeping/rooms',
    column: 'Tầng',
    key: 'floor',
  },
  {
    Page: RoomTypeManagementPage,
    path: '/management/room-types',
    endpoint: '/management/room-types',
    column: 'Giá / đêm',
    key: 'basePrice',
  },
  {
    Page: RoomManagementPage,
    path: '/management/rooms',
    endpoint: '/management/rooms',
    column: 'Số phòng',
    key: 'roomNumber',
  },
  {
    Page: MaintenancePage,
    path: '/maintenance',
    endpoint: '/maintenance-requests',
    column: 'Ngày tạo',
    key: 'createdAt',
  },
  {
    Page: TodayDeparturesPage,
    path: '/front-desk/departures/today',
    endpoint: '/front-desk/departures/today',
    column: 'Số khách',
    key: 'guests',
  },
  {
    Page: PaymentMonitoringPage,
    path: '/payment-monitoring/payments',
    endpoint: '/payment-monitoring/payments',
    column: 'Số tiền',
    key: 'amount',
  },
  {
    Page: PaymentMonitoringPage,
    path: '/payment-monitoring/webhook-events',
    endpoint: '/payment-monitoring/webhook-events',
    column: 'Số lần thử',
    key: 'attempts',
  },
  {
    Page: RefundRequestsPage,
    path: '/payment-monitoring/refunds',
    endpoint: '/payment-monitoring/refunds',
    column: 'Yêu cầu lúc',
    key: 'requestedAt',
  },
];

function open(Page: typeof RoomManagementPage, path: string) {
  return render(
    <QueryClientProvider client={client}>
      <App>
        <MemoryRouter initialEntries={[path]}>
          <Page />
        </MemoryRouter>
      </App>
    </QueryClientProvider>,
  );
}

function latestParams(endpoint: string) {
  return vi
    .mocked(apiClient.get)
    .mock.calls.filter(([url]) => url === endpoint)
    .at(-1)?.[1]?.params as ListParams | undefined;
}

it.each(cases)(
  '$path sends server sorting, preserves it across pages, and resets the page when changing direction',
  async ({ Page, path, endpoint, column, key }) => {
    const { container } = open(Page, path);
    await waitFor(() => expect(latestParams(endpoint)?.page).toBe(0));
    expect(latestParams(endpoint)?.sort).toBeUndefined();
    const header = screen.getByRole('columnheader', { name: new RegExp(column) });
    expect(
      header.querySelectorAll('.ant-table-column-sorter-up, .ant-table-column-sorter-down'),
    ).toHaveLength(2);
    expect(screen.queryByRole('columnheader', { name: 'Thao tác' })?.className ?? '').not.toContain(
      'ant-table-column-has-sorters',
    );
    fireEvent.click(header);
    await waitFor(() =>
      expect(latestParams(endpoint)).toMatchObject({ page: 0, sort: `${key},asc` }),
    );
    expect(header.getAttribute('aria-sort')).toBe('ascending');
    fireEvent.click(container.querySelector('.ant-pagination-item-2')!);
    await waitFor(() =>
      expect(latestParams(endpoint)).toMatchObject({ page: 1, sort: `${key},asc` }),
    );
    fireEvent.click(header);
    await waitFor(() =>
      expect(latestParams(endpoint)).toMatchObject({ page: 0, sort: `${key},desc` }),
    );
    expect(header.getAttribute('aria-sort')).toBe('descending');
    fireEvent.click(header);
    await waitFor(() => expect(header.hasAttribute('aria-sort')).toBe(false));
    // Clearing sorting can reuse the cached default request; there is no selected sorter.
    expect(
      container.querySelector(
        '.ant-table-column-sorter-up.active, .ant-table-column-sorter-down.active',
      ),
    ).toBeNull();
    expect(apiClient.post).not.toHaveBeenCalled();
    expect(apiClient.patch).not.toHaveBeenCalled();
    expect(apiClient.delete).not.toHaveBeenCalled();
  },
);

it('keeps monitoring filters while sorting and keeps a separate sort per tab', async () => {
  open(PaymentMonitoringPage, '/payment-monitoring/payments');
  const endpoint = '/payment-monitoring/payments';
  fireEvent.change(screen.getByPlaceholderText('Booking hoặc mã thanh toán'), {
    target: { value: 'BK-123' },
  });
  fireEvent.mouseDown(screen.getByRole('combobox'));
  fireEvent.click(await screen.findByText('Thành công'));
  fireEvent.click(screen.getByRole('columnheader', { name: /Số tiền/ }));
  await waitFor(() =>
    expect(latestParams(endpoint)).toMatchObject({
      keyword: 'BK-123',
      status: 'SUCCESS',
      sort: 'amount,asc',
      page: 0,
    }),
  );
  fireEvent.click(screen.getByRole('tab', { name: 'Sự kiện webhook' }));
  await waitFor(() =>
    expect(latestParams('/payment-monitoring/webhook-events')?.sort).toBeUndefined(),
  );
  fireEvent.click(screen.getByRole('columnheader', { name: /Số lần thử/ }));
  await waitFor(() =>
    expect(latestParams('/payment-monitoring/webhook-events')?.sort).toBe('attempts,asc'),
  );
  fireEvent.click(screen.getByRole('tab', { name: 'Thanh toán' }));
  await waitFor(() =>
    expect(screen.getByRole('columnheader', { name: /Số tiền/ }).getAttribute('aria-sort')).toBe(
      'ascending',
    ),
  );
  expect(latestParams(endpoint)?.sort).toBe('amount,asc');
});
