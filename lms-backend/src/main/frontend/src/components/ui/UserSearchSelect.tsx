import { useEffect, useId, useRef, useState } from 'react';
import { useQueries } from '@tanstack/react-query';
import { Search, X } from 'lucide-react';
import api from '@/lib/api';
import Spinner from '@/components/ui/Spinner';
import type { Page, Role, UserSummaryResponse, UUID } from '@/types/api';

const RESULT_LIMIT = 20;

/**
 * Picks one user by searching the server as you type — name, email or student
 * number, the same matching as the Users page.
 *
 * Replaces a `<select>` fed by fixed-size user lists (500 students, 200 of each
 * other role): unusable to scroll at that length, and anyone past the cap was
 * silently missing from it.
 */
export default function UserSearchSelect({
  value,
  onChange,
  disabled,
  roles,
  placeholder = 'Search by name, email or student number…',
}: {
  value: UserSummaryResponse | null;
  onChange: (user: UserSummaryResponse | null) => void;
  /** Shown but not selectable, with the reason: user id → "already a member". */
  disabled?: Map<UUID, string>;
  /** Only users with one of these roles; all roles when omitted. */
  roles?: Role[];
  placeholder?: string;
}) {
  const listId = useId();
  const inputRef = useRef<HTMLInputElement>(null);
  const [text, setText] = useState('');
  const [query, setQuery] = useState('');
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(0);

  useEffect(() => {
    const id = setTimeout(() => setQuery(text.trim()), 300);
    return () => clearTimeout(id);
  }, [text]);

  // The API filters by one role at a time, so a multi-role picker asks once
  // per role and merges.
  const roleQueries = useQueries({
    queries: (roles ?? [undefined]).map((role) => ({
      queryKey: ['admin-users', 'search', query, role ?? 'any'],
      queryFn: () =>
        api
          .get<Page<UserSummaryResponse>>('/admin/users', {
            params: { q: query || undefined, role, status: 'ACTIVE', size: RESULT_LIMIT, sort: 'name' },
          })
          .then((r) => r.data),
      enabled: open,
      staleTime: 30_000,
    })),
  });
  const resultsQ = {
    isLoading: roleQueries.some((q) => q.isLoading),
    isError: roleQueries.some((q) => q.isError),
  };
  const matches = roleQueries.flatMap((q) => q.data?.content ?? []);
  const results = matches.sort((a, b) => a.name.localeCompare(b.name)).slice(0, RESULT_LIMIT);
  const more = roleQueries.reduce((n, q) => n + (q.data?.totalElements ?? 0), 0) - results.length;

  const isDisabled = (u: UserSummaryResponse) => disabled?.has(u.id) ?? false;

  useEffect(() => setActive(0), [query]);

  function choose(u: UserSummaryResponse) {
    if (isDisabled(u)) return;
    onChange(u);
    setText('');
    setOpen(false);
  }

  function onKeyDown(e: React.KeyboardEvent<HTMLInputElement>) {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setOpen(true);
      setActive((i) => Math.min(i + 1, results.length - 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setActive((i) => Math.max(i - 1, 0));
    } else if (e.key === 'Enter' && open && results[active]) {
      e.preventDefault();
      choose(results[active]);
    } else if (e.key === 'Escape') {
      setOpen(false);
    }
  }

  if (value) {
    return (
      <div className="flex min-w-[160px] flex-1 items-center gap-2 rounded-lg border border-primary-300 bg-primary-50 px-3 py-1.5 text-sm">
        <span className="min-w-0 flex-1 truncate">
          <span className="font-medium text-gray-900">{value.name}</span>{' '}
          <span className="text-gray-500">
            {value.email}
            {value.studentNumber && ` · ${value.studentNumber}`} · {value.role}
          </span>
        </span>
        <button
          type="button"
          onClick={() => {
            onChange(null);
            requestAnimationFrame(() => inputRef.current?.focus());
          }}
          aria-label="Clear selection"
          className="flex-shrink-0 text-gray-400 hover:text-gray-700"
        >
          <X size={14} />
        </button>
      </div>
    );
  }

  return (
    <div className="relative min-w-[160px] flex-1">
      <Search size={14} className="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-gray-400" />
      <input
        ref={inputRef}
        type="text"
        role="combobox"
        aria-expanded={open}
        aria-controls={listId}
        aria-autocomplete="list"
        aria-activedescendant={open && results[active] ? `${listId}-${active}` : undefined}
        value={text}
        placeholder={placeholder}
        onChange={(e) => {
          setText(e.target.value);
          setOpen(true);
        }}
        onFocus={() => setOpen(true)}
        // Delay so a click on an option lands before the list closes.
        onBlur={() => setTimeout(() => setOpen(false), 150)}
        onKeyDown={onKeyDown}
        className="w-full rounded-lg border border-gray-300 py-1.5 pl-8 pr-3 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
      />
      {open && (
        <ul
          id={listId}
          role="listbox"
          className="absolute z-20 mt-1 max-h-64 w-full overflow-y-auto rounded-lg border border-gray-200 bg-white py-1 text-sm shadow-lg"
        >
          {resultsQ.isLoading ? (
            <li className="flex justify-center py-3"><Spinner size="sm" /></li>
          ) : resultsQ.isError ? (
            <li className="px-3 py-2 text-red-600">Couldn't search users. Try again.</li>
          ) : results.length === 0 ? (
            <li className="px-3 py-2 text-gray-400">
              {query ? `No active users match "${query}".` : 'No active users.'}
            </li>
          ) : (
            <>
              {results.map((u, i) => {
                const reason = disabled?.get(u.id);
                return (
                  <li
                    key={u.id}
                    id={`${listId}-${i}`}
                    role="option"
                    aria-selected={i === active}
                    aria-disabled={!!reason}
                    onMouseDown={(e) => e.preventDefault()}
                    onClick={() => choose(u)}
                    onMouseEnter={() => setActive(i)}
                    className={`px-3 py-1.5 ${
                      reason
                        ? 'cursor-not-allowed text-gray-400'
                        : `cursor-pointer ${i === active ? 'bg-primary-50' : ''}`
                    }`}
                  >
                    <span className={reason ? '' : 'font-medium text-gray-900'}>{u.name}</span>{' '}
                    <span className="text-xs text-gray-500">
                      {u.email}
                      {u.studentNumber && ` · ${u.studentNumber}`} · {u.role}
                    </span>
                    {reason && <span className="ml-1 text-xs italic">({reason})</span>}
                  </li>
                );
              })}
              {more > 0 && (
                <li className="px-3 py-1.5 text-xs text-gray-400">
                  {more} more — keep typing to narrow the list.
                </li>
              )}
            </>
          )}
        </ul>
      )}
    </div>
  );
}
