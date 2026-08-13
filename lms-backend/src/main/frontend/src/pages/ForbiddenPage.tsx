import { Link } from 'react-router-dom';
import { ShieldAlert, ArrowLeft } from 'lucide-react';

/**
 * Shown when an authenticated user opens a page their role does not cover.
 *
 * The router used to answer this by silently redirecting to the dashboard,
 * which reads as a broken link — the user clicks something, lands somewhere
 * else, and has no idea why.
 */
export default function ForbiddenPage() {
  return (
    <div className="flex flex-col items-center justify-center px-4 py-20 text-center">
      <div className="mb-4 flex h-14 w-14 items-center justify-center rounded-full bg-amber-100">
        <ShieldAlert className="h-7 w-7 text-amber-600" />
      </div>
      <h1 className="text-xl font-semibold text-gray-900">You don&apos;t have access to this page</h1>
      <p className="mt-2 max-w-md text-sm text-gray-500">
        This section is restricted to a different role. If you think you should have
        access, ask an administrator to review your account permissions.
      </p>
      <Link
        to="/dashboard"
        className="mt-6 inline-flex items-center gap-2 rounded-lg bg-primary-600 px-4 py-2.5 text-sm font-medium text-white transition-colors hover:bg-primary-700"
      >
        <ArrowLeft size={16} />
        Back to dashboard
      </Link>
    </div>
  );
}
