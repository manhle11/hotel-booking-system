import type { LayoutElement } from './api';

export type LobbyElement = Pick<LayoutElement, 'kind' | 'label' | 'x' | 'y' | 'width' | 'height'>;
const areaNames = {
  'quay le tan': 'reception',
  'sanh cho': 'lounge',
  'gui hanh ly': 'luggage',
  'wc chung': 'restroom',
  'ho tro khach': 'concierge',
  'sanh don khach': 'welcome',
  'loi vao chinh': 'entrance',
} as const;
export type LobbyAreaKind = (typeof areaNames)[keyof typeof areaNames];

export function lobbyAreaKind(
  element: Pick<LobbyElement, 'kind' | 'label'>,
): LobbyAreaKind | undefined {
  if (element.kind !== 'COMMON_AREA') return;
  const name = (element.label ?? '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .replaceAll('đ', 'd')
    .trim();
  return areaNames[name as keyof typeof areaNames];
}

export function lobbyZoneClass(element: Pick<LobbyElement, 'kind' | 'label'>) {
  return `lobby-map-zone lobby-map-zone--${lobbyAreaKind(element) ?? element.kind.toLowerCase()}`;
}
