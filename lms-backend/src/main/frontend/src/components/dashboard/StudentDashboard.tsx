import { useQueries, useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import {
  BookOpen,
  ClipboardCheck,
  FileText,
  Bell,
  GraduationCap,
  ChevronRight,
} from 'lucide-react';
import api from '@/lib/api';
import { useAuthStore } from '@/store/authStore';
import Card, { CardHeader } from '@/components/ui/Card';
import Badge from '@/components/ui/Badge';
import Spinner from '@/components/ui/Spinner';
import type {
  EnrollmentResponse,
  OfferingSummaryResponse,
  TranscriptSummaryResponse,
  NotificationResponse,
  NotificationPage,
} from '@/types/api';

/** Coloured attendance percentage pill */
function AttendancePill({ pct }: { pct: number }) {
  let cls = 'bg-green-100 text-green-700';
  if (pct < 75) cls = 'bg-red-100 text-red-700';
  else if (pct < 85) cls = 'bg-amber-100 text-amber-700';
  return (
    <span className={`rounded-full px-2 py-0.5 text-xs font-semibold ${cls}`}>
      {pct.toFixed(0)}%
    </span>
  );
}

interface AttendanceSummaryResponse {
  presentCount: number;
  absentCount: number;
  lateCount: number;
  totalSessions: number;
  attendancePercentage: number;
}

export default function StudentDashboard() {
  const user = useAuthStore((s) => s.user);

  // 1. Enrolled courses
  const enrollmentsQ = useQuery({
    queryKey: ['me', 'enrollments', 'active'],
    queryFn: () =>
      api
        .get<EnrollmentResponse[]>('/me/enrollments?status=ENROLLED')
        .then((r) => r.data),
  });
  const enrollments: EnrollmentResponse[] = enrollmentsQ.data ?? [];

  // 2. Offering details for each enrollment (parallel)
  const offeringQueries = useQueries({
    queries: enrollments.map((e) => ({
      queryKey: ['offerings', e.pscId],
      queryFn: () =>
        api.get<OfferingSummaryResponse>(`/offerings/${e.pscId}`).then((r) => r.data),
      enabled: enrollments.length > 0,
    })),
  });

  // 3. Attendance summary for each offering (parallel)
  const attendanceQueries = useQueries({
    queries: enrollments.map((e) => ({
      queryKey: ['me', 'attendance', e.pscId],
      queryFn: () =>
        api
          .get<AttendanceSummaryResponse>(`/me/attendance/${e.pscId}`)
          .then((r) => r.data),
      enabled: enrollments.length > 0,
      retry: false, // OK if no sessions exist yet
    })),
  });

  // 4. My transcripts
  const transcriptsQ = useQuery({
    queryKey: ['me', 'transcripts'],
    queryFn: () =>
      api.get<TranscriptSummaryResponse[]>('/me/transcripts').then((r) => r.data),
  });
  const transcripts: TranscriptSummaryResponse[] = transcriptsQ.data ?? [];
  const latestTranscript = transcripts[0] ?? null;

  // 5. Recent notifications
  const notificationsQ = useQuery({
    queryKey: ['notifications', 'recent'],
    queryFn: () =>
      api.get<NotificationPage>('/notifications?size=5').then((r) => r.data),
  });
  const recentNotifications: NotificationResponse[] =
    notificationsQ.data?.content ?? [];

  const loadingEnrollments =
    enrollmentsQ.isLoading || offeringQueries.some((q) => q.isLoading);

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold text-gray-900">My Dashboard</h1>
        <p className="mt-1 text-sm text-gray-500">Welcome back, {user?.name}</p>
      </div>

      {/* Stat cards */}
      <div className="grid gap-4 sm:grid-cols-3">
        <div className="animate-slide-up animation-delay-75">
          <Card>
            <div className="flex items-center gap-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-indigo-100 text-indigo-600">
                <BookOpen size={20} />
              </div>
              <div>
                <p className="text-2xl font-bold text-gray-900">
                  {enrollmentsQ.isLoading ? <Spinner size="sm" /> : enrollments.length}
                </p>
                <p className="text-sm text-gray-500">Enrolled Courses</p>
              </div>
            </div>
          </Card>
        </div>

        <div className="animate-slide-up animation-delay-150">
          <Card>
            <div className="flex items-center gap-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-green-100 text-green-600">
                <GraduationCap size={20} />
              </div>
              <div>
                {transcriptsQ.isLoading ? (
                  <Spinner size="sm" />
                ) : latestTranscript ? (
                  <p className="text-2xl font-bold text-gray-900">
                    {latestTranscript.cgpa.toFixed(2)}
                  </p>
                ) : (
                  <p className="text-2xl font-bold text-gray-400">—</p>
                )}
                <p className="text-sm text-gray-500">CGPA</p>
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
                  {recentNotifications.filter((n) => !n.isRead).length}
                </p>
                <p className="text-sm text-gray-500">Unread Alerts</p>
              </div>
            </div>
          </Card>
        </div>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        {/* Enrolled courses with attendance */}
        <Card padding={false}>
          <div className="flex items-center justify-between border-b border-gray-100 px-5 py-4">
            <h3 className="font-semibold text-gray-900">My Courses</h3>
            <Link
              to="/courses"
              className="flex items-center gap-0.5 text-xs font-medium text-primary-600 hover:text-primary-700"
            >
              View all
              <ChevronRight size={13} />
            </Link>
          </div>

          {loadingEnrollments ? (
            <div className="flex justify-center py-8">
              <Spinner />
            </div>
          ) : enrollments.length === 0 ? (
            <div className="px-5 py-8 text-center">
              <BookOpen size={32} className="mx-auto mb-2 text-gray-300" />
              <p className="text-sm text-gray-400">You are not enrolled in any courses.</p>
            </div>
          ) : (
            <ul className="divide-y divide-gray-50">
              {enrollments.map((enr, idx) => {
                const offering = offeringQueries[idx]?.data;
                const attendance = attendanceQueries[idx]?.data;
                return (
                  <li key={enr.id} className="px-5 py-3 animate-slide-up" style={{ animationDelay: `${idx * 60}ms` }}>
                    <div className="flex items-start justify-between gap-2">
                      <div className="min-w-0">
                        {offering ? (
                          <>
                            <p className="text-sm font-medium text-gray-800">
                              {offering.courseCode} — {offering.courseName}
                            </p>
                            <p className="mt-0.5 text-xs text-gray-400">
                              {offering.creditHours} credit hrs
                            </p>
                          </>
                        ) : (
                          <p className="text-sm text-gray-400">Loading…</p>
                        )}
                      </div>
                      <div className="flex shrink-0 items-center gap-2">
                        {attendance ? (
                          <AttendancePill pct={attendance.attendancePercentage} />
                        ) : (
                          <Badge variant="default">—</Badge>
                        )}
                      </div>
                    </div>
                  </li>
                );
              })}
            </ul>
          )}
        </Card>

        {/* Right column: transcript + notifications */}
        <div className="space-y-4">
          {/* Latest transcript */}
          <Card padding={false}>
            <div className="flex items-center justify-between border-b border-gray-100 px-5 py-4">
              <h3 className="font-semibold text-gray-900">Latest Transcript</h3>
              <Link
                to="/transcripts"
                className="flex items-center gap-0.5 text-xs font-medium text-primary-600 hover:text-primary-700"
              >
                All transcripts
                <ChevronRight size={13} />
              </Link>
            </div>

            {transcriptsQ.isLoading ? (
              <div className="flex justify-center py-6">
                <Spinner />
              </div>
            ) : !latestTranscript ? (
              <div className="px-5 py-6 text-center">
                <GraduationCap size={28} className="mx-auto mb-1 text-gray-300" />
                <p className="text-sm text-gray-400">No transcripts generated yet.</p>
              </div>
            ) : (
              <div className="grid grid-cols-2 gap-px bg-gray-100">
                {[
                  { label: 'CGPA', value: latestTranscript.cgpa.toFixed(2) },
                  { label: 'SGPA', value: latestTranscript.sgpa.toFixed(2) },
                  { label: 'Semester', value: latestTranscript.semesterName },
                  {
                    label: 'Credit Hrs',
                    value: latestTranscript.totalCreditHours,
                  },
                ].map(({ label, value }) => (
                  <div key={label} className="bg-white px-4 py-3">
                    <p className="text-xs text-gray-500">{label}</p>
                    <p className="mt-0.5 text-sm font-semibold text-gray-800">{value}</p>
                  </div>
                ))}
              </div>
            )}
          </Card>

          {/* Recent notifications */}
          <Card padding={false}>
            <div className="flex items-center justify-between border-b border-gray-100 px-5 py-4">
              <h3 className="font-semibold text-gray-900">Notifications</h3>
              <Link
                to="/notifications"
                className="flex items-center gap-0.5 text-xs font-medium text-primary-600 hover:text-primary-700"
              >
                View all
                <ChevronRight size={13} />
              </Link>
            </div>
            {notificationsQ.isLoading ? (
              <div className="flex justify-center py-6">
                <Spinner />
              </div>
            ) : recentNotifications.length === 0 ? (
              <p className="px-5 py-4 text-sm text-gray-400">No notifications.</p>
            ) : (
              <ul className="divide-y divide-gray-50">
                {recentNotifications.slice(0, 4).map((n, idx) => (
                  <li key={n.id} className="flex items-start gap-3 px-5 py-2.5 animate-slide-up" style={{ animationDelay: `${idx * 60}ms` }}>
                    <span
                      className={`mt-1.5 h-2 w-2 shrink-0 rounded-full ${
                        n.isRead ? 'bg-gray-200' : 'bg-primary-500'
                      }`}
                    />
                    <div className="min-w-0">
                      <p className="truncate text-xs font-medium text-gray-800">{n.title}</p>
                      <p className="line-clamp-1 text-xs text-gray-400">{n.body}</p>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </Card>
        </div>
      </div>

      {/* Quick actions */}
      <Card>
        <CardHeader title="Quick Actions" />
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          {[
            { label: 'My Courses', to: '/courses', icon: BookOpen, color: 'bg-indigo-50 text-indigo-600' },
            { label: 'Attendance', to: '/attendance', icon: ClipboardCheck, color: 'bg-green-50 text-green-600' },
            { label: 'Assessment', to: '/assessment', icon: FileText, color: 'bg-blue-50 text-blue-600' },
            { label: 'Transcripts', to: '/transcripts', icon: GraduationCap, color: 'bg-purple-50 text-purple-600' },
          ].map(({ label, to, icon: Icon, color }) => (
            <Link
              key={to}
              to={to}
              className="flex flex-col items-center gap-2 rounded-xl border border-gray-100 p-4 text-center transition-colors hover:border-gray-200 hover:bg-gray-50"
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
