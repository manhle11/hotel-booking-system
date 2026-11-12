import { describe, expect, it } from 'vitest';
import { roomThemeClass } from './room-map-theme';
import type { MapRoom } from './api';

const room: MapRoom = {
  key: 'r',
  roomNumber: '201',
  roomTypeId: 'type',
  roomTypeName: 'Tên tùy chỉnh',
};
describe('stable room-category colors', () => {
  it.each(['STANDARD', 'SUPERIOR', 'DELUXE', 'TRIPLE', 'FAMILY', 'SUITE'])(
    'keeps %s independent from room name and status',
    (code) => {
      expect(roomThemeClass({ ...room, categoryCode: code, operationalStatus: 'AVAILABLE' })).toBe(
        `map-room--${code.toLowerCase()}`,
      );
      expect(
        roomThemeClass({
          ...room,
          categoryCode: code,
          roomNumber: '999',
          operationalStatus: 'OCCUPIED',
        }),
      ).toBe(roomThemeClass({ ...room, categoryCode: code }));
    },
  );
  it('uses structured type metadata and a neutral fallback for unrecognized categories', () => {
    expect(
      roomThemeClass({
        ...room,
        typeInfo: { category: { id: 'c', code: 'FAMILY', name: 'Tên nhóm mới' } },
      }),
    ).toBe('map-room--family');
    expect(roomThemeClass({ ...room, roomTypeName: 'Suite', categoryCode: 'CUSTOM' })).toBe(
      'map-room--neutral',
    );
    expect(roomThemeClass(undefined)).toBe('map-room--neutral');
  });
});
