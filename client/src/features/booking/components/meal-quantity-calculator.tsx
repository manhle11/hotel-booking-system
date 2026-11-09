import { InputNumber, Space, Typography } from 'antd';
import { calculateMealQuantity, type MealQuantityInput } from '../utils/meal-quantity';

export function MealQuantityCalculator({
  nights,
  value,
  onChange,
  disabled,
}: {
  nights: number;
  value?: MealQuantityInput;
  onChange: (value: MealQuantityInput) => void;
  disabled?: boolean;
}) {
  const input = value ?? { people: null, days: Math.max(1, nights) };
  const result = calculateMealQuantity(input, nights);
  return (
    <div>
      <Typography.Text type="secondary">
        Tự tính số suất và tiền dịch vụ theo người × ngày. Trẻ em chỉ tính khi bạn chọn.
      </Typography.Text>
      <Space wrap>
        <InputNumber
          aria-label="Số người dùng bữa"
          precision={0}
          value={input.people}
          placeholder="Số người"
          onChange={(people) => onChange({ ...input, people })}
          disabled={disabled}
        />
        <span>người ×</span>
        <InputNumber
          aria-label="Số ngày dùng bữa"
          precision={0}
          value={input.days}
          onChange={(days) => onChange({ ...input, days })}
          disabled={disabled}
        />
        <span>ngày = {result.error ? '—' : result.quantity} suất</span>
      </Space>
      {result.error && (
        <Typography.Paragraph type="danger" role="alert" style={{ marginBottom: 0 }}>
          {result.error}
        </Typography.Paragraph>
      )}
    </div>
  );
}
