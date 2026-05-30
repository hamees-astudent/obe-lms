import { useQueries, useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { BookOpen, ClipboardCheck, FileText, Bell, ChevronRight, Users } from 'lucide-react';
import api from '@/lib/api';
import { useAuthStore } from '@/store/authStore';
import Card, { CardHeader } from '@/components/ui/Card';
import Badge from '@/components/ui/Badge';
import Spinner from '@/components/ui/Spinner';
import type {
  ProgramSummaryResponse,
  SemesterResponse,
  OfferingSummaryResponse,
  NotificationResponse,
  NotificationPage,
  Page,
} from '@/types/api';

function SemesterBadge({ status }: { status: string }) {
  if (status === 'ACTIVE') return <Badge variant="success">Active</Badge>;
  if (status === 'UPCOMING') return <Badge variant="info">Upcoming</Badge>;
  return <Badge variant="default">Completed</Badge>;
}

export default function TeacherDashboard() {
  const user = useAuthStore((s) => s.user);
  const isAssistant = user?.role === 'ASSISTANT';

  // 1. Load all active programs
  const programsQ = useQuery({
    queryKey: ['programs', 'active'],
    queryFn: () =>
      api.get<Page<ProgramSummaryResponse>>('/programs?status=ACTIVE&size=100').then((r) => r.data),
  });
  const activePrograms = programsQ.data?.content ?? [];

  // 2. Load active semesters for each program (parallel)
  const semesterQueries = useQueries({
    queries: activePrograms.map((p) => ({
      queryKey: ['programs', p.id, 'semesters', 'active'],
      queryFn: () =>
        api
          .get<SemesterResponse[]>(`/programs/${p.id}/semesters?status=ACTIVE`)
          .then((r) => r.data),
      enabled: activePrograms.length > 0,
    })),
  });
  const activeSemesters = semesterQueries.flatMap((q) => q.data ?? []);

  // 3. Load offerings for each active semester (parallel)
  const offeringQueries = useQueries({
    queries: activeSemesters.map((s) => ({
      queryKey: ['semesters', s.id, 'offerings'],
      queryFn: () =>
        api
          .get<OfferingSummaryResponse[]>(`/semesters/${s.id}/offerings`)
          .then((r) => r.data),
      enabled: activeSemesters.length > 0,
    })),
  });
  const allOfferings = offeringQueries.flatMap((q) => q.data ?? []);

  // 4. Filter to this teacher's offerings
  const myOfferings = allOfferings.filter((o) => o.teacherId === user?.id);

  // 5. Recent notifications
  const notificationsQ = useQuery({
    queryKey: ['notifications', 'recent'],
    queryFn: () =>
      api.get<NotificationPage>('/notifications?size=5').then((r) => r.data),
  });
  const recentNotifications: NotificationResponse[] =
    notificationsQ.data?.content ?? [];

  const loadingOfferings =
    programsQ.isLoading ||
    semesterQueries.some((q) => q.isLoading) ||
    offeringQueries.some((q) => q.isLoading);

  // Enrich offering with semester info
  function semesterForOffering(o: OfferingSummaryResponse): SemesterResponse | undefined {
    return activeSemesters.find((s) => s.id === o.semesterId);
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold text-gray-900">
          {isAssistant ? 'Assistant' : 'Teacher'} Dashboard
        </h1>
        <p className="mt-1 text-sm text-gray-500">
          Welcome back, {user?.name}
        </p>
      </div>

      {/* Stats row */}
      <div className="grid gap-4 sm:grid-cols-3">
        <div className="animate-slide-up animation-delay-75">
          <Card>
            <div className="flex items-center gap-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-indigo-100 text-indigo-600">
                <BookOpen size={20} />
              </div>
              <div>
                <p className="text-2xl font-bold text-gray-900">
                  {loadingOfferings ? <Spinner size="sm" /> : myOfferings.length}
                </p>
                <p className="text-sm text-gray-500">Active Courses</p>
              </div>
            </div>
          </Card>
        </div>
        <div className="animate-slide-up animation-delay-150">
          <Card>
            <div className="flex items-center gap-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-green-100 text-green-600">
                <ClipboardCheck size={20} />
              </div>
              <div>
                <p className="text-2xl font-bold text-gray-900">{activeSemesters.length}</p>
                <p className="text-sm text-gray-500">Active Semesters</p>
              </div>
            </div>
          </Card>
        </div>
        <div className="animate-slide-up animation-delay-300">
          <Card>
            <div className="flex items-center gap-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-orange-100 text-orange-600">
                <Bell size={20} />
              </div>
              <div>
                <p className="text-2xl font-bold text-gray-900">
                  {notificationsQ.isLoading ? <Spinner size="sm" /> : recentNotifications.filter((n) => !n.isRead).length}
                </p>
              <p className="text-sm text-gray-500">Unread Alerts</p>
            </div>
          </div>
        </Card>
        </div>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        {/* My courses */}
        <Card padding={false}>
          <div className="flex items-center justify-between border-b border-gray-100 px-5 py-4">
            <h3 className="font-semibold text-gray-900">My Courses</h3>
            <Link
              to="/courses"
              className="flex items-center gap-0.5 text-xs font-medium text-primary-600 hover:text-primary-700"
            >
              All courses
              <ChevronRight size={13} />
            </Link>
          </div>

          {loadingOfferings ? (
            <div className="flex justify-center py-8">
              <Spinner />
            </div>
          ) : myOfferings.length === 0 ? (
            <div className="px-5 py-8 text-center">
              <BookOpen size={32} className="mx-auto mb-2 text-gray-300" />
              <p className="text-sm text-gray-400">No active course assignments.</p>
            </div>
          ) : (
            <ul className="divide-y divide-gray-50">
              {myOfferings.map((offering, idx) => {
                const sem = semesterForOffering(offering);
                return (
                  <li key={offering.id} className="px-5 py-3 animate-slide-up" style={{ animationDelay: `${idx * 60}ms` }}>
                    <div className="flex items-start justify-between gap-2">
                      <div className="min-w-0">
                        <p className="text-sm font-medium text-gray-800">
                          {offering.courseCode} — {offering.courseName}
                        </p>
                        <p className="mt-0.5 text-xs text-gray-400">
                          {sem?.programName} · {sem?.name}
                        </p>
                      </div>
                      <div className="flex shrink-0 items-center gap-2">
                        {sem && <SemesterBadge status={sem.status} />}
                        <span className="text-xs text-gray-400">{offering.creditHours} cr</span>
                      </div>
                    </div>
                    <div className="mt-2 flex gap-2">
                      <Link
                        to={`/attendance?pscId=${offering.id}`}
                        className="flex items-center gap-1 rounded-md bg-green-50 px-2 py-1 text-xs font-medium text-green-700 hover:bg-green-100"
                      >
                        <ClipboardCheck size={11} />
                        Attendance
                      </Link>
                      <Link
                        to={`/assessment?pscId=${offering.id}`}
                        className="flex items-center gap-1 rounded-md bg-blue-50 px-2 py-1 text-xs font-medium text-blue-700 hover:bg-blue-100"
                      >
                        <FileText size={11} />
                        Assessment
                      </Link>
                      <Link
                        to={`/courses?pscId=${offering.id}`}
                        className="flex items-center gap-1 rounded-md bg-gray-100 px-2 py-1 text-xs font-medium text-gray-600 hover:bg-gray-200"
                      >
                        <Users size={11} />
                        Roster
                      </Link>
                    </div>
                  </li>
                );
              })}
            </ul>
          )}
        </Card>

        {/* Recent notifications */}
        <Card padding={false}>
          <div className="flex items-center justify-between border-b border-gray-100 px-5 py-4">
            <h3 className="font-semibold text-gray-900">Recent Notifications</h3>
            <Link
              to="/notifications"
              className="flex items-center gap-0.5 text-xs font-medium text-primary-600 hover:text-primary-700"
            >
              View all
              <ChevronRight size={13} />
            </Link>
          </div>

          {notificationsQ.isLoading ? (
            <div className="flex justify-center py-8">
              <Spinner />
            </div>
          ) : recentNotifications.length === 0 ? (
            <div className="px-5 py-8 text-center">
              <Bell size={32} className="mx-auto mb-2 text-gray-300" />
              <p className="text-sm text-gray-400">No notifications yet.</p>
            </div>
          ) : (
            <ul className="divide-y divide-gray-50">
              {recentNotifications.map((n, idx) => (
                <li key={n.id} className="flex items-start gap-3 px-5 py-3 animate-slide-up" style={{ animationDelay: `${idx * 60}ms` }}>
                  <span
                    className={`mt-1.5 h-2 w-2 shrink-0 rounded-full ${
                      n.isRead ? 'bg-gray-200' : 'bg-primary-500'
                    }`}
                  />
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium text-gray-800">{n.title}</p>
                    <p className="mt-0.5 line-clamp-1 text-xs text-gray-400">{n.body}</p>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>

      {/* Quick actions */}
      <Card>
        <CardHeader title="Quick Actions" />
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          {[
            { label: 'Mark Attendance', to: '/attendance', icon: ClipboardCheck, color: 'bg-green-50 text-green-600' },
            { label: 'Assignments', to: '/assessment', icon: FileText, color: 'bg-blue-50 text-blue-600' },
            { label: 'My Courses', to: '/courses', icon: BookOpen, color: 'bg-indigo-50 text-indigo-600' },
            { label: 'Notifications', to: '/notifications', icon: Bell, color: 'bg-orange-50 text-orange-600' },
          ].map(({ label, to, icon: Icon, color }) => (
            <Link
              key={to}
              to={to}
              className="flex flex-col items-center gap-2 rounded-xl border border-gray-100 p-4 text-center transition-all duration-150 hover:border-gray-200 hover:bg-gray-50 hover:-translate-y-0.5 hover:shadow-sm active:scale-95"
            >
              <div className={`flex h-10 w-10 items-center justify-center rounded-full ${color}`}>
                <Icon size={20} />
              </div>
              <span className="text-xs font-medium text-gray-700">{label}</span>
            </Link>
          ))}
        </div>
      </Card>
    </div>
  );
}
