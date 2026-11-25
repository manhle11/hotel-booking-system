// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { recommendation } from '../test/fixtures';
import { RoomRecommendationResults } from './room-recommendation-results';

beforeAll(() => {
  window.matchMedia = vi
    .fn()
    .mockReturnValue({ matches: false, addListener: vi.fn(), removeListener: vi.fn() });
});
afterEach(cleanup);
describe('conversational recommendation cards', () => {
  it('shows verified facts and sends inspection/selection back through chat', () => {
    const onAsk = vi.fn();
    render(<RoomRecommendationResults result={recommendation} onAsk={onAsk} />);
    expect(screen.getByText('Deluxe Twin')).toBeVisible();
    expect(screen.getByText('2 giường đơn')).toBeVisible();
    expect(screen.getByText('Wi-Fi tốc độ cao')).toBeVisible();
    expect(screen.getByText(/5\.000\.000/)).toBeVisible();
    expect(screen.queryByText(/81[,.]3|trọng số|100/)).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Tiếp tục đặt phòng' })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Tìm hiểu thêm' }));
    fireEvent.click(screen.getByRole('button', { name: 'Chọn phòng này' }));
    expect(onAsk.mock.calls).toEqual([
      ['Cho tôi tìm hiểu phòng Deluxe Twin'],
      ['Tôi chọn phòng Deluxe Twin'],
    ]);
  });
  it('defers clarification and no-match explanations to the conversation', () => {
    for (const status of ['NEEDS_CLARIFICATION', 'NO_MATCH', 'TEMPORARILY_UNAVAILABLE'] as const) {
      const { container, unmount } = render(
        <RoomRecommendationResults
          result={{ ...recommendation, status, recommendations: [] }}
          onAsk={vi.fn()}
        />,
      );
      expect(container).toBeEmptyDOMElement();
      unmount();
    }
  });
  it('prevents another selection while the current turn is pending', () => {
    const onAsk = vi.fn();
    render(<RoomRecommendationResults result={recommendation} onAsk={onAsk} disabled />);
    fireEvent.click(screen.getByRole('button', { name: 'Chọn phòng này' }));
    expect(onAsk).not.toHaveBeenCalled();
  });
});
