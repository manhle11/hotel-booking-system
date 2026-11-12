import type { MapRoom, WriteElement } from './api';
import { MAP_COLUMNS, MAP_ROWS, MAP_CORE, roomFootprint } from './room-map-scale';

export interface EditorElement extends WriteElement {
  editorId: string;
}
export interface DraftLayout {
  columns: number;
  rows: number;
  elements: EditorElement[];
}
export type LayoutTemplate = 'double-sided' | 'single-sided' | 'fill-floor' | 'lobby';
export const canFillFloor = (count: number) => count >= 2 && count <= 10 && count % 2 === 0;
export const elementLabels = {
  ROOM: 'Phòng',
  CORRIDOR: 'Hành lang',
  ELEVATOR: 'Thang máy',
  STAIRS: 'Cầu thang',
  COMMON_AREA: 'Khu vực chung',
};

export function overlaps(a: GeometryChange, b: GeometryChange) {
  return a.x < b.x + b.width && a.x + a.width > b.x && a.y < b.y + b.height && a.y + a.height > b.y;
}

export function layoutIssue(layout: DraftLayout): string | undefined {
  if (
    !Number.isInteger(layout.columns) ||
    !Number.isInteger(layout.rows) ||
    layout.columns < 8 ||
    layout.columns > 80 ||
    layout.rows < 8 ||
    layout.rows > 80
  )
    return 'Kích thước mặt bằng phải từ 8 đến 80 ô mỗi chiều.';
  if (layout.elements.length > 400) return 'Một tầng chỉ có tối đa 400 ô.';
  const roomIds = new Set<string>();
  for (let i = 0; i < layout.elements.length; i++) {
    const e = layout.elements[i];
    if ((e.kind === 'ROOM') !== !!e.roomId) return 'Hãy chọn phòng cho mọi ô phòng.';
    if (e.roomId && roomIds.has(e.roomId)) return 'Một phòng chỉ được bố trí một lần.';
    if (e.roomId) roomIds.add(e.roomId);
    if (
      ![e.x, e.y, e.width, e.height].every(Number.isInteger) ||
      e.x < 0 ||
      e.y < 0 ||
      e.width < 1 ||
      e.height < 1 ||
      e.x + e.width > layout.columns ||
      e.y + e.height > layout.rows
    )
      return 'Có ô nằm ngoài mặt bằng hoặc có kích thước không hợp lệ.';
    if (e.label.length > 80) return 'Tên khu vực chỉ có tối đa 80 ký tự.';
    if (layout.elements.slice(0, i).some((other) => overlaps(e, other)))
      return 'Các ô đang chồng lên nhau. Hãy kéo sang vùng trống hoặc dùng Tự xếp đều.';
  }
}

export function createLayoutTemplate(rooms: MapRoom[], template: LayoutTemplate): DraftLayout {
  if (template === 'lobby') {
    if (rooms.length)
      throw new Error('Mẫu sảnh dành cho tầng không có phòng lưu trú. Hãy chọn mẫu bố trí phòng.');
    const areas = [
      { label: 'Gửi hành lý', x: 2, y: 4, width: 12, height: 10 },
      { label: 'Quầy lễ tân', x: 16, y: 4, width: 26, height: 10 },
      { label: 'Sảnh chờ', x: 44, y: 4, width: 34, height: 12 },
      { label: 'WC chung', x: 2, y: 26, width: 12, height: 8 },
      { label: 'Sảnh đón khách', x: 18, y: 24, width: 44, height: 7 },
      { label: 'Hỗ trợ khách', x: 64, y: 26, width: 14, height: 8 },
      { label: 'Lối vào chính', x: 32, y: 33, width: 16, height: 5 },
    ];
    return {
      columns: MAP_COLUMNS,
      rows: MAP_ROWS,
      elements: [
        ...areas.map((area) => ({
          ...area,
          editorId: crypto.randomUUID(),
          kind: 'COMMON_AREA' as const,
          roomId: null,
        })),
        ...MAP_CORE.map((core) => ({ ...core, editorId: crypto.randomUUID(), roomId: null })),
      ],
    };
  }
  const sorted = [...rooms].sort((a, b) =>
    a.roomNumber.localeCompare(b.roomNumber, 'vi', { numeric: true }),
  );
  if (sorted.some((room) => !room.roomId))
    throw new Error('Không thể bố trí vì thiếu thông tin phòng. Hãy tải lại.');
  if (template === 'fill-floor') {
    if (!canFillFloor(sorted.length))
      throw new Error(
        'Mẫu dàn đầy tầng dùng cho số phòng chẵn từ 2 đến 10. Hãy chọn mẫu phù hợp với danh mục phòng.',
      );
    const perSide = sorted.length / 2;
    // Match the outer room bounds and gutters of the ten-room floor (floor 4).
    const inset = 6;
    const gap = 2;
    const availableWidth = MAP_COLUMNS - inset * 2 - (perSide - 1) * gap;
    // Distribute whole grid units symmetrically; six-room floors need a one-unit
    // rounding difference to keep both outer margins and every gutter identical.
    const boundary = (index: number) => Math.round((index * availableWidth) / perSide);
    return {
      columns: MAP_COLUMNS,
      rows: MAP_ROWS,
      elements: [
        ...sorted.map((room, index) => ({
          editorId: crypto.randomUUID(),
          kind: 'ROOM' as const,
          roomId: room.roomId!,
          label: '',
          x: inset + boundary(index % perSide) + (index % perSide) * gap,
          y: index < perSide ? 4 : 24,
          width: boundary((index % perSide) + 1) - boundary(index % perSide),
          height: 12,
        })),
        ...MAP_CORE.map((core) => ({ ...core, editorId: crypto.randomUUID(), roomId: null })),
      ],
    };
  }
  const entries = sorted.map((room) => ({ room, ...roomFootprint(room.areaM2) }));
  if (entries.some((entry) => entry.height > 15))
    throw new Error('Phòng quá lớn cho mẫu tự xếp. Hãy bố trí thủ công trong mục Nâng cao.');
  type Entry = (typeof entries)[number];
  const rowWidth = (row: Entry[]) =>
    row.reduce((sum, e) => sum + e.width, 0) + Math.max(0, row.length - 1) * 2;
  let groups: Entry[][] = [];
  const middle = Math.ceil(entries.length / 2);
  if (
    template === 'double-sided' &&
    rowWidth(entries.slice(0, middle)) <= 76 &&
    rowWidth(entries.slice(middle)) <= 76
  ) {
    groups = [entries.slice(0, middle), entries.slice(middle)];
  } else {
    for (const entry of entries) {
      const last = groups.at(-1);
      if (!last || rowWidth([...last, entry]) > 76) groups.push([entry]);
      else last.push(entry);
    }
  }
  const groupsPerBlock = template === 'double-sided' ? 2 : 1;
  const blocks = Math.max(1, Math.ceil(groups.length / groupsPerBlock));
  const rows = MAP_ROWS * blocks;
  if (rows > 80)
    throw new Error(
      'Số phòng vượt sức chứa của mặt bằng ở tỷ lệ diện tích hiện tại. Hãy kiểm tra lại bố cục tầng.',
    );
  const elements: EditorElement[] = [];
  groups.forEach((group, index) => {
    let x = Math.round((MAP_COLUMNS - rowWidth(group)) / 2);
    const block = Math.floor(index / groupsPerBlock);
    const bottom = template === 'double-sided' && index % 2 === 1;
    for (const entry of group) {
      elements.push({
        editorId: crypto.randomUUID(),
        kind: 'ROOM',
        roomId: entry.room.roomId!,
        label: '',
        x,
        y: block * MAP_ROWS + (bottom ? 24 : 16 - entry.height),
        width: entry.width,
        height: entry.height,
      });
      x += entry.width + 2;
    }
  });
  if (entries.length) {
    MAP_CORE.forEach((core) =>
      elements.push({ ...core, editorId: crypto.randomUUID(), roomId: null }),
    );
    for (let block = 1; block < blocks; block++)
      elements.push({
        editorId: crypto.randomUUID(),
        kind: 'CORRIDOR',
        roomId: null,
        label: 'Hành lang',
        x: 2,
        y: 18 + block * MAP_ROWS,
        width: 76,
        height: 4,
      });
  }
  return { columns: MAP_COLUMNS, rows, elements };
}

export function findFreePosition(layout: DraftLayout, width: number, height: number) {
  for (let y = 0; y <= layout.rows - height; y++)
    for (let x = 0; x <= layout.columns - width; x++) {
      if (!layout.elements.some((e) => overlaps({ x, y, width, height }, e))) return { x, y };
    }
}

export function addLayoutElement(
  layout: DraftLayout,
  element: Omit<EditorElement, 'x' | 'y'>,
): DraftLayout {
  let position = findFreePosition(layout, element.width, element.height);
  let rows = layout.rows;
  if (!position) {
    rows = layout.rows + element.height + 2;
    if (rows > 80 || element.width > layout.columns)
      throw new Error('Mặt bằng đã kín. Hãy thu nhỏ ô hoặc tăng kích thước trong mục Nâng cao.');
    position = { x: 0, y: layout.rows + 1 };
  }
  return { ...layout, rows, elements: [...layout.elements, { ...element, ...position }] };
}

export type GeometryChange = Pick<WriteElement, 'x' | 'y' | 'width' | 'height'>;
export function proposeGeometry(
  layout: DraftLayout,
  id: string,
  patch: Partial<GeometryChange>,
  allowSwap = true,
): { layout?: DraftLayout; issue?: string; swappedId?: string } {
  const original = layout.elements.find((e) => e.editorId === id);
  if (!original) return { issue: 'Ô này không còn trong bố cục.' };
  const changed = { ...original, ...patch };
  const colliding = layout.elements.filter((e) => e.editorId !== id && overlaps(changed, e));
  const other = colliding[0];
  const centerX = changed.x + changed.width / 2,
    centerY = changed.y + changed.height / 2;
  const canSwap =
    allowSwap &&
    original.kind === 'ROOM' &&
    colliding.length === 1 &&
    other.kind === 'ROOM' &&
    original.width === changed.width &&
    original.height === changed.height &&
    other.width === original.width &&
    other.height === original.height &&
    centerX >= other.x &&
    centerX <= other.x + other.width &&
    centerY >= other.y &&
    centerY <= other.y + other.height;
  const elements = layout.elements.map((e) =>
    e.editorId === id
      ? canSwap
        ? { ...original, x: other.x, y: other.y }
        : changed
      : canSwap && e.editorId === other.editorId
        ? { ...e, x: original.x, y: original.y }
        : e,
  );
  const next = { ...layout, elements };
  const issue = layoutIssue(next);
  return issue ? { issue } : { layout: next, swappedId: canSwap ? other.editorId : undefined };
}
