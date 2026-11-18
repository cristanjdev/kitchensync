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
  const [showPassword, setShowPassword] = useState(false);
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
    <main className="center-screen login-screen">
      <form className="login-card" onSubmit={submit}>
        <div className="login-header">
          <div className="login-logo" aria-hidden="true">
            <svg viewBox="0 0 64 64" fill="none" xmlns="http://www.w3.org/2000/svg">
              {/* Outer rounded square frame like ORDIQ icon */}
              <rect
                x="8"
                y="12"
                width="48"
                height="44"
                rx="9"
                stroke="currentColor"
                strokeWidth="3"
              />
              {/* Top kitchen ticket / calendar rings */}
              <path
                d="M22 8V15M32 8V15M42 8V15"
                stroke="currentColor"
                strokeWidth="3"
                strokeLinecap="round"
              />
              {/* Chef toque + sync waves inside */}
              <path
                d="M32 21C27.2 21 23.4 24.5 23.1 29C20.2 29.4 18 31.8 18 34.8C18 37.6 20 39.9 22.7 40.5V46H41.3V40.5C44 39.9 46 37.6 46 34.8C46 31.8 43.8 29.4 40.9 29C40.6 24.5 36.8 21 32 21Z"
                stroke="currentColor"
                strokeWidth="2.4"
                strokeLinejoin="round"
              />
              <path
                d="M27.5 35.5C29.9 33.3 34.1 33.3 36.5 35.5"
                stroke="currentColor"
                strokeWidth="2.2"
                strokeLinecap="round"
              />
              <path
                d="M29.8 38.5C31.1 37.4 32.9 37.4 34.2 38.5"
                stroke="currentColor"
                strokeWidth="2.2"
                strokeLinecap="round"
              />
              <circle cx="32" cy="41.2" r="1.3" fill="currentColor" />
            </svg>
          </div>

          <h1 className="login-title">Sign in to KitchenSync 360</h1>
          <p className="login-subtitle">Expeditor Order Management Portal</p>
        </div>

        {DEV_TOOLS && (
          <div className="login-field">
            <span className="login-label">Preset account</span>
            <div className="login-presets">
              {DEMO_ACCOUNTS.map((acct) => (
                <button
                  key={acct.email}
                  type="button"
                  className={`login-preset-btn${email === acct.email ? ' active' : ''}`}
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

        <label className="login-field">
          <span className="login-label">Username or email</span>
          <div className="login-input-wrap">
            <svg
              className="login-input-icon"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.8"
              strokeLinecap="round"
              strokeLinejoin="round"
              aria-hidden="true"
            >
              <circle cx="12" cy="8" r="4" />
              <path d="M5.5 20a6.5 6.5 0 0 1 13 0" />
            </svg>
            <input
              type="email"
              autoComplete="username"
              placeholder="controller@kitchensync360.demo"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
            />
          </div>
        </label>

        <label className="login-field">
          <span className="login-label">Password</span>
          <div className="login-input-wrap">
            <svg
              className="login-input-icon"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.8"
              strokeLinecap="round"
              strokeLinejoin="round"
              aria-hidden="true"
            >
              <rect x="4" y="11" width="16" height="10" rx="2" />
              <path d="M8 11V7a4 4 0 0 1 8 0v4" />
            </svg>
            <input
              type={showPassword ? 'text' : 'password'}
              autoComplete="current-password"
              placeholder="••••••••••••"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
            <button
              type="button"
              className="login-eye-btn"
              onClick={() => setShowPassword((v) => !v)}
              aria-label={showPassword ? 'Hide password' : 'Show password'}
              tabIndex={-1}
            >
              {showPassword ? (
                <svg
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="1.8"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  aria-hidden="true"
                >
                  <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94" />
                  <path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19" />
                  <line x1="1" y1="1" x2="23" y2="23" />
                </svg>
              ) : (
                <svg
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="1.8"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  aria-hidden="true"
                >
                  <path d="M1 12s4-7 11-7 11 7 11 7-4 7-11 7-11-7-11-7Z" />
                  <circle cx="12" cy="12" r="3" />
                </svg>
              )}
            </button>
          </div>
        </label>

        {error && <p className="login-error">{error}</p>}

        <button className="login-submit-btn" type="submit" disabled={busy}>
          <span>{busy ? 'Signing in…' : 'Sign in'}</span>
          {!busy && (
            <svg
              className="login-submit-arrow"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2.2"
              strokeLinecap="round"
              strokeLinejoin="round"
              aria-hidden="true"
            >
              <line x1="5" y1="12" x2="19" y2="12" />
              <polyline points="13 6 19 12 13 18" />
            </svg>
          )}
        </button>

        <div className="login-footer-note">
          <svg
            className="login-shield-icon"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.8"
            strokeLinecap="round"
            strokeLinejoin="round"
            aria-hidden="true"
          >
            <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
            <polyline points="9 12 11 14 15 10" />
          </svg>
          <span>Authorized Administrative Access Only</span>
        </div>
      </form>
    </main>
  );
}
