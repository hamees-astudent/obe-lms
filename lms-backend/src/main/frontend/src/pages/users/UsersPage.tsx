import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Plus, Edit2, ShieldCheck, UserX, UserCheck, Search } from 'lucide-react';
import api from '@/lib/api';
import Modal from '@/components/ui/Modal';
import Button from '@/components/ui/Button';
import Input from '@/components/ui/Input';
import Badge from '@/components/ui/Badge';
import Spinner from '@/components/ui/Spinner';
import type {
  UserSummaryResponse,
  Page,
  Role,
} from '@/types/api';

// ─── Schemas ────────────────────────────────────────────────────────────────

const createSchema = z.object({
  name:     z.string().min(1, 'Name is required').max(120),
  email:    z.string().email('Invalid email').max(180),
  password: z.string().min(8, 'Min 8 characters').max(128),
  role:     z.enum(['STUDENT', 'TEACHER', 'ADMIN'] as const),
});

const editSchema = z.object({
  name: z.string().min(1, 'Name is required').max(120),
});

type CreateForm = z.infer<typeof createSchema>;
type EditForm   = z.infer<typeof editSchema>;

// ─── API calls ───────────────────────────────────────────────────────────────

const ROLES: Role[] = ['STUDENT', 'TEACHER', 'ADMIN'];
const PAGE_SIZE = 20;

function roleBadge(role: Role) {
  const map: Record<Role, 'info' | 'warning' | 'danger'> = {
    STUDENT:   'info',
    TEACHER:   'warning',
    ADMIN:     'danger',
    ASSISTANT: 'info',
  };
  return <Badge variant={map[role]}>{role}</Badge>;
}

function statusBadge(status: string) {
  return status === 'ACTIVE'
    ? <Badge variant="success">Active</Badge>
    : <Badge variant="danger">Inactive</Badge>;
}

// ─── Component ───────────────────────────────────────────────────────────────

export default function UsersPage() {
  const qc = useQueryClient();

  // ── filters / pagination ──────────────────────────────────────────────────
  const [search,    setSearch]    = useState('');
  const [roleFilter, setRoleFilter] = useState<string>('');
  const [statusFilter, setStatusFilter] = useState<string>('');
  const [page, setPage] = useState(0);

  // ── modal state ───────────────────────────────────────────────────────────
  const [showCreate, setShowCreate] = useState(false);
  const [editing,    setEditing]    = useState<UserSummaryResponse | null>(null);
  const [changingRole, setChangingRole] = useState<UserSummaryResponse | null>(null);
  const [newRole, setNewRole] = useState<Role>('STUDENT');

  // ── query ─────────────────────────────────────────────────────────────────
  const { data, isLoading } = useQuery<Page<UserSummaryResponse>>({
    queryKey: ['admin-users', page, roleFilter, statusFilter],
    queryFn: () =>
      api
        .get('/admin/users', {
          params: { page, size: PAGE_SIZE, ...(roleFilter && { role: roleFilter }), ...(statusFilter && { status: statusFilter }) },
        })
        .then((r) => r.data),
  });

  const users = data?.content ?? [];

  // client-side name search (backend has no search param)
  const visible = search
    ? users.filter(
        (u) =>
          u.name.toLowerCase().includes(search.toLowerCase()) ||
          u.email.toLowerCase().includes(search.toLowerCase()),
      )
    : users;

  // ── mutations ─────────────────────────────────────────────────────────────
  const invalidate = () => qc.invalidateQueries({ queryKey: ['admin-users'] });

  const createMut = useMutation({
    mutationFn: (body: CreateForm) => api.post('/admin/users', body),
    onSuccess: () => { invalidate(); setShowCreate(false); },
  });

  const editMut = useMutation({
    mutationFn: ({ id, body }: { id: string; body: EditForm }) =>
      api.put(`/admin/users/${id}`, body),
    onSuccess: () => { invalidate(); setEditing(null); },
  });

  const changeRoleMut = useMutation({
    mutationFn: ({ id, role }: { id: string; role: Role }) =>
      api.patch(`/admin/users/${id}/role`, { role }),
    onSuccess: () => { invalidate(); setChangingRole(null); },
  });

  const toggleStatusMut = useMutation({
    mutationFn: (user: UserSummaryResponse) =>
      api.patch(`/admin/users/${user.id}/status`, {
        status: user.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE',
      }),
    onSuccess: () => invalidate(),
  });

  // ── forms ─────────────────────────────────────────────────────────────────
  const createForm = useForm<CreateForm>({
    resolver: zodResolver(createSchema),
    defaultValues: { role: 'STUDENT' },
  });

  const editForm = useForm<EditForm>({ resolver: zodResolver(editSchema) });

  function openEdit(u: UserSummaryResponse) {
    editForm.reset({ name: u.name });
    setEditing(u);
  }

  function openChangeRole(u: UserSummaryResponse) {
    setNewRole(u.role);
    setChangingRole(u);
  }

  // ── render ────────────────────────────────────────────────────────────────
  return (
    <div className="space-y-5 animate-slide-up">
      {/* Header */}
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900">Users</h1>
        <Button onClick={() => { createForm.reset({ role: 'STUDENT' }); setShowCreate(true); }}>
          <Plus size={16} className="mr-1" /> Add User
        </Button>
      </div>

      {/* Filters */}
      <div className="flex flex-wrap gap-3">
        <div className="relative">
          <Search size={16} className="absolute left-2.5 top-2.5 text-gray-400" />
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search by name or email…"
            className="pl-8 pr-3 py-2 rounded-lg border border-gray-300 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 w-64"
          />
        </div>
        <select
          value={roleFilter}
          onChange={(e) => { setRoleFilter(e.target.value); setPage(0); }}
          className="rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
        >
          <option value="">All Roles</option>
          {ROLES.map((r) => <option key={r} value={r}>{r}</option>)}
        </select>
        <select
          value={statusFilter}
          onChange={(e) => { setStatusFilter(e.target.value); setPage(0); }}
          className="rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
        >
          <option value="">All Statuses</option>
          <option value="ACTIVE">Active</option>
          <option value="INACTIVE">Inactive</option>
        </select>
      </div>

      {/* Table */}
      {isLoading ? (
        <div className="flex justify-center py-16"><Spinner size="lg" /></div>
      ) : (
        <div className="overflow-x-auto rounded-xl border border-gray-200 bg-white shadow-sm">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 text-gray-600">
              <tr>
                <th className="px-4 py-3 text-left font-medium">Name</th>
                <th className="px-4 py-3 text-left font-medium">Email</th>
                <th className="px-4 py-3 text-left font-medium">Role</th>
                <th className="px-4 py-3 text-left font-medium">Status</th>
                <th className="px-4 py-3 text-left font-medium">Created</th>
                <th className="px-4 py-3 text-right font-medium">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {visible.length === 0 ? (
                <tr>
                  <td colSpan={6} className="py-10 text-center text-gray-400">No users found.</td>
                </tr>
              ) : visible.map((u) => (
                <tr key={u.id} className="hover:bg-gray-50 transition-colors">
                  <td className="px-4 py-3 font-medium text-gray-900">{u.name}</td>
                  <td className="px-4 py-3 text-gray-600">{u.email}</td>
                  <td className="px-4 py-3">{roleBadge(u.role)}</td>
                  <td className="px-4 py-3">{statusBadge(u.status)}</td>
                  <td className="px-4 py-3 text-gray-500">{new Date(u.createdAt).toLocaleDateString()}</td>
                  <td className="px-4 py-3">
                    <div className="flex justify-end gap-2">
                      <button
                        title="Edit name"
                        onClick={() => openEdit(u)}
                        className="rounded p-1 text-gray-400 hover:bg-gray-100 hover:text-gray-700 transition"
                      >
                        <Edit2 size={15} />
                      </button>
                      <button
                        title="Change role"
                        onClick={() => openChangeRole(u)}
                        className="rounded p-1 text-gray-400 hover:bg-gray-100 hover:text-primary-600 transition"
                      >
                        <ShieldCheck size={15} />
                      </button>
                      <button
                        title={u.status === 'ACTIVE' ? 'Deactivate' : 'Activate'}
                        onClick={() => toggleStatusMut.mutate(u)}
                        className={`rounded p-1 transition hover:bg-gray-100 ${
                          u.status === 'ACTIVE' ? 'text-red-400 hover:text-red-600' : 'text-green-400 hover:text-green-600'
                        }`}
                      >
                        {u.status === 'ACTIVE' ? <UserX size={15} /> : <UserCheck size={15} />}
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Pagination */}
      {data && data.totalPages > 1 && (
        <div className="flex items-center justify-between text-sm text-gray-600">
          <span>{data.totalElements} users total</span>
          <div className="flex gap-2">
            <Button variant="secondary" size="sm" disabled={data.first} onClick={() => setPage((p) => p - 1)}>
              Previous
            </Button>
            <span className="px-3 py-1 bg-gray-100 rounded-lg">
              {data.number + 1} / {data.totalPages}
            </span>
            <Button variant="secondary" size="sm" disabled={data.last} onClick={() => setPage((p) => p + 1)}>
              Next
            </Button>
          </div>
        </div>
      )}

      {/* Create Modal */}
      <Modal open={showCreate} onClose={() => setShowCreate(false)} title="Add New User">
        <form onSubmit={createForm.handleSubmit((d) => createMut.mutate(d))} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Full Name</label>
            <Input {...createForm.register('name')} placeholder="Jane Smith" />
            {createForm.formState.errors.name && (
              <p className="text-xs text-red-500 mt-1">{createForm.formState.errors.name.message}</p>
            )}
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Email</label>
            <Input type="email" {...createForm.register('email')} placeholder="jane@example.com" />
            {createForm.formState.errors.email && (
              <p className="text-xs text-red-500 mt-1">{createForm.formState.errors.email.message}</p>
            )}
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Password</label>
            <Input type="password" {...createForm.register('password')} placeholder="Min 8 characters" />
            {createForm.formState.errors.password && (
              <p className="text-xs text-red-500 mt-1">{createForm.formState.errors.password.message}</p>
            )}
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Role</label>
            <select
              {...createForm.register('role')}
              className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
            >
              {ROLES.map((r) => <option key={r} value={r}>{r}</option>)}
            </select>
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="secondary" onClick={() => setShowCreate(false)}>Cancel</Button>
            <Button type="submit" loading={createMut.isPending}>Create User</Button>
          </div>
        </form>
      </Modal>

      {/* Edit Modal */}
      <Modal open={!!editing} onClose={() => setEditing(null)} title="Edit User">
        <form onSubmit={editForm.handleSubmit((d) => editing && editMut.mutate({ id: editing.id, body: d }))} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Full Name</label>
            <Input {...editForm.register('name')} />
            {editForm.formState.errors.name && (
              <p className="text-xs text-red-500 mt-1">{editForm.formState.errors.name.message}</p>
            )}
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="secondary" onClick={() => setEditing(null)}>Cancel</Button>
            <Button type="submit" loading={editMut.isPending}>Save Changes</Button>
          </div>
        </form>
      </Modal>

      {/* Change Role Modal */}
      <Modal open={!!changingRole} onClose={() => setChangingRole(null)} title="Change Role">
        <div className="space-y-4">
          <p className="text-sm text-gray-600">
            Changing role for <strong>{changingRole?.name}</strong>
          </p>
          <select
            value={newRole}
            onChange={(e) => setNewRole(e.target.value as Role)}
            className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
          >
            {ROLES.map((r) => <option key={r} value={r}>{r}</option>)}
          </select>
          <div className="flex justify-end gap-2">
            <Button variant="secondary" onClick={() => setChangingRole(null)}>Cancel</Button>
            <Button
              loading={changeRoleMut.isPending}
              onClick={() => changingRole && changeRoleMut.mutate({ id: changingRole.id, role: newRole })}
            >
              Update Role
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
