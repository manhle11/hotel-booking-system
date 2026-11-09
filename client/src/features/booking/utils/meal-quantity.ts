export interface MealQuantityInput {
  people: number | null;
  days: number | null;
}

export function calculateMealQuantity(input: MealQuantityInput, nights: number) {
  // An empty guest count means the optional meal service is not selected.
  if (input.people == null || input.people === 0) return { quantity: 0, error: undefined };
  if (!Number.isInteger(input.people) || input.people < 0 || input.people > 20) {
    return { quantity: 0, error: 'Số người dùng bữa phải là số nguyên từ 1 đến 20.' };
  }
  const maxDays = Math.max(1, nights + 1);
  if (
    input.days == null ||
    !Number.isInteger(input.days) ||
    input.days < 1 ||
    input.days > maxDays
  ) {
    return {
      quantity: 0,
      error: `Số ngày dùng bữa phải từ 1 đến ${maxDays} ngày trong kỳ lưu trú.`,
    };
  }
  const quantity = input.people * input.days;
  if (quantity > 1000) return { quantity: 0, error: 'Tổng số suất không được vượt quá 1000.' };
  return { quantity, error: undefined };
}
