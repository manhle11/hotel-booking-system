import { Typography } from 'antd';
import { useHotelPolicy } from '../../hooks/use-stay-policy';

export function ChildPolicyNote() {
  const policy = useHotelPolicy();
  return (
    <Typography.Paragraph type="secondary">
      Tuổi tính tại ngày nhận phòng: dưới {policy.youngChildUnderAge ?? 6} tuổi là trẻ nhỏ; từ{' '}
      {policy.youngChildUnderAge ?? 6} đến dưới {policy.adultFromAge ?? 12} tuổi là trẻ em; từ{' '}
      {policy.adultFromAge ?? 12} tuổi tính như người lớn. Trẻ nhỏ và trẻ em đều tính vào giới hạn
      trẻ em và tổng khách, dùng giường có sẵn. Dịch vụ mua thêm tính theo số lượng đã chọn, không
      tự miễn phí theo tuổi.
    </Typography.Paragraph>
  );
}
