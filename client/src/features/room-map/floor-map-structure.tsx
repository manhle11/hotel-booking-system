import type { ElementKind, LayoutElement } from './api';
import type { GeometryChange } from './floor-plan-layout';

type PlanShape = Pick<LayoutElement, 'kind' | 'x' | 'y' | 'width' | 'height'>;

interface BackdropProps {
  columns: number;
  rows: number;
  lobby?: boolean;
}
export function FloorMapBackdrop({ columns, rows, lobby = false }: BackdropProps) {
  return (
    <g
      className={`floor-map-backdrop ${lobby ? 'floor-map-backdrop--lobby' : ''}`}
      aria-hidden="true"
      pointerEvents="none"
    >
      <rect
        x=".5"
        y=".5"
        width={Math.max(0, columns - 1)}
        height={Math.max(0, rows - 1)}
        rx=".35"
        className="floor-map-building-surface"
      />
      <rect
        x=".5"
        y=".5"
        width={Math.max(0, columns - 1)}
        height={Math.max(0, rows - 1)}
        rx=".35"
        className="floor-map-building-wall"
      />
    </g>
  );
}

export function RoomStructure({ room }: { room: GeometryChange }) {
  const r = Math.min(0.65, room.width / 8, room.height / 8);
  return (
    <g className="floor-map-room-structure" aria-hidden="true" pointerEvents="none">
      <path
        className="floor-map-room-wall"
        d={`M ${room.x + r} ${room.y} h ${room.width - r * 2} q ${r} 0 ${r} ${r} v ${room.height - r * 2} q 0 ${r} ${-r} ${r} h ${-room.width + r * 2} q ${-r} 0 ${-r} ${-r} v ${-room.height + r * 2} q 0 ${-r} ${r} ${-r} Z`}
      />
    </g>
  );
}

export function CommonAreaSymbol({ element }: { element: PlanShape }) {
  const paths: Partial<Record<ElementKind, string>> = {
    STAIRS: 'M -.9 .8 H -.45 V .35 H 0 V -.1 H .45 V -.55 H .9 V -1',
    ELEVATOR:
      'M -.8 -1 H .8 V 1 H -.8 Z M -.4 .2 V -.6 M -.65 -.3 L -.4 -.6 -.15 -.3 M .4 -.2 V .6 M .15 .3 L .4 .6 .65 .3',
    CORRIDOR: 'M -1 0 H 1 M .6 -.4 L 1 0 .6 .4 M -.6 -.4 L -1 0 -.6 .4',
    COMMON_AREA: 'M 0 -.9 A .9 .9 0 1 0 0 .9 A .9 .9 0 1 0 0 -.9 M 0 -.5 V -.45 M 0 -.1 V .5',
  };
  const scale = Math.min(0.75, element.height * 0.15, element.width * 0.13);
  return (
    <g
      className="floor-map-common-symbol"
      aria-hidden="true"
      pointerEvents="none"
      transform={`translate(${element.x + element.width / 2} ${element.y + element.height * 0.3}) scale(${scale})`}
    >
      <path
        d={paths[element.kind]}
        transform={
          element.kind === 'CORRIDOR' && element.height > element.width ? 'rotate(90)' : undefined
        }
      />
    </g>
  );
}

export function FloorMapStructureLegend() {
  return (
    <div className="floor-map-structure-legend" aria-label="Chú thích mặt bằng">
      <span>
        <i className="floor-map-legend-wall" />
        Đường bao minh họa
      </span>
    </div>
  );
}
