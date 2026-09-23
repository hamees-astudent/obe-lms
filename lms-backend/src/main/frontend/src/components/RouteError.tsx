import { useEffect } from 'react';
import { useRouteError } from 'react-router-dom';
import { ErrorFallback } from '@/components/ErrorBoundary';

/**
 * A lazy page whose chunk no longer exists — the app was redeployed while this
 * tab was open, and the old file names are gone.
 */
function isStaleChunk(error: unknown): boolean {
  return (
    error instanceof Error &&
    /dynamically imported module|Importing a module script failed|error loading dynamically imported/i.test(
      error.message,
    )
  );
}

/**
 * The router's `errorElement`.
 *
 * React Router catches any error thrown while rendering a route and, with no
 * `errorElement` of its own, shows its developer screen — "Unexpected
 * Application Error!" and a stack trace — which is what QA saw when the
 * teacher pages crashed. The app-level `ErrorBoundary` never gets a chance.
 * This replaces that screen with one a user can act on; inside the layout the
 * sidebar stays, so they can also just go elsewhere.
 */
export default function RouteError() {
  const error = useRouteError();

  useEffect(() => {
    console.error('Route error:', error);
  }, [error]);

  if (isStaleChunk(error)) {
    return (
      <ErrorFallback
        error={error}
        title="A new version is available"
        message="The app was updated while this page was open. Reload to continue."
      />
    );
  }

  return (
    <ErrorFallback
      error={error}
      message="This page ran into a problem and couldn't be shown. Reload the page, or use the menu to go somewhere else. If it keeps happening, let support know what you were doing."
    />
  );
}
