import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Plus, Edit2, Trash2, ChevronDown, ChevronUp, Star } from 'lucide-react';
import api from '@/lib/api';
import Modal from '@/components/ui/Modal';
import Button from '@/components/ui/Button';
import Input from '@/components/ui/Input';
import Badge from '@/components/ui/Badge';
import Spinner from '@/components/ui/Spinner';
import Card from '@/components/ui/Card';
import type {
  GradingScaleResponse,
  GradingScaleEntryResponse,
  ProgramSummaryResponse,
  Page,
  UUID,
} from '@/types/api';

// ─── Schemas ─────────────────────────────────────────────────────────────────

const scaleSchema = z.object({
  name:      z.string().min(1, 'Name required'),
  programId: z.string().optional(),
  isDefault: z.boolean().optional(),
});

const entrySchema = z.object({
  gradeLetter:   z.string().min(1, 'Required').max(5),
  minPercentage: z.coerce.number().min(0).max(100),
  maxPercentage: z.coerce.number().min(0).max(100),
  gradePoints:   z.coerce.number().min(0).max(10),
  orderIndex:    z.coerce.number().min(1),
});

type ScaleForm = z.infer<typeof scaleSchema>;
type EntryForm = z.infer<typeof entrySchema>;

// ─── Entries sub-panel ────────────────────────────────────────────────────────

function EntriesPanel({ scale }: { scale: GradingScaleResponse }) {
  const qc = useQueryClient();
  const [showAdd, setShowAdd] = useState(false);

  const addForm = useForm<EntryForm>({
    resolver: zodResolver(entrySchema),
    defaultValues: { orderIndex: (scale.entries?.length ?? 0) + 1 },
  });

  const invalidate = () => qc.invalidateQueries({ queryKey: ['grading-scales'] });

  const addMut = useMutation({
    mutationFn: (body: EntryForm) => api.post(`/admin/grading-scales/${scale.id}/entries`, body),
    onSuccess: () => { invalidate(); setShowAdd(false); addForm.reset(); },
  });

  const deleteMut = useMutation({
    mutationFn: (entryId: UUID) => api.delete(`/admin/grading-scales/${scale.id}/entries/${entryId}`),
    onSuccess: () => invalidate(),
  });

  const entries: GradingScaleEntryResponse[] = [...(scale.entries ?? [])].sort((a, b) => b.minPercentage - a.minPercentage);

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <h5 className="font-medium text-gray-700">Grade Entries ({entries.length})</h5>
        <Button size="sm" onClick={() => { addForm.reset({ orderIndex: entries.length + 1 }); setShowAdd(true); }}>
          <Plus size={13} className="mr-1" /> Add Entry
        </Button>
      </div>

      {entries.length === 0 ? (
        <p className="text-xs text-gray-400 text-center py-3">No entries yet. Add grade thresholds.</p>
      ) : (
        <div className="overflow-x-auto rounded-lg border border-gray-100">
          <table className="w-full text-xs">
            <thead className="bg-gray-50 text-gray-600">
              <tr>
                <th className="px-3 py-2 text-left font-medium">Grade</th>
                <th className="px-3 py-2 text-left font-medium">Min %</th>
                <th className="px-3 py-2 text-left font-medium">Max %</th>
                <th className="px-3 py-2 text-left font-medium">Points</th>
                <th className="px-3 py-2 text-right font-medium">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {entries.map((e) => (
                <tr key={e.id} className="hover:bg-gray-50">
                  <td className="px-3 py-2 font-semibold text-primary-700">{e.gradeLetter}</td>
                  <td className="px-3 py-2 text-gray-600">{e.minPercentage}%</td>
                  <td className="px-3 py-2 text-gray-600">{e.maxPercentage}%</td>
                  <td className="px-3 py-2 text-gray-600">{e.gradePoints}</td>
                  <td className="px-3 py-2 text-right">
                    <button
                      onClick={() => { if (confirm(`Delete grade "${e.gradeLetter}"?`)) deleteMut.mutate(e.id); }}
                      className="rounded p-1 text-red-400 hover:bg-red-50 hover:text-red-600 transition"
                    >
                      <Trash2 size={12} />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <Modal open={showAdd} onClose={() => setShowAdd(false)} title="Add Grade Entry">
        <form onSubmit={addForm.handleSubmit((d) => addMut.mutate(d))} className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Letter Grade</label>
              <Input {...addForm.register('gradeLetter')} placeholder="A+" maxLength={5} />
              {addForm.formState.errors.gradeLetter && <p className="text-xs text-red-500 mt-1">{addForm.formState.errors.gradeLetter.message}</p>}
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Grade Points</label>
              <Input type="number" step="0.01" min={0} max={10} {...addForm.register('gradePoints')} placeholder="4.00" />
              {addForm.formState.errors.gradePoints && <p className="text-xs text-red-500 mt-1">{addForm.formState.errors.gradePoints.message}</p>}
            </div>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Min Percentage</label>
              <Input type="number" step="0.1" min={0} max={100} {...addForm.register('minPercentage')} placeholder="80" />
              {addForm.formState.errors.minPercentage && <p className="text-xs text-red-500 mt-1">{addForm.formState.errors.minPercentage.message}</p>}
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Max Percentage</label>
              <Input type="number" step="0.1" min={0} max={100} {...addForm.register('maxPercentage')} placeholder="100" />
              {addForm.formState.errors.maxPercentage && <p className="text-xs text-red-500 mt-1">{addForm.formState.errors.maxPercentage.message}</p>}
            </div>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Order Index</label>
            <Input type="number" min={1} {...addForm.register('orderIndex')} />
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="secondary" onClick={() => setShowAdd(false)}>Cancel</Button>
            <Button type="submit" loading={addMut.isPending}>Add Entry</Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}

// ─── Scale row ────────────────────────────────────────────────────────────────

function ScaleRow({
  scale,
  onEdit,
  onDelete,
}: {
  scale: GradingScaleResponse;
  onEdit: (s: GradingScaleResponse) => void;
  onDelete: (s: GradingScaleResponse) => void;
}) {
  const [expanded, setExpanded] = useState(false);

  return (
    <div className="rounded-xl border border-gray-200 bg-white shadow-sm overflow-hidden">
      <div className="flex items-center justify-between px-5 py-4">
        <div className="flex items-center gap-3">
          <div>
            <div className="flex items-center gap-2">
              <span className="font-semibold text-gray-900">{scale.name}</span>
              {scale.isDefault && (
                <Badge variant="warning">
                  <Star size={11} className="inline mr-0.5" />Default
                </Badge>
              )}
              <span className="text-xs text-gray-500">{scale.entries?.length ?? 0} entries</span>
            </div>
          </div>
        </div>
        <div className="flex items-center gap-2 shrink-0">
          <button
            onClick={() => onEdit(scale)}
            className="rounded p-1.5 text-gray-400 hover:bg-gray-100 hover:text-gray-700 transition"
            title="Edit"
          >
            <Edit2 size={14} />
          </button>
          <button
            onClick={() => onDelete(scale)}
            className="rounded p-1.5 text-gray-400 hover:bg-gray-100 hover:text-red-600 transition"
            title="Delete"
          >
            <Trash2 size={14} />
          </button>
          <button
            onClick={() => setExpanded((e) => !e)}
            className="rounded p-1.5 text-gray-400 hover:bg-gray-100 transition"
          >
            {expanded ? <ChevronUp size={15} /> : <ChevronDown size={15} />}
          </button>
        </div>
      </div>

      {expanded && (
        <div className="border-t border-gray-100 px-5 py-4">
          <EntriesPanel scale={scale} />
        </div>
      )}
    </div>
  );
}

// ─── Main page ────────────────────────────────────────────────────────────────

export default function GradingScalesPage() {
  const qc = useQueryClient();
  const [programFilter, setProgramFilter] = useState('');
  const [showCreate, setShowCreate] = useState(false);
  const [editing,    setEditing]    = useState<GradingScaleResponse | null>(null);

  const { data: programsData } = useQuery<Page<ProgramSummaryResponse>>({
    queryKey: ['programs'],
    queryFn: () => api.get('/programs', { params: { size: 100 } }).then((r) => r.data),
  });
  const programs = programsData?.content ?? [];

  const { data: scales = [], isLoading } = useQuery<GradingScaleResponse[]>({
    queryKey: ['grading-scales', programFilter],
    queryFn: () =>
      api
        .get('/admin/grading-scales', { params: programFilter ? { programId: programFilter } : {} })
        .then((r) => r.data),
  });

  const invalidate = () => qc.invalidateQueries({ queryKey: ['grading-scales'] });

  const createMut = useMutation({
    mutationFn: (body: ScaleForm) =>
      api.post('/admin/grading-scales', {
        name: body.name,
        programId: body.programId || null,
        isDefault: body.isDefault ?? false,
      }),
    onSuccess: () => { invalidate(); setShowCreate(false); },
  });

  const editMut = useMutation({
    mutationFn: ({ id, body }: { id: UUID; body: ScaleForm }) =>
      api.put(`/admin/grading-scales/${id}`, {
        name: body.name,
        programId: body.programId || null,
        isDefault: body.isDefault ?? false,
      }),
    onSuccess: () => { invalidate(); setEditing(null); },
  });

  const deleteMut = useMutation({
    mutationFn: (id: UUID) => api.delete(`/admin/grading-scales/${id}`),
    onSuccess: () => invalidate(),
  });

  const createForm = useForm<ScaleForm>({ resolver: zodResolver(scaleSchema) });
  const editForm   = useForm<ScaleForm>({ resolver: zodResolver(scaleSchema) });

  function openEdit(s: GradingScaleResponse) {
    editForm.reset({ name: s.name, programId: s.programId ?? '', isDefault: s.isDefault });
    setEditing(s);
  }

  function handleDelete(s: GradingScaleResponse) {
    if (confirm(`Delete grading scale "${s.name}"? This will remove all its entries.`)) {
      deleteMut.mutate(s.id);
    }
  }

  const ScaleFormBody = ({ form, onSubmit, loading, onCancel, label }: {
    form: ReturnType<typeof useForm<ScaleForm>>;
    onSubmit: (d: ScaleForm) => void;
    loading: boolean;
    onCancel: () => void;
    label: string;
  }) => (
    <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">Scale Name</label>
        <Input {...form.register('name')} placeholder="e.g. Standard Grading Scale" />
        {form.formState.errors.name && <p className="text-xs text-red-500 mt-1">{form.formState.errors.name.message}</p>}
      </div>
      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">Program (optional — leave blank for global)</label>
        <select
          {...form.register('programId')}
          className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
        >
          <option value="">Global (all programs)</option>
          {programs.map((p) => <option key={p.id} value={p.id}>{p.name} ({p.code})</option>)}
        </select>
      </div>
      <div className="flex items-center gap-2">
        <input type="checkbox" id="isDefault" {...form.register('isDefault')} className="rounded" />
        <label htmlFor="isDefault" className="text-sm text-gray-700">Set as default scale</label>
      </div>
      <div className="flex justify-end gap-2 pt-2">
        <Button type="button" variant="secondary" onClick={onCancel}>Cancel</Button>
        <Button type="submit" loading={loading}>{label}</Button>
      </div>
    </form>
  );

  return (
    <div className="space-y-5 animate-slide-up">
      {/* Header */}
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900">Grading Scales</h1>
        <Button onClick={() => { createForm.reset(); setShowCreate(true); }}>
          <Plus size={16} className="mr-1" /> Add Scale
        </Button>
      </div>

      {/* Filter */}
      <div className="flex gap-3">
        <select
          value={programFilter}
          onChange={(e) => setProgramFilter(e.target.value)}
          className="rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
        >
          <option value="">Global scales</option>
          {programs.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
        </select>
      </div>

      {/* List */}
      {isLoading ? (
        <div className="flex justify-center py-16"><Spinner size="lg" /></div>
      ) : scales.length === 0 ? (
        <Card>
          <div className="flex flex-col items-center py-12 text-gray-400">
            <p className="font-medium">No grading scales found</p>
            <p className="text-sm">Create a scale to define letter grade thresholds.</p>
          </div>
        </Card>
      ) : (
        <div className="space-y-3">
          {scales.map((s) => (
            <ScaleRow key={s.id} scale={s} onEdit={openEdit} onDelete={handleDelete} />
          ))}
        </div>
      )}

      {/* Create Modal */}
      <Modal open={showCreate} onClose={() => setShowCreate(false)} title="Create Grading Scale">
        <ScaleFormBody
          form={createForm}
          onSubmit={(d) => createMut.mutate(d)}
          loading={createMut.isPending}
          onCancel={() => setShowCreate(false)}
          label="Create Scale"
        />
      </Modal>

      {/* Edit Modal */}
      <Modal open={!!editing} onClose={() => setEditing(null)} title="Edit Grading Scale">
        <ScaleFormBody
          form={editForm}
          onSubmit={(d) => editing && editMut.mutate({ id: editing.id, body: d })}
          loading={editMut.isPending}
          onCancel={() => setEditing(null)}
          label="Save Changes"
        />
      </Modal>
    </div>
  );
}
