import { useState } from 'react';
import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Bell,
  CheckCircle,
  AlertTriangle,
  FileText,
  BookOpen,
  Award,
  GraduationCap,
  CheckCheck,
  Filter,
} from 'lucide-react';
import api from '@/lib/api';
import type { NotificationResponse, NotificationPage, NotificationEventType } from '@/types/api';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------
function timeAgo(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime();
  const mins = Math.floor(diff / 60_000);
  if (mins < 1) return 'just now';
  if (mins < 60) return `${mins}m ago`;
  const hrs = Math.floor(mins / 60);
  if (hrs < 24) return `${hrs}h ago`;
  const days = Math.floor(hrs / 24);
  if (days < 30) return `${days}d ago`;
  return new Date(iso).toLocaleDateString();
}

interface EventMeta {
  icon: React.ReactNode;
  bg: string;
  label: string;
}

function eventMeta(type: NotificationEventType): EventMeta {
  switch (type) {
    case 'ENROLLMENT_CONFIRMED':
      return {
        icon: <CheckCircle className="h-5 w-5 text-green-600" />,
        bg: 'bg-green-100',
        label: 'Enrollment',
      };
    case 'ATTENDANCE_ALERT':
      return {
        icon: <AlertTriangle className="h-5 w-5 text-amber-600" />,
        bg: 'bg-amber-100',
        label: 'Attendance',
      };
    case 'ASSIGNMENT_CREATED':
      return {
        icon: <FileText className="h-5 w-5 text-blue-600" />,
        bg: 'bg-blue-100',
        label: 'Assignment',
      };
    case 'QUIZ_CREATED':
      return {
        icon: <BookOpen className="h-5 w-5 text-purple-600" />,
        bg: 'bg-purple-100',
        label: 'Quiz',
      };
    case 'RESULTS_PUBLISHED':
      return {
        icon: <Award className="h-5 w-5 text-indigo-600" />,
        bg: 'bg-indigo-100',
        label: 'Results',
      };
    case 'SEMESTER_COMPLETED':
      return {
        icon: <GraduationCap className="h-5 w-5 text-teal-600" />,
        bg: 'bg-teal-100',
        label: 'Semester',
      };
    default:
      return {
        icon: <Bell className="h-5 w-5 text-gray-500" />,
        bg: 'bg-gray-100',
        label: 'Notification',
      };
  }
}

// ---------------------------------------------------------------------------
// Notification Item
// ---------------------------------------------------------------------------
function NotificationItem({
  n,
  onRead,
}: {
  n: NotificationResponse;
  onRead: (id: string) => void;
}) {
  const meta = eventMeta(n.eventType);

  return (
    <div
      onClick={() => { if (!n.isRead) onRead(n.id); }}
      className={`flex gap-4 rounded-xl border p-4 transition-colors ${
        n.isRead
          ? 'bg-white hover:bg-gray-50'
          : 'cursor-pointer border-primary-200 bg-primary-50 hover:bg-primary-100'
      }`}
    >
      {/* Icon */}
      <div className={`mt-0.5 flex h-10 w-10 shrink-0 items-center justify-center rounded-full ${meta.bg}`}>
        {meta.icon}
      </div>

      {/* Content */}
      <div className="flex-1 min-w-0">
        <div className="flex items-start justify-between gap-2">
          <p className={`text-sm leading-snug ${n.isRead ? 'font-normal text-gray-800' : 'font-semibold text-gray-900'}`}>
            {n.title}
          </p>
          <div className="flex shrink-0 items-center gap-2">
            <span className="whitespace-nowrap text-xs text-gray-400">{timeAgo(n.createdAt)}</span>
            {!n.isRead && (
              <span className="h-2 w-2 rounded-full bg-primary-500" title="Unread" />
            )}
          </div>
        </div>
        <p className="mt-0.5 text-sm text-gray-600 line-clamp-2">{n.body}</p>
        <span className="mt-1.5 inline-block rounded-full bg-gray-100 px-2 py-0.5 text-[11px] text-gray-500">
          {meta.label}
        </span>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Main Page
// ---------------------------------------------------------------------------
export default function NotificationsPage() {
  const [unreadOnly, setUnreadOnly] = useState(false);
  const queryClient = useQueryClient();

  const query = useInfiniteQuery({
    queryKey: ['notifications', { unreadOnly }],
    queryFn: ({ pageParam }) =>
      api
        .get<NotificationPage>(
          `/notifications?unreadOnly=${unreadOnly}&page=${pageParam}&size=20`,
        )
        .then((r) => r.data),
    getNextPageParam: (last) =>
      last.number < last.totalPages - 1 ? last.number + 1 : undefined,
    initialPageParam: 0,
  });

  const allNotifications: NotificationResponse[] =
    query.data?.pages.flatMap((p) => p.content) ?? [];

  const totalElements = query.data?.pages[0]?.totalElements ?? 0;
  const unreadCount = allNotifications.filter((n) => !n.isRead).length;

  const markReadMutation = useMutation({
    mutationFn: (id: string) =>
      api.put<NotificationResponse>(`/notifications/${id}/read`).then((r) => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notifications'] });
    },
  });

  const markAllMutation = useMutation({
    mutationFn: () => api.put('/notifications/read-all'),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notifications'] });
    },
  });

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <Bell className="h-7 w-7 text-primary-600" />
          <div>
            <h1 className="text-2xl font-bold text-gray-900">Notifications</h1>
            <p className="text-sm text-gray-500">
              {totalElements} total
              {unreadCount > 0 && (
                <span className="ml-2 rounded-full bg-primary-100 px-2 py-0.5 text-xs font-semibold text-primary-700">
                  {unreadCount} unread
                </span>
              )}
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          {/* Unread filter toggle */}
          <button
            onClick={() => setUnreadOnly((v) => !v)}
            className={`inline-flex items-center gap-2 rounded-lg border px-3 py-1.5 text-sm font-medium transition-colors ${
              unreadOnly
                ? 'border-primary-400 bg-primary-50 text-primary-700'
                : 'border-gray-300 text-gray-600 hover:bg-gray-50'
            }`}
          >
            <Filter className="h-4 w-4" />
            {unreadOnly ? 'Unread only' : 'All'}
          </button>

          {/* Mark all read */}
          {unreadCount > 0 && (
            <button
              onClick={() => markAllMutation.mutate()}
              disabled={markAllMutation.isPending}
              className="inline-flex items-center gap-2 rounded-lg border border-gray-300 px-3 py-1.5 text-sm font-medium text-gray-600 hover:bg-gray-50 disabled:opacity-50"
            >
              <CheckCheck className="h-4 w-4" />
              Mark all read
            </button>
          )}
        </div>
      </div>

      {/* List */}
      {query.isLoading && (
        <div className="space-y-3">
          {[1, 2, 3].map((i) => (
            <div key={i} className="h-20 animate-pulse rounded-xl bg-gray-100" />
          ))}
        </div>
      )}

      {!query.isLoading && allNotifications.length === 0 && (
        <div className="rounded-xl border border-dashed p-12 text-center">
          <Bell className="mx-auto mb-3 h-10 w-10 text-gray-300" />
          <p className="text-sm text-gray-500">
            {unreadOnly ? 'No unread notifications.' : 'No notifications yet.'}
          </p>
          {unreadOnly && (
            <button
              onClick={() => setUnreadOnly(false)}
              className="mt-2 text-sm text-primary-600 hover:underline"
            >
              View all notifications
            </button>
          )}
        </div>
      )}

      {allNotifications.length > 0 && (
        <div className="space-y-2">
          {allNotifications.map((n) => (
            <NotificationItem
              key={n.id}
              n={n}
              onRead={(id) => markReadMutation.mutate(id)}
            />
          ))}
        </div>
      )}

      {/* Load more */}
      {query.hasNextPage && (
        <div className="flex justify-center">
          <button
            onClick={() => query.fetchNextPage()}
            disabled={query.isFetchingNextPage}
            className="rounded-lg border px-5 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-50"
          >
            {query.isFetchingNextPage ? 'Loading…' : 'Load more'}
          </button>
        </div>
      )}
    </div>
  );
}

