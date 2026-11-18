import { useCallback, useEffect, useRef, useState } from 'react';
import { supabase, errorMessage } from './supabase';
import type { Order } from './types';

export type LiveStatus = 'connecting' | 'live' | 'reconnecting' | 'offline';

const ACTIVE_STATUSES = ['preparing', 'ready', 'acknowledged'] as const;
const PICKED_UP_SHOWN = 12;
/** Coalesces the burst of row events one RPC produces (e.g. 1 order + 3 items) into one fetch. */
const EVENT_DEBOUNCE_MS = 120;
/** Safety-net polling: fast while realtime is down, slow while it is healthy. */
const POLL_WHEN_DEGRADED_MS = 3_000;
const POLL_WHEN_LIVE_MS = 20_000;

/**
 * Live order feed for the dashboard.
 *
 * - Initial snapshot, then a Supabase Realtime subscription on orders and order_items.
 * - Every change event triggers a fresh snapshot (same strategy as the Android app), so the
 *   screen always shows the backend's current, consistent state rather than patched rows.
 * - On (re)subscribe, network recovery, or the tab becoming visible, a fresh snapshot is taken
 *   so changes missed while disconnected are recovered without a page refresh.
 * - Overlapping fetches apply only the newest result.
 */
export function useLiveOrders(enabled: boolean) {
  const [orders, setOrders] = useState<Order[]>([]);
  const [status, setStatus] = useState<LiveStatus>('connecting');
  const [lastSyncedAt, setLastSyncedAt] = useState<number | null>(null);
  const [syncError, setSyncError] = useState<string | null>(null);
  const [loaded, setLoaded] = useState(false);
  const [refreshing, setRefreshing] = useState(false);

  const requestSeq = useRef(0);
  const appliedSeq = useRef(0);
  const debounceTimer = useRef<number | undefined>(undefined);
  const statusRef = useRef<LiveStatus>('connecting');

  const updateStatus = useCallback((next: LiveStatus) => {
    statusRef.current = next;
    setStatus(next);
  }, []);

  const refresh = useCallback(async () => {
    const mySeq = ++requestSeq.current;
    const [active, pickedUp] = await Promise.all([
      supabase
        .from('orders')
        .select('*, order_items(*)')
        .in('status', [...ACTIVE_STATUSES])
        .order('order_number', { ascending: true }),
      supabase
        .from('orders')
        .select('*, order_items(*)')
        .eq('status', 'picked_up')
        .order('picked_up_at', { ascending: false })
        .limit(PICKED_UP_SHOWN),
    ]);
    if (mySeq <= appliedSeq.current) return; // a newer snapshot already landed
    const error = active.error ?? pickedUp.error;
    if (error) {
      setSyncError(errorMessage(error));
      return;
    }
    appliedSeq.current = mySeq;
    const sortItems = (order: Order): Order => ({
      ...order,
      order_items: [...(order.order_items ?? [])].sort((a, b) =>
        a.created_at.localeCompare(b.created_at),
      ),
    });
    setOrders([...(active.data as Order[]), ...(pickedUp.data as Order[])].map(sortItems));
    setLastSyncedAt(Date.now());
    setSyncError(null);
    setLoaded(true);
  }, []);

  const scheduleRefresh = useCallback(
    (delay = EVENT_DEBOUNCE_MS) => {
      window.clearTimeout(debounceTimer.current);
      debounceTimer.current = window.setTimeout(() => {
        void refresh();
      }, delay);
    },
    [refresh],
  );

  const forceRefresh = useCallback(async () => {
    window.clearTimeout(debounceTimer.current);
    setRefreshing(true);
    const minSpin = new Promise((resolve) => window.setTimeout(resolve, 360));
    try {
      await Promise.all([refresh(), minSpin]);
    } finally {
      setRefreshing(false);
    }
  }, [refresh]);

  // Realtime subscription
  useEffect(() => {
    if (!enabled) return;
    updateStatus(navigator.onLine ? 'connecting' : 'offline');
    void refresh();

    const channel = supabase
      .channel('ks360-web-dashboard')
      .on('postgres_changes', { event: '*', schema: 'public', table: 'orders' }, () =>
        scheduleRefresh(),
      )
      .on('postgres_changes', { event: '*', schema: 'public', table: 'order_items' }, () =>
        scheduleRefresh(),
      )
      .subscribe((state) => {
        if (state === 'SUBSCRIBED') {
          updateStatus('live');
          scheduleRefresh(0); // recover anything missed while (re)connecting
        } else if (state === 'CHANNEL_ERROR' || state === 'TIMED_OUT' || state === 'CLOSED') {
          updateStatus(navigator.onLine ? 'reconnecting' : 'offline');
        }
      });

    return () => {
      window.clearTimeout(debounceTimer.current);
      void supabase.removeChannel(channel);
    };
  }, [enabled, refresh, scheduleRefresh, updateStatus]);

  // Safety-net polling and recovery on network / visibility changes
  useEffect(() => {
    if (!enabled) return;
    let timer: number | undefined;
    const loop = () => {
      const delay = statusRef.current === 'live' ? POLL_WHEN_LIVE_MS : POLL_WHEN_DEGRADED_MS;
      timer = window.setTimeout(() => {
        void refresh().finally(loop);
      }, delay);
    };
    loop();

    const onOnline = () => {
      if (statusRef.current === 'offline') updateStatus('reconnecting');
      scheduleRefresh(0);
    };
    const onOffline = () => updateStatus('offline');
    const onVisible = () => {
      if (document.visibilityState === 'visible') scheduleRefresh(0);
    };
    window.addEventListener('online', onOnline);
    window.addEventListener('offline', onOffline);
    document.addEventListener('visibilitychange', onVisible);
    return () => {
      window.clearTimeout(timer);
      window.removeEventListener('online', onOnline);
      window.removeEventListener('offline', onOffline);
      document.removeEventListener('visibilitychange', onVisible);
    };
  }, [enabled, refresh, scheduleRefresh, updateStatus]);

  return {
    orders,
    status,
    lastSyncedAt,
    syncError,
    loaded,
    refreshing,
    refresh: scheduleRefresh,
    forceRefresh,
  };
}
