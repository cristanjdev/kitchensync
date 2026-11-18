import { useEffect, useMemo, useState, type CSSProperties } from 'react';
import { errorMessage, supabase } from '../supabase';
import type { Order, OrderStatus, Profile } from '../types';
import { useLiveOrders, type LiveStatus } from '../useLiveOrders';
import { UNACKNOWLEDGED_TIMEOUT_SECONDS } from '../menu';
import { elapsedSeconds, formatClock, formatDuration, orderLabel, toMillis } from '../time';
import { OrderCard } from './OrderCard';
import { NewOrderModal } from './NewOrderModal';
import { ConfirmModal } from './ConfirmModal';
import { FloatingRefreshButton } from './FloatingRefreshButton';

type ViewFilter = 'all' | 'active' | OrderStatus;

const COLUMNS: { status: OrderStatus; title: string; empty: string }[] = [
  { status: 'preparing', title: 'Preparing', empty: 'No Orders in Kitchen' },
  { status: 'ready', title: 'Ready', empty: 'No Orders Waiting for Waiter' },
  { status: 'acknowledged', title: 'Acknowledged', empty: 'No Acknowledged Orders' },
  { status: 'picked_up', title: 'Picked Up', empty: 'No Recent Pickups' },
];

const LIVE_LABEL: Record<LiveStatus, string> = {
  connecting: 'Connecting',
  live: 'Live',
  reconnecting: 'Reconnecting',
  offline: 'Offline',
};

export function Dashboard({ profile }: { profile: Profile }) {
  const { orders, status, syncError, loaded, refreshing, refresh, forceRefresh } = useLiveOrders(true);
  const [now, setNow] = useState(() => Date.now());
  const [viewFilter, setViewFilter] = useState<ViewFilter>('all');
  const [showNewOrder, setShowNewOrder] = useState(false);
  const [cancelTarget, setCancelTarget] = useState<Order | null>(null);
  const [cancelModalError, setCancelModalError] = useState<string | null>(null);
  const [confirmSignOut, setConfirmSignOut] = useState(false);
  const [signingOut, setSigningOut] = useState(false);
  const [signOutError, setSignOutError] = useState<string | null>(null);
  const [pending, setPending] = useState<Record<string, boolean>>({});
  const [cardErrors, setCardErrors] = useState<Record<string, string>>({});
  const canControl = profile.role === 'controller';

  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);

  const grouped = useMemo(() => {
    const byStatus: Record<string, Order[]> = {};
    for (const column of COLUMNS) byStatus[column.status] = [];
    for (const order of orders) byStatus[order.status]?.push(order);
    return byStatus;
  }, [orders]);

  const overdueOrders = useMemo(
    () =>
      grouped.ready.filter(
        (order) => elapsedSeconds(order.ready_at, now) >= UNACKNOWLEDGED_TIMEOUT_SECONDS,
      ),
    [grouped.ready, now],
  );

  const visibleColumns = useMemo(() => {
    if (viewFilter === 'all') return COLUMNS;
    if (viewFilter === 'active') return COLUMNS.filter((c) => c.status !== 'picked_up');
    return COLUMNS.filter((c) => c.status === viewFilter);
  }, [viewFilter]);

  const activeTotal =
    grouped.preparing.length + grouped.ready.length + grouped.acknowledged.length;

  const cancelOrder = async (orderId: string) => {
    setPending((p) => ({ ...p, [orderId]: true }));
    setCancelModalError(null);
    setCardErrors(({ [orderId]: _removed, ...rest }) => rest);
    const { error } = await supabase.rpc('cancel_order', { p_order_id: orderId });
    setPending(({ [orderId]: _removed, ...rest }) => rest);
    if (error) {
      const msg = `Cancel Failed: ${errorMessage(error)}`;
      setCancelModalError(msg);
      setCardErrors((e) => ({ ...e, [orderId]: msg }));
      return;
    }
    setCancelTarget(null);
    refresh(0);
  };

  const handleSignOut = async () => {
    setSigningOut(true);
    setSignOutError(null);
    const { error } = await supabase.auth.signOut();
    setSigningOut(false);
    if (error) {
      setSignOutError(errorMessage(error));
      return;
    }
    setConfirmSignOut(false);
  };

  const toggleFullscreen = () => {
    if (document.fullscreenElement) void document.exitFullscreen();
    else void document.documentElement.requestFullscreen?.();
  };

  const filterTabs: { id: ViewFilter; label: string; count: number }[] = [
    { id: 'all', label: 'All Columns', count: orders.length },
    { id: 'active', label: 'Active Only', count: activeTotal },
    { id: 'preparing', label: 'Preparing', count: grouped.preparing.length },
    { id: 'ready', label: 'Ready', count: grouped.ready.length },
    { id: 'acknowledged', label: 'Acknowledged', count: grouped.acknowledged.length },
    { id: 'picked_up', label: 'Picked Up', count: grouped.picked_up.length },
  ];

  return (
    <div className="dashboard">
      <header className="topbar">
        <div className="topbar-left">
          <span className="topbar-brand">KitchenSync 360</span>
          <span className="topbar-divider">/</span>
          <span className="topbar-subtitle">Expeditor Pass</span>
          <span className={`status-badge status-${status}`} title={syncError ?? undefined}>
            <span className="status-dot" />
            {LIVE_LABEL[status]}
          </span>
        </div>

        <nav className="filter-tabs" aria-label="Board View Filter">
          {filterTabs.map((tab) => (
            <button
              key={tab.id}
              type="button"
              className={`filter-tab filter-tab-${tab.id}${viewFilter === tab.id ? ' active' : ''}`}
              onClick={() => setViewFilter(tab.id)}
            >
              <span>{tab.label}</span>
              <span className="filter-tab-count">{tab.count}</span>
            </button>
          ))}
        </nav>

        <span className="clock">
          {new Date(now).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
        </span>

        <div className="topbar-actions">
          {canControl && (
            <button className="btn primary" onClick={() => setShowNewOrder(true)}>
              + New Order
            </button>
          )}
          <button className="btn subtle" onClick={toggleFullscreen} title="Toggle Full Screen">
            Full Screen
          </button>
          <button
            className="btn subtle"
            onClick={() => {
              setSignOutError(null);
              setConfirmSignOut(true);
            }}
            title={`Signed In as ${profile.email}`}
          >
            Sign Out
          </button>
        </div>
      </header>

      {!canControl && (
        <div className="banner info">
          <div className="alert-headline">
            <span className="alert-dot info" aria-hidden="true" />
            <strong className="alert-title">View-Only Session</strong>
            <span className="alert-subtitle">
              Signed in as {profile.display_name} ({profile.role}) — switch to Controller to create or cancel orders.
            </span>
          </div>
        </div>
      )}

      {syncError && (
        <div className="banner warn" role="status">
          <div className="alert-headline">
            <span className="alert-dot warn" aria-hidden="true" />
            <strong className="alert-title">Connection Issue — Retrying Automatically</strong>
            <span className="alert-subtitle">{syncError}</span>
          </div>
          <button
            type="button"
            className="btn alert-retry-btn"
            disabled={refreshing}
            onClick={() => void forceRefresh()}
          >
            {refreshing ? 'Retrying…' : 'Retry Now'}
          </button>
        </div>
      )}

      {overdueOrders.length > 0 && (
        <div className="banner danger alert-overdue" role="alert">
          <div className="alert-overdue-main">
            <span className="alert-dot danger" aria-hidden="true" />
            <strong className="alert-title">Waiting for Waiter</strong>
            <div className="alert-chips">
              {overdueOrders.map((o) => {
                const waiting = formatDuration(elapsedSeconds(o.ready_at, now));
                const content = (
                  <>
                    <span className="alert-chip-table">Table {o.table_number}</span>
                    <span className="alert-chip-sep" aria-hidden="true">·</span>
                    <span className="alert-chip-order">{orderLabel(o.order_number)}</span>
                    <span className="alert-chip-time">{waiting}</span>
                  </>
                );
                return viewFilter !== 'ready' ? (
                  <button
                    key={o.id}
                    type="button"
                    className="alert-chip alert-chip-action"
                    onClick={() => setViewFilter('ready')}
                    title="View Ready Orders"
                  >
                    {content}
                  </button>
                ) : (
                  <span key={o.id} className="alert-chip">
                    {content}
                  </span>
                );
              })}
            </div>
          </div>
          {viewFilter !== 'ready' && (
            <button
              type="button"
              className="btn alert-jump-btn"
              onClick={() => setViewFilter('ready')}
            >
              View Ready ({grouped.ready.length})
            </button>
          )}
        </div>
      )}

      <main
        className="board"
        style={{ '--cols': visibleColumns.length } as CSSProperties}
      >
        {visibleColumns.map((column) => {
          const list = grouped[column.status] ?? [];
          const isSingleColumnView = visibleColumns.length === 1;
          return (
            <section
              key={column.status}
              className={`column column-${column.status}${isSingleColumnView ? ' column-single' : ''}`}
            >
              <header className="column-header">
                <h2 className="column-title">{column.title}</h2>
                <span className="column-count">{list.length}</span>
              </header>

              <div className="column-body">
                {!loaded ? (
                  <p className="empty-column">Loading Orders…</p>
                ) : list.length === 0 ? (
                  <p className="empty-column">{column.empty}</p>
                ) : (
                  list.map((order) => (
                    <OrderCard
                      key={order.id}
                      order={order}
                      now={now}
                      canControl={canControl}
                      pending={!!pending[order.id]}
                      error={cardErrors[order.id]}
                      onCancel={() => {
                        setCancelModalError(null);
                        setCancelTarget(order);
                      }}
                    />
                  ))
                )}
              </div>
            </section>
          );
        })}
      </main>

      {showNewOrder && (
        <NewOrderModal
          onClose={() => setShowNewOrder(false)}
          onCreated={() => {
            setShowNewOrder(false);
            refresh(0);
          }}
        />
      )}

      {cancelTarget && (() => {
        const totalQty = cancelTarget.order_items.reduce((sum, item) => sum + item.quantity, 0);
        const statusLabel =
          cancelTarget.status === 'preparing'
            ? 'Preparing'
            : cancelTarget.status === 'ready'
              ? 'Ready'
              : 'Acknowledged';
        let timingLabel = 'Elapsed';
        let timingValue = formatDuration(elapsedSeconds(cancelTarget.created_at, now));
        if (cancelTarget.status === 'ready') {
          timingLabel = 'Waiting';
          timingValue = formatDuration(elapsedSeconds(cancelTarget.ready_at, now));
        } else if (cancelTarget.status === 'acknowledged') {
          const readyMs = toMillis(cancelTarget.ready_at);
          const ackMs = toMillis(cancelTarget.acknowledged_at);
          const ackSec =
            readyMs !== null && ackMs !== null ? Math.max(0, Math.round((ackMs - readyMs) / 1000)) : null;
          timingLabel = 'Waiter Ack';
          timingValue = ackSec !== null ? `${formatDuration(ackSec)}` : 'Confirmed';
        }
        const statusImpact =
          cancelTarget.status === 'preparing'
            ? 'Removes this ticket from active Grill, Fryer, and Pastry displays immediately.'
            : cancelTarget.status === 'ready'
              ? 'Clears the active ready alert from the waiter phone and paired Wear OS watch.'
              : 'The waiter already acknowledged this alert. Notify them directly if items were already plated.';

        return (
          <ConfirmModal
            title={`Cancel Table ${cancelTarget.table_number}`}
            subtitle={`Order ${orderLabel(cancelTarget.order_number)} · Placed ${formatClock(cancelTarget.created_at)}`}
            confirmLabel={pending[cancelTarget.id] ? 'Canceling…' : 'Yes, Cancel Order'}
            cancelLabel="Keep Order"
            tone="danger"
            busy={!!pending[cancelTarget.id]}
            error={cancelModalError}
            onCancel={() => {
              if (!pending[cancelTarget.id]) {
                setCancelModalError(null);
                setCancelTarget(null);
              }
            }}
            onConfirm={() => void cancelOrder(cancelTarget.id)}
          >
            <div className="confirm-summary-card">
              <div className="confirm-meta-grid confirm-meta-3">
                <div className="confirm-meta-cell">
                  <span className="confirm-meta-label">Status</span>
                  <span className={`confirm-meta-value status-text-${cancelTarget.status}`}>
                    {statusLabel}
                  </span>
                </div>
                <div className="confirm-meta-cell">
                  <span className="confirm-meta-label">Items</span>
                  <span className="confirm-meta-value">
                    {totalQty} ({cancelTarget.order_items.length}{' '}
                    {cancelTarget.order_items.length === 1 ? 'line' : 'lines'})
                  </span>
                </div>
                <div className="confirm-meta-cell">
                  <span className="confirm-meta-label">{timingLabel}</span>
                  <span className="confirm-meta-value">{timingValue}</span>
                </div>
              </div>

              <ul className="confirm-items">
                {cancelTarget.order_items.map((item) => (
                  <li key={item.id} className="confirm-item-row">
                    <span className="qty">{item.quantity}×</span>
                    <span className="confirm-item-name">{item.menu_label}</span>
                    <span className={`item-meta ${item.status}`}>
                      {item.station.charAt(0).toUpperCase() + item.station.slice(1)} ·{' '}
                      {item.status === 'ready'
                        ? 'Ready'
                        : item.status === 'preparing'
                          ? 'Cooking'
                          : 'Pending'}
                    </span>
                  </li>
                ))}
              </ul>
            </div>

            <div className="confirm-notice danger">
              <strong>Action Cannot Be Undone</strong>
              <span>{statusImpact}</span>
            </div>
          </ConfirmModal>
        );
      })()}

      {confirmSignOut && (
        <ConfirmModal
          title="Sign Out of KitchenSync 360?"
          subtitle="Live updates and order controls on this screen will stop until you sign back in."
          confirmLabel={signingOut ? 'Signing Out…' : 'Sign Out'}
          cancelLabel="Stay Signed In"
          tone="primary"
          busy={signingOut}
          error={signOutError}
          onCancel={() => {
            if (!signingOut) {
              setSignOutError(null);
              setConfirmSignOut(false);
            }
          }}
          onConfirm={() => void handleSignOut()}
        >
          <div className="confirm-summary-card">
            <div className="confirm-meta-grid confirm-meta-2">
              <div className="confirm-meta-cell">
                <span className="confirm-meta-label">Operator</span>
                <span className="confirm-meta-value">{profile.display_name}</span>
                <span className="confirm-meta-sub">{profile.email}</span>
              </div>
              <div className="confirm-meta-cell">
                <span className="confirm-meta-label">Role &amp; Sync</span>
                <span className="confirm-meta-value">
                  {profile.role.charAt(0).toUpperCase() + profile.role.slice(1)}
                </span>
                <span className={`confirm-meta-sub status-text-${status === 'live' ? 'ready' : 'preparing'}`}>
                  {LIVE_LABEL[status]}
                </span>
              </div>
            </div>

            <div className="confirm-stats-row">
              <div className="confirm-stat">
                <span className="confirm-stat-label status-text-preparing">Preparing</span>
                <span className="confirm-stat-num status-text-preparing">{grouped.preparing.length}</span>
              </div>
              <div className="confirm-stat">
                <span className="confirm-stat-label status-text-ready">Ready</span>
                <span className="confirm-stat-num status-text-ready">{grouped.ready.length}</span>
              </div>
              <div className="confirm-stat">
                <span className="confirm-stat-label status-text-acknowledged">Acknowledged</span>
                <span className="confirm-stat-num status-text-acknowledged">{grouped.acknowledged.length}</span>
              </div>
            </div>
          </div>

          {overdueOrders.length > 0 ? (
            <div className="confirm-notice danger">
              <strong>
                {overdueOrders.length} ready {overdueOrders.length === 1 ? 'order' : 'orders'} waiting for waiter
              </strong>
              <span>Food is plated at the pass and still needs waiter confirmation.</span>
            </div>
          ) : (
            <div className="confirm-notice neutral">
              <span>Kitchen stations and waiter devices stay online independently.</span>
            </div>
          )}
        </ConfirmModal>
      )}

      <FloatingRefreshButton refreshing={refreshing} onRefresh={() => void forceRefresh()} />
    </div>
  );
}
