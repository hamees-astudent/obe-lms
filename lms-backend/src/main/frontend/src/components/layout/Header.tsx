import { Link, useNavigate } from 'react-router-dom';
import { Bell, LogOut, User } from 'lucide-react';
import { useQuery } from '@tanstack/react-query';
import { useAuthStore } from '@/store/authStore';
import api from '@/lib/api';

export default function Header() {
  const { user, logout } = useAuthStore();
  const navigate = useNavigate();

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
    <header className="flex h-16 items-center justify-between border-b border-gray-200 bg-white px-6">
      <div /> {/* left spacer */}

      <div className="flex items-center gap-4">
        {/* Notification bell */}
        <Link
          to="/notifications"
          className="relative rounded-full p-2 text-gray-500 hover:bg-gray-100 hover:text-gray-700"
          aria-label="Notifications"
        >
          <Bell size={20} />
          {!!unreadCount && unreadCount > 0 && (
            <span className="absolute right-1 top-1 flex h-4 min-w-[1rem] items-center justify-center rounded-full bg-red-500 px-1 text-[10px] font-bold text-white">
              {unreadCount > 99 ? '99+' : unreadCount}
            </span>
          )}
        </Link>

        {/* User */}
        <div className="flex items-center gap-2 text-sm text-gray-700">
          <User size={18} className="text-gray-400" />
          <span className="hidden font-medium sm:inline">{user?.name}</span>
        </div>

        {/* Logout */}
        <button
          onClick={handleLogout}
          className="flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-sm text-gray-500 hover:bg-gray-100 hover:text-gray-700"
          aria-label="Log out"
        >
          <LogOut size={16} />
          <span className="hidden sm:inline">Logout</span>
        </button>
      </div>
    </header>
  );
}
