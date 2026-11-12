import { describe, expect, it } from 'vitest';
import type { MapRoom } from './api';
import {
  addLayoutElement,
  createLayoutTemplate,
  layoutIssue,
  proposeGeometry,
  type DraftLayout,
} from './floor-plan-layout';

const rooms = (count: number): MapRoom[] =>
  Array.from({ length: count }, (_, i) => ({
    key: `r-${i + 1}`,
    roomId: `r-${i + 1}`,
    roomNumber: String(201 + i),
    roomTypeId: 'standard',
    roomTypeName: 'Standard Queen',
    areaM2: 22,
  }));
const paired: DraftLayout = {
  columns: 24,
  rows: 12,
  elements: [
    { editorId: 'a', kind: 'ROOM', roomId: 'a', label: '', x: 1, y: 1, width: 6, height: 3 },
    { editorId: 'b', kind: 'ROOM', roomId: 'b', label: '', x: 9, y: 1, width: 6, height: 3 },
  ],
};

describe('floor-plan layout tools', () => {
  it.each([2, 4, 6, 8, 10])(
    'fills both banks evenly for %i rooms without altering areas or circulation',
    (count) => {
      const source = rooms(count).reverse();
      const before = JSON.stringify(source);
      const layout = createLayoutTemplate(source, 'fill-floor');
      expect(layoutIssue(layout)).toBeUndefined();
      expect([layout.columns, layout.rows]).toEqual([80, 40]);
      const tiles = layout.elements.filter((e) => e.kind === 'ROOM');
      const reference = createLayoutTemplate(
        rooms(10).map((room) => ({ ...room, areaM2: 36 })),
        'double-sided',
      ).elements.filter((element) => element.kind === 'ROOM');
      expect(tiles).toHaveLength(count);
      expect(
        Math.max(...tiles.map((e) => e.width)) - Math.min(...tiles.map((e) => e.width)),
      ).toBeLessThanOrEqual(1);
      for (const y of [4, 24]) {
        const bank = tiles.filter((e) => e.y === y);
        const referenceBank = reference.filter((element) => element.y === y);
        expect(bank).toHaveLength(count / 2);
        expect(bank[0].x).toBe(referenceBank[0].x);
        expect(bank.at(-1)!.x + bank.at(-1)!.width).toBe(
          referenceBank.at(-1)!.x + referenceBank.at(-1)!.width,
        );
        expect(bank.every((e) => e.height === referenceBank[0].height)).toBe(true);
        for (let i = 1; i < bank.length; i++)
          expect(bank[i].x - bank[i - 1].x - bank[i - 1].width).toBe(
            referenceBank[1].x - referenceBank[0].x - referenceBank[0].width,
          );
      }
      expect(tiles.map((e) => e.roomId)).toEqual(source.toReversed().map((r) => r.roomId));
      expect(layout.elements.find((e) => e.kind === 'CORRIDOR')).toMatchObject({
        x: 8,
        y: 18,
        width: 64,
        height: 4,
      });
      expect(JSON.stringify(source)).toBe(before);
    },
  );
  it.each([0, 1, 3, 5, 7, 12])(
    'refuses to fabricate rooms when the fill template receives %i rooms',
    (count) => {
      expect(() => createLayoutTemplate(rooms(count), 'fill-floor')).toThrow(
        'số phòng chẵn từ 2 đến 10',
      );
    },
  );
  it('builds the balanced floor-2 layout with equal room sizes and a central corridor', () => {
    const source = rooms(6).reverse();
    const layout = createLayoutTemplate(source, 'double-sided');
    expect([layout.columns, layout.rows]).toEqual([80, 40]);
    expect(
      layout.elements
        .filter((e) => e.kind === 'ROOM')
        .map((e) => [e.roomId, e.x, e.y, e.width, e.height]),
    ).toEqual([
      ['r-1', 23, 7, 10, 9],
      ['r-2', 35, 7, 10, 9],
      ['r-3', 47, 7, 10, 9],
      ['r-4', 23, 24, 10, 9],
      ['r-5', 35, 24, 10, 9],
      ['r-6', 47, 24, 10, 9],
    ]);
    expect(layout.elements.find((e) => e.kind === 'CORRIDOR')).toMatchObject({
      x: 8,
      y: 18,
      width: 64,
      height: 4,
    });
    expect(layoutIssue(layout)).toBeUndefined();
    expect(source[0].roomNumber).toBe('206');
  });

  it.each([1, 2, 3, 4, 6, 10])(
    'places all %i rooms once in both templates without overlap',
    (count) => {
      for (const template of ['double-sided', 'single-sided'] as const) {
        const layout = createLayoutTemplate(rooms(count), template);
        expect(layoutIssue(layout)).toBeUndefined();
        expect(
          new Set(layout.elements.filter((e) => e.kind === 'ROOM').map((e) => e.roomId)).size,
        ).toBe(count);
        expect(layout.elements.filter((e) => e.kind === 'STAIRS')).toHaveLength(1);
        expect(layout.elements.filter((e) => e.kind === 'ELEVATOR')).toHaveLength(1);
      }
    },
  );

  it.each([11, 20])('keeps the same room size for a dense floor with %i rooms', (count) => {
    const dense = createLayoutTemplate(rooms(count), 'double-sided');
    expect(dense.columns).toBe(80);
    expect(dense.rows).toBeLessThanOrEqual(80);
    expect(layoutIssue(dense)).toBeUndefined();
    expect(dense.elements.filter((e) => e.kind === 'ROOM')).toHaveLength(count);
    expect(
      dense.elements
        .filter((e) => e.kind === 'ROOM')
        .every((e) => e.width === 10 && e.height === 9),
    ).toBe(true);
  });
  it.each([35, 75, 121])('rejects %i rooms instead of shrinking the shared room scale', (count) => {
    expect(() => createLayoutTemplate(rooms(count), 'double-sided')).toThrow('vượt sức chứa');
  });

  it('adds a new area in free space and grows the drawing when needed', () => {
    const full: DraftLayout = {
      columns: 80,
      rows: 40,
      elements: [
        {
          editorId: 'full',
          kind: 'COMMON_AREA',
          roomId: null,
          label: '',
          x: 0,
          y: 0,
          width: 80,
          height: 40,
        },
      ],
    };
    const next = addLayoutElement(full, {
      editorId: 'new',
      kind: 'COMMON_AREA',
      roomId: null,
      label: 'Sảnh',
      width: 4,
      height: 2,
    });
    expect(next.rows).toBeGreaterThan(full.rows);
    expect(layoutIssue(next)).toBeUndefined();
    expect(full.elements).toHaveLength(1);
  });

  it('swaps equal-sized rooms atomically when dropping onto the other room', () => {
    const result = proposeGeometry(paired, 'a', { x: 9, y: 1 });
    expect(result.swappedId).toBe('b');
    expect(result.layout?.elements[0]).toMatchObject({ roomId: 'a', x: 9, y: 1 });
    expect(result.layout?.elements[1]).toMatchObject({ roomId: 'b', x: 1, y: 1 });
    expect(paired.elements[0].x).toBe(1);
  });

  it('rejects overlaps, invalid bounds and resizing into another room', () => {
    expect(proposeGeometry(paired, 'a', { width: 9 }, false).layout).toBeUndefined();
    expect(proposeGeometry(paired, 'a', { x: -1 }).issue).toContain('ngoài mặt bằng');
    const different = {
      ...paired,
      elements: paired.elements.map((e) => (e.editorId === 'b' ? { ...e, width: 5 } : e)),
    };
    expect(proposeGeometry(different, 'a', { x: 9, y: 1 }).layout).toBeUndefined();
  });
  it('builds a lobby without inventing physical rooms or disrupting the shared circulation core', () => {
    const lobby = createLayoutTemplate([], 'lobby');
    expect(layoutIssue(lobby)).toBeUndefined();
    expect([lobby.columns, lobby.rows]).toEqual([80, 40]);
    expect(lobby.elements.filter((element) => element.kind === 'ROOM')).toHaveLength(0);
    expect(lobby.elements.map((element) => element.label)).toEqual(
      expect.arrayContaining([
        'Quầy lễ tân',
        'Sảnh chờ',
        'Gửi hành lý',
        'WC chung',
        'Hỗ trợ khách',
        'Lối vào chính',
      ]),
    );
    expect(() => createLayoutTemplate(rooms(1), 'lobby')).toThrow('không có phòng lưu trú');
  });
});
