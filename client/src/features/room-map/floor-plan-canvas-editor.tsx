import { useId, useRef, useState, type PointerEvent as ReactPointerEvent } from 'react';
import type { MapRoom } from './api';
import './room-map.css';
import { knownRoomArea, mapCanvasStyle } from './room-map-scale';
import { LobbyAreaArtwork } from './lobby-area-artwork';
import { lobbyAreaKind, lobbyZoneClass } from './lobby-area-theme';
import { roomThemeClass } from './room-map-theme';
import { RoomBedSymbol } from './room-map-symbols';
import {
  CommonAreaSymbol,
  FloorMapBackdrop,
  FloorMapStructureLegend,
  RoomStructure,
} from './floor-map-structure';
import {
  elementLabels,
  proposeGeometry,
  type DraftLayout,
  type EditorElement,
  type GeometryChange,
} from './floor-plan-layout';

interface Props {
  layout: DraftLayout;
  rooms: MapRoom[];
  selectedId?: string;
  onSelect: (id?: string) => void;
  onChange: (layout: DraftLayout) => void;
  onNotice: (message: string) => void;
  disabled?: boolean;
  showGrid: boolean;
  allowRoomResize?: boolean;
  isLobby?: boolean;
}
interface Drag {
  id: string;
  pointerId: number;
  mode: 'move' | 'resize';
  startX: number;
  startY: number;
  original: EditorElement;
}

export function FloorPlanCanvasEditor({
  layout,
  rooms,
  selectedId,
  onSelect,
  onChange,
  onNotice,
  disabled,
  showGrid,
  allowRoomResize = false,
  isLobby = false,
}: Props) {
  const svg = useRef<SVGSVGElement>(null);
  const dragging = useRef<Drag | null>(null);
  const panning = useRef<{
    pointerId: number;
    x: number;
    y: number;
    left: number;
    top: number;
  } | null>(null);
  const [preview, setPreview] = useState<{
    id: string;
    geometry: GeometryChange;
    mode: 'move' | 'resize';
  }>();
  const gridId = `floor-grid-${useId()}`;
  const byId = new Map(rooms.map((room) => [room.roomId, room]));
  const label = (element: EditorElement) =>
    element.kind === 'ROOM'
      ? `Phòng ${byId.get(element.roomId ?? '')?.roomNumber ?? '?'}`
      : element.label || elementLabels[element.kind];
  const point = (event: ReactPointerEvent) => {
    const bounds = svg.current?.getBoundingClientRect();
    if (!bounds?.width || !bounds.height) return null;
    return {
      x: ((event.clientX - bounds.left) * layout.columns) / bounds.width,
      y: ((event.clientY - bounds.top) * layout.rows) / bounds.height,
    };
  };
  const begin = (event: ReactPointerEvent, element: EditorElement, mode: 'move' | 'resize') => {
    if (disabled || event.button !== 0) return;
    if (
      mode === 'resize' &&
      !allowRoomResize &&
      element.kind === 'ROOM' &&
      knownRoomArea(byId.get(element.roomId ?? '')?.areaM2)
    )
      return;
    const start = point(event);
    if (!start) return;
    event.preventDefault();
    event.stopPropagation();
    onSelect(element.editorId);
    onNotice('');
    dragging.current = {
      id: element.editorId,
      pointerId: event.pointerId,
      mode,
      startX: start.x,
      startY: start.y,
      original: element,
    };
    panning.current = null;
    svg.current?.setPointerCapture(event.pointerId);
  };
  const beginPan = (event: ReactPointerEvent) => {
    if (event.button !== 0 || !svg.current?.parentElement) return;
    event.preventDefault();
    event.stopPropagation();
    onSelect(undefined);
    dragging.current = null;
    setPreview(undefined);
    panning.current = {
      pointerId: event.pointerId,
      x: event.clientX,
      y: event.clientY,
      left: svg.current.parentElement.scrollLeft,
      top: svg.current.parentElement.scrollTop,
    };
    svg.current.setPointerCapture(event.pointerId);
  };
  const geometry = (event: ReactPointerEvent, drag: Drag): GeometryChange | undefined => {
    const current = point(event);
    if (!current) return;
    const dx = Math.round(current.x - drag.startX),
      dy = Math.round(current.y - drag.startY);
    const original = drag.original;
    return drag.mode === 'move'
      ? {
          x: Math.max(0, Math.min(layout.columns - original.width, original.x + dx)),
          y: Math.max(0, Math.min(layout.rows - original.height, original.y + dy)),
          width: original.width,
          height: original.height,
        }
      : {
          x: original.x,
          y: original.y,
          width: Math.max(1, Math.min(layout.columns - original.x, original.width + dx)),
          height: Math.max(1, Math.min(layout.rows - original.y, original.height + dy)),
        };
  };
  const cancel = () => {
    dragging.current = null;
    panning.current = null;
    setPreview(undefined);
  };
  const move = (event: ReactPointerEvent) => {
    const pan = panning.current;
    const container = svg.current?.parentElement;
    if (pan && pan.pointerId === event.pointerId && container) {
      container.scrollLeft = pan.left - (event.clientX - pan.x);
      container.scrollTop = pan.top - (event.clientY - pan.y);
      return;
    }
    const drag = dragging.current;
    if (!drag || drag.pointerId !== event.pointerId) return;
    const next = geometry(event, drag);
    if (next) setPreview({ id: drag.id, geometry: next, mode: drag.mode });
  };
  const finish = (event: ReactPointerEvent) => {
    if (panning.current?.pointerId === event.pointerId) {
      panning.current = null;
      if (svg.current?.hasPointerCapture(event.pointerId))
        svg.current.releasePointerCapture(event.pointerId);
      return;
    }
    const drag = dragging.current;
    if (!drag || drag.pointerId !== event.pointerId) return;
    const next = geometry(event, drag);
    cancel();
    if (svg.current?.hasPointerCapture(event.pointerId))
      svg.current.releasePointerCapture(event.pointerId);
    if (!next || disabled) return;
    const result = proposeGeometry(layout, drag.id, next, drag.mode === 'move');
    if (result.layout) onChange(result.layout);
    else onNotice(result.issue ?? 'Không thể đặt ô ở đây.');
  };
  const proposed = preview
    ? proposeGeometry(layout, preview.id, preview.geometry, preview.mode === 'move')
    : undefined;
  const visible =
    proposed?.layout?.elements ??
    layout.elements.map((e) => (e.editorId === preview?.id ? { ...e, ...preview.geometry } : e));
  return (
    <div className="floor-plan-drag-wrap">
      <div className="floor-plan-drag-canvas room-map-canvas">
        <svg
          ref={svg}
          viewBox={`0 0 ${layout.columns} ${layout.rows}`}
          style={mapCanvasStyle(layout.columns, layout.rows)}
          role="group"
          aria-label="Chỉnh sửa sơ đồ bằng kéo thả"
          onPointerMove={move}
          onPointerUp={finish}
          onPointerCancel={cancel}
          onLostPointerCapture={cancel}
          onPointerDown={(event) => {
            if (event.target === event.currentTarget) beginPan(event);
          }}
        >
          <defs>
            <pattern id={`${gridId}-minor`} width="1" height="1" patternUnits="userSpaceOnUse">
              <path d="M 1 0 H 0 V 1" fill="none" stroke="#b4c4d6" strokeWidth="0.04" />
            </pattern>
            <pattern id={gridId} width="5" height="5" patternUnits="userSpaceOnUse">
              <rect width="5" height="5" fill={`url(#${gridId}-minor)`} />
              <path d="M 5 0 H 0 V 5" fill="none" stroke="#8fa6bf" strokeWidth="0.07" />
            </pattern>
          </defs>
          <rect
            width={layout.columns}
            height={layout.rows}
            className="room-map-canvas-base"
            onPointerDown={beginPan}
          />
          <FloorMapBackdrop columns={layout.columns} rows={layout.rows} lobby={isLobby} />
          {showGrid && (
            <rect
              data-testid="floor-plan-grid"
              width={layout.columns}
              height={layout.rows}
              fill={`url(#${gridId})`}
              pointerEvents="none"
            />
          )}
          {visible.map((e) => {
            const room = byId.get(e.roomId ?? '');
            const selected = e.editorId === selectedId;
            const invalid = preview?.id === e.editorId && !!proposed?.issue;
            const title = label(e);
            const isRoom = e.kind === 'ROOM';
            const illustrated = isLobby && !!lobbyAreaKind(e);
            const areaSized = isRoom && knownRoomArea(room?.areaM2) && !allowRoomResize;
            return (
              <g
                key={e.editorId}
                role="button"
                tabIndex={disabled ? -1 : 0}
                aria-label={`Chỉnh ${title}`}
                aria-pressed={selected}
                aria-disabled={disabled}
                className={`floor-plan-edit-node ${isRoom ? `map-room ${roomThemeClass(room)}` : 'floor-plan-edit-common'} ${selected ? 'floor-plan-edit-node--selected' : ''} ${invalid ? 'floor-plan-edit-node--invalid' : ''}`}
                onClick={() => {
                  if (!disabled) onSelect(e.editorId);
                }}
                onPointerDown={(event) => begin(event, e, 'move')}
                onKeyDown={(event) => {
                  if (disabled) return;
                  if (event.key === 'Escape') {
                    cancel();
                    return;
                  }
                  if (event.key === 'Enter' || event.key === ' ') {
                    event.preventDefault();
                    onSelect(e.editorId);
                    return;
                  }
                  const delta: Record<string, [number, number]> = {
                    ArrowLeft: [-1, 0],
                    ArrowRight: [1, 0],
                    ArrowUp: [0, -1],
                    ArrowDown: [0, 1],
                  };
                  const direction = delta[event.key];
                  if (!direction) return;
                  event.preventDefault();
                  onSelect(e.editorId);
                  if (event.shiftKey && areaSized) {
                    onNotice(
                      'Kích thước ô phòng được giữ theo bố cục. Mở Nâng cao để chỉnh thủ công.',
                    );
                    return;
                  }
                  const result = proposeGeometry(
                    layout,
                    e.editorId,
                    event.shiftKey
                      ? { width: e.width + direction[0], height: e.height + direction[1] }
                      : { x: e.x + direction[0], y: e.y + direction[1] },
                    !event.shiftKey,
                  );
                  if (result.layout) {
                    onChange(result.layout);
                    onNotice('');
                  } else onNotice(result.issue ?? 'Không thể di chuyển ô.');
                }}
              >
                <title>
                  {title}. Kéo hoặc dùng mũi tên để di chuyển.
                  {areaSized
                    ? ' Kích thước được giữ theo bố cục.'
                    : ' Kéo góc dưới phải để đổi kích thước.'}
                </title>
                <rect
                  x={e.x + (isRoom ? 0 : 0.05)}
                  y={e.y + (isRoom ? 0 : 0.05)}
                  width={Math.max(0.2, e.width - (isRoom ? 0 : 0.1))}
                  height={Math.max(0.2, e.height - (isRoom ? 0 : 0.1))}
                  rx={isRoom ? 0.65 : isLobby ? 0.6 : 0.45}
                  className={isLobby && !isRoom ? lobbyZoneClass(e) : undefined}
                />
                {isRoom ? (
                  <RoomStructure room={e} />
                ) : illustrated ? (
                  <LobbyAreaArtwork element={e} />
                ) : (
                  <CommonAreaSymbol element={e} />
                )}
                {isRoom && <RoomBedSymbol room={e} />}
                <text
                  x={e.x + e.width / 2}
                  y={e.y + e.height * (isRoom ? 0.43 : illustrated ? 0.82 : 0.66)}
                  className={`floor-plan-edit-label ${isLobby && !isRoom ? 'lobby-map-label' : ''}`}
                  style={{
                    fontSize: Math.min(
                      isRoom ? 1.65 : 1,
                      e.height * 0.23,
                      (e.width - 0.3) / (Math.max(1, title.length) * 0.55),
                    ),
                  }}
                >
                  {isRoom ? (room?.roomNumber ?? '?') : title}
                </text>
                {isRoom && (
                  <text
                    x={e.x + e.width / 2}
                    y={e.y + e.height * 0.63}
                    className="floor-plan-edit-caption"
                    style={{
                      fontSize: Math.min(
                        0.85,
                        e.height * 0.16,
                        (e.width - 0.3) / Math.max(1, (room?.roomTypeName.length ?? 0) * 0.6),
                      ),
                    }}
                  >
                    {room?.roomTypeName}
                  </text>
                )}
                {selected && !disabled && !areaSized && (
                  <rect
                    className="floor-plan-resize-handle"
                    x={e.x + e.width - 0.95}
                    y={e.y + e.height - 0.95}
                    width=".8"
                    height=".8"
                    rx=".07"
                    onPointerDown={(event) => begin(event, e, 'resize')}
                  >
                    <title>Kéo góc này để đổi kích thước</title>
                  </rect>
                )}
              </g>
            );
          })}
        </svg>
      </div>
      <FloorMapStructureLegend />
      <div className="floor-plan-drag-help" aria-live="polite">
        {proposed?.swappedId
          ? 'Thả chuột để đổi chỗ hai phòng.'
          : proposed?.issue
            ? 'Vị trí đang chồng ô. Thả chuột sẽ giữ vị trí cũ.'
            : `Kéo để di chuyển · Kéo vùng nền để cuộn · ${allowRoomResize ? 'Kéo góc ô để đổi kích thước.' : 'Mở Nâng cao để cho phép chỉnh kích thước phòng.'}`}
      </div>
    </div>
  );
}
