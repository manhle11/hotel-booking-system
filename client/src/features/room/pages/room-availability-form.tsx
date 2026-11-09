import { Button, Card, Col, DatePicker, Form, InputNumber, Row, Select, Typography } from 'antd';
import type { FormInstance } from 'antd';
import { useStayPolicy } from '../../../hooks/use-stay-policy';
import { countStayNights } from '../../../utils/hotel-time';
import { ChildPolicyNote } from '../../../components/room/ChildPolicyNote';
import { StayPolicyNote } from '../../../components/room/StayPolicyNote';

import { BookingSteps } from '@components/booking/BookingSteps';
import type { SearchFormValues } from './room-search-utils';

const { RangePicker } = DatePicker;
const { Text, Title } = Typography;

interface RoomAvailabilityFormProps {
  form: FormInstance<SearchFormValues>;
  initialValues: SearchFormValues;
  searchLoading: boolean;
  onSearch: (values: SearchFormValues) => void;
}

export function RoomAvailabilityForm({
  form,
  initialValues,
  searchLoading,
  onSearch,
}: RoomAvailabilityFormProps) {
  const { disabledArrivalDate } = useStayPolicy();
  return (
    <Card className="booking-panel" bordered>
      <div className="booking-panel-top">
        <div>
          <Text className="eyebrow">Kiểm tra phòng trống</Text>
          <Title level={3}>Tìm phòng phù hợp</Title>
        </div>
        <BookingSteps current={0} className="booking-steps" />
      </div>

      <Form
        form={form}
        layout="vertical"
        initialValues={initialValues}
        requiredMark={false}
        onFinish={(values) => onSearch(values)}
      >
        <Row gutter={[14, 4]} align="bottom">
          <Col xs={24} lg={8}>
            <Form.Item
              label="Ngày nhận phòng → Ngày trả phòng"
              name="stayDates"
              rules={[
                { required: true, message: 'Vui lòng chọn ngày lưu trú.' },
                {
                  validator: (_, dates: SearchFormValues['stayDates'] | undefined) =>
                    countStayNights(dates?.[0], dates?.[1]) > 0
                      ? Promise.resolve()
                      : Promise.reject(new Error('Ngày trả phòng phải sau ngày nhận phòng.')),
                },
              ]}
            >
              <RangePicker
                allowClear={false}
                format="DD/MM/YYYY"
                placeholder={['Ngày nhận phòng', 'Ngày trả phòng']}
                disabledDate={disabledArrivalDate}
                style={{ width: '100%' }}
              />
            </Form.Item>
          </Col>
          <Col xs={12} md={6} lg={3}>
            <Form.Item label="Người lớn" name="adults" rules={[{ type: 'number', min: 1 }]}>
              <InputNumber min={1} max={12} style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col xs={12} md={6} lg={3}>
            <Form.Item label="Trẻ em" name="children" rules={[{ type: 'number', min: 0 }]}>
              <InputNumber min={0} max={8} style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col xs={24} lg={10}>
            <Form.Item>
              <Button block htmlType="submit" loading={searchLoading} size="large" type="primary">
                Tìm kiếm
              </Button>
            </Form.Item>
          </Col>
        </Row>
        <Row gutter={14}>
          <Col xs={24} md={8}>
            <Form.Item name="viewType" label="Hướng nhìn">
              <Select
                allowClear
                placeholder="Tất cả hướng nhìn"
                options={[
                  { value: 'COURTYARD', label: 'Hướng sân trong' },
                  { value: 'CITY', label: 'Hướng thành phố' },
                  { value: 'SEA', label: 'Hướng biển' },
                ]}
              />
            </Form.Item>
          </Col>
          <Col xs={12} md={8}>
            <Form.Item
              name="hasBalcony"
              label="Ban công"
              getValueProps={(value) => ({
                value: value === undefined ? undefined : String(value),
              })}
              normalize={(value) => (value === undefined ? undefined : value === 'true')}
            >
              <Select
                allowClear
                placeholder="Tất cả"
                options={[
                  { value: 'true', label: 'Có ban công' },
                  { value: 'false', label: 'Không có ban công' },
                ]}
              />
            </Form.Item>
          </Col>
          <Col xs={12} md={8}>
            <Form.Item
              name="highFloor"
              label="Vị trí tầng"
              getValueProps={(value) => ({
                value: value === undefined ? undefined : String(value),
              })}
              normalize={(value) => (value === undefined ? undefined : value === 'true')}
            >
              <Select
                allowClear
                placeholder="Tất cả"
                options={[
                  { value: 'true', label: 'Tầng cao' },
                  { value: 'false', label: 'Không ở tầng cao' },
                ]}
              />
            </Form.Item>
          </Col>
        </Row>
        <ChildPolicyNote />
        <StayPolicyNote />
      </Form>
    </Card>
  );
}
