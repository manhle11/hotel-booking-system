export type AddOnQuantityUnit = 'UNIT' | 'SERVING' | 'TRIP' | 'PACKAGE';
export const ADD_ON_UNIT_LABELS: Record<AddOnQuantityUnit, string> = {
  UNIT: 'đơn vị',
  SERVING: 'suất',
  TRIP: 'lượt',
  PACKAGE: 'gói',
};
export function addOnUnitLabel(unit?: AddOnQuantityUnit) {
  return ADD_ON_UNIT_LABELS[unit ?? 'UNIT'];
}
