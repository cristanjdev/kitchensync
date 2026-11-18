import { createClient } from '@supabase/supabase-js';

const url = (import.meta.env.VITE_SUPABASE_URL as string | undefined)?.trim();
const key = (import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY as string | undefined)?.trim();

export const configError =
  !url || !key
    ? 'VITE_SUPABASE_URL and VITE_SUPABASE_PUBLISHABLE_KEY are not set. Add them to .env.local (local) or the Vercel project environment variables, then rebuild.'
    : null;

// The publishable key is safe in a browser; access is enforced by RLS and role-checked RPCs.
export const supabase = createClient(url || 'http://localhost.invalid', key || 'missing-key', {
  auth: { persistSession: true, autoRefreshToken: true, storageKey: 'ks360-dashboard-auth' },
});

/** Turns Supabase/PostgREST errors into a readable sentence. */
export function errorMessage(error: unknown): string {
  if (!error) return 'Unknown Error';
  if (typeof error === 'string') return error;
  if (typeof error === 'object' && 'message' in error && typeof error.message === 'string') {
    return error.message;
  }
  return String(error);
}
