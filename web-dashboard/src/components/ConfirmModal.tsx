import { useEffect, type ReactNode } from 'react';

interface Props {
  title: string;
  subtitle?: string;
  confirmLabel: string;
  cancelLabel?: string;
  tone?: 'danger' | 'primary';
  busy?: boolean;
  error?: string | null;
  onCancel: () => void;
  onConfirm: () => void;
  children: ReactNode;
}

export function ConfirmModal({
  title,
  subtitle,
  confirmLabel,
  cancelLabel = 'Cancel',
  tone = 'primary',
  busy = false,
  error = null,
  onCancel,
  onConfirm,
  children,
}: Props) {
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !busy) onCancel();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [busy, onCancel]);

  return (
    <div className="modal-backdrop" onMouseDown={() => !busy && onCancel()}>
      <div
        className="modal modal-sm"
        role="dialog"
        aria-modal="true"
        aria-labelledby="confirm-modal-title"
        onMouseDown={(e) => e.stopPropagation()}
      >
        <header className="modal-header">
          <div>
            <h2 id="confirm-modal-title">{title}</h2>
            {subtitle && <p className="muted small">{subtitle}</p>}
          </div>
          <button
            type="button"
            className="modal-close"
            onClick={onCancel}
            disabled={busy}
            aria-label="Close"
          >
            ✕
          </button>
        </header>

        <div className="confirm-body">{children}</div>

        {error && <p className="error-text small">{error}</p>}

        <footer className="modal-footer modal-footer-confirm">
          <div className="modal-actions modal-actions-grid">
            <button type="button" className="btn" onClick={onCancel} disabled={busy}>
              {cancelLabel}
            </button>
            <button
              type="button"
              className={`btn ${tone === 'danger' ? 'danger' : 'primary'}`}
              onClick={onConfirm}
              disabled={busy}
            >
              {confirmLabel}
            </button>
          </div>
        </footer>
      </div>
    </div>
  );
}
