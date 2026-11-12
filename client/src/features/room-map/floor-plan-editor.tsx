import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  App,
  Button,
  Collapse,
  ConfigProvider,
  Empty,
  Input,
  InputNumber,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
} from 'antd';
import { ApiClientError } from '../../types/api';
import { invalidateRoomMaps, publishFloorPlan, type ElementKind, type FloorPlan } from './api';
import {
  addLayoutElement,
  canFillFloor,
  createLayoutTemplate,
  elementLabels,
  layoutIssue,
  proposeGeometry,
  type DraftLayout,
  type EditorElement,
  type LayoutTemplate,
} from './floor-plan-layout';
import { FloorPlanCanvasEditor } from './floor-plan-canvas-editor';
import { MAP_CORE, knownRoomArea, roomAreaLabel, roomFootprint } from './room-map-scale';

interface Props {
  floor: FloorPlan;
  onReload: () => Promise<void>;
}
type Notice = { type: 'success' | 'error'; message: string };
const kinds = Object.entries(elementLabels).map(([value, label]) => ({
  value: value as ElementKind,
  label,
}));

export function FloorPlanEditor({ floor, onReload }: Props) {
  const client = useQueryClient();
  const { message } = App.useApp();
  const [name, setName] = useState(floor.name);
  const [initial] = useState<DraftLayout>(() => ({
    columns: floor.gridColumns,
    rows: floor.gridRows,
    elements: floor.elements.map((e) => ({
      editorId: e.id,
      kind: e.kind,
      roomId: floor.rooms.find((room) => room.key === e.roomKey)?.roomId ?? null,
      label: e.label ?? '',
      x: e.x,
      y: e.y,
      width: e.width,
      height: e.height,
    })),
  }));
  const [history, setHistory] = useState<{
    past: DraftLayout[];
    present: DraftLayout;
    future: DraftLayout[];
  }>({ past: [], present: initial, future: [] });
  const layout = history.present;
  const [template, setTemplate] = useState<LayoutTemplate>(
    floor.floorNumber === 1 && !floor.rooms.length
      ? 'lobby'
      : canFillFloor(floor.rooms.length)
        ? 'fill-floor'
        : 'double-sided',
  );
  const [allowRoomResize, setAllowRoomResize] = useState(false);
  const [selectedId, setSelectedId] = useState<string>();
  const [showGrid, setShowGrid] = useState(true);
  const [notice, setNotice] = useState<Notice>();
  const selected = layout.elements.find((e) => e.editorId === selectedId);
  const selectedRoom = floor.rooms.find((room) => room.roomId === selected?.roomId);
  const unplaced = floor.rooms.filter(
    (room) => !layout.elements.some((e) => e.roomId === room.roomId),
  );
  const issue = layoutIssue(layout);
  const dirty = name !== floor.name || JSON.stringify(layout) !== JSON.stringify(initial);
  const commit = (next: DraftLayout) => {
    setHistory((current) =>
      JSON.stringify(current.present) === JSON.stringify(next)
        ? current
        : { past: [...current.past, current.present].slice(-30), present: next, future: [] },
    );
  };
  const edit = (id: string, patch: Partial<EditorElement>) => {
    if (patch.roomId && !allowRoomResize && template !== 'fill-floor') {
      const footprint = roomFootprint(
        floor.rooms.find((room) => room.roomId === patch.roomId)?.areaM2,
      );
      patch = { ...patch, width: footprint.width, height: footprint.height };
    }
    commit({
      ...layout,
      elements: layout.elements.map((e) => (e.editorId === id ? { ...e, ...patch } : e)),
    });
    setNotice(undefined);
  };
  const save = useMutation({
    mutationFn: () =>
      publishFloorPlan(client, floor.floorNumber, {
        name,
        gridColumns: layout.columns,
        gridRows: layout.rows,
        expectedVersion: floor.version,
        elements: layout.elements.map(({ kind, roomId, label, x, y, width, height }) => ({
          kind,
          roomId,
          label,
          x,
          y,
          width,
          height,
        })),
      }),
    onSuccess: async (data) => {
      client.setQueryData(['floor-plan', floor.floorNumber], data);
      await invalidateRoomMaps(client);
      void message.success('Đã công bố sơ đồ tầng');
    },
  });
  const applyTemplate = () => {
    try {
      const next = createLayoutTemplate(floor.rooms, template);
      commit(next);
      setAllowRoomResize(false);
      setSelectedId(undefined);
      setNotice({
        type: 'success',
        message:
          template === 'lobby'
            ? 'Đã tạo sảnh, quầy lễ tân và các khu vực phục vụ khách. Bạn có thể kéo thả để chỉnh tiếp rồi công bố.'
            : template === 'fill-floor'
              ? `Đã dàn ${floor.rooms.length} ô phòng bằng nhau ra hai bên hành lang. Kiểm tra và lưu để công bố.`
              : `Đã tự xếp đều ${floor.rooms.length} phòng. Bạn có thể kéo thả để chỉnh tiếp hoặc lưu bố cục này.`,
      });
    } catch (error) {
      setNotice({ type: 'error', message: (error as Error).message });
    }
  };
  const add = (kind: ElementKind, roomId: string | null = null) => {
    try {
      const size =
        kind === 'ROOM'
          ? roomFootprint(floor.rooms.find((room) => room.roomId === roomId)?.areaM2)
          : (MAP_CORE.find((core) => core.kind === kind) ?? { width: 8, height: 4 });
      const element = {
        editorId: crypto.randomUUID(),
        kind,
        roomId,
        label: kind === 'ROOM' ? '' : elementLabels[kind],
        width: size.width,
        height: size.height,
      };
      commit(addLayoutElement(layout, element));
      setSelectedId(element.editorId);
      setNotice(undefined);
    } catch (error) {
      setNotice({ type: 'error', message: (error as Error).message });
    }
  };
  const undo = () => {
    setHistory((current) =>
      !current.past.length
        ? current
        : {
            past: current.past.slice(0, -1),
            present: current.past.at(-1)!,
            future: [current.present, ...current.future],
          },
    );
    setNotice(undefined);
  };
  const redo = () => {
    setHistory((current) =>
      !current.future.length
        ? current
        : {
            past: [...current.past, current.present],
            present: current.future[0],
            future: current.future.slice(1),
          },
    );
    setNotice(undefined);
  };
  const remove = (id: string) => {
    commit({ ...layout, elements: layout.elements.filter((e) => e.editorId !== id) });
    if (id === selectedId) setSelectedId(undefined);
    setNotice(undefined);
  };
  const errorMessage =
    save.error instanceof ApiClientError
      ? save.error.data?.errors?.floorPlan || save.error.message
      : save.error?.message;
  const coordinate = (field: 'x' | 'y' | 'width' | 'height', label: string) => ({
    title: label,
    width: 85,
    render: (_: unknown, e: EditorElement) => (
      <InputNumber
        aria-label={`${label} ${layout.elements.indexOf(e) + 1}`}
        value={e[field]}
        disabled={
          save.isPending ||
          ((field === 'width' || field === 'height') &&
            !allowRoomResize &&
            e.kind === 'ROOM' &&
            knownRoomArea(floor.rooms.find((room) => room.roomId === e.roomId)?.areaM2))
        }
        min={field === 'x' || field === 'y' ? 0 : 1}
        max={80}
        precision={0}
        onChange={(value) => {
          if (value !== null) edit(e.editorId, { [field]: value });
        }}
      />
    ),
  });
  return (
    <ConfigProvider componentDisabled={save.isPending}>
      <div className="floor-plan-easy-editor">
        <div className="floor-plan-easy-heading">
          <div>
            <Typography.Title level={4}>Sắp xếp phòng trực quan</Typography.Title>
            <Typography.Paragraph type="secondary">
              Chọn mẫu, tự xếp đều rồi kéo thả để chỉnh theo mặt bằng thực tế.
            </Typography.Paragraph>
          </div>
          <Tag color={dirty ? 'gold' : 'green'}>
            {dirty ? 'Bản nháp chưa công bố' : 'Đang xem bản đã lưu'}
          </Tag>
        </div>
        <div className="floor-plan-template-bar">
          <label>
            Tên tầng
            <Input
              aria-label="Tên tầng"
              value={name}
              maxLength={80}
              onChange={(event) => setName(event.target.value)}
            />
          </label>
          <label>
            Bố cục mẫu
            <Select
              aria-label="Bố cục mẫu"
              value={template}
              onChange={setTemplate}
              options={[
                { value: 'double-sided', label: 'Hai dãy đối diện · hành lang ở giữa' },
                { value: 'single-sided', label: 'Một dãy · hành lang phía dưới' },
                {
                  value: 'fill-floor',
                  label: 'Dàn đầy tầng · hai dãy cân đối',
                  disabled: !canFillFloor(floor.rooms.length),
                },
                ...(!floor.rooms.length
                  ? [{ value: 'lobby', label: 'Sảnh đón khách · quầy lễ tân' }]
                  : []),
              ]}
            />
          </label>
          <Button
            type="primary"
            onClick={applyTemplate}
            disabled={save.isPending || (!floor.rooms.length && template !== 'lobby')}
          >
            Tự xếp đều
          </Button>
        </div>
        <Typography.Paragraph type="secondary" className="floor-plan-template-note">
          {template === 'fill-floor'
            ? 'Mẫu dàn đầy chia đều phòng ra hai bên hành lang, kéo dài ô theo khung hiển thị. Kích thước ô minh họa bố cục; diện tích trong danh mục hạng không thay đổi.'
            : 'Mẫu một/hai dãy định cỡ phòng theo diện tích hạng. Có thể chọn mẫu dàn đầy cho tầng có số phòng chẵn từ 2 đến 10.'}{' '}
          Có thể chỉnh kích thước thủ công trong Nâng cao. Hành lang, cầu thang và thang máy được
          căn đồng bộ. Thay đổi chỉ được công bố khi bấm Lưu và công bố.
        </Typography.Paragraph>
        <div className="room-map-toolbar">
          <Space wrap>
            <Button onClick={undo} disabled={save.isPending || !history.past.length}>
              Hoàn tác
            </Button>
            <Button onClick={redo} disabled={save.isPending || !history.future.length}>
              Làm lại
            </Button>
            <Switch aria-label="Hiện lưới căn chỉnh" checked={showGrid} onChange={setShowGrid} />
            <span>Hiện lưới căn chỉnh</span>
          </Space>
          <Typography.Text type="secondary">
            {floor.rooms.length
              ? `${floor.rooms.length - unplaced.length}/${floor.rooms.length} phòng đã bố trí`
              : `${layout.elements.length} khu vực đã bố trí`}
          </Typography.Text>
        </div>
        <Typography.Paragraph type="secondary" className="floor-plan-template-note">
          Lưới giúp căn thẳng hàng và khoảng cách khi kéo thả; đường đậm cách nhau 5 ô. Bật/tắt lưới
          chỉ đổi cách nhìn. Các ô luôn di chuyển theo từng bước lưới.
        </Typography.Paragraph>
        {(notice || issue || save.isError) && (
          <Alert
            showIcon
            type={issue || save.isError ? 'error' : notice!.type}
            title={errorMessage || issue || notice?.message}
          />
        )}
        <div className="floor-plan-workspace">
          <div className="floor-plan-drawing">
            <FloorPlanCanvasEditor
              layout={layout}
              rooms={floor.rooms}
              selectedId={selectedId}
              onSelect={setSelectedId}
              onChange={commit}
              disabled={save.isPending}
              showGrid={showGrid}
              isLobby={floor.floorNumber === 1 && !floor.rooms.length}
              allowRoomResize={allowRoomResize}
              onNotice={(text) => setNotice(text ? { type: 'error', message: text } : undefined)}
            />
            {!layout.elements.length && (
              <div className="floor-plan-empty-hint">
                <Empty description="Bấm Tự xếp đều để tạo sơ đồ đầu tiên" />
              </div>
            )}
          </div>
          <aside className="floor-plan-tools" aria-label="Công cụ chỉnh sơ đồ">
            <Typography.Title level={5}>Thêm vào sơ đồ</Typography.Title>
            {unplaced.length ? (
              <Select
                aria-label="Thêm phòng vào sơ đồ"
                placeholder={`Chọn phòng (${unplaced.length} chưa bố trí)`}
                value={null}
                options={unplaced.map((room) => ({
                  value: room.roomId,
                  label: `${room.roomNumber} · ${room.roomTypeName}`,
                }))}
                onChange={(value) => add('ROOM', value)}
              />
            ) : (
              <Typography.Paragraph type="secondary">
                {floor.rooms.length
                  ? 'Đã bố trí đủ các phòng.'
                  : 'Tầng khu vực chung, không có phòng lưu trú.'}
              </Typography.Paragraph>
            )}
            <div className="floor-plan-add-buttons">
              {(['CORRIDOR', 'STAIRS', 'ELEVATOR', 'COMMON_AREA'] as const).map((kind) => (
                <Button key={kind} onClick={() => add(kind)}>
                  {elementLabels[kind]}
                </Button>
              ))}
            </div>
            <div className="floor-plan-selected-tools">
              <Typography.Title level={5}>
                {selected
                  ? selected.kind === 'ROOM'
                    ? `Phòng ${selectedRoom?.roomNumber ?? ''}`
                    : elementLabels[selected.kind]
                  : 'Chỉnh một ô'}
              </Typography.Title>
              {!selected ? (
                <Typography.Paragraph type="secondary">
                  Bấm vào một phòng hoặc khu vực để chọn. Kéo ô để di chuyển, kéo góc dưới phải để
                  đổi kích thước.
                </Typography.Paragraph>
              ) : (
                <>
                  {selected.kind === 'ROOM' ? (
                    <>
                      <Typography.Paragraph type="secondary">
                        {selectedRoom?.roomTypeName}
                      </Typography.Paragraph>
                      <Typography.Paragraph strong>
                        {roomAreaLabel(selectedRoom?.areaM2)}
                      </Typography.Paragraph>
                      {knownRoomArea(selectedRoom?.areaM2) && (
                        <Typography.Paragraph type="secondary">
                          {allowRoomResize
                            ? 'Đang cho phép chỉnh kích thước trên sơ đồ. Diện tích hạng phòng giữ nguyên.'
                            : 'Kích thước ô được giữ theo bố cục; mở Nâng cao để chỉnh thủ công.'}
                        </Typography.Paragraph>
                      )}
                      <Select
                        aria-label="Đổi chỗ với phòng"
                        value={null}
                        placeholder="Đổi chỗ với phòng…"
                        options={layout.elements
                          .filter(
                            (e) =>
                              e.kind === 'ROOM' &&
                              e.editorId !== selected.editorId &&
                              e.width === selected.width &&
                              e.height === selected.height,
                          )
                          .map((e) => ({
                            value: e.editorId,
                            label: `Phòng ${floor.rooms.find((room) => room.roomId === e.roomId)?.roomNumber ?? ''}`,
                          }))}
                        onChange={(id) => {
                          const other = layout.elements.find((e) => e.editorId === id);
                          if (!other) return;
                          const result = proposeGeometry(layout, selected.editorId, {
                            x: other.x,
                            y: other.y,
                          });
                          if (result.layout) {
                            commit(result.layout);
                            setNotice(undefined);
                          } else
                            setNotice({
                              type: 'error',
                              message: result.issue ?? 'Không thể đổi chỗ.',
                            });
                        }}
                      />
                    </>
                  ) : (
                    <label>
                      Tên hiển thị
                      <Input
                        aria-label="Tên khu vực đang chọn"
                        value={selected.label}
                        maxLength={80}
                        onChange={(event) => edit(selected.editorId, { label: event.target.value })}
                      />
                    </label>
                  )}
                  <Typography.Paragraph type="secondary" className="floor-plan-keyboard-help">
                    Dùng phím mũi tên để dịch ô. Giữ Shift để đổi kích thước khu vực chung
                    {allowRoomResize ? ' hoặc phòng.' : '.'}
                  </Typography.Paragraph>
                  <Button danger onClick={() => remove(selected.editorId)}>
                    Gỡ ô đã chọn
                  </Button>
                </>
              )}
            </div>
          </aside>
        </div>
        <Collapse
          items={[
            {
              key: 'advanced',
              label: 'Nâng cao: kích thước mặt bằng và bảng tọa độ',
              children: (
                <>
                  <Typography.Paragraph type="secondary">
                    Rộng/Cao là số ô trên sơ đồ, không phải mét thực tế. Kích thước theo mẫu đã
                    chọn: theo diện tích hoặc dàn đầy mặt bằng. Bật chỉnh thủ công khi cần khớp hình
                    dạng mặt bằng; thao tác này không thay diện tích trong danh mục hạng.
                  </Typography.Paragraph>
                  {floor.rooms.length > 0 && (
                    <Space wrap style={{ marginBottom: 16 }}>
                      <Switch
                        aria-label="Cho phép chỉnh kích thước phòng thủ công"
                        checked={allowRoomResize}
                        onChange={setAllowRoomResize}
                      />
                      <span>Chỉnh kích thước phòng thủ công</span>
                      {allowRoomResize && (
                        <Button onClick={applyTemplate}>Đặt lại theo bố cục mẫu</Button>
                      )}
                    </Space>
                  )}
                  <Space wrap>
                    <label>
                      Số cột
                      <InputNumber
                        aria-label="Số cột"
                        value={layout.columns}
                        min={8}
                        max={80}
                        precision={0}
                        onChange={(value) => {
                          if (value !== null) commit({ ...layout, columns: value });
                        }}
                      />
                    </label>
                    <label>
                      Số hàng
                      <InputNumber
                        aria-label="Số hàng"
                        value={layout.rows}
                        min={8}
                        max={80}
                        precision={0}
                        onChange={(value) => {
                          if (value !== null) commit({ ...layout, rows: value });
                        }}
                      />
                    </label>
                  </Space>
                  <Table<EditorElement>
                    rowKey="editorId"
                    pagination={false}
                    dataSource={layout.elements}
                    scroll={{ x: 1050 }}
                    size="small"
                    columns={[
                      {
                        title: 'Loại',
                        width: 145,
                        render: (_, e) => (
                          <Select
                            aria-label={`Loại phần tử ${layout.elements.indexOf(e) + 1}`}
                            value={e.kind}
                            options={kinds}
                            style={{ width: '100%' }}
                            onChange={(kind) => edit(e.editorId, { kind, roomId: null, label: '' })}
                          />
                        ),
                      },
                      {
                        title: 'Phòng / Nhãn',
                        width: 225,
                        render: (_, e) =>
                          e.kind === 'ROOM' ? (
                            <Select
                              aria-label={`Phòng của ô ${layout.elements.indexOf(e) + 1}`}
                              value={e.roomId}
                              style={{ width: '100%' }}
                              options={floor.rooms
                                .filter(
                                  (room) =>
                                    room.roomId === e.roomId ||
                                    !layout.elements.some((other) => other.roomId === room.roomId),
                                )
                                .map((room) => ({
                                  value: room.roomId,
                                  label: `${room.roomNumber} · ${room.roomTypeName}`,
                                }))}
                              onChange={(roomId) => edit(e.editorId, { roomId })}
                            />
                          ) : (
                            <Input
                              aria-label={`Nhãn phần tử ${layout.elements.indexOf(e) + 1}`}
                              value={e.label}
                              maxLength={80}
                              onChange={(event) => edit(e.editorId, { label: event.target.value })}
                            />
                          ),
                      },
                      coordinate('x', 'X'),
                      coordinate('y', 'Y'),
                      coordinate('width', 'Rộng'),
                      coordinate('height', 'Cao'),
                      {
                        title: '',
                        width: 75,
                        render: (_, e) => (
                          <Button danger onClick={() => remove(e.editorId)}>
                            Gỡ
                          </Button>
                        ),
                      },
                    ]}
                  />
                </>
              ),
            },
          ]}
        />
        <div className="floor-plan-save-bar">
          <Space wrap>
            <Button
              type="primary"
              loading={save.isPending}
              disabled={!!issue || !name.trim()}
              onClick={() => {
                if (!issue && name.trim()) save.mutate();
              }}
            >
              Lưu và công bố
            </Button>
            <Button onClick={() => void onReload()}>Tải lại bản đã công bố</Button>
          </Space>
          <Typography.Text type="secondary">
            Phiên bản {floor.version} · Hoàn tác và kéo thả chưa thay bản đang công bố
          </Typography.Text>
        </div>
      </div>
    </ConfigProvider>
  );
}
