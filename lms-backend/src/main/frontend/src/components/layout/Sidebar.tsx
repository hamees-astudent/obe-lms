import { NavLink } from 'react-router-dom';
import {
  LayoutDashboard,
  BookOpen,
  ClipboardCheck,
  FileText,
  Bell,
  Users,
  GraduationCap,
  Building2,
  BarChart2,
  Library,
  type LucideIcon,
} from 'lucide-react';
import { useAuthStore } from '@/store/authStore';
import type { Role } from '@/types/api';

interface NavItem {
  to: string;
  label: string;
  icon: LucideIcon;
  roles?: Role[];
}

const NAV_ITEMS: NavItem[] = [
  { to: '/dashboard',     label: 'Dashboard',     icon: LayoutDashboard },
  { to: '/courses',       label: 'Courses',        icon: BookOpen },
  { to: '/attendance',    label: 'Attendance',     icon: ClipboardCheck },
  { to: '/assessment',    label: 'Assessment',     icon: FileText },
  { to: '/transcripts',   label: 'Transcripts',   icon: GraduationCap },
  { to: '/notifications', label: 'Notifications', icon: Bell },
  { to: '/users',         label: 'Users',          icon: Users,       roles: ['ADMIN'] },
  { to: '/programs',      label: 'Programs',       icon: GraduationCap, roles: ['ADMIN'] },
  { to: '/offerings',     label: 'Offerings',      icon: Building2,   roles: ['ADMIN'] },
  { to: '/grading-scales',  label: 'Grading Scales',  icon: BarChart2,    roles: ['ADMIN'] },
  { to: '/course-catalog',   label: 'Course Catalog',   icon: Library,      roles: ['ADMIN'] },
];

export default function Sidebar() {
  const user = useAuthStore((s) => s.user);

  const visible = NAV_ITEMS.filter(
    (item) => !item.roles || (user && item.roles.includes(user.role)),
  );

  return (
    <aside className="flex w-60 flex-col border-r border-gray-200 bg-white animate-slide-in-left">
      {/* Logo */}
      <div className="flex h-16 items-center gap-2 border-b border-gray-100 px-6">
        <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary-600">
          <span className="text-sm font-bold text-white">LMS</span>
        </div>
        <span className="text-base font-semibold text-gray-900">Learning Hub</span>
      </div>

      {/* Navigation */}
      <nav className="flex-1 overflow-y-auto px-3 py-4 scrollbar-thin">
        <ul className="space-y-1">
          {visible.map(({ to, label, icon: Icon }) => (
            <li key={to}>
              <NavLink
                to={to}
                className={({ isActive }) =>
                  [
                    'flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-all duration-150',
                    isActive
                      ? 'bg-primary-50 text-primary-700 shadow-sm'
                      : 'text-gray-600 hover:bg-gray-100 hover:text-gray-900 hover:translate-x-0.5',
                  ].join(' ')
                }
              >
                <Icon size={18} />
                {label}
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>

      {/* User info */}
      {user && (
        <div className="border-t border-gray-100 px-4 py-3">
          <p className="truncate text-xs font-medium text-gray-800">{user.name}</p>
          <p className="truncate text-xs text-gray-500">{user.email}</p>
        </div>
      )}
    </aside>
  );
}
