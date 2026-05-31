import { lazy, Suspense } from 'react';
import {
  createBrowserRouter,
  Navigate,
  Outlet,
  RouterProvider,
} from 'react-router-dom';
import { useAuthStore } from '@/store/authStore';
import AppLayout from '@/components/layout/AppLayout';
import Spinner from '@/components/ui/Spinner';
import type { Role } from '@/types/api';

// ─── Lazy pages ────────────────────────────────────────────────────────────
const LoginPage           = lazy(() => import('@/pages/auth/LoginPage'));
const ForgotPasswordPage  = lazy(() => import('@/pages/auth/ForgotPasswordPage'));
const ResetPasswordPage   = lazy(() => import('@/pages/auth/ResetPasswordPage'));
const DashboardPage       = lazy(() => import('@/pages/dashboard/DashboardPage'));
const CoursesPage         = lazy(() => import('@/pages/courses/CoursesPage'));
const CourseDetailPage    = lazy(() => import('@/pages/courses/CourseDetailPage'));
const AttendancePage          = lazy(() => import('@/pages/attendance/AttendancePage'));
const CourseAttendancePage    = lazy(() => import('@/pages/attendance/CourseAttendancePage'));
const AssessmentPage          = lazy(() => import('@/pages/assessment/AssessmentPage'));
const CourseAssessmentPage    = lazy(() => import('@/pages/assessment/CourseAssessmentPage'));
const TranscriptsPage     = lazy(() => import('@/pages/transcripts/TranscriptsPage'));
const NotificationsPage   = lazy(() => import('@/pages/notifications/NotificationsPage'));
const UsersPage           = lazy(() => import('@/pages/users/UsersPage'));
const ProgramsPage        = lazy(() => import('@/pages/programs/ProgramsPage'));
const OfferingsPage       = lazy(() => import('@/pages/offerings/OfferingsPage'));
const GradingScalesPage     = lazy(() => import('@/pages/grading/GradingScalesPage'));
const CoursesCatalogPage    = lazy(() => import('@/pages/courses/CoursesCatalogPage'));
const NotFoundPage          = lazy(() => import('@/pages/NotFoundPage'));

// ─── Guards ─────────────────────────────────────────────────────────────────
function RequireAuth() {
  const accessToken = useAuthStore((s) => s.accessToken);
  if (!accessToken) return <Navigate to="/login" replace />;
  return <Outlet />;
}

function RequireRole({ roles }: { roles: Role[] }) {
  const user = useAuthStore((s) => s.user);
  if (!user || !roles.includes(user.role)) {
    return <Navigate to="/dashboard" replace />;
  }
  return <Outlet />;
}

function LazyPage({ children }: { children: React.ReactNode }) {
  return (
    <Suspense
      fallback={
        <div className="flex h-full items-center justify-center">
          <Spinner size="lg" />
        </div>
      }
    >
      {children}
    </Suspense>
  );
}

// ─── Router ──────────────────────────────────────────────────────────────────
const router = createBrowserRouter([
  {
    path: '/login',
    element: (
      <LazyPage>
        <LoginPage />
      </LazyPage>
    ),
  },
  {
    path: '/forgot-password',
    element: (
      <LazyPage>
        <ForgotPasswordPage />
      </LazyPage>
    ),
  },
  {
    path: '/reset-password',
    element: (
      <LazyPage>
        <ResetPasswordPage />
      </LazyPage>
    ),
  },
  {
    element: <RequireAuth />,
    children: [
      {
        element: <AppLayout />,
        children: [
          { index: true, element: <Navigate to="/dashboard" replace /> },
          {
            path: 'dashboard',
            element: (
              <LazyPage>
                <DashboardPage />
              </LazyPage>
            ),
          },
          {
            path: 'courses',
            element: (
              <LazyPage>
                <CoursesPage />
              </LazyPage>
            ),
          },
          {
            path: 'courses/:pscId',
            element: (
              <LazyPage>
                <CourseDetailPage />
              </LazyPage>
            ),
          },
          {
            path: 'attendance',
            element: (
              <LazyPage>
                <AttendancePage />
              </LazyPage>
            ),
          },
          {
            path: 'attendance/:pscId',
            element: (
              <LazyPage>
                <CourseAttendancePage />
              </LazyPage>
            ),
          },
          {
            path: 'assessment',
            element: (
              <LazyPage>
                <AssessmentPage />
              </LazyPage>
            ),
          },
          {
            path: 'assessment/:pscId',
            element: (
              <LazyPage>
                <CourseAssessmentPage />
              </LazyPage>
            ),
          },
          {
            path: 'transcripts',
            element: (
              <LazyPage>
                <TranscriptsPage />
              </LazyPage>
            ),
          },
          {
            path: 'notifications',
            element: (
              <LazyPage>
                <NotificationsPage />
              </LazyPage>
            ),
          },
          // Admin-only
          {
            element: (
              <RequireRole roles={['ADMIN']} />
            ),
            children: [
              {
                path: 'users',
                element: (
                  <LazyPage>
                    <UsersPage />
                  </LazyPage>
                ),
              },
              {
                path: 'programs',
                element: (
                  <LazyPage>
                    <ProgramsPage />
                  </LazyPage>
                ),
              },
              {
                path: 'offerings',
                element: (
                  <LazyPage>
                    <OfferingsPage />
                  </LazyPage>
                ),
              },
              {
                path: 'grading-scales',
                element: (
                  <LazyPage>
                    <GradingScalesPage />
                  </LazyPage>
                ),
              },
              {
                path: 'course-catalog',
                element: (
                  <LazyPage>
                    <CoursesCatalogPage />
                  </LazyPage>
                ),
              },
            ],
          },
        ],
      },
    ],
  },
  {
    path: '*',
    element: (
      <LazyPage>
        <NotFoundPage />
      </LazyPage>
    ),
  },
]);

export function AppRouter() {
  return <RouterProvider router={router} />;
}
