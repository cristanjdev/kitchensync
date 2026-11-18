export type OrderStatus = 'preparing' | 'ready' | 'acknowledged' | 'picked_up' | 'canceled';
export type ItemStatus = 'pending' | 'preparing' | 'ready';
export type Station = 'grill' | 'fryer' | 'pastry';
export type Role = 'controller' | 'kitchen' | 'waiter';

export interface OrderItem {
  id: string;
  order_id: string;
  menu_item_id: string;
  menu_label: string;
  quantity: number;
  station: Station;
  status: ItemStatus;
  created_at: string;
  ready_at: string | null;
}

export interface Order {
  id: string;
  order_number: number;
  restaurant_id: string;
  table_number: number;
  assigned_waiter_id: string | null;
  status: OrderStatus;
  created_at: string;
  ready_at: string | null;
  acknowledged_at: string | null;
  picked_up_at: string | null;
  canceled_at: string | null;
  updated_at: string;
  order_items: OrderItem[];
}

export interface Profile {
  id: string;
  email: string;
  restaurant_id: string;
  role: Role;
  display_name: string;
}

export interface CreateOrderItem {
  menu_item_id: string;
  menu_label: string;
  quantity: number;
  station: Station;
}
