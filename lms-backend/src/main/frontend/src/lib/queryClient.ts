import { QueryClient, MutationCache } from '@tanstack/react-query';
import { parseApiError, statusOf } from '@/lib/apiError';
import { toast } from '@/components/ui/Toast';

const queryClient = new QueryClient({
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
