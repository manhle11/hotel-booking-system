// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest';
import { QueryClient, QueryClientProvider, QueryObserver } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { App, ConfigProvider } from 'antd';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { apiClient, publicApiClient } from '../../lib/axios';
import { RoomTypeManagementPage, type ManagedRoomType } from './room-type-management-page';
import { getPublicMap, type FloorPlan, type HotelMapResponse } from '../room-map/api';

vi.mock('../../lib/axios', () => ({
  apiClient: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
  publicApiClient: { get: vi.fn() },
}));

const category = { id: 'category-deluxe', code: 'DELUXE', name: 'Deluxe' };
const legacy: ManagedRoomType = {
  id: 'legacy-type',
  code: 'LEGACY',
  name: 'Loại phòng cũ',
  description: 'Mô tả hiện có',
  shortDescription: 'Mô tả ngắn',
  basePrice: 1000000,
  maxGuests: 3,
  capacityAdults: 2,
  capacityChildren: 1,
  bedType: 'King hoặc Twin',
  areaM2: 30,
  status: 'ACTIVE',
  images: [],
  amenityIds: [],
  category: null,
  beds: [],
  bedConfigurationStatus: 'UNCONFIGURED',
};
let records: ManagedRoomType[];

beforeAll(() => {
  globalThis.ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  };
  window.matchMedia = vi.fn().mockImplementation((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  }));
});

beforeEach(() => {
  vi.clearAllMocks();
  records = [legacy];
  vi.mocked(apiClient.get).mockImplementation(async (url) => {
    if (url === '/management/room-categories') return { data: { data: [category] } };
    if (url === '/management/room-types/amenities') return { data: { data: [] } };
    if (url === '/management/room-types')
      return {
        data: {
          data: {
            content: records,
            page: 0,
            size: 20,
            totalElements: records.length,
            totalPages: 1,
            first: true,
            last: true,
          },
        },
      };
    throw new Error(`Unexpected request: ${url}`);
  });
  vi.mocked(apiClient.post).mockResolvedValue({ data: { data: {} } });
  vi.mocked(apiClient.put).mockResolvedValue({ data: { data: {} } });
  vi.mocked(apiClient.delete).mockResolvedValue({ data: { data: {} } });
});
afterEach(cleanup);

function mount(
  client = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
  }),
) {
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <ConfigProvider>
          <App>
            <RoomTypeManagementPage />
          </App>
        </ConfigProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

async function openCreate() {
  mount();
  await screen.findByText(legacy.code);
  fireEvent.click(screen.getByRole('button', { name: 'Thêm loại phòng' }));
  return within(await screen.findByRole('dialog'));
}

async function openEdit(client?: QueryClient) {
  mount(client);
  fireEvent.click(await screen.findByRole('button', { name: 'Chi tiết / Sửa' }));
  return within(await screen.findByRole('dialog'));
}

async function selectOption(input: HTMLElement, label: string) {
  fireEvent.mouseDown(input);
  const options = await screen.findAllByText(label, {
    selector: '.ant-select-item-option-content',
  });
  fireEvent.click(options.at(-1)!);
}

async function fillCreate(dialog: ReturnType<typeof within>) {
  fireEvent.change(dialog.getByLabelText('Mã loại phòng'), { target: { value: 'DELUXE_MIXED' } });
  fireEvent.change(dialog.getByLabelText('Tên loại phòng'), {
    target: { value: 'Deluxe hỗn hợp' },
  });
  await selectOption(dialog.getByLabelText('Nhóm phòng'), 'Deluxe');
}

describe('Room Type bed options', () => {
  it('updates the room-type details embedded in an active map after saving a new price', async () => {
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false, staleTime: 60_000 }, mutations: { retry: false } },
    });
    let price = legacy.basePrice;
    const floor: FloorPlan = {
      floorNumber: 4,
      name: 'Tầng 4',
      gridColumns: 80,
      gridRows: 40,
      version: 1,
      publishedAt: '2026-11-11T10:00:00Z',
      elements: [],
      rooms: [{ key: '406', roomNumber: '406', roomTypeId: legacy.id, roomTypeName: legacy.name }],
    };
    vi.mocked(publicApiClient.get).mockImplementation(async (url) => ({
      data: {
        data:
          url === '/hotel-map'
            ? { floors: [floor] }
            : { ...legacy, basePrice: price, amenities: [] },
      },
    }));
    vi.mocked(apiClient.put).mockImplementation(async (_url, payload) => {
      price = (payload as { basePrice: number }).basePrice;
      return { data: { data: {} } };
    });
    client.setQueryData(['hotel-map'], await getPublicMap(client));
    const observer = new QueryObserver(client, {
      queryKey: ['hotel-map'],
      queryFn: () => getPublicMap(client),
    });
    const unsubscribe = observer.subscribe(() => {});
    try {
      const dialog = await openEdit(client);
      fireEvent.change(dialog.getByLabelText('Giá cơ bản (VND)'), {
        target: { value: '1200000' },
      });
      fireEvent.click(dialog.getByRole('button', { name: 'Lưu' }));
      await screen.findByText('Đã lưu loại phòng');
      expect(
        client.getQueryData<HotelMapResponse>(['hotel-map'])?.floors[0].rooms[0].typeInfo
          ?.basePrice,
      ).toBe(1_200_000);
    } finally {
      unsubscribe();
      client.clear();
    }
  });

  it.each(['save', 'reactivate', 'deactivate'] as const)(
    'refreshes cached public catalog and checkout after %s',
    async (action) => {
      records = [{ ...legacy, status: action === 'reactivate' ? 'INACTIVE' : 'ACTIVE' }];
      const client = new QueryClient({
        defaultOptions: {
          queries: { retry: false, staleTime: 60_000 },
          mutations: { retry: false },
        },
      });
      const catalogKey = [
        'room-type-catalog',
        { page: 0, size: 6, sortBy: 'name', direction: 'asc' },
      ];
      const checkoutKey = ['order-room-types', '2026-11-10', '2026-11-12'];
      client.setQueryData(catalogKey, { content: records });
      client.setQueryData(checkoutKey, { content: records });
      client.setQueryData(['booking', 'add-ons'], []);
      try {
        if (action === 'deactivate') {
          mount(client);
          fireEvent.click(await screen.findByRole('button', { name: 'Ngừng kinh doanh' }));
          fireEvent.click(await screen.findByRole('button', { name: 'OK' }));
          await screen.findByText('Đã ngừng kinh doanh loại phòng');
          expect(apiClient.delete).toHaveBeenCalledWith('/management/room-types/legacy-type');
        } else {
          const dialog = await openEdit(client);
          if (action === 'reactivate') {
            await selectOption(dialog.getByLabelText('Trạng thái'), 'Đang kinh doanh');
          } else {
            fireEvent.change(dialog.getByLabelText('Giá cơ bản (VND)'), {
              target: { value: '1200000' },
            });
          }
          fireEvent.click(dialog.getByRole('button', { name: 'Lưu' }));
          await screen.findByText('Đã lưu loại phòng');
          expect(apiClient.put).toHaveBeenCalledWith(
            '/management/room-types/legacy-type',
            expect.objectContaining({ status: 'ACTIVE' }),
          );
        }
        expect({
          catalog: client.getQueryState(catalogKey)?.isInvalidated,
          checkout: client.getQueryState(checkoutKey)?.isInvalidated,
        }).toEqual({ catalog: true, checkout: true });
        expect(client.getQueryState(['booking', 'add-ons'])?.isInvalidated).toBe(false);
      } finally {
        client.clear();
      }
    },
  );

  it('submits descriptive room features separately from beds, capacity and amenities', async () => {
    const dialog = await openCreate();
    await fillCreate(dialog);
    await selectOption(dialog.getByLabelText('Hướng nhìn'), 'Hướng biển');
    fireEvent.click(
      within(dialog.getByRole('radiogroup', { name: 'Ban công riêng' })).getByLabelText('Có'),
    );
    fireEvent.click(
      within(dialog.getByRole('radiogroup', { name: 'Tầng cao' })).getByLabelText('Có'),
    );
    await selectOption(
      dialog.getByRole('combobox', { name: 'Loại giường 1' }),
      'Giường Queen (QUEEN)',
    );
    fireEvent.click(dialog.getByRole('button', { name: 'Lưu' }));
    await waitFor(() =>
      expect(apiClient.post).toHaveBeenCalledWith(
        '/management/room-types',
        expect.objectContaining({
          viewType: 'SEA',
          hasBalcony: true,
          highFloor: true,
          amenityIds: [],
          beds: [{ bedType: 'QUEEN', quantity: 1 }],
          maxGuests: 2,
          capacityAdults: 2,
          capacityChildren: 0,
        }),
      ),
    );
  });

  it('sends selected bed types and quantities with the chosen category, keeping capacity independent', async () => {
    const dialog = await openCreate();
    await fillCreate(dialog);
    await selectOption(
      dialog.getByRole('combobox', { name: 'Loại giường 1' }),
      'Giường King (KING)',
    );
    fireEvent.click(dialog.getByRole('button', { name: 'Thêm loại giường' }));
    await selectOption(
      dialog.getByRole('combobox', { name: 'Loại giường 2' }),
      'Giường đơn (SINGLE)',
    );
    const quantity = dialog.getByRole('spinbutton', { name: 'Số lượng giường 2' });
    fireEvent.change(quantity, { target: { value: '2' } });
    fireEvent.blur(quantity);
    fireEvent.click(dialog.getByRole('button', { name: 'Lưu' }));
    await waitFor(() =>
      expect(apiClient.post).toHaveBeenCalledWith(
        '/management/room-types',
        expect.objectContaining({
          categoryId: category.id,
          beds: [
            { bedType: 'KING', quantity: 1 },
            { bedType: 'SINGLE', quantity: 2 },
          ],
          maxGuests: 2,
          capacityAdults: 2,
          capacityChildren: 0,
        }),
      ),
    );
    expect(vi.mocked(apiClient.post).mock.calls[0][1]).not.toHaveProperty('bedType');
  });

  it('offers the four supported choices and disables a bed type already selected in another row', async () => {
    const dialog = await openCreate();
    await selectOption(
      dialog.getByRole('combobox', { name: 'Loại giường 1' }),
      'Giường King (KING)',
    );
    fireEvent.click(dialog.getByRole('button', { name: 'Thêm loại giường' }));
    fireEvent.mouseDown(dialog.getByRole('combobox', { name: 'Loại giường 2' }));
    const kingOptions = await screen.findAllByText('Giường King (KING)', {
      selector: '.ant-select-item-option-content',
    });
    expect(kingOptions.at(-1)?.closest('.ant-select-item-option')).toHaveClass(
      'ant-select-item-option-disabled',
    );
    for (const label of ['Giường đơn (SINGLE)', 'Giường đôi (DOUBLE)', 'Giường Queen (QUEEN)']) {
      expect(
        screen.getAllByText(label, { selector: '.ant-select-item-option-content' }).length,
      ).toBeGreaterThan(0);
    }
  });

  it('requires an explicit bed choice instead of defaulting new types to SINGLE', async () => {
    const dialog = await openCreate();
    await fillCreate(dialog);
    fireEvent.click(dialog.getByRole('button', { name: 'Lưu' }));
    expect(await dialog.findByText('Vui lòng chọn loại giường.')).toBeInTheDocument();
    expect(apiClient.post).not.toHaveBeenCalled();
  });

  it('rejects creating a type after all bed rows have been removed', async () => {
    const dialog = await openCreate();
    await fillCreate(dialog);
    fireEvent.click(dialog.getByRole('button', { name: 'Bỏ loại giường 1' }));
    fireEvent.click(dialog.getByRole('button', { name: 'Lưu' }));
    expect(
      (await dialog.findAllByText('Vui lòng thêm ít nhất một loại giường.')).length,
    ).toBeGreaterThan(0);
    expect(apiClient.post).not.toHaveBeenCalled();
  });

  it('keeps ambiguous legacy beds unchanged when editing only other room information', async () => {
    const dialog = await openEdit();
    expect(dialog.getByText(/Thông tin hiện có: King hoặc Twin/)).toBeInTheDocument();
    expect(dialog.queryByRole('combobox', { name: 'Loại giường 1' })).not.toBeInTheDocument();
    fireEvent.change(dialog.getByLabelText('Mô tả ngắn'), { target: { value: 'Mô tả mới' } });
    fireEvent.click(dialog.getByRole('button', { name: 'Lưu' }));
    await waitFor(() =>
      expect(apiClient.put).toHaveBeenCalledWith(
        '/management/room-types/legacy-type',
        expect.objectContaining({ shortDescription: 'Mô tả mới', beds: undefined }),
      ),
    );
    expect(vi.mocked(apiClient.put).mock.calls[0][1]).not.toHaveProperty('bedType');
  });

  it('loads existing structured beds and allows saving the same configuration', async () => {
    records = [
      {
        ...legacy,
        category,
        beds: [{ bedType: 'QUEEN', quantity: 2 }],
        bedType: '2 QUEEN',
        bedConfigurationStatus: 'CONFIGURED',
      },
    ];
    const dialog = await openEdit();
    expect(dialog.getByRole('spinbutton', { name: 'Số lượng giường 1' })).toHaveValue('2');
    expect(dialog.queryByText('Chưa xác minh cấu hình giường')).not.toBeInTheDocument();
    fireEvent.click(dialog.getByRole('button', { name: 'Lưu' }));
    await waitFor(() =>
      expect(apiClient.put).toHaveBeenCalledWith(
        '/management/room-types/legacy-type',
        expect.objectContaining({
          categoryId: category.id,
          beds: [{ bedType: 'QUEEN', quantity: 2 }],
        }),
      ),
    );
  });

  it('shows booking history conflicts without closing the form or reporting success', async () => {
    vi.mocked(apiClient.put).mockRejectedValue(
      new Error('Loại phòng đã có đặt phòng. Cấu hình giường được giữ nguyên.'),
    );
    const dialog = await openEdit();
    fireEvent.click(dialog.getByRole('button', { name: 'Thêm loại giường' }));
    await selectOption(
      dialog.getByRole('combobox', { name: 'Loại giường 1' }),
      'Giường đôi (DOUBLE)',
    );
    fireEvent.click(dialog.getByRole('button', { name: 'Lưu' }));
    expect(
      await screen.findByText('Loại phòng đã có đặt phòng. Cấu hình giường được giữ nguyên.'),
    ).toBeInTheDocument();
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(screen.queryByText('Đã lưu loại phòng')).not.toBeInTheDocument();
  });
});
