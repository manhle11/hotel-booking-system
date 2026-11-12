import { useState } from 'react';
import { Alert, Empty, Segmented, Typography } from 'antd';
import { roomStatuses, type FloorPlan, type MapRoom } from './api';
import './room-map.css';
import { knownRoomArea, mapCanvasStyle } from './room-map-scale';
import { LobbyAreaArtwork } from './lobby-area-artwork';
import { lobbyAreaKind, lobbyZoneClass } from './lobby-area-theme';
import { roomThemeClass } from './room-map-theme';
import { RoomBedSymbol, RoomStatusBadge } from './room-map-symbols';
import {
  CommonAreaSymbol,
  FloorMapBackdrop,
  FloorMapStructureLegend,
  RoomStructure,
} from './floor-map-structure';

interface Props {
  floor: FloorPlan;
  showStatus?: boolean;
  selectedKey?: string;
  onRoomClick?: (room: MapRoom) => void;
  reason?: (room: MapRoom) => string | undefined;
  visibleKeys?: Set<string>;
}
const kindLabels = {
  CORRIDOR: 'Hành lang',
  ELEVATOR: 'Thang máy',
  STAIRS: 'Cầu thang',
  COMMON_AREA: 'Khu vực chung',
};
const fittedSize = (text: string, width: number, height: number, size: number) =>
  Math.min(size, height * 0.25, (width - 0.25) / Math.max(1, text.length * 0.6));

export function HotelFloorMap({
  floor,
  showStatus = false,
  selectedKey,
  onRoomClick,
  reason,
  visibleKeys,
}: Props) {
  const [view, setView] = useState<string | number>('map');
  const hasRooms = floor.rooms.length > 0;
  const isLobby = floor.floorNumber === 1 && !hasRooms;
  const hasLayout = !!floor.publishedAt && floor.elements.length > 0;
  const byKey = new Map(floor.rooms.map((room) => [room.key, room]));
  const placed = new Set(floor.elements.filter((e) => e.kind === 'ROOM').map((e) => e.roomKey));
  const unplaced = floor.rooms.filter((room) => !placed.has(room.key));
  const types = [...new Set(floor.rooms.map((r) => r.roomTypeId))];
  const state = (room: MapRoom) => {
    const blocked =
      reason?.(room) ??
      (visibleKeys && !visibleKeys.has(room.key) ? 'Không thuộc bộ lọc' : undefined);
    const label = room.roomTypeName;
    const status =
      showStatus && room.operationalStatus ? roomStatuses[room.operationalStatus] : undefined;
    const className = `map-room ${roomThemeClass(room)} ${selectedKey === room.key ? 'map-room--selected' : ''} ${blocked ? 'map-room--blocked' : ''} ${knownRoomArea(room.areaM2) ? '' : 'map-room--unknown-area'}`;
    return {
      blocked,
      label,
      className,
      accessible: `Phòng ${room.roomNumber}, ${room.roomTypeName}${status ? `, ${status}` : ''}${blocked ? `, ${blocked}` : ''}`,
    };
  };
  const list = (rooms: MapRoom[]) => (
    <div className="room-map-list">
      {rooms
        .filter((room) => !visibleKeys || visibleKeys.has(room.key))
        .map((room) => {
          const s = state(room);
          return (
            <button
              key={room.key}
              type="button"
              className={s.className}
              aria-label={s.accessible}
              aria-pressed={selectedKey === room.key}
              disabled={!!s.blocked || !onRoomClick}
              onClick={() => onRoomClick?.(room)}
            >
              <strong>{room.roomNumber}</strong>
              <span>{room.roomTypeName}</span>
              {showStatus && room.operationalStatus && (
                <span className={`room-map-status-label room-status--${room.operationalStatus}`}>
                  <i />
                  {roomStatuses[room.operationalStatus]}
                </span>
              )}
              {s.blocked && <small>{s.blocked}</small>}
            </button>
          );
        })}
    </div>
  );
  return (
    <section
      className="hotel-floor-map"
      aria-label={`${floor.name}, ${hasRooms ? `${floor.rooms.length} phòng` : 'khu vực chung'}`}
    >
      <div className="room-map-toolbar">
        <Typography.Text strong>
          {floor.name}
          {hasRooms ? ` · ${floor.rooms.length} phòng` : ' · Khu vực chung'}
        </Typography.Text>
        <Segmented
          aria-label="Cách xem phòng"
          value={view}
          onChange={setView}
          options={[
            { value: 'map', label: 'Sơ đồ' },
            { value: 'list', label: hasRooms ? 'Danh sách' : 'Khu vực' },
          ]}
        />
      </div>
      {showStatus && hasRooms && (
        <div className="room-map-legend room-map-status-legend" aria-label="Trạng thái hiện tại">
          <strong>Trạng thái</strong>
          {Object.entries(roomStatuses).map(([status, label]) => (
            <span key={status}>
              <i className={`room-status-dot room-status--${status}`} />
              {label}
            </span>
          ))}
        </div>
      )}
      {hasRooms && (
        <div className="room-map-legend" aria-label="Màu hạng phòng">
          <strong>Hạng phòng</strong>
          {types.map((id) => (
            <span key={id}>
              <i className={roomThemeClass(floor.rooms.find((room) => room.roomTypeId === id))} />
              {floor.rooms.find((r) => r.roomTypeId === id)?.roomTypeName}
            </span>
          ))}
        </div>
      )}
      {!hasRooms && !hasLayout && <Empty description="Tầng này chưa có phòng hoặc khu vực chung" />}
      {view === 'list' ? (
        hasRooms ? (
          list(floor.rooms)
        ) : (
          <ul className="room-map-area-list">
            {floor.elements
              .filter((element) => element.kind !== 'ROOM')
              .map((element) => (
                <li key={element.id}>
                  {element.label || kindLabels[element.kind as keyof typeof kindLabels]}
                </li>
              ))}
          </ul>
        )
      ) : (
        <>
          {hasLayout ? (
            <div className="room-map-canvas">
              <svg
                viewBox={`0 0 ${floor.gridColumns} ${floor.gridRows}`}
                style={mapCanvasStyle(floor.gridColumns, floor.gridRows)}
                role="group"
                aria-label={`Mặt bằng ${floor.name}`}
              >
                <rect
                  width={floor.gridColumns}
                  height={floor.gridRows}
                  className="room-map-canvas-base"
                />
                <FloorMapBackdrop
                  columns={floor.gridColumns}
                  rows={floor.gridRows}
                  lobby={isLobby}
                />
                {floor.elements.map((element) => {
                  const illustrated = isLobby && !!lobbyAreaKind(element);
                  if (element.kind !== 'ROOM')
                    return (
                      <g key={element.id}>
                        <rect
                          x={element.x + 0.05}
                          y={element.y + 0.05}
                          width={element.width - 0.1}
                          height={element.height - 0.1}
                          rx={isLobby ? 0.6 : 0.45}
                          className={`room-map-common room-map-common--${element.kind} ${isLobby ? lobbyZoneClass(element) : ''}`}
                        />
                        {illustrated ? (
                          <LobbyAreaArtwork element={element} />
                        ) : (
                          <CommonAreaSymbol element={element} />
                        )}
                        <text
                          x={element.x + element.width / 2}
                          y={element.y + element.height * (illustrated ? 0.82 : 0.66)}
                          className={`room-map-common-label ${isLobby ? 'lobby-map-label' : ''}`}
                          style={{
                            fontSize: fittedSize(
                              element.label || kindLabels[element.kind],
                              element.width,
                              element.height,
                              1,
                            ),
                          }}
                        >
                          {element.label || kindLabels[element.kind]}
                        </text>
                      </g>
                    );
                  const room = byKey.get(element.roomKey ?? '');
                  if (!room) return null;
                  const s = state(room);
                  const click = () => {
                    if (!s.blocked) onRoomClick?.(room);
                  };
                  return (
                    <g
                      key={element.id}
                      role="button"
                      tabIndex={0}
                      aria-label={s.accessible}
                      aria-disabled={!!s.blocked || !onRoomClick}
                      aria-pressed={selectedKey === room.key}
                      className={s.className}
                      onClick={click}
                      onKeyDown={(event) => {
                        if (event.key === 'Enter' || event.key === ' ') {
                          event.preventDefault();
                          click();
                        }
                      }}
                    >
                      <title>{s.accessible}</title>
                      <rect
                        x={element.x}
                        y={element.y}
                        width={element.width}
                        height={element.height}
                        rx="0.65"
                      />
                      <RoomStructure room={element} />
                      <RoomBedSymbol room={element} />
                      <text
                        x={element.x + element.width / 2}
                        y={element.y + element.height * 0.43}
                        className="room-map-number"
                        style={{
                          fontSize: fittedSize(
                            room.roomNumber,
                            element.width,
                            element.height,
                            1.65,
                          ),
                        }}
                      >
                        {room.roomNumber}
                      </text>
                      <text
                        x={element.x + element.width / 2}
                        y={element.y + element.height * 0.63}
                        className="room-map-caption"
                        style={{
                          fontSize: fittedSize(s.label, element.width, element.height, 0.85),
                        }}
                      >
                        {s.label}
                      </text>
                      {showStatus && room.operationalStatus && (
                        <RoomStatusBadge room={element} status={room.operationalStatus} />
                      )}
                      {s.blocked && (
                        <text
                          x={element.x + element.width / 2}
                          y={element.y + element.height * 0.95}
                          className="room-map-reason"
                          style={{
                            fontSize: fittedSize(s.blocked, element.width, element.height, 0.65),
                          }}
                        >
                          {s.blocked}
                        </text>
                      )}
                    </g>
                  );
                })}
              </svg>
            </div>
          ) : (
            <Alert
              type="info"
              showIcon
              title="Chưa cấu hình sơ đồ tầng này"
              description="Bạn vẫn có thể xem và chọn phòng từ danh sách bên dưới."
            />
          )}
          {hasLayout && <FloorMapStructureLegend />}
          {hasLayout && (
            <Typography.Paragraph className="room-map-scale-note" type="secondary">
              {hasRooms
                ? 'Sơ đồ minh họa vị trí và cách bố trí phòng. Kích thước ô được điều chỉnh theo từng tầng; xem diện tích trong chi tiết hạng phòng.'
                : 'Sơ đồ minh họa khu vực đón tiếp và phục vụ khách tại tầng này.'}
            </Typography.Paragraph>
          )}
          {unplaced.length > 0 && (
            <div className="room-map-unplaced">
              <Typography.Title level={5}>
                Chưa bố trí trên sơ đồ ({unplaced.length})
              </Typography.Title>
              {list(unplaced)}
            </div>
          )}
        </>
      )}
    </section>
  );
}
