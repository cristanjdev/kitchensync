import type { Station } from './types';

// Mirrors PresetMenu in the Android app (app/src/main/java/.../model/Models.kt).
export interface MenuItem {
  id: string;
  label: string;
  station: Station;
}

export const STATIONS: { id: Station; label: string }[] = [
  { id: 'grill', label: 'Grill' },
  { id: 'fryer', label: 'Fryer' },
  { id: 'pastry', label: 'Pastry' },
];

export const MENU: MenuItem[] = [
  { id: 'grill_chicken', label: 'Grilled Chicken', station: 'grill' },
  { id: 'grill_salmon', label: 'Pan-Seared Salmon', station: 'grill' },
  { id: 'grill_burger', label: 'Beef Burger', station: 'grill' },
  { id: 'grill_pasta', label: 'Herb Charred Pasta', station: 'grill' },
  { id: 'fryer_fries', label: 'French Fries', station: 'fryer' },
  { id: 'fryer_onion_rings', label: 'Onion Rings', station: 'fryer' },
  { id: 'fryer_croquettes', label: 'Potato Croquettes', station: 'fryer' },
  { id: 'fryer_calamari', label: 'Crispy Calamari', station: 'fryer' },
  { id: 'pastry_choco_cake', label: 'Chocolate Cake', station: 'pastry' },
  { id: 'pastry_cheesecake', label: 'New York Cheesecake', station: 'pastry' },
  { id: 'pastry_apple_tart', label: 'Warm Apple Tart', station: 'pastry' },
];

/** Demonstration default, matching UNACKNOWLEDGED_TIMEOUT_SECONDS in the Android build. */
export const UNACKNOWLEDGED_TIMEOUT_SECONDS = 30;
