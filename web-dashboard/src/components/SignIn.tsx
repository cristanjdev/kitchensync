import { useState, type FormEvent } from 'react';
import { errorMessage, supabase } from '../supabase';

// Local development only (npm run dev): one-tap role buttons and a password pre-filled from
// VITE_DEMO_PASSWORD in .env.local. Production builds (Vercel) show a plain email + password form.
const DEV_TOOLS = import.meta.env.DEV;
const DEV_PASSWORD: string = DEV_TOOLS ? (import.meta.env.VITE_DEMO_PASSWORD ?? '') : '';

const DEMO_ACCOUNTS = [
  { label: 'Controller', email: 'controller@kitchensync360.demo' },
  { label: 'Kitchen', email: 'kitchen@kitchensync360.demo' },
  { label: 'Waiter', email: 'waiter@kitchensync360.demo' },
];

export function SignIn() {
  const [email, setEmail] = useState(DEV_TOOLS ? 'controller@kitchensync360.demo' : '');
  const [password, setPassword] = useState(DEV_PASSWORD);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    const { error: signInError } = await supabase.auth.signInWithPassword({
      email: email.trim().toLowerCase(),
      password,
    });
    if (signInError) setError(errorMessage(signInError));
    setBusy(false);
  };

  return (
    <main className="center-screen">
      <form className="panel narrow" onSubmit={submit}>
        <div className="panel-header">
          <h1 className="brand">KitchenSync 360</h1>
          <p className="muted small">
            Expeditor web dashboard. Sign in with the controller account to create and cancel orders.
          </p>
        </div>

        {DEV_TOOLS && (
          <div className="field">
            <span>Preset Account</span>
            <div className="preset-accounts">
              {DEMO_ACCOUNTS.map((acct) => (
                <button
                  key={acct.email}
                  type="button"
                  className={`table-pill${email === acct.email ? ' active' : ''}`}
                  onClick={() => {
                    setEmail(acct.email);
                    setError(null);
                  }}
                >
                  {acct.label}
                </button>
              ))}
            </div>
          </div>
        )}

        <label className="field">
          <span>Email</span>
          <input
            type="email"
            autoComplete="username"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
          />
        </label>

        <label className="field">
          <span>Password</span>
          <input
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        </label>

        {error && <p className="error-text small">Sign-In Failed: {error}</p>}

        <button className="btn primary full" type="submit" disabled={busy}>
          {busy ? 'Signing In…' : 'Sign In'}
        </button>
      </form>
    </main>
  );
}
