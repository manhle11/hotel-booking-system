import { roomStatuses, type RoomStatus } from './api';
import type { GeometryChange } from './floor-plan-layout';

export function RoomBedSymbol({ room }: { room: GeometryChange }) {
  const scale = Math.min(0.7, room.width * 0.06, room.height * 0.07);
  return (
    <g
      className="room-map-bed"
      transform={`translate(${room.x + room.width / 2} ${room.y + room.height * 0.2}) scale(${scale})`}
      aria-hidden="true"
      pointerEvents="none"
    >
      <path d="M -1.2 1 V -.5 Q -1.2 -.7 -1 -.7 H 1 Q 1.2 -.7 1.2 -.5 V 1 M -1.2 .5 H 1.2 M -1 -.7 V -1.25 H -.1 V -.7 M .1 -.7 V -1.25 H 1 V -.7" />
    </g>
  );
}

export function RoomStatusBadge({ room, status }: { room: GeometryChange; status: RoomStatus }) {
  const label = roomStatuses[status];
  const width = Math.min(room.width - 0.6, label.length * 0.43 + 1.5);
  const height = Math.min(1.25, room.height * 0.13);
  const fontSize = Math.min(0.73, height * 0.58, (width - 1.1) / Math.max(1, label.length * 0.58));
  const x = room.x + (room.width - width) / 2,
    y = room.y + room.height * 0.79;
  return (
    <g
      className={`room-map-status-badge room-status--${status}`}
      pointerEvents="none"
      aria-hidden="true"
    >
      <rect x={x} y={y} width={width} height={height} rx={height / 2} />
      <circle cx={x + 0.55} cy={y + height / 2} r={Math.min(0.14, height * 0.16)} />
      <text x={x + (width + 0.65) / 2} y={y + height / 2} style={{ fontSize }}>
        {label}
      </text>
    </g>
  );
}
