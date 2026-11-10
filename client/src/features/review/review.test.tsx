// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { App } from 'antd';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { apiClient } from '../../lib/axios';
import {
  createReview,
  deleteReview,
  getManagedReviews,
  getManagedReviewSummary,
  getReviewEligibility,
  getReviewSummary,
  getRoomReviews,
  replyReview,
  type RoomReview,
  type ManagedReview,
} from './api';
import { ReviewAction } from './review-action';
import { ReviewManagementPage } from './review-management-page';
import { RoomReviewsSection } from './room-reviews';

vi.mock('../../components/layout/MainHeader', () => ({ MainHeader: () => null }));
vi.mock('../../components/layout/MainFooter', () => ({ MainFooter: () => null }));

let authenticated = true;
vi.mock('../authentication/providers/auth-context', () => ({
  useAuth: () => ({
    isAuthenticated: authenticated,
    session: authenticated ? { user: { id: 'customer-1' } } : null,
  }),
}));
vi.mock('../../lib/axios', () => ({ apiClient: { get: vi.fn() } }));
vi.mock('./api', () => ({
  createReview: vi.fn(),
  deleteReview: vi.fn(),
  getManagedReviews: vi.fn(),
  getManagedReviewSummary: vi.fn(),
  getReviewEligibility: vi.fn(),
  getReviewSummary: vi.fn(),
  getRoomReviews: vi.fn(),
  replyReview: vi.fn(),
  removeReviewReply: vi.fn(),
}));
const review: RoomReview = {
  id: 'review-1',
  roomTypeId: 'deluxe',
  roomTypeName: 'Deluxe Twin',
  authorName: 'Nguyễn ***',
  rating: 4,
  comment: 'Phòng sạch, thoải mái.',
  createdAt: '2026-11-09T12:00:00Z',
};
const managed: ManagedReview = {
  review,
  bookingCode: 'BOOK-1',
  orderCode: 'ORD-1',
  guestName: 'Nguyễn Văn An',
  stays: [
    {
      bookingCode: 'BOOK-1',
      roomNumber: '501',
      checkInDate: '2026-11-07',
      checkOutDate: '2026-11-09',
      checkedInAt: null,
      checkedOutAt: '2026-11-09T05:00:00Z',
    },
  ],
  deletedAt: null,
  deletedByName: null,
  deleteReason: null,
  repliedByName: null,
};
let client: QueryClient;
const pageOf = <T,>(content: T[]) => ({
  content,
  page: 0,
  size: 20,
  totalElements: content.length,
  totalPages: 1,
  first: true,
  last: true,
});

beforeAll(() => {
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
});
beforeEach(() => {
  authenticated = true;
  vi.clearAllMocks();
  client = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
  });
  vi.mocked(apiClient.get).mockResolvedValue({ data: { data: pageOf([]) } });
  vi.mocked(getReviewEligibility).mockResolvedValue({
    canReview: true,
    reasonCode: null,
    message: 'Bạn có thể đánh giá.',
    deadline: '2026-12-09T12:00:00Z',
    review: null,
  });
  vi.mocked(createReview).mockResolvedValue(review);
  vi.mocked(getReviewSummary).mockResolvedValue({ averageRating: 4, totalReviews: 1 });
  vi.mocked(getRoomReviews).mockResolvedValue(pageOf([review]));
  vi.mocked(getManagedReviews).mockResolvedValue(pageOf([managed]));
  vi.mocked(getManagedReviewSummary).mockResolvedValue({
    totalReviews: 1,
    unrepliedReviews: 1,
    repliedReviews: 0,
    lowRatingReviews: 0,
  });
  vi.mocked(deleteReview).mockResolvedValue();
  vi.mocked(replyReview).mockResolvedValue({ ...review, hotelReply: 'Khách sạn đã khắc phục.' });
});
afterEach(() => {
  cleanup();
  client.clear();
});
function mount(component: React.ReactNode, route = '/') {
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[route]}>
        <App>{component}</App>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('account reviews', () => {
  it('requires stars and submits the signed-in customer score and comment', async () => {
    mount(<ReviewAction bookingId="booking-1" roomTypeName="Deluxe Twin" />);
    fireEvent.click(screen.getByRole('button', { name: /Đánh giá hạng phòng/ }));
    await screen.findByLabelText('Số sao đánh giá');
    fireEvent.click(screen.getByRole('button', { name: 'Gửi đánh giá' }));
    await screen.findAllByText('Vui lòng chọn từ 1 đến 5 sao');
    expect(createReview).not.toHaveBeenCalled();
    const stars = document.querySelectorAll('.ant-rate-star-second');
    fireEvent.click(stars[3]);
    fireEvent.change(screen.getByRole('textbox'), { target: { value: review.comment } });
    fireEvent.click(screen.getByRole('button', { name: 'Gửi đánh giá' }));
    await waitFor(() =>
      expect(createReview).toHaveBeenCalledWith('booking-1', {
        rating: 4,
        comment: review.comment,
      }),
    );
    expect(await screen.findByRole('button', { name: /Xem đánh giá của bạn/ })).toBeInTheDocument();
  });

  it.each(['Đã hết thời hạn đánh giá 30 ngày.', 'Đánh giá đã được khách sạn xóa.'])(
    'shows the server eligibility reason and prevents another submission: %s',
    async (reason) => {
      vi.mocked(getReviewEligibility).mockResolvedValue({
        canReview: false,
        reasonCode: 'REVIEW_REMOVED',
        message: reason,
        deadline: null,
        review: null,
      });
      mount(<ReviewAction bookingId="booking-1" roomTypeName="Deluxe Twin" />);
      fireEvent.click(screen.getByRole('button', { name: /Đánh giá hạng phòng/ }));
      expect(await screen.findByText(reason)).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'Gửi đánh giá' })).not.toBeInTheDocument();
      expect(createReview).not.toHaveBeenCalled();
    },
  );

  it('shows existing review without a second submission form', async () => {
    vi.mocked(getReviewEligibility).mockResolvedValue({
      canReview: false,
      reasonCode: 'REVIEW_ALREADY_EXISTS',
      message: 'Đã đánh giá',
      deadline: null,
      review,
    });
    mount(<ReviewAction bookingId="booking-1" roomTypeName="Deluxe Twin" />);
    fireEvent.click(screen.getByRole('button', { name: /Đánh giá hạng phòng/ }));
    expect(await screen.findByText(review.comment!)).toBeInTheDocument();
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
  });

  it('renders review content as text and supports filtering by stars', async () => {
    vi.mocked(getRoomReviews).mockResolvedValue(
      pageOf([{ ...review, comment: '<script>alert(1)</script>' }]),
    );
    mount(<RoomReviewsSection roomTypeId="deluxe" />);
    expect(await screen.findByText('<script>alert(1)</script>')).toBeInTheDocument();
    expect(document.querySelector('section script')).toBeNull();
    fireEvent.mouseDown(screen.getByRole('combobox', { name: 'Lọc đánh giá theo số sao' }));
    fireEvent.click(await screen.findByText('1 sao'));
    await waitFor(() => expect(getRoomReviews).toHaveBeenCalledWith('deluxe', 0, 1));
  });
});

describe('manager reviews', () => {
  it('shows whole-result counts rather than current-page counts and keeps them across pagination', async () => {
    vi.mocked(getManagedReviewSummary).mockResolvedValue({
      totalReviews: 45,
      unrepliedReviews: 30,
      repliedReviews: 15,
      lowRatingReviews: 11,
    });
    vi.mocked(getManagedReviews).mockImplementation(async (page) => ({
      ...pageOf([managed]),
      page,
      totalElements: 45,
      totalPages: 3,
      last: page === 2,
    }));
    mount(<ReviewManagementPage />);
    const overview = screen.getByLabelText('Tổng quan kết quả đánh giá');
    expect(await within(overview).findByText('45')).toBeInTheDocument();
    expect(within(overview).getByText('30')).toBeInTheDocument();
    expect(within(overview).getByText('11')).toBeInTheDocument();
    fireEvent.click(document.querySelector('.ant-pagination-item-2')!);
    await waitFor(() => expect(getManagedReviews).toHaveBeenLastCalledWith(1, expect.any(Object)));
    expect(getManagedReviewSummary).toHaveBeenCalledTimes(1);
    expect(within(overview).getByText('30')).toBeInTheDocument();
  });

  it('restores reply/search/date filters and applies the same changes to the list and summary', async () => {
    mount(
      <ReviewManagementPage />,
      '/management/reviews?roomTypeId=deluxe&replied=false&search=RV-01&fromDate=2026-11-01&toDate=2026-11-09',
    );
    await screen.findByText(review.comment!);
    const initial = {
      roomTypeId: 'deluxe',
      replied: false,
      search: 'RV-01',
      fromDate: '2026-11-01',
      toDate: '2026-11-09',
      deleted: false,
    };
    expect(getManagedReviews).toHaveBeenLastCalledWith(0, expect.objectContaining(initial));
    expect(getManagedReviewSummary).toHaveBeenLastCalledWith(expect.objectContaining(initial));
    expect(screen.getByPlaceholderText('Từ ngày')).toHaveValue('01/11/2026');
    expect(screen.getByPlaceholderText('Đến ngày')).toHaveValue('09/11/2026');
    const searchInput = screen.getByRole('searchbox', { name: 'Tìm khách hoặc mã đặt phòng' });
    expect(searchInput).toHaveValue('RV-01');
    fireEvent.change(searchInput, { target: { value: ' Nguyễn ' } });
    fireEvent.click(screen.getByRole('button', { name: 'Tìm' }));
    await waitFor(() =>
      expect(getManagedReviews).toHaveBeenLastCalledWith(
        0,
        expect.objectContaining({ ...initial, search: 'Nguyễn' }),
      ),
    );
    fireEvent.mouseDown(screen.getByRole('combobox', { name: 'Lọc trạng thái phản hồi' }));
    fireEvent.click(await screen.findByText('Đã phản hồi'));
    await waitFor(() =>
      expect(getManagedReviewSummary).toHaveBeenLastCalledWith(
        expect.objectContaining({ ...initial, search: 'Nguyễn', replied: true }),
      ),
    );
    fireEvent.click(screen.getByRole('tab', { name: 'Lịch sử đã xóa' }));
    await waitFor(() =>
      expect(getManagedReviews).toHaveBeenLastCalledWith(
        0,
        expect.objectContaining({ ...initial, search: 'Nguyễn', replied: true, deleted: true }),
      ),
    );
    fireEvent.click(screen.getByRole('button', { name: 'Xóa bộ lọc' }));
    await waitFor(() =>
      expect(getManagedReviewSummary).toHaveBeenLastCalledWith({
        roomTypeId: undefined,
        rating: undefined,
        replied: undefined,
        search: undefined,
        fromDate: undefined,
        toDate: undefined,
        deleted: true,
      }),
    );
    expect(screen.getByRole('searchbox', { name: 'Tìm khách hoặc mã đặt phòng' })).toHaveValue('');
    expect(screen.getByPlaceholderText('Từ ngày')).toHaveValue('');
    expect(screen.getByPlaceholderText('Đến ngày')).toHaveValue('');
  });

  it('keeps the list usable and displays an error instead of false zero statistics', async () => {
    vi.mocked(getManagedReviewSummary).mockRejectedValue(new Error('offline'));
    mount(<ReviewManagementPage />);
    expect(await screen.findByText('Không tải được thống kê đánh giá')).toBeInTheDocument();
    expect(screen.getByText(review.comment!)).toBeInTheDocument();
    expect(
      within(screen.getByLabelText('Tổng quan kết quả đánh giá')).getAllByText('—'),
    ).toHaveLength(3);
  });

  it('refreshes complete statistics when replying removes the last unreplied match', async () => {
    mount(<ReviewManagementPage />, '/management/reviews?replied=false');
    await screen.findByText(review.comment!);
    fireEvent.click(screen.getByRole('button', { name: 'Phản hồi' }));
    fireEvent.change(await screen.findByLabelText('Nội dung phản hồi'), {
      target: { value: 'Cảm ơn bạn' },
    });
    vi.mocked(getManagedReviews).mockResolvedValue(pageOf([]));
    vi.mocked(getManagedReviewSummary).mockResolvedValue({
      totalReviews: 0,
      unrepliedReviews: 0,
      repliedReviews: 0,
      lowRatingReviews: 0,
    });
    fireEvent.click(screen.getByRole('button', { name: 'Lưu phản hồi' }));
    expect(await screen.findByText('Chưa có đánh giá phù hợp')).toBeInTheDocument();
    await waitFor(() =>
      expect(
        within(screen.getByLabelText('Tổng quan kết quả đánh giá')).getAllByText('0'),
      ).toHaveLength(3),
    );
    expect(getManagedReviewSummary).toHaveBeenCalledTimes(2);
  });

  it('restores filters from the URL and preserves them when switching tabs', async () => {
    mount(<ReviewManagementPage />, '/management/reviews?roomTypeId=deluxe&rating=2&view=deleted');
    await screen.findByText(review.comment!);
    expect(getManagedReviews).toHaveBeenCalledWith(
      0,
      expect.objectContaining({ roomTypeId: 'deluxe', rating: 2, deleted: true }),
    );
    expect(screen.getByRole('button', { name: 'Lọc 2 sao' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );

    fireEvent.click(screen.getByRole('tab', { name: 'Đang hiển thị' }));
    await waitFor(() =>
      expect(getManagedReviews).toHaveBeenLastCalledWith(
        0,
        expect.objectContaining({ roomTypeId: 'deluxe', rating: 2, deleted: false }),
      ),
    );
    fireEvent.click(screen.getByRole('button', { name: 'Lọc 5 sao' }));
    await waitFor(() =>
      expect(getManagedReviews).toHaveBeenLastCalledWith(
        0,
        expect.objectContaining({ roomTypeId: 'deluxe', rating: 5, deleted: false }),
      ),
    );
    fireEvent.click(screen.getByRole('button', { name: 'Xóa bộ lọc' }));
    await waitFor(() =>
      expect(getManagedReviews).toHaveBeenLastCalledWith(
        0,
        expect.objectContaining({ roomTypeId: undefined, rating: undefined, deleted: false }),
      ),
    );
    expect(screen.getByRole('button', { name: 'Tất cả số sao' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
  });

  it('opens reviews of a specific room type and waits for confirmation before deleting', async () => {
    mount(<ReviewManagementPage />, '/management/reviews?roomTypeId=deluxe');
    await screen.findByText(review.comment!);
    expect(getManagedReviews).toHaveBeenCalledWith(
      0,
      expect.objectContaining({ roomTypeId: 'deluxe', rating: undefined, deleted: false }),
    );
    const table = document.querySelector('table')!;
    fireEvent.click(within(table).getByRole('button', { name: /Xóa/ }));
    expect(deleteReview).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: 'Giữ lại' }));
    expect(deleteReview).not.toHaveBeenCalled();
    fireEvent.click(within(table).getByRole('button', { name: /Xóa/ }));
    vi.mocked(getManagedReviews).mockResolvedValue(pageOf([]));
    fireEvent.change(await screen.findByLabelText('Lý do xóa'), {
      target: { value: 'Nội dung spam' },
    });
    fireEvent.click(await screen.findByRole('button', { name: 'Xóa đánh giá' }));
    await waitFor(() =>
      expect(deleteReview).toHaveBeenCalledWith({ id: 'review-1', reason: 'Nội dung spam' }),
    );
    expect(await screen.findByText('Chưa có đánh giá phù hợp')).toBeInTheDocument();
  });

  it('refreshes the public summary after manager deletes the last review', async () => {
    vi.mocked(deleteReview).mockImplementation(async () => {
      vi.mocked(getManagedReviews).mockResolvedValue(pageOf([]));
      vi.mocked(getRoomReviews).mockResolvedValue(pageOf([]));
      vi.mocked(getReviewSummary).mockResolvedValue({ averageRating: null, totalReviews: 0 });
    });
    mount(
      <>
        <ReviewManagementPage />
        <RoomReviewsSection roomTypeId="deluxe" />
      </>,
    );
    await screen.findByText(/1 lượt đánh giá/);
    const table = document.querySelector('table')!;
    fireEvent.click(within(table).getByRole('button', { name: /Xóa/ }));
    fireEvent.change(await screen.findByLabelText('Lý do xóa'), {
      target: { value: 'Nội dung spam' },
    });
    fireEvent.click(await screen.findByRole('button', { name: 'Xóa đánh giá' }));
    expect(await screen.findByText('Chưa có đánh giá')).toBeInTheDocument();
    expect(await screen.findByText(/Hạng phòng chưa có đánh giá/)).toBeInTheDocument();
  });
});

it('shows full stay details to manager and requires a deletion reason', async () => {
  mount(<ReviewManagementPage />);
  fireEvent.click(await screen.findByRole('button', { name: 'Chi tiết lưu trú' }));
  expect(await screen.findByText('501')).toBeInTheDocument();
  expect(screen.getAllByText('Nguyễn Văn An').length).toBeGreaterThan(0);
  fireEvent.click(screen.getByRole('button', { name: 'Close' }));
  fireEvent.click(within(document.querySelector('table')!).getByRole('button', { name: /Xóa/ }));
  fireEvent.click(await screen.findByRole('button', { name: 'Xóa đánh giá' }));
  await screen.findByText('Vui lòng nhập lý do xóa');
  expect(deleteReview).not.toHaveBeenCalled();
});

it('shows deletion history with the actor and reason and no delete action', async () => {
  mount(<ReviewManagementPage />);
  await screen.findByText(review.comment!);
  vi.mocked(getManagedReviews).mockResolvedValue(
    pageOf([
      {
        ...managed,
        deletedAt: '2026-11-09T13:00:00Z',
        deletedByName: 'Quản lý A',
        deleteReason: 'Spam',
      },
    ]),
  );
  fireEvent.click(screen.getByRole('tab', { name: 'Lịch sử đã xóa' }));
  expect(await screen.findByText('Quản lý A')).toBeInTheDocument();
  expect(screen.getByText('Spam')).toBeInTheDocument();
  expect(getManagedReviews).toHaveBeenLastCalledWith(0, expect.objectContaining({ deleted: true }));
  expect(
    within(document.querySelector('table')!).queryByRole('button', { name: /Xóa/ }),
  ).not.toBeInTheDocument();
});

it('saves the hotel reply and shows the reply on the public list', async () => {
  mount(
    <>
      <ReviewManagementPage />
      <RoomReviewsSection roomTypeId="deluxe" />
    </>,
  );
  fireEvent.click(await screen.findByRole('button', { name: 'Phản hồi' }));
  fireEvent.change(await screen.findByLabelText('Nội dung phản hồi'), {
    target: { value: 'Khách sạn đã khắc phục.' },
  });
  vi.mocked(getRoomReviews).mockResolvedValue(
    pageOf([{ ...review, hotelReply: 'Khách sạn đã khắc phục.' }]),
  );
  fireEvent.click(screen.getByRole('button', { name: 'Lưu phản hồi' }));
  await waitFor(() =>
    expect(replyReview).toHaveBeenCalledWith('review-1', 'Khách sạn đã khắc phục.'),
  );
  expect(
    await within(screen.getByRole('region', { name: 'Đánh giá từ khách lưu trú' })).findByText(
      'Phản hồi từ HANBAY Hotel',
    ),
  ).toBeInTheDocument();
});

it('uses the page returned by the server when deletion removes the last page', async () => {
  const first = { ...pageOf([review]), size: 6, totalElements: 7, totalPages: 2, last: false };
  vi.mocked(getRoomReviews).mockResolvedValue(first);
  mount(<RoomReviewsSection roomTypeId="deluxe" />);
  await screen.findByText(review.comment!);
  vi.mocked(getRoomReviews).mockResolvedValue({
    ...first,
    page: 0,
    totalElements: 6,
    totalPages: 1,
    last: true,
  });
  fireEvent.click(document.querySelector('.ant-pagination-item-2')!);
  await waitFor(() => expect(getRoomReviews).toHaveBeenCalledWith('deluxe', 1, undefined));
  await waitFor(() => expect(getRoomReviews).toHaveBeenLastCalledWith('deluxe', 0, undefined));
  expect(screen.getByText(review.comment!)).toBeInTheDocument();
  expect(screen.queryByText(/Hạng phòng chưa có đánh giá/)).not.toBeInTheDocument();
});

it('hides the review action and makes no eligibility request when signed out', () => {
  authenticated = false;
  mount(<ReviewAction bookingId="booking-1" roomTypeName="Deluxe Twin" />);
  expect(screen.queryByRole('button', { name: /Đánh giá hạng phòng/ })).not.toBeInTheDocument();
  expect(getReviewEligibility).not.toHaveBeenCalled();
});
