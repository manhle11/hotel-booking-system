// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { API_CLIENT_EVENTS } from '../../../lib/axios';
import { authStorage } from '../../../lib/storage';
import { useAuth } from './auth-context';
import { AuthProvider } from './auth-provider';

vi.mock('../refresh-token', () => ({ refreshAccessToken: vi.fn() }));

const owner = {
  id: '2b513a5a-8e19-5c7c-84c7-cd13df6cd541',
  email: 'review.customer@example.test',
  fullName: 'Khách A',
  phone: null,
};
const other = { ...owner, id: '18a1fe26-902a-5663-b6ef-b4e9a08d750a', fullName: 'Khách B' };
let client: QueryClient;

function SessionControls() {
  const auth = useAuth();
  return (
    <>
      <span>{auth.session?.user?.fullName ?? 'Đã đăng xuất'}</span>
      <button onClick={auth.logout}>Đăng xuất</button>
      <button
        onClick={() => auth.startSession('other-access', 'other-refresh', [], 3600, true, other)}
      >
        Đổi tài khoản
      </button>
    </>
  );
}

beforeEach(() => {
  localStorage.clear();
  sessionStorage.clear();
  authStorage.setSession('owner-access', [], 3600, true, 'owner-refresh', owner);
  client = new QueryClient({ defaultOptions: { queries: { staleTime: 60_000, retry: false } } });
});
afterEach(() => {
  cleanup();
  client.clear();
  localStorage.clear();
  sessionStorage.clear();
});

function mount() {
  render(
    <QueryClientProvider client={client}>
      <AuthProvider>
        <SessionControls />
      </AuthProvider>
    </QueryClientProvider>,
  );
}

it('removes cached private data on logout and prevents a late query response from restoring it', async () => {
  mount();
  client.setQueryData(['booking-order', 'private-order', owner.id], { reviewOwner: true });
  let finish!: (value: { reviewOwner: boolean }) => void;
  let signal!: AbortSignal;
  const request = client
    .fetchQuery({
      queryKey: ['booking-history'],
      queryFn: (context) => {
        signal = context.signal;
        return new Promise<{ reviewOwner: boolean }>((resolve) => {
          finish = resolve;
        });
      },
    })
    .catch(() => undefined);

  fireEvent.click(screen.getByRole('button', { name: 'Đăng xuất' }));
  expect(screen.getByText('Đã đăng xuất')).toBeInTheDocument();
  expect(signal.aborted).toBe(true);
  expect(client.getQueryCache().getAll()).toHaveLength(0);
  expect(authStorage.getSession()).toBeNull();
  await act(async () => {
    finish({ reviewOwner: true });
    await request;
  });
  expect(client.getQueryCache().getAll()).toHaveLength(0);
});

it('clears the old account cache and mutation results when starting another session without logout', async () => {
  mount();
  client.setQueryData(['booking-order', 'private-order', owner.id], { reviewOwner: true });
  await client
    .getMutationCache()
    .build(client, { mutationFn: async () => ({ contactEmail: owner.email }) })
    .execute(undefined);
  fireEvent.click(screen.getByRole('button', { name: 'Đổi tài khoản' }));
  expect(screen.getByText('Khách B')).toBeInTheDocument();
  expect(client.getQueryCache().getAll()).toHaveLength(0);
  expect(client.getMutationCache().getAll()).toHaveLength(0);
  expect(authStorage.getSession()?.user?.id).toBe(other.id);
});

it('also clears private data when the API expires the session', () => {
  mount();
  client.setQueryData(['review-eligibility', owner.id, 'private-booking'], { canReview: true });
  act(() => {
    window.dispatchEvent(new CustomEvent(API_CLIENT_EVENTS.authExpired));
  });
  expect(screen.getByText('Đã đăng xuất')).toBeInTheDocument();
  expect(client.getQueryCache().getAll()).toHaveLength(0);
});
