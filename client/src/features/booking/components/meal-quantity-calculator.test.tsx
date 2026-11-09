// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest';
import { render, screen, fireEvent, cleanup } from '@testing-library/react';
import { afterEach, it, expect, vi } from 'vitest';
import { useState } from 'react';
import { MealQuantityCalculator } from './meal-quantity-calculator';
import { calculateMealQuantity, type MealQuantityInput } from '../utils/meal-quantity';
afterEach(cleanup);

function Calculator({
  nights = 3,
  onQuantityChange,
}: {
  nights?: number;
  onQuantityChange: (quantity: number) => void;
}) {
  const [value, setValue] = useState<MealQuantityInput>();
  return (
    <MealQuantityCalculator
      nights={nights}
      value={value}
      onChange={(input) => {
        setValue(input);
        onQuantityChange(calculateMealQuantity(input, nights).quantity);
      }}
    />
  );
}

it('does not select meal services automatically when first displayed', () => {
  const change = vi.fn();
  render(<Calculator onQuantityChange={change} />);
  expect(change).not.toHaveBeenCalled();
  expect(screen.getByText('ngày = 0 suất')).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'Áp dụng số suất' })).not.toBeInTheDocument();
});

it('updates the selected quantity immediately when people or days change', () => {
  const change = vi.fn();
  render(<Calculator onQuantityChange={change} />);
  fireEvent.change(screen.getByRole('spinbutton', { name: 'Số người dùng bữa' }), {
    target: { value: '2' },
  });
  expect(change).toHaveBeenLastCalledWith(6);
  expect(screen.getByText('ngày = 6 suất')).toBeInTheDocument();
  fireEvent.change(screen.getByRole('spinbutton', { name: 'Số ngày dùng bữa' }), {
    target: { value: '2' },
  });
  expect(change).toHaveBeenLastCalledWith(4);
});

it('removes the selected meal when people are cleared and does not substitute one', () => {
  const change = vi.fn();
  render(<Calculator onQuantityChange={change} />);
  fireEvent.change(screen.getByRole('spinbutton', { name: 'Số người dùng bữa' }), {
    target: { value: '2' },
  });
  fireEvent.change(screen.getByRole('spinbutton', { name: 'Số người dùng bữa' }), {
    target: { value: '' },
  });
  expect(change).toHaveBeenLastCalledWith(0);
  expect(screen.getByText('ngày = 0 suất')).toBeInTheDocument();
});

it('shows the stay limit and clears stale quantities when days are invalid or empty', () => {
  const change = vi.fn();
  render(<Calculator nights={1} onQuantityChange={change} />);
  fireEvent.change(screen.getByRole('spinbutton', { name: 'Số người dùng bữa' }), {
    target: { value: '2' },
  });
  expect(change).toHaveBeenLastCalledWith(2);
  fireEvent.change(screen.getByRole('spinbutton', { name: 'Số ngày dùng bữa' }), {
    target: { value: '3' },
  });
  expect(change).toHaveBeenLastCalledWith(0);
  expect(screen.getByRole('alert')).toHaveTextContent('từ 1 đến 2 ngày');
  fireEvent.change(screen.getByRole('spinbutton', { name: 'Số ngày dùng bữa' }), {
    target: { value: '' },
  });
  expect(change).toHaveBeenLastCalledWith(0);
  fireEvent.change(screen.getByRole('spinbutton', { name: 'Số ngày dùng bữa' }), {
    target: { value: '1' },
  });
  expect(change).toHaveBeenLastCalledWith(2);
  expect(screen.queryByRole('alert')).not.toBeInTheDocument();
});
