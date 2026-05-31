import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import {
  Plus,
  Edit2,
  ToggleLeft,
  ToggleRight,
  ChevronDown,
  ChevronUp,
  BookOpen,
  Trash2,
} from 'lucide-react';
import api from '@/lib/api';
import Modal from '@/components/ui/Modal';
import Button from '@/components/ui/Button';
import Input from '@/components/ui/Input';
import Badge from '@/components/ui/Badge';
import Spinner from '@/components/ui/Spinner';
import Card from '@/components/ui/Card';
import type { Page, UUID } from '@/types/api';

// ---------------------------------------------------------------------------
// Local types (aligned with Java backend DTOs)
// ---------------------------------------------------------------------------
interface CourseCatalogItem {
  id: UUID;
  code: string;
  name: string;
  description?: string;
  creditHours: number;
  status: 'ACTIVE' | 'INACTIVE';
  createdAt: string;
}

interface CourseCloItem {
  id: UUID;
  courseId: UUID;
  code: string;
  title: string;
  description?: string;
  orderIndex: number;
  createdAt: string;
}

// ---------------------------------------------------------------------------
// Zod schemas
// ---------------------------------------------------------------------------
const courseSchema = z.object({
  code: z
    .string()
    .min(1, 'Code required')
    .max(20, 'Max 20 characters')
    .regex(/^[A-Z0-9_]+$/, 'Uppercase letters, digits, underscores only'),
  name: z.string().min(1, 'Name required').max(255),
  description: z.string().optional(),
  creditHours: z.coerce.number().min(1, 'Min 1').max(20, 'Max 20'),
});

const courseEditSchema = courseSchema.omit({ code: true });

const cloSchema = z.object({
  code: z.string().min(1, 'Code required').max(20),
  title: z.string().min(1, 'Title required').max(255),
  description: z.string().optional(),
  orderIndex: z.coerce.number().int().min(1, 'Min 1'),
});

type CourseForm = z.infer<typeof courseSchema>;
type CourseEditForm = z.infer<typeof courseEditSchema>;
type CloForm = z.infer<typeof cloSchema>;

// ---------------------------------------------------------------------------
// CLO sub-panel
// ---------------------------------------------------------------------------
function CloPanel({ courseId }: { courseId: UUID }) {
  const qc = useQueryClient();
  const [showAdd, setShowAdd] = useState(false);
  const [editing, setEditing] = useState<CourseCloItem | null>(null);

  const { data: clos = [], isLoading } = useQuery<CourseCloItem[]>({
    queryKey: ['courses', courseId, 'clos'],
    queryFn: () => api.get(`/courses/${courseId}/clos`).then((r) => r.data),
  });

  const sortedClos = [...clos].sort((a, b) => a.orderIndex - b.orderIndex);

  const invalidate = () => qc.invalidateQueries({ queryKey: ['courses', courseId, 'clos'] });

  const addMut = useMutation({
    mutationFn: (body: CloForm) =>
      api.post<CourseCloItem>(`/admin/courses/${courseId}/clos`, body).then((r) => r.data),
    onSuccess: () => {
      invalidate();
      setShowAdd(false);
      addForm.reset({ orderIndex: sortedClos.length + 2 });
    },
  });

  const editMut = useMutation({
    mutationFn: ({ id, body }: { id: UUID; body: CloForm }) =>
      api
        .put<CourseCloItem>(`/admin/courses/${courseId}/clos/${id}`, body)
        .then((r) => r.data),
    onSuccess: () => {
      invalidate();
      setEditing(null);
    },
  });

  const deleteMut = useMutation({
    mutationFn: (id: UUID) => api.delete(`/admin/courses/${courseId}/clos/${id}`),
    onSuccess: () => invalidate(),
  });

  const addForm = useForm<CloForm>({
    resolver: zodResolver(cloSchema),
    defaultValues: { orderIndex: sortedClos.length + 1 },
  });

  const editForm = useForm<CloForm>({ resolver: zodResolver(cloSchema) });

  function openEdit(c: CourseCloItem) {
    editForm.reset({
      code: c.code,
      title: c.title,
      description: c.description ?? '',
      orderIndex: c.orderIndex,
    });
    setEditing(c);
  }

  return (
    <div className="mt-3 space-y-3">
      <div className="flex items-center justify-between">
        <h5 className="text-sm font-semibold text-gray-700">
          Course Learning Outcomes (CLOs)
        </h5>
        <Button size="sm" variant="secondary" onClick={() => setShowAdd(true)}>
          <Plus size={13} className="mr-1" />
          Add CLO
        </Button>
      </div>

      {isLoading ? (
        <div className="flex justify-center py-2">
          <Spinner size="sm" />
        </div>
      ) : sortedClos.length === 0 ? (
        <p className="rounded-lg border border-dashed border-gray-200 py-4 text-center text-xs text-gray-400">
          No CLOs defined yet. Add the first one.
        </p>
      ) : (
        <div className="space-y-1.5">
          {sortedClos.map((c) => (
            <div
              key={c.id}
              className="flex items-start justify-between rounded-lg border border-gray-100 bg-white px-3 py-2"
            >
              <div className="min-w-0 flex-1">
                <div className="flex items-baseline gap-2">
                  <span className="flex-shrink-0 rounded bg-primary-100 px-1.5 py-0.5 text-xs font-bold text-primary-700">
                    {c.code}
                  </span>
                  <span className="text-sm text-gray-800">{c.title}</span>
                </div>
                {c.description && (
                  <p className="mt-0.5 line-clamp-1 pl-10 text-xs text-gray-400">
                    {c.description}
                  </p>
                )}
              </div>
              <div className="ml-2 flex flex-shrink-0 items-center gap-1">
                <span className="mr-1 text-xs text-gray-300">#{c.orderIndex}</span>
                <button
                  onClick={() => openEdit(c)}
                  className="rounded p-1 text-gray-400 hover:text-gray-700"
                  title="Edit CLO"
                >
                  <Edit2 size={13} />
                </button>
                <button
                  onClick={() => {
                    if (confirm(`Delete CLO "${c.code}"?`)) deleteMut.mutate(c.id);
                  }}
                  className="rounded p-1 text-gray-400 hover:text-red-500"
                  title="Delete CLO"
                >
                  <Trash2 size={13} />
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Add CLO Modal */}
      {showAdd && (
        <Modal open onClose={() => setShowAdd(false)} title="Add CLO">
          <form
            onSubmit={addForm.handleSubmit((d) => addMut.mutate(d))}
            className="space-y-4"
          >
            <div className="grid grid-cols-2 gap-3">
              <Input
                label="Code"
                placeholder="CLO1"
                error={addForm.formState.errors.code?.message}
                {...addForm.register('code')}
              />
              <Input
                label="Order Index"
                type="number"
                min="1"
                error={addForm.formState.errors.orderIndex?.message}
                {...addForm.register('orderIndex')}
              />
            </div>
            <Input
              label="Title"
              placeholder="e.g. Apply OOP principles…"
              error={addForm.formState.errors.title?.message}
              {...addForm.register('title')}
            />
            <div>
              <label className="mb-1 block text-sm font-medium text-gray-700">
                Description
              </label>
              <textarea
                {...addForm.register('description')}
                rows={2}
                className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500"
                placeholder="Optional detail…"
              />
            </div>
            {addMut.isError && (
              <p className="text-xs text-red-600">Failed to add CLO. Please try again.</p>
            )}
            <div className="flex justify-end gap-2">
              <Button type="button" variant="secondary" onClick={() => setShowAdd(false)}>
                Cancel
              </Button>
              <Button type="submit" loading={addMut.isPending}>
                Add CLO
              </Button>
            </div>
          </form>
        </Modal>
      )}

      {/* Edit CLO Modal */}
      {editing && (
        <Modal
          open
          onClose={() => setEditing(null)}
          title={`Edit CLO — ${editing.code}`}
        >
          <form
            onSubmit={editForm.handleSubmit((d) =>
              editMut.mutate({ id: editing.id, body: d }),
            )}
            className="space-y-4"
          >
            <div className="grid grid-cols-2 gap-3">
              <Input
                label="Code"
                error={editForm.formState.errors.code?.message}
                {...editForm.register('code')}
              />
              <Input
                label="Order Index"
                type="number"
                min="1"
                error={editForm.formState.errors.orderIndex?.message}
                {...editForm.register('orderIndex')}
              />
            </div>
            <Input
              label="Title"
              error={editForm.formState.errors.title?.message}
              {...editForm.register('title')}
            />
            <div>
              <label className="mb-1 block text-sm font-medium text-gray-700">
                Description
              </label>
              <textarea
                {...editForm.register('description')}
                rows={2}
                className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500"
              />
            </div>
            {editMut.isError && (
              <p className="text-xs text-red-600">Failed to update CLO. Please try again.</p>
            )}
            <div className="flex justify-end gap-2">
              <Button type="button" variant="secondary" onClick={() => setEditing(null)}>
                Cancel
              </Button>
              <Button type="submit" loading={editMut.isPending}>
                Save Changes
              </Button>
            </div>
          </form>
        </Modal>
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Page
// ---------------------------------------------------------------------------
export default function CoursesCatalogPage() {
  const qc = useQueryClient();
  const [showCreate, setShowCreate] = useState(false);
  const [editing, setEditing] = useState<CourseCatalogItem | null>(null);
  const [expanded, setExpanded] = useState<UUID | null>(null);
  const [statusFilter, setStatusFilter] = useState<'ALL' | 'ACTIVE' | 'INACTIVE'>('ALL');

  const { data, isLoading } = useQuery<Page<CourseCatalogItem>>({
    queryKey: ['admin', 'courses', 'catalog'],
    queryFn: () => api.get('/courses?size=200').then((r) => r.data),
  });

  const allCourses = data?.content ?? [];
  const courses =
    statusFilter === 'ALL'
      ? allCourses
      : allCourses.filter((c) => c.status === statusFilter);

  const invalidate = () =>
    qc.invalidateQueries({ queryKey: ['admin', 'courses', 'catalog'] });

  // Create
  const createMut = useMutation({
    mutationFn: (body: CourseForm) =>
      api.post<CourseCatalogItem>('/admin/courses', body).then((r) => r.data),
    onSuccess: () => {
      invalidate();
      setShowCreate(false);
      createForm.reset();
    },
  });

  // Edit
  const editMut = useMutation({
    mutationFn: ({ id, body }: { id: UUID; body: CourseEditForm }) =>
      api
        .put<CourseCatalogItem>(`/admin/courses/${id}`, body)
        .then((r) => r.data),
    onSuccess: () => {
      invalidate();
      setEditing(null);
    },
  });

  // Status toggle
  const statusMut = useMutation({
    mutationFn: ({ id, status }: { id: UUID; status: 'ACTIVE' | 'INACTIVE' }) =>
      api
        .patch<CourseCatalogItem>(`/admin/courses/${id}/status`, { status })
        .then((r) => r.data),
    onSuccess: () => invalidate(),
  });

  const createForm = useForm<CourseForm>({ resolver: zodResolver(courseSchema) });
  const editForm = useForm<CourseEditForm>({
    resolver: zodResolver(courseEditSchema),
  });

  function openEdit(c: CourseCatalogItem) {
    editForm.reset({
      name: c.name,
      description: c.description ?? '',
      creditHours: c.creditHours,
    });
    setEditing(c);
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Course Catalog</h1>
          <p className="mt-1 text-sm text-gray-500">
            Manage courses and their learning outcomes (CLOs)
          </p>
        </div>
        <Button onClick={() => { setShowCreate(true); createForm.reset(); }}>
          <Plus size={16} className="mr-1.5" />
          Add Course
        </Button>
      </div>

      {/* Filter bar */}
      <div className="flex items-center gap-2">
        {(['ALL', 'ACTIVE', 'INACTIVE'] as const).map((f) => (
          <button
            key={f}
            onClick={() => setStatusFilter(f)}
            className={`rounded-full px-3 py-1 text-xs font-medium transition-colors ${
              statusFilter === f
                ? 'bg-primary-600 text-white'
                : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
            }`}
          >
            {f === 'ALL' ? 'All' : f.charAt(0) + f.slice(1).toLowerCase()}
          </button>
        ))}
        {!isLoading && (
          <span className="ml-auto text-xs text-gray-400">
            {courses.length} course{courses.length !== 1 ? 's' : ''}
          </span>
        )}
      </div>

      {/* List */}
      {isLoading ? (
        <div className="flex justify-center py-16">
          <Spinner />
        </div>
      ) : courses.length === 0 ? (
        <div className="flex flex-col items-center py-16 text-center">
          <BookOpen size={40} className="mb-3 text-gray-300" />
          <p className="text-sm text-gray-400">
            {statusFilter === 'ALL'
              ? 'No courses in the catalog yet.'
              : `No ${statusFilter.toLowerCase()} courses.`}
          </p>
        </div>
      ) : (
        <div className="space-y-3">
          {courses.map((c) => {
            const isExpanded = expanded === c.id;
            const toggleStatus = () =>
              statusMut.mutate({
                id: c.id,
                status: c.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE',
              });

            return (
              <Card key={c.id} className="p-0 overflow-hidden">
                {/* Course row */}
                <div className="flex items-center gap-3 p-4">
                  <div className="flex h-10 w-10 flex-shrink-0 items-center justify-center rounded-lg bg-primary-50 text-primary-600">
                    <BookOpen size={20} />
                  </div>

                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="font-bold text-gray-900">{c.code}</span>
                      <span className="text-gray-400">—</span>
                      <span className="font-medium text-gray-800">{c.name}</span>
                      <Badge variant={c.status === 'ACTIVE' ? 'success' : 'default'}>
                        {c.status}
                      </Badge>
                    </div>
                    <p className="mt-0.5 text-xs text-gray-400">
                      {c.creditHours} credit hr{c.creditHours !== 1 ? 's' : ''}
                      {c.description && ` · ${c.description}`}
                    </p>
                  </div>

                  {/* Actions */}
                  <div className="flex flex-shrink-0 items-center gap-0.5">
                    <button
                      onClick={toggleStatus}
                      disabled={statusMut.isPending}
                      className="rounded p-1.5 text-gray-400 hover:text-primary-600 disabled:opacity-50"
                      title={c.status === 'ACTIVE' ? 'Deactivate' : 'Activate'}
                    >
                      {c.status === 'ACTIVE' ? (
                        <ToggleRight size={20} className="text-primary-600" />
                      ) : (
                        <ToggleLeft size={20} />
                      )}
                    </button>
                    <button
                      onClick={() => openEdit(c)}
                      className="rounded p-1.5 text-gray-400 hover:text-gray-700"
                      title="Edit course"
                    >
                      <Edit2 size={16} />
                    </button>
                    <button
                      onClick={() => setExpanded(isExpanded ? null : c.id)}
                      className="rounded p-1.5 text-primary-400 hover:text-primary-700"
                      title="Manage CLOs"
                    >
                      {isExpanded ? (
                        <ChevronUp size={16} />
                      ) : (
                        <ChevronDown size={16} />
                      )}
                    </button>
                  </div>
                </div>

                {/* CLO panel */}
                {isExpanded && (
                  <div className="border-t border-gray-100 bg-gray-50 px-4 pb-4">
                    <CloPanel courseId={c.id} />
                  </div>
                )}
              </Card>
            );
          })}
        </div>
      )}

      {/* Create Course Modal */}
      {showCreate && (
        <Modal
          open
          onClose={() => setShowCreate(false)}
          title="Create Course"
          maxWidth="max-w-lg"
        >
          <form
            onSubmit={createForm.handleSubmit((d) => createMut.mutate(d))}
            className="space-y-4"
          >
            <div className="grid grid-cols-2 gap-3">
              <Input
                label="Course Code"
                placeholder="CS101"
                error={createForm.formState.errors.code?.message}
                {...createForm.register('code')}
              />
              <Input
                label="Credit Hours"
                type="number"
                min="1"
                max="20"
                error={createForm.formState.errors.creditHours?.message}
                {...createForm.register('creditHours')}
              />
            </div>
            <Input
              label="Course Name"
              placeholder="Introduction to Programming"
              error={createForm.formState.errors.name?.message}
              {...createForm.register('name')}
            />
            <div>
              <label className="mb-1 block text-sm font-medium text-gray-700">
                Description
              </label>
              <textarea
                {...createForm.register('description')}
                rows={3}
                className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500"
                placeholder="Optional course description…"
              />
            </div>
            {createMut.isError && (
              <p className="text-xs text-red-600">
                Failed to create course. Please try again.
              </p>
            )}
            <div className="flex justify-end gap-2 pt-1">
              <Button
                type="button"
                variant="secondary"
                onClick={() => setShowCreate(false)}
              >
                Cancel
              </Button>
              <Button type="submit" loading={createMut.isPending}>
                Create Course
              </Button>
            </div>
          </form>
        </Modal>
      )}

      {/* Edit Course Modal */}
      {editing && (
        <Modal
          open
          onClose={() => setEditing(null)}
          title={`Edit — ${editing.code}`}
          maxWidth="max-w-lg"
        >
          <form
            onSubmit={editForm.handleSubmit((d) =>
              editMut.mutate({ id: editing.id, body: d }),
            )}
            className="space-y-4"
          >
            <Input
              label="Course Name"
              error={editForm.formState.errors.name?.message}
              {...editForm.register('name')}
            />
            <Input
              label="Credit Hours"
              type="number"
              min="1"
              max="20"
              error={editForm.formState.errors.creditHours?.message}
              {...editForm.register('creditHours')}
            />
            <div>
              <label className="mb-1 block text-sm font-medium text-gray-700">
                Description
              </label>
              <textarea
                {...editForm.register('description')}
                rows={3}
                className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500"
              />
            </div>
            {editMut.isError && (
              <p className="text-xs text-red-600">
                Failed to update course. Please try again.
              </p>
            )}
            <div className="flex justify-end gap-2 pt-1">
              <Button
                type="button"
                variant="secondary"
                onClick={() => setEditing(null)}
              >
                Cancel
              </Button>
              <Button type="submit" loading={editMut.isPending}>
                Save Changes
              </Button>
            </div>
          </form>
        </Modal>
      )}
    </div>
  );
}
