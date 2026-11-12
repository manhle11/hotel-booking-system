import type { MapRoom } from './api';

export const roomCategoryThemes = {
  STANDARD: 'standard',
  SUPERIOR: 'superior',
  DELUXE: 'deluxe',
  TRIPLE: 'triple',
  FAMILY: 'family',
  SUITE: 'suite',
} as const;

export function roomThemeClass(room: MapRoom | undefined) {
  const code = room?.categoryCode ?? room?.typeInfo?.category?.code;
  const theme = code
    ? roomCategoryThemes[code.toUpperCase() as keyof typeof roomCategoryThemes]
    : undefined;
  return `map-room--${theme ?? 'neutral'}`;
}
