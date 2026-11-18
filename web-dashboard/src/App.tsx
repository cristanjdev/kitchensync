import { useEffect, useState } from 'react';
import type { Session } from '@supabase/supabase-js';
import { configError, errorMessage, supabase } from './supabase';
import type { Profile } from './types';
import { SignIn } from './components/SignIn';
import { Dashboard } from './components/Dashboard';

type ProfileState =
  | { kind: 'idle' }
  | { kind: 'loading' }
  | { kind: 'ready'; profile: Profile }
  | { kind: 'error'; message: string };

export function App() {
  const [session, setSession] = useState<Session | null>(null);
  const [sessionChecked, setSessionChecked] = useState(false);
  const [profileState, setProfileState] = useState<ProfileState>({ kind: 'idle' });

  useEffect(() => {
    if (configError) return;
    void supabase.auth.getSession().then(({ data }) => {
      setSession(data.session);
      setSessionChecked(true);
    });
    const { data } = supabase.auth.onAuthStateChange((_event, next) => setSession(next));
    return () => data.subscription.unsubscribe();
  }, []);

  const userId = session?.user.id ?? null;
  useEffect(() => {
    if (!userId) {
      setProfileState({ kind: 'idle' });
      return;
    }
    let cancelled = false;
    setProfileState({ kind: 'loading' });
    void supabase
      .from('profiles')
      .select('id, email, restaurant_id, role, display_name')
      .eq('id', userId)
      .maybeSingle()
      .then(({ data, error }) => {
        if (cancelled) return;
        if (error) setProfileState({ kind: 'error', message: errorMessage(error) });
        else if (!data)
          setProfileState({
            kind: 'error',
            message: 'This account has no KitchenSync 360 profile. Ask the administrator to add it to public.profiles.',
          });
        else setProfileState({ kind: 'ready', profile: data as Profile });
      });
    return () => {
      cancelled = true;
    };
  }, [userId]);

  if (configError) {
    return (
      <main className="center-screen">
        <div className="panel narrow">
          <h1 className="brand">KitchenSync 360</h1>
          <p className="error-text">{configError}</p>
        </div>
      </main>
    );
  }

  if (!sessionChecked) {
    return <main className="center-screen muted">Loading…</main>;
  }

  if (!session) return <SignIn />;

  if (profileState.kind === 'ready') {
    return <Dashboard profile={profileState.profile} />;
  }

  return (
    <main className="center-screen">
      <div className="panel narrow">
        <h1 className="brand">KitchenSync 360</h1>
        {profileState.kind === 'error' ? (
          <>
            <p className="error-text">{profileState.message}</p>
            <button className="btn" onClick={() => void supabase.auth.signOut()}>
              Sign Out
            </button>
          </>
        ) : (
          <p className="muted">Loading Profile…</p>
        )}
      </div>
    </main>
  );
}
