import { useEffect, useRef, useState, type PointerEvent as ReactPointerEvent } from 'react';

const BUTTON_SIZE = 46;
const EDGE_MARGIN = 12;
const DRAG_THRESHOLD_PX = 6;
const STORAGE_KEY = 'ks360_floating_refresh_pos_v1';

type DockSide = 'left' | 'right';

interface DockPosition {
  side: DockSide;
  yRatio: number;
}

interface PixelPosition {
  x: number;
  y: number;
}

function getBounds(viewportWidth = window.innerWidth, viewportHeight = window.innerHeight) {
  const minX = EDGE_MARGIN;
  const minY = EDGE_MARGIN;
  const maxX = Math.max(minX, viewportWidth - BUTTON_SIZE - EDGE_MARGIN);
  const maxY = Math.max(minY, viewportHeight - BUTTON_SIZE - EDGE_MARGIN);
  return { minX, maxX, minY, maxY, spanY: Math.max(1, maxY - minY) };
}

function clampPixel(x: number, y: number): PixelPosition {
  const { minX, maxX, minY, maxY } = getBounds();
  return {
    x: Math.min(Math.max(minX, x), maxX),
    y: Math.min(Math.max(minY, y), maxY),
  };
}

function dockToPixel(dock: DockPosition, viewportWidth: number, viewportHeight: number): PixelPosition {
  const { minX, maxX, minY, spanY } = getBounds(viewportWidth, viewportHeight);
  const clampedRatio = Math.min(Math.max(0, dock.yRatio), 1);
  return {
    x: dock.side === 'left' ? minX : maxX,
    y: minY + clampedRatio * spanY,
  };
}

function pixelToDock(x: number, y: number): DockPosition {
  const { minY, maxY, spanY } = getBounds();
  const clampedY = Math.min(Math.max(minY, y), maxY);
  const side: DockSide = x + BUTTON_SIZE / 2 < window.innerWidth / 2 ? 'left' : 'right';
  return {
    side,
    yRatio: Math.min(Math.max(0, (clampedY - minY) / spanY), 1),
  };
}

function loadInitialDock(): DockPosition {
  const fallback: DockPosition = { side: 'right', yRatio: 0.92 };
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return fallback;
    const parsed = JSON.parse(raw) as Partial<DockPosition & PixelPosition>;
    if ((parsed.side === 'left' || parsed.side === 'right') && typeof parsed.yRatio === 'number') {
      return {
        side: parsed.side,
        yRatio: Math.min(Math.max(0, parsed.yRatio), 1),
      };
    }
    if (typeof parsed.x === 'number' && typeof parsed.y === 'number') {
      return pixelToDock(parsed.x, parsed.y);
    }
  } catch {
    // Ignore storage read errors
  }
  return fallback;
}

function saveDock(dock: DockPosition) {
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(dock));
  } catch {
    // Ignore storage write errors
  }
}

interface Props {
  refreshing: boolean;
  onRefresh: () => void;
}

export function FloatingRefreshButton({ refreshing, onRefresh }: Props) {
  const [dock, setDock] = useState<DockPosition>(() => loadInitialDock());
  const [viewport, setViewport] = useState(() => ({
    width: window.innerWidth,
    height: window.innerHeight,
  }));
  const [dragPos, setDragPos] = useState<PixelPosition | null>(null);
  const [dragging, setDragging] = useState(false);

  const dragRef = useRef<{
    pointerId: number;
    startX: number;
    startY: number;
    originX: number;
    originY: number;
    moved: boolean;
  } | null>(null);

  useEffect(() => {
    const onResize = () => {
      setViewport({ width: window.innerWidth, height: window.innerHeight });
    };
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, []);

  const currentPos = dragPos ?? dockToPixel(dock, viewport.width, viewport.height);

  const onPointerDown = (event: ReactPointerEvent<HTMLButtonElement>) => {
    if (event.button !== 0 && event.pointerType === 'mouse') return;
    event.currentTarget.setPointerCapture(event.pointerId);
    const origin = dockToPixel(dock, viewport.width, viewport.height);
    dragRef.current = {
      pointerId: event.pointerId,
      startX: event.clientX,
      startY: event.clientY,
      originX: origin.x,
      originY: origin.y,
      moved: false,
    };
  };

  const onPointerMove = (event: ReactPointerEvent<HTMLButtonElement>) => {
    const active = dragRef.current;
    if (!active || active.pointerId !== event.pointerId) return;

    const dx = event.clientX - active.startX;
    const dy = event.clientY - active.startY;
    if (!active.moved && Math.hypot(dx, dy) >= DRAG_THRESHOLD_PX) {
      active.moved = true;
      setDragging(true);
    }
    if (active.moved) {
      setDragPos(clampPixel(active.originX + dx, active.originY + dy));
    }
  };

  const finishDrag = (event: ReactPointerEvent<HTMLButtonElement>, cancelled: boolean) => {
    const active = dragRef.current;
    if (!active || active.pointerId !== event.pointerId) return;
    dragRef.current = null;
    setDragging(false);
    setDragPos(null);

    if (event.currentTarget.hasPointerCapture(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId);
    }

    if (cancelled) return;

    if (!active.moved) {
      if (!refreshing) onRefresh();
      return;
    }

    const dx = event.clientX - active.startX;
    const dy = event.clientY - active.startY;
    const released = clampPixel(active.originX + dx, active.originY + dy);
    const nextDock = pixelToDock(released.x, released.y);
    setDock(nextDock);
    saveDock(nextDock);
  };

  return (
    <button
      type="button"
      className={`floating-refresh-btn${dragging ? ' dragging' : ''}${refreshing ? ' refreshing' : ''}`}
      style={{ transform: `translate3d(${currentPos.x}px, ${currentPos.y}px, 0)` }}
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={(e) => finishDrag(e, false)}
      onPointerCancel={(e) => finishDrag(e, true)}
      onKeyDown={(e) => {
        if ((e.key === 'Enter' || e.key === ' ') && !refreshing) {
          e.preventDefault();
          onRefresh();
        }
      }}
      aria-label="Refresh Orders (Drag to Reposition)"
      title="Refresh Orders (Drag to Move)"
    >
      <svg
        className="floating-refresh-icon"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
        aria-hidden="true"
      >
        <path d="M21 12a9 9 0 1 1-2.64-6.36" />
        <polyline points="21 3 21 9 15 9" />
      </svg>
    </button>
  );
}
