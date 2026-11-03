import { Typography } from 'antd';

import { useStayPolicy } from '../../hooks/use-stay-policy';

export function StayPolicyNote() {
  const { policy } = useStayPolicy();
  return (
    <Typography.Paragraph type="secondary" style={{ marginTop: 8, marginBottom: 0 }}>
      Nhận phòng từ {policy.checkInTime.slice(0, 5)} · Trả phòng trước{' '}
      {policy.checkOutTime.slice(0, 5)} (giờ khách sạn). Ngày trả phòng không tính thêm đêm.
    </Typography.Paragraph>
  );
}
