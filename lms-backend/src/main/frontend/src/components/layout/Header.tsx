import { Link, useNavigate, useLocation } from 'react-router-dom';
import { Bell, LogOut, User, Menu } from 'lucide-react';
import { useQuery } from '@tanstack/react-query';
import { useAuthStore } from '@/store/authStore';
import api from '@/lib/api';

/**
 * Page titles keyed by route prefix. The header used to render an empty spacer
 * where this belongs, so a nested page such as /attendance/:pscId gave no
 * indication of where the user was.
 */
const PAGE_TITLES: ReadonlyArray<readonly [string, string]> = [
  ['/dashboard', 'Dashboard'],
  ['/courses', 'Courses'],
  ['/attendance', 'Attendance'],
  ['/assessment', 'Assessment'],
  ['/transcripts', 'Transcripts'],
  ['/notifications', 'Notifications'],
  ['/users', 'Users'],
  ['/programs', 'Programs'],
  ['/offerings', 'Offerings'],
  ['/course-catalog', 'Course Catalog'],
  ['/grading-scales', 'Grading Scales'],
];

function titleFor(pathname: string): string {
  const match = PAGE_TITLES.find(
    ([prefix]) => pathname === prefix || pathname.startsWith(`${prefix}/`),
  );
  return match?.[1] ?? 'Learning Hub';
}

export default function Header({ onOpenNav }: { onOpenNav: () => void }) {
  const { user, logout } = useAuthStore();
  const navigate = useNavigate();
  const { pathname } = useLocation();

  const { data: unreadCount } = useQuery<number>({
    queryKey: ['notifications', 'unread-count'],
    queryFn: () => api.get<number>('/notifications/unread-count').then((r) => r.data),
    refetchInterval: 60_000,
    enabled: !!user,
  });

  function handleLogout() {
    logout();
    navigate('/login', { replace: true });
  }

  return (
    <header className="flex h-16 flex-shrink-0 items-center justify-between gap-3 border-b border-gray-200 bg-white px-4 sm:px-6">
      <div className="flex min-w-0 items-center gap-3">
        <button
          onClick={onOpenNav}
          aria-label="Open navigation"
          className="-ml-1 rounded-lg p-2.5 text-gray-500 hover:bg-gray-100 hover:text-gray-700 lg:hidden"
        >
          <Menu size={20} />
        </button>
        <h1 className="truncate text-base font-semibold text-gray-900">
          {titleFor(pathname)}
        </h1>
      </div>

      <div className="flex flex-shrink-0 items-center gap-1 sm:gap-3">
        {/* Notification bell */}
        <Link
          to="/notifications"
          className="relative rounded-full p-2.5 text-gray-500 transition-transform duration-150 hover:bg-gray-100 hover:text-gray-700 active:scale-95"
          aria-label={
            unreadCount ? `Notifications, ${unreadCount} unread` : 'Notifications'
          }
        >
          <Bell size={20} />
          {!!unreadCount && unreadCount > 0 && (
            <span className="absolute right-1 top-1 flex h-4 min-w-[1rem] items-center justify-center rounded-full bg-red-500 px-1 text-[10px] font-bold text-white">
              {unreadCount > 99 ? '99+' : unreadCount}
            </span>
          )}
        </Link>

        {/* User */}
        <div className="hidden items-center gap-2 text-sm text-gray-700 sm:flex">
          <User size={18} className="text-gray-400" />
          <span className="max-w-[12rem] truncate font-medium">{user?.name}</span>
        </div>

        {/* Logout */}
        <button
          onClick={handleLogout}
          className="flex items-center gap-1.5 rounded-lg p-2.5 text-sm text-gray-500 transition-all duration-150 hover:bg-gray-100 hover:text-gray-700 active:scale-95 sm:px-3 sm:py-2"
          aria-label="Log out"
        >
          <LogOut size={16} />
          <span className="hidden sm:inline">Logout</span>
        </button>
      </div>
    </header>
  );
}
