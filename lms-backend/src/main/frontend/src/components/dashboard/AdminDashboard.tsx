import { useQueries } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { Users, BookOpen, GraduationCap, Plus, Settings, ChevronRight, type LucideIcon } from 'lucide-react';
import api from '@/lib/api';
import Card, { CardHeader } from '@/components/ui/Card';
import Badge from '@/components/ui/Badge';
import Spinner from '@/components/ui/Spinner';
import type { Page, UserSummaryResponse, ProgramSummaryResponse, SemesterResponse } from '@/types/api';

interface StatCardProps {
  label: string;
  value: number | string;
  icon: LucideIcon;
  color: string;
  loading?: boolean;
}

function StatCard({ label, value, icon: Icon, color, loading }: StatCardProps) {
  return (
    <Card>
      <div className="flex items-center gap-4">
        <div className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-xl ${color}`}>
          <Icon size={22} />
        </div>
        <div>
          {loading ? (
            <Spinner size="sm" />
          ) : (
            <p className="text-2xl font-bold text-gray-900">{value}</p>
          )}
          <p className="text-sm text-gray-500">{label}</p>
        </div>
      </div>
    </Card>
  );
}

export default function AdminDashboard() {
  const results = useQueries({
    queries: [
      {
        queryKey: ['admin', 'users', 'count'],
        queryFn: () =>
          api.get<Page<UserSummaryResponse>>('/admin/users?size=1').then((r) => r.data),
      },
      {
        queryKey: ['programs', 'all'],
        queryFn: () =>
          api.get<Page<ProgramSummaryResponse>>('/programs?size=100').then((r) => r.data),
      },
      {
        queryKey: ['admin', 'users', 'recent'],
        queryFn: () =>
          api
            .get<Page<UserSummaryResponse>>('/admin/users?size=8&sort=createdAt,desc')
            .then((r) => r.data),
      },
    ],
  });

  const [usersCountQ, programsQ, recentUsersQ] = results;
  const loading = results.some((r) => r.isLoading);

  const programs: ProgramSummaryResponse[] = programsQ.data?.content ?? [];
  const activePrograms = programs.filter((p) => p.status === 'ACTIVE');
  const recentUsers: UserSummaryResponse[] = recentUsersQ.data?.content ?? [];

  // Collect active semesters across all active programs
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

  const activeSemesterCount = semesterQueries.flatMap((q) => q.data ?? []).length;

  function roleVariant(role: string) {
    const map: Record<string, 'info' | 'success' | 'warning' | 'purple' | 'default'> = {
      ADMIN: 'danger' as unknown as 'info',
      TEACHER: 'info',
      ASSISTANT: 'warning',
      STUDENT: 'success',
    };
    return map[role] ?? 'default';
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Admin Dashboard</h1>
          <p className="mt-1 text-sm text-gray-500">System overview and management</p>
        </div>
        <div className="flex gap-2">
          <Link
            to="/users"
            className="inline-flex items-center gap-1.5 rounded-lg border border-gray-300 bg-white px-3 py-1.5 text-sm font-medium text-gray-700 shadow-sm hover:bg-gray-50"
          >
            <Settings size={15} />
            Manage Users
          </Link>
          <Link
            to="/users"
            className="inline-flex items-center gap-1.5 rounded-lg bg-primary-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-primary-700"
          >
            <Plus size={15} />
            New User
          </Link>
        </div>
      </div>

      {/* Stat cards */}
      <div className="grid gap-4 sm:grid-cols-3">
        <div className="animate-slide-up animation-delay-75">
          <StatCard
            label="Total Users"
            value={usersCountQ.data?.totalElements ?? 0}
            icon={Users}
            color="bg-blue-100 text-blue-600"
            loading={usersCountQ.isLoading}
          />
        </div>
        <div className="animate-slide-up animation-delay-150">
          <StatCard
            label="Active Programs"
            value={loading ? '' : activePrograms.length}
            icon={BookOpen}
            color="bg-indigo-100 text-indigo-600"
            loading={programsQ.isLoading}
          />
        </div>
        <div className="animate-slide-up animation-delay-300">
          <StatCard
            label="Active Semesters"
            value={semesterQueries.some((q) => q.isLoading) ? '' : activeSemesterCount}
            icon={GraduationCap}
            color="bg-green-100 text-green-600"
            loading={semesterQueries.some((q) => q.isLoading)}
          />
        </div>
      </div>

      {/* Programs overview */}
      <div className="grid gap-4 lg:grid-cols-2">
        <Card padding={false}>
          <div className="flex items-center justify-between border-b border-gray-100 px-5 py-4">
            <h3 className="font-semibold text-gray-900">Programs</h3>
            <span className="text-xs text-gray-400">{programs.length} total</span>
          </div>
          {programsQ.isLoading ? (
            <div className="flex justify-center py-8">
              <Spinner />
            </div>
          ) : programs.length === 0 ? (
            <p className="px-5 py-6 text-sm text-gray-400">No programs found.</p>
          ) : (
            <ul className="divide-y divide-gray-50">
              {programs.slice(0, 6).map((prog, idx) => (
                <li key={prog.id} className="flex items-center justify-between px-5 py-3 animate-slide-up" style={{ animationDelay: `${idx * 50}ms` }}>
                  <div>
                    <p className="text-sm font-medium text-gray-800">{prog.name}</p>
                    <p className="text-xs text-gray-400">{prog.code} · {prog.durationYears} years</p>
                  </div>
                  <Badge variant={prog.status === 'ACTIVE' ? 'success' : 'default'}>
                    {prog.status}
                  </Badge>
                </li>
              ))}
            </ul>
          )}
        </Card>

        {/* Recent users */}
        <Card padding={false}>
          <div className="flex items-center justify-between border-b border-gray-100 px-5 py-4">
            <h3 className="font-semibold text-gray-900">Recent Users</h3>
            <Link
              to="/users"
              className="flex items-center gap-0.5 text-xs font-medium text-primary-600 hover:text-primary-700"
            >
              View all
              <ChevronRight size={13} />
            </Link>
          </div>
          {recentUsersQ.isLoading ? (
            <div className="flex justify-center py-8">
              <Spinner />
            </div>
          ) : recentUsers.length === 0 ? (
            <p className="px-5 py-6 text-sm text-gray-400">No users yet.</p>
          ) : (
            <ul className="divide-y divide-gray-50">
              {recentUsers.map((u, idx) => (
                <li key={u.id} className="flex items-center justify-between px-5 py-3 animate-slide-up" style={{ animationDelay: `${idx * 50}ms` }}>
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium text-gray-800">{u.name}</p>
                    <p className="truncate text-xs text-gray-400">{u.email}</p>
                  </div>
                  <Badge variant={roleVariant(u.role)}>{u.role}</Badge>
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
            { label: 'Manage Users', to: '/users', icon: Users, color: 'bg-blue-50 text-blue-600' },
            { label: 'Courses', to: '/courses', icon: BookOpen, color: 'bg-indigo-50 text-indigo-600' },
            { label: 'Attendance', to: '/attendance', icon: GraduationCap, color: 'bg-green-50 text-green-600' },
            { label: 'Assessment', to: '/assessment', icon: Settings, color: 'bg-orange-50 text-orange-600' },
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
