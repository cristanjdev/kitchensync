import type { Order, Station } from '../types';
import { STATIONS, UNACKNOWLEDGED_TIMEOUT_SECONDS } from '../menu';
import { elapsedSeconds, formatClock, formatDuration, orderLabel, toMillis } from '../time';

type StationState = 'not_required' | 'pending' | 'preparing' | 'ready';

const STATION_STATE_LABEL: Record<StationState, string> = {
  not_required: 'Not Required',
  pending: 'Pending',
  preparing: 'Cooking',
  ready: 'Ready',
};

const STATION_TITLE: Record<Station, string> = {
  grill: 'Grill',
  fryer: 'Fryer',
  pastry: 'Pastry',
};

function stationState(order: Order, station: Station): StationState {
  const items = order.order_items.filter((item) => item.station === station);
  if (items.length === 0) return 'not_required';
  if (items.every((item) => item.status === 'ready')) return 'ready';
  if (items.some((item) => item.status !== 'pending')) return 'preparing';
  return 'pending';
}

interface Props {
  order: Order;
  now: number;
  canControl: boolean;
  pending: boolean;
  error?: string;
  onCancel: () => void;
}

export function OrderCard({ order, now, canControl, pending, error, onCancel }: Props) {
  const readyFor = elapsedSeconds(order.ready_at, now);
  const overdue = order.status === 'ready' && readyFor >= UNACKNOWLEDGED_TIMEOUT_SECONDS;
  const cancellable =
    canControl &&
    (order.status === 'preparing' || order.status === 'ready' || order.status === 'acknowledged');

  let timingBadgeText: string;
  switch (order.status) {
    case 'preparing':
      timingBadgeText = formatDuration(elapsedSeconds(order.created_at, now));
      break;
    case 'ready':
      timingBadgeText = overdue
        ? `Overdue · ${formatDuration(readyFor)}`
        : `Ready ${readyFor}s`;
      break;
    case 'acknowledged': {
      const ready = toMillis(order.ready_at);
      const acked = toMillis(order.acknowledged_at);
      const took =
        ready !== null && acked !== null ? Math.max(0, Math.round((acked - ready) / 1000)) : null;
      timingBadgeText = took !== null ? `Ack in ${formatDuration(took)}` : 'Acknowledged';
      break;
    }
    case 'picked_up':
      timingBadgeText = formatClock(order.picked_up_at);
      break;
    default:
      timingBadgeText = '';
  }

  return (
    <article className={`card card-${order.status}${overdue ? ' overdue' : ''}`}>
      <div className="card-head">
        <div className="card-head-left">
          <span className="table-no">Table {order.table_number}</span>
          <span className="order-no">{orderLabel(order.order_number)}</span>
        </div>
        {timingBadgeText && <span className="timing-badge">{timingBadgeText}</span>}
      </div>

      {/* 3-Station Progress Strip (only needed while active; picked_up orders are already complete) */}
      {order.status !== 'picked_up' && (
        <div className="stations">
          {STATIONS.map((station) => {
            const state = stationState(order, station.id);
            return (
              <div key={station.id} className={`station state-${state}`}>
                <span className="station-name">{station.label}</span>
                <span className="station-state">{STATION_STATE_LABEL[state]}</span>
              </div>
            );
          })}
        </div>
      )}

      <ul className="items">
        {order.order_items.map((item) => {
          const showLiveItemStatus = order.status === 'preparing';
          return (
            <li key={item.id} className="item">
              <span className="qty">{item.quantity}×</span>
              <span className="label">{item.menu_label}</span>
              {showLiveItemStatus ? (
                <span className={`item-meta ${item.status}`}>
                  {item.status === 'ready'
                    ? 'Ready'
                    : item.status === 'preparing'
                      ? 'Cooking'
                      : 'Pending'}
                </span>
              ) : (
                <span className="item-meta">{STATION_TITLE[item.station]}</span>
              )}
            </li>
          );
        })}
      </ul>

      {error && <p className="error-text small">{error}</p>}

      {cancellable && (
        <div className="card-footer">
          <button
            type="button"
            className="btn ghost-danger small-btn"
            onClick={onCancel}
            disabled={pending}
          >
            {pending ? 'Canceling…' : 'Cancel Order'}
          </button>
        </div>
      )}
    </article>
  );
}
