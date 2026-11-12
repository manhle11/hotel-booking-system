import { describe, expect, it } from 'vitest';
import type { MapRoom } from './api';
import { createLayoutTemplate, layoutIssue } from './floor-plan-layout';
import { MAP_CORE, mapCanvasStyle, roomAreaLabel, roomFootprint } from './room-map-scale';

describe('shared room-area scale', () => {
  it.each([
    [22, 10, 9],
    [30, 11, 11],
    [36, 12, 12],
    [38, 13, 12],
    [48, 14, 14],
    [60, 16, 15],
  ])('represents %i m² with a comparable nearly square footprint', (area, width, height) => {
    const shape = roomFootprint(area);
    expect(shape).toEqual({ width, height, known: true });
    expect(shape.width * shape.height).toBeGreaterThanOrEqual(area * 4);
    expect(shape.width * shape.height).toBeLessThan(area * 4 * 1.03);
  });
  it('uses identical frames and circulation areas for all 36 hotel rooms', () => {
    const profiles = [
      [2, 6, 22],
      [3, 10, 30],
      [4, 10, 36],
      [5, 4, 38],
      [6, 4, 48],
      [7, 2, 60],
    ];
    let count = 0;
    for (const [floor, number, areaM2] of profiles) {
      const rooms: MapRoom[] = Array.from({ length: number }, (_, i) => ({
        key: `${floor}-${i}`,
        roomId: `${floor}-${i}`,
        roomNumber: String(floor * 100 + i + 1),
        roomTypeId: `type-${floor}`,
        roomTypeName: 'Test room',
        areaM2,
      }));
      const layout = createLayoutTemplate(rooms, 'fill-floor');
      expect([layout.columns, layout.rows]).toEqual([80, 40]);
      expect(mapCanvasStyle(layout.columns, layout.rows)).toMatchObject({
        width: 1120,
        height: 560,
      });
      expect(layoutIssue(layout)).toBeUndefined();
      for (const core of MAP_CORE)
        expect(layout.elements.find((e) => e.kind === core.kind)).toMatchObject(core);
      for (const room of layout.elements.filter((e) => e.kind === 'ROOM'))
        expect(room.width * room.height).toBeGreaterThanOrEqual(areaM2 * 4);
      count += number;
    }
    expect(count).toBe(36);
  });
  it.each([null, undefined, 0, -1, Number.NaN])('does not invent an area for %s', (area) => {
    expect(roomFootprint(area).known).toBe(false);
    expect(roomAreaLabel(area)).toBe('Chưa có diện tích');
  });
  it('never makes either side smaller when room area increases', () => {
    let previous = roomFootprint(1);
    for (let area = 1; area <= 60; area += 0.25) {
      const next = roomFootprint(area);
      expect(next.width).toBeGreaterThanOrEqual(previous.width);
      expect(next.height).toBeGreaterThanOrEqual(previous.height);
      previous = next;
    }
    const deluxe = roomFootprint(36),
      triple = roomFootprint(38);
    expect(triple.width * triple.height).toBeGreaterThan(deluxe.width * deluxe.height);
    expect(triple.width).toBeGreaterThan(deluxe.width);
    expect(triple.height).toBeGreaterThanOrEqual(deluxe.height);
  });
});
