import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { CheckCircle2, AlertTriangle, Bell, Info, X } from 'lucide-react';

type ToastVariant = 'success' | 'error' | 'info' | 'notification';

interface Toast {
  id: number;
  variant: ToastVariant;
  message: string;
  title?: string;
  onClick?: () => void;
}

/** A notification that has just arrived, shown with its title and opened on click. */
export interface NotificationToast {
  title: string;
  message: string;
  onClick?: () => void;
}

interface ToastApi {
  success: (message: string) => void;
  error: (message: string) => void;
  info: (message: string) => void;
  notify: (notification: NotificationToast) => void;
}

const ToastContext = createContext<ToastApi | null>(null);

/**
 * Module-level handle on the live toast API.
 *
 * The QueryClient is constructed outside React and so cannot use a hook, but it
 * is the right place to catch *every* mutation failure in one go. The provider
 * registers itself here on mount; before that, and after unmount, calls no-op.
 */
let externalApi: ToastApi | null = null;

export const toast: ToastApi = {
  success: (message) => externalApi?.success(message),
  error: (message) => externalApi?.error(message),
  info: (message) => externalApi?.info(message),
  notify: (notification) => externalApi?.notify(notification),
};

/**
 * Feedback for actions whose result is otherwise invisible.
 *
 * Mutations previously succeeded silently — a modal closed and nothing said
 * whether the save landed — and failed into a fixed line of red text inside the
 * form, which is easy to miss and gone once the form unmounts.
 */
export function ToastProvider({ children }: { children: React.ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const dismiss = useCallback((id: number) => {
    setToasts((current) => current.filter((t) => t.id !== id));
  }, []);

  const push = useCallback(
    (variant: ToastVariant, message: string, extra: Pick<Toast, 'title' | 'onClick'> = {}) => {
      const id = Date.now() + Math.random();
      // One outage fails many requests at once (a query per semester, say);
      // show the message once rather than stacking identical toasts.
      setToasts((current) =>
        current.some((t) => t.variant === variant && t.message === message && t.title === extra.title)
          ? current
          : [...current, { id, variant, message, ...extra }],
      );
      // Errors and notifications linger — the user may need to read and act on them.
      setTimeout(() => dismiss(id), variant === 'error' || variant === 'notification' ? 8000 : 4000);
    },
    [dismiss],
  );

  const api = useMemo<ToastApi>(
    () => ({
      success: (message) => push('success', message),
      error: (message) => push('error', message),
      info: (message) => push('info', message),
      notify: ({ title, message, onClick }) => push('notification', message, { title, onClick }),
    }),
    [push],
  );

  useEffect(() => {
    externalApi = api;
    return () => {
      externalApi = null;
    };
  }, [api]);

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div
        aria-live="polite"
        aria-atomic="false"
        className="pointer-events-none fixed inset-x-0 bottom-0 z-[100] flex flex-col items-center gap-2 p-4 sm:inset-x-auto sm:right-0 sm:items-end"
      >
        {toasts.map((toast) => (
          <ToastCard key={toast.id} toast={toast} onDismiss={() => dismiss(toast.id)} />
        ))}
      </div>
    </ToastContext.Provider>
  );
}

const VARIANT_STYLES: Record<ToastVariant, { wrap: string; icon: React.ReactNode }> = {
  success: {
    wrap: 'border-green-200 bg-green-50 text-green-800',
    icon: <CheckCircle2 size={16} className="text-green-600" />,
  },
  error: {
    wrap: 'border-red-200 bg-red-50 text-red-800',
    icon: <AlertTriangle size={16} className="text-red-600" />,
  },
  info: {
    wrap: 'border-gray-200 bg-white text-gray-800',
    icon: <Info size={16} className="text-gray-500" />,
  },
  notification: {
    wrap: 'border-primary-200 bg-white text-gray-800',
    icon: <Bell size={16} className="text-primary-600" />,
  },
};

function ToastCard({ toast, onDismiss }: { toast: Toast; onDismiss: () => void }) {
  const style = VARIANT_STYLES[toast.variant];
  return (
    <div
      role={toast.variant === 'error' ? 'alert' : 'status'}
      className={`pointer-events-auto flex w-full max-w-sm items-start gap-2.5 rounded-xl border p-3 text-sm shadow-lg animate-slide-up ${style.wrap}`}
    >
      <span className="mt-0.5 flex-shrink-0">{style.icon}</span>
      {toast.title ? (
        <button
          type="button"
          onClick={() => {
            toast.onClick?.();
            onDismiss();
          }}
          className="min-w-0 flex-1 text-left"
        >
          <p className="break-words font-semibold text-gray-900">{toast.title}</p>
          <p className="mt-0.5 line-clamp-3 break-words text-gray-600">{toast.message}</p>
        </button>
      ) : (
        <p className="min-w-0 flex-1 break-words">{toast.message}</p>
      )}
      <button
        type="button"
        onClick={onDismiss}
        aria-label="Dismiss"
        className="-m-1 flex-shrink-0 rounded p-1 opacity-60 transition-opacity hover:opacity-100"
      >
        <X size={14} />
      </button>
    </div>
  );
}

export function useToast(): ToastApi {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error('useToast must be used inside a ToastProvider');
  }
  return context;
}
