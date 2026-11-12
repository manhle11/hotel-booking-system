import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, Button, InputNumber, Select, Space, Spin, Typography } from 'antd';
import { getFloorPlan, usePublicMap } from './api';
import { FloorPlanEditor } from './floor-plan-editor';

export function FloorPlanManagementPage() {
  const client = useQueryClient();
  const floors = usePublicMap();
  const [selected, setSelected] = useState<number>();
  const [reload, setReload] = useState(0);
  const floorNumber = selected ?? floors.data?.floors[0]?.floorNumber ?? 1;
  const query = useQuery({
    queryKey: ['floor-plan', floorNumber],
    queryFn: () => getFloorPlan(client, floorNumber),
    refetchOnWindowFocus: false,
    enabled: selected !== undefined || floors.isSuccess,
  });
  return (
    <main className="floor-plan-editor">
      <Typography.Title level={2}>Cấu hình sơ đồ tầng</Typography.Title>
      <Typography.Paragraph type="secondary">
        Bố trí phòng và khu vực chung theo mặt bằng thực tế. Lưu và công bố để khách và lễ tân cùng
        sử dụng.
      </Typography.Paragraph>
      <Space wrap style={{ marginBottom: 20 }}>
        <Select
          aria-label="Chọn tầng cấu hình"
          value={floorNumber}
          style={{ width: 180 }}
          onChange={setSelected}
          options={floors.data?.floors.map((f) => ({ value: f.floorNumber, label: f.name }))}
        />
        <span>Hoặc nhập tầng:</span>
        <InputNumber
          aria-label="Số tầng cấu hình"
          value={floorNumber}
          min={-10}
          max={200}
          precision={0}
          onChange={(value) => {
            if (value !== null) setSelected(value);
          }}
        />
      </Space>
      {(floors.isLoading || query.isLoading) && <Spin />}
      {floors.isError && (
        <Alert
          type="error"
          title="Không thể tải danh sách tầng"
          description="Hãy thử lại hoặc nhập số tầng để cấu hình trực tiếp."
          action={<Button onClick={() => floors.refetch()}>Thử lại danh sách tầng</Button>}
        />
      )}
      {query.isError && (
        <Alert
          type="error"
          title={query.error.message}
          action={<Button onClick={() => query.refetch()}>Thử lại</Button>}
        />
      )}
      {query.data && (
        <FloorPlanEditor
          key={`${floorNumber}-${query.data.version}-${reload}`}
          floor={query.data}
          onReload={async () => {
            const result = await query.refetch();
            if (result.isSuccess) setReload((value) => value + 1);
          }}
        />
      )}
    </main>
  );
}
