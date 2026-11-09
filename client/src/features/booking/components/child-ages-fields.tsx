import { Form, InputNumber, Space } from 'antd';
import { useHotelPolicy } from '../../../hooks/use-stay-policy';
import { ChildPolicyNote } from '../../../components/room/ChildPolicyNote';

export function ChildAgesFields({ count }: { count: number }) {
  const policy = useHotelPolicy();
  return (
    <>
      <ChildPolicyNote />
      <Space wrap>
        {Array.from({ length: Math.min(count, 20) }, (_, i) => (
          <Form.Item
            key={i}
            name={['childAges', i]}
            label={`Tuổi trẻ em ${i + 1} tại ngày nhận phòng`}
            rules={[
              { required: true, message: 'Vui lòng nhập tuổi trẻ em.' },
              {
                type: 'number',
                min: 0,
                max: (policy.adultFromAge ?? 12) - 1,
                message: `Khách từ ${policy.adultFromAge ?? 12} tuổi cần tính là người lớn.`,
              },
            ]}
          >
            <InputNumber precision={0} />
          </Form.Item>
        ))}
      </Space>
    </>
  );
}
