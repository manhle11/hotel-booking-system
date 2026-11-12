import type { CSSProperties } from 'react';

export const MAP_COLUMNS = 80;
export const MAP_ROWS = 40;
export const MAP_UNIT_PX = 14;
export const ROOM_AREA_UNITS = 4;
export const MAP_CORE = [
  { kind: 'STAIRS' as const, label: 'Cầu thang', x: 2, y: 18, width: 6, height: 4 },
  { kind: 'CORRIDOR' as const, label: 'Hành lang', x: 8, y: 18, width: 64, height: 4 },
  { kind: 'ELEVATOR' as const, label: 'Thang máy', x: 72, y: 18, width: 6, height: 4 },
];

export function knownRoomArea(area: number | null | undefined): area is number {
  return typeof area === 'number' && Number.isFinite(area) && area > 0;
}

// Keep both sides monotone as area grows. Round up to the next nearly square
// integer footprint, instead of exact factors that can make a larger room flatter.
// Grid dimensions illustrate relative size; they are not architectural lengths.
export function roomFootprint(area: number | null | undefined) {
  if (!knownRoomArea(area)) return { width: 10, height: 9, known: false };
  const cells = Math.max(1, Math.ceil(area * ROOM_AREA_UNITS));
  const side = Math.floor(Math.sqrt(cells));
  const width = cells === side * side ? side : side + 1;
  const height = cells <= side * width ? side : side + 1;
  if (width > 76) throw new Error('Diện tích phòng vượt kích thước hỗ trợ của sơ đồ.');
  return { width, height, known: true };
}

export function roomAreaLabel(area: number | null | undefined) {
  return knownRoomArea(area)
    ? `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 2 }).format(area)} m²`
    : 'Chưa có diện tích';
}

export function mapCanvasStyle(columns: number, rows: number): CSSProperties {
  return {
    width: columns * MAP_UNIT_PX,
    height: rows * MAP_UNIT_PX,
    minWidth: columns * MAP_UNIT_PX,
    maxWidth: 'none',
  };
}
