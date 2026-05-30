import { Link } from 'react-router-dom';

export default function NotFoundPage() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-4 text-center animate-scale-in">
      <p className="text-6xl font-bold text-primary-600">404</p>
      <h1 className="text-2xl font-semibold text-gray-800">Page not found</h1>
      <p className="text-sm text-gray-500">
        The page you&apos;re looking for doesn&apos;t exist.
      </p>
      <Link
        to="/dashboard"
        className="mt-2 rounded-lg bg-primary-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary-700"
      >
        Back to Dashboard
      </Link>
    </div>
  );
}
