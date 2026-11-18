import { useEffect, useRef, useState } from 'react';
import { errorMessage, supabase } from '../supabase';
import { MENU, STATIONS } from '../menu';
import type { CreateOrderItem } from '../types';

const TOUCH_TABLES = Array.from({ length: 20 }, (_, i) => i + 1);

interface Props {
  onClose: () => void;
  onCreated: () => void;
}

export function NewOrderModal({ onClose, onCreated }: Props) {
  const [table, setTable] = useState('12');
  const [quantities, setQuantities] = useState<Record<string, number>>({});
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const requestRef = useRef<{ fingerprint: string; id: string } | null>(null);
  const inFlight = useRef(false);

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !inFlight.current) onClose();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  const setQty = (id: string, next: number) => {
    setError(null);
    setQuantities((q) => {
      const clamped = Math.max(0, Math.min(20, next));
      if (clamped === 0) {
        const copy = { ...q };
        delete copy[id];
        return copy;
      }
      return { ...q, [id]: clamped };
    });
  };

  const parsedTable = Number.parseInt(table, 10);
  const hasValidTable = Number.isFinite(parsedTable) && parsedTable > 0 && parsedTable <= 999;

  const stepTable = (delta: number) => {
    const base = hasValidTable ? parsedTable : 1;
    const next = Math.max(1, Math.min(999, base + delta));
    setTable(String(next));
    setError(null);
  };

  const selectedEntries = MENU.filter((m) => (quantities[m.id] ?? 0) > 0);
  const totalItems = selectedEntries.reduce((sum, m) => sum + (quantities[m.id] ?? 0), 0);
  const activeStationCount = new Set(selectedEntries.map((m) => m.station)).size;

  const submit = async () => {
    if (inFlight.current) return;
    const tableNumber = Number.parseInt(table, 10);
    const items: CreateOrderItem[] = selectedEntries.map((m) => ({
      menu_item_id: m.id,
      menu_label: m.label,
      quantity: quantities[m.id],
      station: m.station,
    }));
    if (!Number.isFinite(tableNumber) || tableNumber <= 0 || tableNumber > 999) {
      setError('Select or enter a table number between 1 and 999.');
      return;
    }
    if (items.length === 0) {
      setError('Tap at least one dish to add it to the order.');
      return;
    }

    const fingerprint = JSON.stringify([tableNumber, items]);
    if (!requestRef.current || requestRef.current.fingerprint !== fingerprint) {
      requestRef.current = { fingerprint, id: crypto.randomUUID() };
    }

    inFlight.current = true;
    setSubmitting(true);
    setError(null);
    const { error: rpcError } = await supabase.rpc('create_order', {
      p_table_number: tableNumber,
      p_items: items,
      p_request_id: requestRef.current.id,
    });
    inFlight.current = false;
    setSubmitting(false);
    if (rpcError) {
      setError(`Could not send order: ${errorMessage(rpcError)}`);
      return;
    }
    requestRef.current = null;
    onCreated();
  };

  return (
    <div className="modal-backdrop" onMouseDown={() => !inFlight.current && onClose()}>
      <div
        className="modal order-modal-touch"
        role="dialog"
        aria-modal="true"
        aria-labelledby="new-order-title"
        onMouseDown={(e) => e.stopPropagation()}
      >
        <header className="modal-header order-touch-header">
          <div>
            <h2 id="new-order-title">New Table Order</h2>
            <p className="muted small">
              Tap a table number, then tap dishes to build the ticket.
            </p>
          </div>
          <button
            type="button"
            className="modal-close touch-close-btn"
            onClick={onClose}
            disabled={submitting}
            aria-label="Close"
          >
            ✕
          </button>
        </header>

        <div className="order-touch-workspace">
          {/* LEFT PANE: Touch Table Grid + 3-Station Dish Touch Pads */}
          <div className="order-touch-main">
            <section className="order-table-touch-panel" aria-label="Table selection">
              <div className="order-table-touch-top">
                <span className="order-section-label">1. Select Table</span>
                <div className="order-table-stepper">
                  <button
                    type="button"
                    className="touch-step-btn"
                    onClick={() => stepTable(-1)}
                    disabled={hasValidTable && parsedTable <= 1}
                    aria-label="Previous table"
                  >
                    −
                  </button>
                  <label className="order-table-input-box" aria-label="Table number">
                    <span className="order-table-hash">Table #</span>
                    <input
                      inputMode="numeric"
                      placeholder="12"
                      value={table}
                      onChange={(e) => {
                        setTable(e.target.value.replace(/\D/g, '').slice(0, 3));
                        setError(null);
                      }}
                    />
                  </label>
                  <button
                    type="button"
                    className="touch-step-btn"
                    onClick={() => stepTable(1)}
                    aria-label="Next table"
                  >
                    +
                  </button>
                </div>
              </div>

              <div className="touch-table-grid">
                {TOUCH_TABLES.map((num) => (
                  <button
                    key={num}
                    type="button"
                    className={`touch-table-btn${table === String(num) ? ' active' : ''}`}
                    onClick={() => {
                      setTable(String(num));
                      setError(null);
                    }}
                  >
                    {num}
                  </button>
                ))}
              </div>
            </section>

            <section className="order-dishes-section" aria-label="Menu items by station">
              <div className="order-dishes-header">
                <span className="order-section-label">2. Tap Dishes to Add</span>
                <span className="muted small">Tap a dish tile multiple times to increase quantity</span>
              </div>

              <div className="menu-grid">
                {STATIONS.map((station) => {
                  const stationItems = MENU.filter((m) => m.station === station.id);
                  const stationQty = stationItems.reduce((sum, m) => sum + (quantities[m.id] ?? 0), 0);
                  return (
                    <div key={station.id} className="menu-station">
                      <header className="menu-station-header">
                        <h3 className="menu-station-title">{station.label}</h3>
                        <span className={`menu-station-badge${stationQty > 0 ? ' active' : ''}`}>
                          {stationQty}
                        </span>
                      </header>
                      <div className="menu-station-items">
                        {stationItems.map((m) => {
                          const qty = quantities[m.id] ?? 0;
                          return (
                            <div
                              key={m.id}
                              role="button"
                              tabIndex={0}
                              className={`menu-item touch-dish-card${qty > 0 ? ' selected' : ''}`}
                              onClick={() => setQty(m.id, qty + 1)}
                              onKeyDown={(e) => {
                                if (e.key === 'Enter' || e.key === ' ') {
                                  e.preventDefault();
                                  setQty(m.id, qty + 1);
                                }
                              }}
                            >
                              <div className="touch-dish-info">
                                <span className="menu-item-name">{m.label}</span>
                                <span className="touch-dish-hint">
                                  {qty > 0 ? `${qty} in order` : 'Tap to add'}
                                </span>
                              </div>
                              <div className="stepper" onClick={(e) => e.stopPropagation()}>
                                {qty > 0 ? (
                                  <>
                                    <button
                                      type="button"
                                      className="stepper-btn"
                                      onClick={() => setQty(m.id, qty - 1)}
                                      aria-label={`Decrease ${m.label}`}
                                    >
                                      −
                                    </button>
                                    <span className="qty">{qty}×</span>
                                    <button
                                      type="button"
                                      className="stepper-btn"
                                      onClick={() => setQty(m.id, qty + 1)}
                                      aria-label={`Increase ${m.label}`}
                                    >
                                      +
                                    </button>
                                  </>
                                ) : (
                                  <button
                                    type="button"
                                    className="stepper-add-btn"
                                    onClick={() => setQty(m.id, 1)}
                                    aria-label={`Add ${m.label}`}
                                  >
                                    +
                                  </button>
                                )}
                              </div>
                            </div>
                          );
                        })}
                      </div>
                    </div>
                  );
                })}
              </div>
            </section>
          </div>

          {/* RIGHT PANE: Live Kitchen Ticket Rail */}
          <aside className="order-ticket-rail" aria-label="Live order ticket">
            <div className="order-ticket-rail-header">
              <div>
                <div className="order-ticket-table-title">
                  {hasValidTable ? `Table ${parsedTable}` : 'Select Table'}
                </div>
                <div className="order-ticket-subtitle">
                  {totalItems > 0
                    ? `${totalItems} ${totalItems === 1 ? 'dish' : 'dishes'} · ${activeStationCount} ${
                        activeStationCount === 1 ? 'station' : 'stations'
                      }`
                    : 'Empty ticket'}
                </div>
              </div>
              {totalItems > 0 && (
                <button
                  type="button"
                  className="btn subtle ticket-clear-btn"
                  onClick={() => {
                    setQuantities({});
                    setError(null);
                  }}
                >
                  Clear
                </button>
              )}
            </div>

            <div className="order-ticket-rail-body">
              {selectedEntries.length > 0 ? (
                <ul className="order-ticket-list">
                  {selectedEntries.map((m) => {
                    const qty = quantities[m.id] ?? 0;
                    const stationLabel =
                      STATIONS.find((s) => s.id === m.station)?.label ?? m.station;
                    return (
                      <li key={m.id} className="order-ticket-row">
                        <div className="order-ticket-row-info">
                          <span className="order-ticket-row-name">{m.label}</span>
                          <span className="order-ticket-row-station">{stationLabel}</span>
                        </div>
                        <div className="stepper">
                          <button
                            type="button"
                            className="stepper-btn"
                            onClick={() => setQty(m.id, qty - 1)}
                            aria-label={`Decrease ${m.label}`}
                          >
                            −
                          </button>
                          <span className="qty">{qty}×</span>
                          <button
                            type="button"
                            className="stepper-btn"
                            onClick={() => setQty(m.id, qty + 1)}
                            aria-label={`Increase ${m.label}`}
                          >
                            +
                          </button>
                        </div>
                      </li>
                    );
                  })}
                </ul>
              ) : (
                <div className="order-ticket-empty-state">
                  <p className="order-ticket-empty-title">No dishes added yet</p>
                  <p className="muted small">
                    Tap any dish on the left to add it to{' '}
                    {hasValidTable ? `Table ${parsedTable}'s` : 'this'} ticket.
                  </p>
                </div>
              )}
            </div>

            {error && <p className="error-text small ticket-error">{error}</p>}

            <div className="order-ticket-rail-footer">
              <button
                type="button"
                className="btn primary touch-send-btn"
                onClick={() => void submit()}
                disabled={submitting || totalItems === 0 || !hasValidTable}
              >
                {submitting
                  ? 'Sending to Kitchen…'
                  : totalItems > 0
                    ? `Send to Kitchen (${totalItems})`
                    : 'Select Dishes to Send'}
              </button>
              <button
                type="button"
                className="btn subtle touch-cancel-btn"
                onClick={onClose}
                disabled={submitting}
              >
                Cancel
              </button>
            </div>
          </aside>
        </div>
      </div>
    </div>
  );
}
