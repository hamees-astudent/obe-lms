import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import {
  Plus, Edit2, ToggleLeft, ToggleRight, ChevronDown, ChevronUp,
  BookOpen, Calendar, Trash2, Lock, Unlock,
} from 'lucide-react';
import api from '@/lib/api';
import Modal from '@/components/ui/Modal';
import Button from '@/components/ui/Button';
import Input from '@/components/ui/Input';
import Badge from '@/components/ui/Badge';
import Spinner from '@/components/ui/Spinner';
import Card from '@/components/ui/Card';
import type {
  ProgramSummaryResponse,
  PloResponse,
  SemesterResponse,
  Page,
  UUID,
} from '@/types/api';

// ─── Schemas ─────────────────────────────────────────────────────────────────

const programSchema = z.object({
  name:          z.string().min(1, 'Name required').max(180),
  code:          z.string().min(1, 'Code required').max(20).regex(/^[A-Z0-9_]+$/, 'Uppercase letters, digits, underscores only'),
  description:   z.string().optional(),
  durationYears: z.coerce.number().min(1).max(10),
});

const ploSchema = z.object({
  code:        z.string().min(1, 'Code required').max(20),
  title:       z.string().min(1, 'Title required').max(255),
  description: z.string().optional(),
  orderIndex:  z.coerce.number().min(1),
});

const semesterSchema = z.object({
  name:      z.string().min(1, 'Name required').max(100),
  startDate: z.string().min(1, 'Start date required'),
  endDate:   z.string().min(1, 'End date required'),
});

type ProgramForm  = z.infer<typeof programSchema>;
type PloForm      = z.infer<typeof ploSchema>;
type SemesterForm = z.infer<typeof semesterSchema>;

// ─── Helpers ─────────────────────────────────────────────────────────────────

function semesterStatusBadge(s: string) {
  const map: Record<string, 'success' | 'warning'> = {
    OPEN:   'success',
    CLOSED: 'warning',
  };
  return <Badge variant={map[s] ?? 'info'}>{s}</Badge>;
}

// ─── PLO sub-panel ───────────────────────────────────────────────────────────

function PloPanel({ programId }: { programId: UUID }) {
  const qc = useQueryClient();
  const [showAdd, setShowAdd] = useState(false);
  const [editing, setEditing] = useState<PloResponse | null>(null);

  const { data: plos = [], isLoading } = useQuery<PloResponse[]>({
    queryKey: ['plos', programId],
    queryFn: () => api.get(`/programs/${programId}/plos`).then((r) => r.data),
  });

  const invalidate = () => qc.invalidateQueries({ queryKey: ['plos', programId] });

  const addMut = useMutation({
    mutationFn: (body: PloForm) => api.post(`/admin/programs/${programId}/plos`, body),
    onSuccess: () => { invalidate(); setShowAdd(false); },
  });

  const editMut = useMutation({
    mutationFn: ({ id, body }: { id: UUID; body: PloForm }) =>
      api.put(`/admin/programs/${programId}/plos/${id}`, body),
    onSuccess: () => { invalidate(); setEditing(null); },
  });

  const deleteMut = useMutation({
    mutationFn: (id: UUID) => api.delete(`/admin/programs/${programId}/plos/${id}`),
    onSuccess: () => invalidate(),
  });

  const addForm  = useForm<PloForm>({ resolver: zodResolver(ploSchema), defaultValues: { orderIndex: (plos.length || 0) + 1 } });
  const editForm = useForm<PloForm>({ resolver: zodResolver(ploSchema) });

  function openEdit(p: PloResponse) {
    editForm.reset({ code: p.code, title: p.title, description: p.description ?? '', orderIndex: p.orderIndex });
    setEditing(p);
  }

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <h4 className="font-semibold text-gray-800">Programme Learning Outcomes</h4>
        <Button size="sm" onClick={() => { addForm.reset({ orderIndex: plos.length + 1 }); setShowAdd(true); }}>
          <Plus size={14} className="mr-1" /> Add PLO
        </Button>
      </div>

      {isLoading ? (
        <div className="flex justify-center py-4"><Spinner /></div>
      ) : plos.length === 0 ? (
        <p className="text-sm text-gray-400 text-center py-4">No PLOs defined yet.</p>
      ) : (
        <div className="space-y-2">
          {plos.map((p) => (
            <div key={p.id} className="flex items-start justify-between rounded-lg border border-gray-100 bg-gray-50 px-4 py-3">
              <div>
                <span className="text-xs font-bold text-primary-700 mr-2">{p.code}</span>
                <span className="text-sm font-medium text-gray-800">{p.title}</span>
                {p.description && <p className="text-xs text-gray-500 mt-0.5">{p.description}</p>}
              </div>
              <div className="flex gap-1 shrink-0 ml-4">
                <button onClick={() => openEdit(p)} className="rounded p-1 text-gray-400 hover:bg-white hover:text-gray-700 transition">
                  <Edit2 size={14} />
                </button>
                <button
                  onClick={() => { if (confirm(`Delete PLO "${p.code}"?`)) deleteMut.mutate(p.id); }}
                  className="rounded p-1 text-gray-400 hover:bg-white hover:text-red-600 transition"
                >
                  <Trash2 size={14} />
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Add PLO Modal */}
      <Modal open={showAdd} onClose={() => setShowAdd(false)} title="Add PLO">
        <form onSubmit={addForm.handleSubmit((d) => addMut.mutate(d))} className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Code</label>
              <Input {...addForm.register('code')} placeholder="PLO1" />
              {addForm.formState.errors.code && <p className="text-xs text-red-500 mt-1">{addForm.formState.errors.code.message}</p>}
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Order</label>
              <Input type="number" {...addForm.register('orderIndex')} />
            </div>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Title</label>
            <Input {...addForm.register('title')} placeholder="e.g. Apply engineering knowledge" />
            {addForm.formState.errors.title && <p className="text-xs text-red-500 mt-1">{addForm.formState.errors.title.message}</p>}
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Description (optional)</label>
            <textarea
              {...addForm.register('description')}
              rows={2}
              className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
            />
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="secondary" onClick={() => setShowAdd(false)}>Cancel</Button>
            <Button type="submit" loading={addMut.isPending}>Add PLO</Button>
          </div>
        </form>
      </Modal>

      {/* Edit PLO Modal */}
      <Modal open={!!editing} onClose={() => setEditing(null)} title="Edit PLO">
        <form onSubmit={editForm.handleSubmit((d) => editing && editMut.mutate({ id: editing.id, body: d }))} className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Code</label>
              <Input {...editForm.register('code')} />
              {editForm.formState.errors.code && <p className="text-xs text-red-500 mt-1">{editForm.formState.errors.code.message}</p>}
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Order</label>
              <Input type="number" {...editForm.register('orderIndex')} />
            </div>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Title</label>
            <Input {...editForm.register('title')} />
            {editForm.formState.errors.title && <p className="text-xs text-red-500 mt-1">{editForm.formState.errors.title.message}</p>}
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Description (optional)</label>
            <textarea
              {...editForm.register('description')}
              rows={2}
              className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
            />
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="secondary" onClick={() => setEditing(null)}>Cancel</Button>
            <Button type="submit" loading={editMut.isPending}>Save Changes</Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}

// ─── Semester sub-panel ───────────────────────────────────────────────────────

function SemestersPanel({ programId }: { programId: UUID }) {
  const qc = useQueryClient();
  const [showAdd,  setShowAdd]  = useState(false);
  const [editing,  setEditing]  = useState<SemesterResponse | null>(null);

  const { data: semesters = [], isLoading } = useQuery<SemesterResponse[]>({
    queryKey: ['semesters', programId],
    queryFn: () => api.get(`/programs/${programId}/semesters`).then((r) => r.data),
  });

  const invalidate = () => qc.invalidateQueries({ queryKey: ['semesters', programId] });

  const addMut = useMutation({
    mutationFn: (body: SemesterForm) => api.post(`/admin/programs/${programId}/semesters`, body),
    onSuccess: () => { invalidate(); setShowAdd(false); },
  });

  const editMut = useMutation({
    mutationFn: ({ id, body }: { id: UUID; body: SemesterForm }) =>
      api.put(`/admin/semesters/${id}`, body),
    onSuccess: () => { invalidate(); setEditing(null); },
  });

  const closeMut  = useMutation({
    mutationFn: (id: UUID) => api.post(`/admin/semesters/${id}/close`),
    onSuccess: () => invalidate(),
  });

  const reopenMut = useMutation({
    mutationFn: (id: UUID) => api.post(`/admin/semesters/${id}/reopen`),
    onSuccess: () => invalidate(),
  });

  const addForm  = useForm<SemesterForm>({ resolver: zodResolver(semesterSchema) });
  const editForm = useForm<SemesterForm>({ resolver: zodResolver(semesterSchema) });

  function openEdit(s: SemesterResponse) {
    editForm.reset({
      name:      s.name,
      startDate: s.startDate.substring(0, 10),
      endDate:   s.endDate.substring(0, 10),
    });
    setEditing(s);
  }

  const SemesterForm = ({ form, onSubmit, loading, onCancel, submitLabel }: {
    form: ReturnType<typeof useForm<SemesterForm>>;
    onSubmit: (d: SemesterForm) => void;
    loading: boolean;
    onCancel: () => void;
    submitLabel: string;
  }) => (
    <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">Name</label>
        <Input {...form.register('name')} placeholder="e.g. Semester 1 2024/2025" />
        {form.formState.errors.name && <p className="text-xs text-red-500 mt-1">{form.formState.errors.name.message}</p>}
      </div>
      <div className="grid grid-cols-2 gap-4">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Start Date</label>
          <Input type="date" {...form.register('startDate')} />
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">End Date</label>
          <Input type="date" {...form.register('endDate')} />
        </div>
      </div>
      <div className="flex justify-end gap-2 pt-2">
        <Button type="button" variant="secondary" onClick={onCancel}>Cancel</Button>
        <Button type="submit" loading={loading}>{submitLabel}</Button>
      </div>
    </form>
  );

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <h4 className="font-semibold text-gray-800">Semesters</h4>
        <Button size="sm" onClick={() => { addForm.reset(); setShowAdd(true); }}>
          <Plus size={14} className="mr-1" /> Add Semester
        </Button>
      </div>

      {isLoading ? (
        <div className="flex justify-center py-4"><Spinner /></div>
      ) : semesters.length === 0 ? (
        <p className="text-sm text-gray-400 text-center py-4">No semesters yet.</p>
      ) : (
        <div className="space-y-2">
          {semesters.map((s) => (
            <div key={s.id} className="flex items-center justify-between rounded-lg border border-gray-100 bg-gray-50 px-4 py-3">
              <div>
                <span className="text-sm font-medium text-gray-800 mr-3">{s.name}</span>
                {semesterStatusBadge(s.status)}
                <p className="text-xs text-gray-500 mt-0.5">
                  {new Date(s.startDate).toLocaleDateString()} – {new Date(s.endDate).toLocaleDateString()}
                </p>
              </div>
              <div className="flex gap-1 shrink-0 ml-4">
                <button onClick={() => openEdit(s)} className="rounded p-1 text-gray-400 hover:bg-white hover:text-gray-700 transition" title="Edit">
                  <Edit2 size={14} />
                </button>
                {s.status !== 'CLOSED' && (
                  <button
                    onClick={() => closeMut.mutate(s.id)}
                    className="rounded p-1 text-gray-400 hover:bg-white hover:text-orange-600 transition"
                    title="Close semester"
                  >
                    <Lock size={14} />
                  </button>
                )}
                {s.status === 'CLOSED' && (
                  <button
                    onClick={() => reopenMut.mutate(s.id)}
                    className="rounded p-1 text-gray-400 hover:bg-white hover:text-green-600 transition"
                    title="Reopen semester"
                  >
                    <Unlock size={14} />
                  </button>
                )}
              </div>
            </div>
          ))}
        </div>
      )}

      <Modal open={showAdd} onClose={() => setShowAdd(false)} title="Add Semester">
        <SemesterForm form={addForm} onSubmit={(d) => addMut.mutate(d)} loading={addMut.isPending} onCancel={() => setShowAdd(false)} submitLabel="Add Semester" />
      </Modal>

      <Modal open={!!editing} onClose={() => setEditing(null)} title="Edit Semester">
        <SemesterForm form={editForm} onSubmit={(d) => editing && editMut.mutate({ id: editing.id, body: d })} loading={editMut.isPending} onCancel={() => setEditing(null)} submitLabel="Save Changes" />
      </Modal>
    </div>
  );
}

// ─── Program row ──────────────────────────────────────────────────────────────

function ProgramRow({
  program,
  onEdit,
  onToggleStatus,
}: {
  program: ProgramSummaryResponse;
  onEdit: (p: ProgramSummaryResponse) => void;
  onToggleStatus: (p: ProgramSummaryResponse) => void;
}) {
  const [expanded, setExpanded] = useState(false);
  const [tab, setTab] = useState<'semesters' | 'plos'>('semesters');

  return (
    <div className="rounded-xl border border-gray-200 bg-white shadow-sm overflow-hidden">
      <div className="flex items-center justify-between px-5 py-4">
        <div className="flex items-center gap-4">
          <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary-50 text-primary-700">
            <BookOpen size={18} />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <span className="font-semibold text-gray-900">{program.name}</span>
              <Badge variant="info">{program.code}</Badge>
              <Badge variant={program.status === 'ACTIVE' ? 'success' : 'warning'}>
                {program.status}
              </Badge>
            </div>
            <p className="text-xs text-gray-500 mt-0.5">{program.durationYears} year{program.durationYears !== 1 ? 's' : ''}</p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <button onClick={() => onEdit(program)} className="rounded p-1.5 text-gray-400 hover:bg-gray-100 hover:text-gray-700 transition" title="Edit">
            <Edit2 size={15} />
          </button>
          <button
            onClick={() => onToggleStatus(program)}
            className="rounded p-1.5 text-gray-400 hover:bg-gray-100 transition"
            title={program.status === 'ACTIVE' ? 'Deactivate' : 'Activate'}
          >
            {program.status === 'ACTIVE' ? <ToggleRight size={18} className="text-green-500" /> : <ToggleLeft size={18} className="text-gray-400" />}
          </button>
          <button
            onClick={() => setExpanded((e) => !e)}
            className="rounded p-1.5 text-gray-400 hover:bg-gray-100 transition"
            title="Expand"
          >
            {expanded ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
          </button>
        </div>
      </div>

      {expanded && (
        <div className="border-t border-gray-100 px-5 py-4">
          {/* Tabs */}
          <div className="flex gap-4 mb-4 border-b border-gray-200">
            <button
              onClick={() => setTab('semesters')}
              className={`pb-2 text-sm font-medium border-b-2 transition-colors ${tab === 'semesters' ? 'border-primary-600 text-primary-700' : 'border-transparent text-gray-500 hover:text-gray-700'}`}
            >
              <Calendar size={14} className="inline mr-1 mb-0.5" />
              Semesters
            </button>
            <button
              onClick={() => setTab('plos')}
              className={`pb-2 text-sm font-medium border-b-2 transition-colors ${tab === 'plos' ? 'border-primary-600 text-primary-700' : 'border-transparent text-gray-500 hover:text-gray-700'}`}
            >
              PLOs
            </button>
          </div>

          {tab === 'semesters' ? (
            <SemestersPanel programId={program.id} />
          ) : (
            <PloPanel programId={program.id} />
          )}
        </div>
      )}
    </div>
  );
}

// ─── Main page ────────────────────────────────────────────────────────────────

export default function ProgramsPage() {
  const qc = useQueryClient();
  const [showCreate, setShowCreate] = useState(false);
  const [editing,    setEditing]    = useState<ProgramSummaryResponse | null>(null);

  const { data, isLoading } = useQuery<Page<ProgramSummaryResponse>>({
    queryKey: ['programs'],
    queryFn: () => api.get('/programs', { params: { size: 100 } }).then((r) => r.data),
  });

  const programs = data?.content ?? [];

  const invalidate = () => qc.invalidateQueries({ queryKey: ['programs'] });

  const createMut = useMutation({
    mutationFn: (body: ProgramForm) => api.post('/admin/programs', body),
    onSuccess: () => { invalidate(); setShowCreate(false); },
  });

  const editMut = useMutation({
    mutationFn: ({ id, body }: { id: UUID; body: ProgramForm }) =>
      api.put(`/admin/programs/${id}`, body),
    onSuccess: () => { invalidate(); setEditing(null); },
  });

  const toggleStatusMut = useMutation({
    mutationFn: (p: ProgramSummaryResponse) =>
      api.patch(`/admin/programs/${p.id}/status`, { status: p.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE' }),
    onSuccess: () => invalidate(),
  });

  const createForm = useForm<ProgramForm>({ resolver: zodResolver(programSchema), defaultValues: { durationYears: 4 } });
  const editForm   = useForm<ProgramForm>({ resolver: zodResolver(programSchema) });

  function openEdit(p: ProgramSummaryResponse) {
    editForm.reset({ name: p.name, code: p.code, durationYears: p.durationYears });
    setEditing(p);
  }

  const ProgramFormBody = ({ form, onSubmit, loading, onCancel, submitLabel }: {
    form: ReturnType<typeof useForm<ProgramForm>>;
    onSubmit: (d: ProgramForm) => void;
    loading: boolean;
    onCancel: () => void;
    submitLabel: string;
  }) => (
    <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
      <div className="grid grid-cols-2 gap-4">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Program Name</label>
          <Input {...form.register('name')} placeholder="Bachelor of Computer Science" />
          {form.formState.errors.name && <p className="text-xs text-red-500 mt-1">{form.formState.errors.name.message}</p>}
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Code</label>
          <Input {...form.register('code')} placeholder="BCS" />
          {form.formState.errors.code && <p className="text-xs text-red-500 mt-1">{form.formState.errors.code.message}</p>}
        </div>
      </div>
      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">Description (optional)</label>
        <textarea
          {...form.register('description')}
          rows={2}
          className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
        />
      </div>
      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">Duration (years)</label>
        <Input type="number" min={1} max={10} {...form.register('durationYears')} />
        {form.formState.errors.durationYears && <p className="text-xs text-red-500 mt-1">{form.formState.errors.durationYears.message}</p>}
      </div>
      <div className="flex justify-end gap-2 pt-2">
        <Button type="button" variant="secondary" onClick={onCancel}>Cancel</Button>
        <Button type="submit" loading={loading}>{submitLabel}</Button>
      </div>
    </form>
  );

  return (
    <div className="space-y-5 animate-slide-up">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900">Programs</h1>
        <Button onClick={() => { createForm.reset({ durationYears: 4 }); setShowCreate(true); }}>
          <Plus size={16} className="mr-1" /> Add Program
        </Button>
      </div>

      {isLoading ? (
        <div className="flex justify-center py-16"><Spinner size="lg" /></div>
      ) : programs.length === 0 ? (
        <Card>
          <div className="flex flex-col items-center py-12 text-gray-400">
            <BookOpen size={40} className="mb-3 opacity-40" />
            <p className="font-medium">No programs yet</p>
            <p className="text-sm">Create your first academic program above.</p>
          </div>
        </Card>
      ) : (
        <div className="space-y-3">
          {programs.map((p) => (
            <ProgramRow
              key={p.id}
              program={p}
              onEdit={openEdit}
              onToggleStatus={(prog) => toggleStatusMut.mutate(prog)}
            />
          ))}
        </div>
      )}

      {/* Create Program Modal */}
      <Modal open={showCreate} onClose={() => setShowCreate(false)} title="Create Program">
        <ProgramFormBody
          form={createForm}
          onSubmit={(d) => createMut.mutate(d)}
          loading={createMut.isPending}
          onCancel={() => setShowCreate(false)}
          submitLabel="Create Program"
        />
      </Modal>

      {/* Edit Program Modal */}
      <Modal open={!!editing} onClose={() => setEditing(null)} title="Edit Program">
        <ProgramFormBody
          form={editForm}
          onSubmit={(d) => editing && editMut.mutate({ id: editing.id, body: d })}
          loading={editMut.isPending}
          onCancel={() => setEditing(null)}
          submitLabel="Save Changes"
        />
      </Modal>
    </div>
  );
}
