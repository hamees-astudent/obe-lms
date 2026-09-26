import { NavLink, useLocation } from 'react-router-dom';
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
  UsersRound,
  type LucideIcon,
  ScanLine,
} from 'lucide-react';
import { useAuthStore } from '@/store/authStore';
import type { Role } from '@/types/api';

interface NavItem {
  to: string;
  label: string;
  icon: LucideIcon;
  roles?: Role[];
}

interface NavSection {
  /** Null for the top group, which needs no heading. */
  heading: string | null;
  items: NavItem[];
}

/**
 * Navigation grouped by what the user is doing, rather than one flat list of
 * eleven destinations with the admin-only entries mixed in among the rest.
 */
const NAV_SECTIONS: NavSection[] = [
  {
    heading: null,
    items: [{ to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard }],
  },
  {
    heading: 'Learning',
    items: [
      { to: '/courses', label: 'Courses', icon: BookOpen },
      { to: '/attendance', label: 'Attendance', icon: ClipboardCheck },
      { to: '/assessment', label: 'Assessment', icon: FileText },
      { to: '/exams', label: 'Exams', icon: ScanLine },
      { to: '/transcripts', label: 'Transcripts', icon: GraduationCap },
      { to: '/notifications', label: 'Notifications', icon: Bell },
    ],
  },
  {
    heading: 'Administration',
    items: [
      { to: '/users', label: 'Users', icon: Users, roles: ['ADMIN'] },
      { to: '/programs', label: 'Programs', icon: GraduationCap, roles: ['ADMIN'] },
      { to: '/offerings', label: 'Offerings', icon: Building2, roles: ['ADMIN'] },
      { to: '/cohorts', label: 'Cohorts', icon: UsersRound, roles: ['ADMIN'] },
      { to: '/course-catalog', label: 'Course Catalog', icon: Library, roles: ['ADMIN'] },
      { to: '/grading-scales', label: 'Grading Scales', icon: BarChart2, roles: ['ADMIN'] },
    ],
  },
];

export default function Sidebar({ onNavigate }: { onNavigate?: () => void }) {
  const user = useAuthStore((s) => s.user);
  const { pathname } = useLocation();

  const sections = NAV_SECTIONS.map((section) => ({
    ...section,
    items: section.items.filter(
      (item) => !item.roles || (user && item.roles.includes(user.role)),
    ),
  })).filter((section) => section.items.length > 0);

  return (
    <aside className="flex h-full w-60 flex-col border-r border-gray-200 bg-white">
      {/* Logo */}
      <div className="flex h-16 flex-shrink-0 items-center gap-2 border-b border-gray-100 px-6">
        <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary-600">
          <span className="text-sm font-bold text-white">LMS</span>
        </div>
        <span className="text-base font-semibold text-gray-900">Learning Hub</span>
      </div>

      {/* Navigation */}
      <nav className="flex-1 overflow-y-auto px-3 py-4 scrollbar-thin">
        {sections.map((section) => (
          <div key={section.heading ?? 'main'} className="mb-4 last:mb-0">
            {section.heading && (
              <p className="mb-1 px-3 text-xs font-semibold uppercase tracking-wide text-gray-400">
                {section.heading}
              </p>
            )}
            <ul className="space-y-1">
              {section.items.map(({ to, label, icon: Icon }) => {
                // Detail routes such as /attendance/:pscId must keep their
                // parent lit, or the user loses track of where they are.
                const isActive = pathname === to || pathname.startsWith(`${to}/`);
                return (
                  <li key={to}>
                    <NavLink
                      to={to}
                      onClick={onNavigate}
                      aria-current={isActive ? 'page' : undefined}
                      className={[
                        'flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-all duration-150',
                        isActive
                          ? 'bg-primary-50 text-primary-700 shadow-sm'
                          : 'text-gray-600 hover:bg-gray-100 hover:text-gray-900',
                      ].join(' ')}
                    >
                      <Icon size={18} className="flex-shrink-0" />
                      {label}
                    </NavLink>
                  </li>
                );
              })}
            </ul>
          </div>
        ))}
      </nav>

      {/* User info */}
      {user && (
        <div className="flex-shrink-0 border-t border-gray-100 px-4 py-3">
          <p className="truncate text-xs font-medium text-gray-800">{user.name}</p>
          <p className="truncate text-xs text-gray-500">{user.email}</p>
        </div>
      )}
    </aside>
  );
}
