import { AlertTriangle, RotateCcw } from 'lucide-react';
import { parseApiError, statusOf } from '@/lib/apiError';

/**
 * The failure state for a query.
 *
 * Several lists used `retry: false` and then rendered nothing when the request
 * failed, so "the server rejected this" looked exactly like "there is nothing
 * here". 403 and 404 are called out separately — "you can't see this" and "this
 * doesn't exist" need different reactions from the user.
 */
export default function QueryError({
  error,
  onRetry,
  compact,
}: {
  error: unknown;
  onRetry?: () => void;
  compact?: boolean;
}) {
  const status = statusOf(error);
  const message =
    status === 403
      ? 'You do not have permission to view this.'
      : status === 404
        ? 'This item no longer exists.'
        : parseApiError(error, "Couldn't load this.");

  // Retrying a 403/404 will fail identically, so don't offer it.
  const retryable = onRetry && status !== 403 && status !== 404;

  if (compact) {
    return (
      <p className="flex items-center gap-1.5 text-xs text-red-600">
        <AlertTriangle size={12} className="flex-shrink-0" />
        {message}
        {retryable && (
          <button onClick={onRetry} className="underline hover:no-underline">
            Retry
          </button>
        )}
      </p>
    );
  }

  return (
    <div className="flex flex-col items-center gap-3 rounded-xl border border-red-100 bg-red-50/50 px-4 py-10 text-center">
      <AlertTriangle className="h-6 w-6 text-red-500" />
      <p className="max-w-md text-sm text-red-700">{message}</p>
      {retryable && (
        <button
          onClick={onRetry}
          className="inline-flex items-center gap-1.5 rounded-lg border border-red-200 bg-white px-3 py-2 text-sm font-medium text-red-700 transition-colors hover:bg-red-50"
        >
          <RotateCcw size={14} />
          Try again
        </button>
      )}
    </div>
  );
}
