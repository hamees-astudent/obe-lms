import { QueryClient, QueryCache, MutationCache } from '@tanstack/react-query';
import { parseApiError, statusOf } from '@/lib/apiError';
import { toast } from '@/components/ui/Toast';

declare module '@tanstack/react-query' {
  interface Register {
    queryMeta: {
      /**
       * The page renders this query's failure itself (usually `<QueryError>`),
       * so the global toast would only repeat it.
       */
      errorShownInline?: boolean;
    };
  }
}

/** What a user is told when data for the page they are on failed to load. */
function loadFailureMessage(error: unknown): string {
  if (statusOf(error) === 403) {
    return 'You do not have permission to view some of the information on this page.';
  }
  return `Some information on this page couldn't be loaded. ${parseApiError(error)}`;
}

const queryClient = new QueryClient({
  // Every failed load reports itself. Most pages don't render a query's error
  // state, so a failure used to look exactly like an empty list ("You are not
  // enrolled in any courses") and the user had no idea anything went wrong.
  queryCache: new QueryCache({
    onError: (error, query) => {
      // See the mutation handler below.
      if (statusOf(error) === 401) return;
      if (query.meta?.errorShownInline) return;
      toast.error(loadFailureMessage(error));
    },
  }),
  // Every failed mutation reports itself. Done centrally rather than at the
  // ~40 call sites, which previously either showed a fixed "Failed to save."
  // or swallowed the error entirely.
  mutationCache: new MutationCache({
    onError: (error) => {
      // 401 is handled by the axios interceptor: it either refreshes the
      // session silently or signs the user out, and both already speak for
      // themselves.
      if (statusOf(error) === 401) return;
      toast.error(parseApiError(error));
    },
  }),
  defaultOptions: {
    queries: {
      staleTime: 60_000,        // 1 minute
      retry: 2,
      refetchOnWindowFocus: false,
    },
    mutations: {
      retry: 0,
    },
  },
});

export default queryClient;
