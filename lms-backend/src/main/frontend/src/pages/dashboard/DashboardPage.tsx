import { lazy, Suspense } from 'react';
import { useAuthStore } from '@/store/authStore';
import Spinner from '@/components/ui/Spinner';

const AdminDashboard   = lazy(() => import('@/components/dashboard/AdminDashboard'));
const TeacherDashboard = lazy(() => import('@/components/dashboard/TeacherDashboard'));
const StudentDashboard = lazy(() => import('@/components/dashboard/StudentDashboard'));

export default function DashboardPage() {
  const user = useAuthStore((s) => s.user);

  function renderDashboard() {
    switch (user?.role) {
      case 'ADMIN':
        return <AdminDashboard />;
      case 'TEACHER':
      case 'ASSISTANT':
        return <TeacherDashboard />;
      case 'STUDENT':
        return <StudentDashboard />;
      default:
        return (
          <div className="text-sm text-gray-500">
            No dashboard configured for role: {user?.role}
          </div>
        );
    }
  }

  return (
    <Suspense
      fallback={
        <div className="flex h-64 items-center justify-center">
          <Spinner size="lg" />
        </div>
      }
    >
      {renderDashboard()}
    </Suspense>
  );
}

