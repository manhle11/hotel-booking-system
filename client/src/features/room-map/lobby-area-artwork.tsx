import { lobbyAreaKind, type LobbyElement, type LobbyAreaKind } from './lobby-area-theme';

function Plant({ x, y }: { x: number; y: number }) {
  return (
    <g transform={`translate(${x} ${y})`}>
      <circle r="9" fill="#e6ddcb" stroke="#cabea4" />
      <ellipse cx="-3" cy="-2" rx="4" ry="8" transform="rotate(-35)" fill="#92ac8e" />
      <ellipse cx="3" cy="-2" rx="4" ry="8" transform="rotate(35)" fill="#6d927c" />
      <ellipse cy="1" rx="3" ry="6" fill="#b2c5a3" />
    </g>
  );
}

function Artwork({ area }: { area: LobbyAreaKind }) {
  switch (area) {
    case 'reception':
      return (
        <>
          <Plant x={10} y={35} />
          <Plant x={150} y={35} />
          <circle cx="57" cy="15" r="7" fill="#b7c8bf" />
          <circle cx="104" cy="15" r="7" fill="#b7c8bf" />
          <path d="M 25 27 Q 80 15 135 27 V 52 Q 80 65 25 52 Z" fill="#d4b488" stroke="#a58860" />
          <path d="M 27 34 Q 80 23 133 34" fill="none" stroke="#f5e9d4" strokeWidth="3" />
          <rect x="45" y="22" width="23" height="10" rx="2" fill="#486274" />
          <rect x="92" y="22" width="23" height="10" rx="2" fill="#486274" />
          <path d="M 73 44 H 87 M 76 48 H 84" stroke="#9f7d52" strokeWidth="2" />
          <circle cx="125" cy="40" r="3" fill="#fff6dd" stroke="#a58860" />
        </>
      );
    case 'lounge':
      return (
        <>
          <rect x="48" y="6" width="64" height="58" rx="12" fill="#e4d9c6" />
          <rect x="53" y="11" width="54" height="48" rx="9" fill="none" stroke="#f8f2e8" />
          {[18, 117].map((x) => (
            <g key={x}>
              <rect x={x} y="12" width="25" height="47" rx="6" fill="#91ac9d" stroke="#648575" />
              <rect x={x + 4} y="18" width="17" height="15" rx="3" fill="#c1d0c3" />
              <rect x={x + 4} y="37" width="17" height="15" rx="3" fill="#c1d0c3" />
            </g>
          ))}
          <circle cx="80" cy="35" r="15" fill="#fffaf0" stroke="#bfa98b" strokeWidth="1.5" />
          <rect
            x="73"
            y="31"
            width="10"
            height="7"
            rx="1"
            transform="rotate(-15 78 35)"
            fill="#d4b488"
          />
          <circle cx="85" cy="33" r="3" fill="#a4ba9a" />
          <Plant x={8} y={8} />
          <Plant x={152} y={62} />
        </>
      );
    case 'luggage':
      return (
        <>
          <path
            d="M 24 58 H 137 M 34 58 V 12 H 126 V 58"
            stroke="#b69a73"
            strokeWidth="3"
            fill="none"
          />
          {[
            { x: 43, y: 19, w: 30, h: 36, color: '#8ca99d' },
            { x: 84, y: 27, w: 26, h: 28, color: '#c5a477' },
          ].map((bag) => (
            <g key={bag.x}>
              <path
                d={`M ${bag.x + 8} ${bag.y} V ${bag.y - 6} H ${bag.x + bag.w - 8} V ${bag.y}`}
                fill="none"
                stroke="#6b7b80"
                strokeWidth="2"
              />
              <rect
                x={bag.x}
                y={bag.y}
                width={bag.w}
                height={bag.h}
                rx="5"
                fill={bag.color}
                stroke="#6c807c"
              />
              <path
                d={`M ${bag.x + 9} ${bag.y + 6} V ${bag.y + bag.h - 6} M ${bag.x + bag.w - 9} ${bag.y + 6} V ${bag.y + bag.h - 6}`}
                stroke="#ffffff"
                strokeOpacity=".5"
              />
              <circle cx={bag.x + 6} cy={bag.y + bag.h + 2} r="2" fill="#596f78" />
              <circle cx={bag.x + bag.w - 6} cy={bag.y + bag.h + 2} r="2" fill="#596f78" />
            </g>
          ))}
          <circle cx="34" cy="63" r="4" fill="#617985" />
          <circle cx="126" cy="63" r="4" fill="#617985" />
        </>
      );
    case 'restroom':
      return (
        <g
          fill="none"
          stroke="#758f9a"
          strokeWidth="3.5"
          strokeLinecap="round"
          strokeLinejoin="round"
        >
          <circle cx="55" cy="15" r="6" fill="#bed0d2" stroke="none" />
          <circle cx="105" cy="15" r="6" fill="#bed0d2" stroke="none" />
          <path d="M 43 40 V 29 Q 55 22 67 29 V 40 M 49 29 V 58 M 61 29 V 58 M 80 10 V 61 M 95 28 L 87 45 H 123 L 115 28 M 100 45 V 59 M 110 45 V 59" />
        </g>
      );
    case 'concierge':
      return (
        <>
          <rect x="29" y="44" width="102" height="17" rx="5" fill="#d8c4a3" stroke="#b69a73" />
          <path
            d="M 54 27 V 22 A 26 21 0 0 1 106 22 V 27"
            fill="none"
            stroke="#6f9287"
            strokeWidth="6"
          />
          <rect x="49" y="22" width="12" height="19" rx="5" fill="#91b0a1" />
          <rect x="99" y="22" width="12" height="19" rx="5" fill="#91b0a1" />
          <path d="M 106 38 Q 106 46 87 46" fill="none" stroke="#6f9287" strokeWidth="3" />
          <rect x="79" y="43" width="13" height="5" rx="2" fill="#6f9287" />
          <circle cx="41" cy="52" r="3" fill="#fcf6e8" />
        </>
      );
    case 'welcome':
      return (
        <>
          <Plant x={17} y={35} />
          <Plant x={143} y={35} />
          <circle cx="80" cy="35" r="26" fill="#eee3cc" stroke="#d5c29e" />
          <circle cx="80" cy="35" r="21" fill="none" stroke="#fdfaf2" strokeWidth="2" />
          <path
            d="M 80 17 L 84 31 L 98 35 L 84 39 L 80 53 L 76 39 L 62 35 L 76 31 Z"
            fill="#b99d71"
          />
          <circle cx="80" cy="35" r="4" fill="#fff9eb" />
        </>
      );
    case 'entrance':
      return (
        <>
          <rect x="27" y="9" width="106" height="50" rx="7" fill="#d7c6a7" />
          <path
            d="M 35 18 H 125 M 35 26 H 125 M 35 34 H 125 M 35 42 H 125 M 35 50 H 125"
            stroke="#b59c78"
            strokeOpacity=".4"
          />
          <path
            d="M 80 49 V 20 M 69 31 L 80 20 L 91 31"
            fill="none"
            stroke="#fffdf6"
            strokeWidth="4"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
          <Plant x={12} y={35} />
          <Plant x={148} y={35} />
        </>
      );
  }
}

export function LobbyAreaArtwork({ element }: { element: LobbyElement }) {
  const area = lobbyAreaKind(element);
  if (!area) return null;
  return (
    <svg
      className="lobby-map-artwork"
      data-area={area}
      x={element.x + element.width * 0.1}
      y={element.y + element.height * 0.1}
      width={element.width * 0.8}
      height={element.height * 0.57}
      viewBox="0 0 160 70"
      preserveAspectRatio="xMidYMid meet"
      aria-hidden="true"
      pointerEvents="none"
    >
      <Artwork area={area} />
    </svg>
  );
}
