import { Component, type ErrorInfo, type ReactNode } from 'react';
import { AlertTriangle, RotateCcw } from 'lucide-react';

interface Props {
  children: ReactNode;
}

interface State {
  error: Error | null;
}

/**
 * Catches render-time errors so one broken component does not blank the app.
 *
 * Without a boundary, React unmounts the entire tree on an uncaught render
 * error and the user is left staring at a white page with no indication that
 * anything happened.
 *
 * Errors inside routed pages never reach this: React Router catches them first
 * and renders `RouteError`. This covers everything outside the router.
 */
export default class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('Unhandled render error:', error, info.componentStack);
  }

  private reset = () => this.setState({ error: null });

  render() {
    const { error } = this.state;
    if (!error) return this.props.children;

    return <ErrorFallback error={error} onRetry={this.reset} />;
  }
}

/** The failure screen shared by `ErrorBoundary` and `RouteError`. */
export function ErrorFallback({
  error,
  title = 'Something went wrong',
  message = 'This part of the page failed to load. You can try again, or reload if the problem persists.',
  onRetry,
}: {
  error?: unknown;
  title?: string;
  message?: string;
  onRetry?: () => void;
}) {
  return (
    <div className="flex min-h-[60vh] flex-col items-center justify-center px-4 py-16 text-center">
      <div className="mb-4 flex h-14 w-14 items-center justify-center rounded-full bg-red-100">
        <AlertTriangle className="h-7 w-7 text-red-600" />
      </div>
      <h1 className="text-xl font-semibold text-gray-900">{title}</h1>
      <p className="mt-2 max-w-md text-sm text-gray-500">{message}</p>
      {import.meta.env.DEV && error instanceof Error && (
        <pre className="mt-4 max-w-xl overflow-x-auto rounded-lg bg-gray-100 p-3 text-left text-xs text-gray-700">
          {error.message}
        </pre>
      )}
      <div className="mt-6 flex flex-wrap justify-center gap-2">
        {onRetry && (
          <button
            onClick={onRetry}
            className="inline-flex items-center gap-2 rounded-lg bg-primary-600 px-4 py-2.5 text-sm font-medium text-white transition-colors hover:bg-primary-700"
          >
            <RotateCcw size={16} />
            Try again
          </button>
        )}
        <button
          onClick={() => window.location.reload()}
          className="rounded-lg border border-gray-300 px-4 py-2.5 text-sm font-medium text-gray-700 transition-colors hover:bg-gray-50"
        >
          Reload page
        </button>
      </div>
    </div>
  );
}
