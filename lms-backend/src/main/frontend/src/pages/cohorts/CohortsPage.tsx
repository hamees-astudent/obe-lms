import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import {
  Plus, Edit2, Trash2, UsersRound, UserPlus, UserMinus, ClipboardList, BookOpen, AlertTriangle,
} from 'lucide-react';
import api from '@/lib/api';
import { toast } from '@/components/ui/Toast';
import QueryError from '@/components/ui/QueryError';
import Modal from '@/components/ui/Modal';
import Button from '@/components/ui/Button';
import Input from '@/components/ui/Input';
import Badge from '@/components/ui/Badge';
import Card from '@/components/ui/Card';
import Spinner from '@/components/ui/Spinner';
import UserSearchSelect from '@/components/ui/UserSearchSelect';
import { useOpenOfferings } from '@/lib/queries';
import { CohortEnrollmentSummary, useEnrollCohort } from '@/components/cohorts/CohortEnrollment';
import type {
  AddCohortMembersResponse,
  CohortDetailResponse,
  CohortEnrollmentResponse,
  CohortSummaryResponse,
  UserSummaryResponse,
  UUID,
} from '@/types/api';

// ─── Schema ──────────────────────────────────────────────────────────────────

const cohortSchema = z.object({
  name:        z.string().trim().min(1, 'Name is required').max(120),
  description: z.string().max(2000).optional(),
});

type CohortForm = z.infer<typeof cohortSchema>;

/** Splits a pasted class list on commas, semicolons, whitespace and new lines. */
function parseRollNumbers(text: string): string[] {
  return text.split(/[\s,;]+/).map((s) => s.trim()).filter(Boolean);
}

// ─── Add members ──────────────────────────────────────────────────────────────

function AddMembers({ cohort }: { cohort: CohortDetailResponse }) {
  const qc = useQueryClient();
  const [picked, setPicked] = useState<UserSummaryResponse | null>(null);
  const [pasting, setPasting] = useState(false);
  const [pasted, setPasted] = useState('');
  const [lastResult, setLastResult] = useState<AddCohortMembersResponse | null>(null);

  const addMut = useMutation({
    mutationFn: (body: { studentIds?: UUID[]; studentNumbers?: string[] }) =>
      api
        .post<AddCohortMembersResponse>(`/admin/cohorts/${cohort.id}/members`, body)
        .then((r) => r.data),
    onSuccess: (res) => {
      qc.setQueryData(['admin-cohorts', res.cohort.id], res.cohort);
      qc.invalidateQueries({ queryKey: ['admin-cohorts', 'list'] });
      setPicked(null);
      setPasted('');
      setLastResult(res);
      const parts = [`${res.added} added`];
      if (res.alreadyMembers) parts.push(`${res.alreadyMembers} already in the cohort`);
      toast.success(parts.join(', ') + '.');
    },
  });

  const members = new Map<UUID, string>(cohort.members.map((m) => [m.studentId, 'already in this cohort']));
  const rollNumbers = parseRollNumbers(pasted);
  const problems = lastResult ? lastResult.notFound.length + lastResult.notStudents.length : 0;

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap gap-2">
        <UserSearchSelect
          value={picked}
          onChange={setPicked}
          roles={['STUDENT']}
          disabled={members}
          placeholder="Search students by name, email or roll number…"
        />
        <Button
          size="sm"
          disabled={!picked}
          loading={addMut.isPending && !pasting}
          onClick={() => picked && addMut.mutate({ studentIds: [picked.id] })}
        >
          <UserPlus size={14} className="mr-1" /> Add
        </Button>
        <Button size="sm" variant="secondary" onClick={() => setPasting((p) => !p)}>
          <ClipboardList size={14} className="mr-1" /> {pasting ? 'Hide list' : 'Paste roll numbers'}
        </Button>
      </div>

      {pasting && (
        <div className="space-y-2 rounded-lg border border-gray-200 bg-gray-50 p-3">
          <label htmlFor="roll-numbers" className="block text-xs font-medium text-gray-600">
            Roll numbers — one per line, or separated by commas or spaces
          </label>
          <textarea
            id="roll-numbers"
            value={pasted}
            onChange={(e) => setPasted(e.target.value)}
            rows={5}
            placeholder={'BSCS-24-001\nBSCS-24-002\nBSCS-24-003'}
            className="w-full rounded-lg border border-gray-300 px-3 py-2 font-mono text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
          />
          <div className="flex items-center justify-between">
            <span className="text-xs text-gray-500">
              {rollNumbers.length} roll number{rollNumbers.length === 1 ? '' : 's'}
            </span>
            <Button
              size="sm"
              disabled={rollNumbers.length === 0}
              loading={addMut.isPending && pasting}
              onClick={() => addMut.mutate({ studentNumbers: rollNumbers })}
            >
              Add {rollNumbers.length || ''} to cohort
            </Button>
          </div>
        </div>
      )}

      {lastResult && problems > 0 && (
        <div className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-900">
          <div className="flex items-start justify-between gap-3">
            <p className="flex items-center gap-1.5 font-medium">
              <AlertTriangle size={15} /> {problems} not added
            </p>
            <button
              onClick={() => setLastResult(null)}
              className="text-xs text-amber-700 hover:underline"
            >
              Dismiss
            </button>
          </div>
          {lastResult.notFound.length > 0 && (
            <p className="mt-1">
              No student with: <span className="font-mono">{lastResult.notFound.join(', ')}</span>
            </p>
          )}
          {lastResult.notStudents.length > 0 && (
            <p className="mt-1">Not students: {lastResult.notStudents.join(', ')}</p>
          )}
        </div>
      )}
    </div>
  );
}

// ─── Enroll in an offering ────────────────────────────────────────────────────

function EnrollCohortModal({
  cohort,
  open,
  onClose,
}: {
  cohort: CohortDetailResponse;
  open: boolean;
  onClose: () => void;
}) {
  const [pscId, setPscId] = useState('');
  const [result, setResult] = useState<CohortEnrollmentResponse | null>(null);
  const { offerings, isLoading } = useOpenOfferings({ enabled: open });
  const enrollMut = useEnrollCohort(setResult);

  function close() {
    setPscId('');
    setResult(null);
    onClose();
  }

  const sorted = [...offerings].sort(
    (a, b) =>
      a.programName.localeCompare(b.programName) ||
      a.semesterName.localeCompare(b.semesterName) ||
      a.courseCode.localeCompare(b.courseCode),
  );

  return (
    <Modal open={open} onClose={close} title={`Enroll “${cohort.name}”`}>
      {result ? (
        <div className="space-y-4">
          <CohortEnrollmentSummary result={result} />
          <div className="flex justify-end">
            <Button onClick={close}>Done</Button>
          </div>
        </div>
      ) : (
        <div className="space-y-4">
          <p className="text-sm text-gray-600">
            All {cohort.members.length} member{cohort.members.length === 1 ? '' : 's'} will be enrolled as
            students. Anyone already in the offering is skipped.
          </p>
          <div>
            <label htmlFor="cohort-offering" className="mb-1 block text-sm font-medium text-gray-700">
              Offering (open semesters)
            </label>
            {isLoading ? (
              <div className="flex justify-center py-2"><Spinner size="sm" /></div>
            ) : (
              <select
                id="cohort-offering"
                value={pscId}
                onChange={(e) => setPscId(e.target.value)}
                className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
              >
                <option value="">Select offering…</option>
                {sorted.map((o) => (
                  <option key={o.id} value={o.id}>
                    {o.courseCode} — {o.courseName} · {o.semesterName} · {o.programName}
                  </option>
                ))}
              </select>
            )}
            {!isLoading && offerings.length === 0 && (
              <p className="mt-1 text-xs text-gray-500">No offerings in open semesters.</p>
            )}
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="secondary" onClick={close}>Cancel</Button>
            <Button
              disabled={!pscId}
              loading={enrollMut.isPending}
              onClick={() => enrollMut.mutate({ cohortId: cohort.id, pscId })}
            >
              Enroll cohort
            </Button>
          </div>
        </div>
      )}
    </Modal>
  );
}

// ─── Cohort detail ────────────────────────────────────────────────────────────

function CohortDetail({
  cohortId,
  onEdit,
  onDelete,
}: {
  cohortId: UUID;
  onEdit: (c: CohortDetailResponse) => void;
  onDelete: (c: CohortDetailResponse) => void;
}) {
  const qc = useQueryClient();
  const [enrolling, setEnrolling] = useState(false);

  const { data: cohort, isLoading, isError, error, refetch } = useQuery<CohortDetailResponse>({
    queryKey: ['admin-cohorts', cohortId],
    meta: { errorShownInline: true },
    queryFn: () => api.get(`/admin/cohorts/${cohortId}`).then((r) => r.data),
  });

  const removeMut = useMutation({
    mutationFn: (studentId: UUID) =>
      api
        .delete<CohortDetailResponse>(`/admin/cohorts/${cohortId}/members/${studentId}`)
        .then((r) => r.data),
    onSuccess: (updated) => {
      qc.setQueryData(['admin-cohorts', cohortId], updated);
      qc.invalidateQueries({ queryKey: ['admin-cohorts', 'list'] });
      toast.success('Removed from cohort.');
    },
  });

  if (isError) return <Card><QueryError error={error} onRetry={() => refetch()} /></Card>;
  if (isLoading || !cohort) {
    return <Card><div className="flex justify-center py-10"><Spinner /></div></Card>;
  }

  const inactive = cohort.members.filter((m) => m.status !== 'ACTIVE').length;

  return (
    <Card className="space-y-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <h2 className="text-lg font-semibold text-gray-900">{cohort.name}</h2>
          {cohort.description && <p className="mt-0.5 text-sm text-gray-500">{cohort.description}</p>}
        </div>
        <div className="flex items-center gap-1">
          <button
            onClick={() => onEdit(cohort)}
            className="rounded p-2 text-gray-400 transition hover:bg-gray-100 hover:text-gray-700"
            title="Rename cohort"
          >
            <Edit2 size={15} />
          </button>
          <button
            onClick={() => onDelete(cohort)}
            className="rounded p-2 text-red-400 transition hover:bg-red-50 hover:text-red-600"
            title="Delete cohort"
          >
            <Trash2 size={15} />
          </button>
          <Button
            size="sm"
            className="ml-1"
            disabled={cohort.members.length === 0}
            title={cohort.members.length === 0 ? 'Add students first' : undefined}
            onClick={() => setEnrolling(true)}
          >
            <BookOpen size={14} className="mr-1" /> Enroll in offering
          </Button>
        </div>
      </div>

      <AddMembers cohort={cohort} />

      <div>
        <h3 className="mb-2 text-sm font-medium text-gray-700">
          Members ({cohort.members.length})
          {inactive > 0 && (
            <span className="ml-2 text-xs font-normal text-amber-700">
              {inactive} inactive — skipped when enrolling
            </span>
          )}
        </h3>
        {cohort.members.length === 0 ? (
          <p className="py-6 text-center text-sm text-gray-400">
            No students yet. Search for them above, or paste a list of roll numbers.
          </p>
        ) : (
          <div className="max-h-[28rem] divide-y divide-gray-100 overflow-y-auto rounded-lg border border-gray-200">
            {cohort.members.map((m) => (
              <div key={m.studentId} className="flex items-center justify-between gap-3 px-3 py-2 text-sm">
                <div className="min-w-0">
                  <span className="text-gray-900">{m.name}</span>
                  {m.studentNumber && (
                    <span className="ml-1.5 font-mono text-xs text-gray-400">{m.studentNumber}</span>
                  )}
                  <p className="truncate text-xs text-gray-500">{m.email}</p>
                </div>
                <div className="flex flex-shrink-0 items-center gap-2">
                  {m.status !== 'ACTIVE' && <Badge variant="warning">{m.status}</Badge>}
                  <button
                    onClick={() => removeMut.mutate(m.studentId)}
                    disabled={removeMut.isPending}
                    className="rounded p-0.5 text-red-400 transition hover:bg-red-50 hover:text-red-600 disabled:opacity-40"
                    title="Remove from cohort"
                  >
                    <UserMinus size={14} />
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      <EnrollCohortModal cohort={cohort} open={enrolling} onClose={() => setEnrolling(false)} />
    </Card>
  );
}

// ─── Main page ────────────────────────────────────────────────────────────────

export default function CohortsPage() {
  const qc = useQueryClient();
  const [selectedId, setSelectedId] = useState<UUID | null>(null);
  const [showCreate, setShowCreate] = useState(false);
  const [editing, setEditing] = useState<CohortDetailResponse | null>(null);
  const [deleting, setDeleting] = useState<CohortDetailResponse | null>(null);

  const { data: cohorts = [], isLoading, isError, error, refetch } = useQuery<CohortSummaryResponse[]>({
    queryKey: ['admin-cohorts', 'list'],
    meta: { errorShownInline: true },
    queryFn: () => api.get('/admin/cohorts').then((r) => r.data),
  });

  const invalidateAll = () => qc.invalidateQueries({ queryKey: ['admin-cohorts'] });

  const createMut = useMutation({
    mutationFn: (body: CohortForm) =>
      api.post<CohortSummaryResponse>('/admin/cohorts', body).then((r) => r.data),
    onSuccess: (created) => {
      toast.success('Cohort created.');
      invalidateAll();
      setShowCreate(false);
      setSelectedId(created.id);
    },
  });

  const updateMut = useMutation({
    mutationFn: ({ id, body }: { id: UUID; body: CohortForm }) => api.put(`/admin/cohorts/${id}`, body),
    onSuccess: () => {
      toast.success('Cohort updated.');
      invalidateAll();
      setEditing(null);
    },
  });

  const deleteMut = useMutation({
    mutationFn: (id: UUID) => api.delete(`/admin/cohorts/${id}`),
    onSuccess: (_, id) => {
      toast.success('Cohort deleted.');
      qc.removeQueries({ queryKey: ['admin-cohorts', id] });
      invalidateAll();
      setDeleting(null);
      setSelectedId(null);
    },
  });

  const createForm = useForm<CohortForm>({ resolver: zodResolver(cohortSchema) });
  const editForm = useForm<CohortForm>({ resolver: zodResolver(cohortSchema) });

  function openCreate() {
    createForm.reset({ name: '', description: '' });
    setShowCreate(true);
  }

  function openEdit(c: CohortDetailResponse) {
    editForm.reset({ name: c.name, description: c.description ?? '' });
    setEditing(c);
  }

  return (
    <div className="space-y-5 animate-slide-up">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Cohorts</h1>
          <p className="text-sm text-gray-500">Groups of students you can enroll in an offering in one step.</p>
        </div>
        <Button onClick={openCreate}>
          <Plus size={16} className="mr-1" /> New Cohort
        </Button>
      </div>

      <div className="grid gap-5 lg:grid-cols-3">
        {/* Cohort list */}
        <div className="space-y-2 lg:col-span-1">
          {isError ? (
            <QueryError error={error} onRetry={() => refetch()} />
          ) : isLoading ? (
            <div className="flex justify-center py-10"><Spinner /></div>
          ) : cohorts.length === 0 ? (
            <Card>
              <div className="flex flex-col items-center py-8 text-center text-gray-400">
                <UsersRound size={36} className="mb-2 opacity-40" />
                <p className="font-medium">No cohorts yet</p>
                <p className="text-sm">Create one for each intake or section.</p>
              </div>
            </Card>
          ) : (
            cohorts.map((c) => (
              <button
                key={c.id}
                onClick={() => setSelectedId(c.id)}
                aria-current={selectedId === c.id ? 'true' : undefined}
                className={`w-full rounded-xl border px-4 py-3 text-left shadow-sm transition ${
                  selectedId === c.id
                    ? 'border-primary-300 bg-primary-50'
                    : 'border-gray-200 bg-white hover:bg-gray-50'
                }`}
              >
                <div className="flex items-center justify-between gap-2">
                  <span className="truncate font-medium text-gray-900">{c.name}</span>
                  <Badge variant="info">{c.memberCount}</Badge>
                </div>
                {c.description && <p className="mt-0.5 truncate text-xs text-gray-500">{c.description}</p>}
              </button>
            ))
          )}
        </div>

        {/* Detail */}
        <div className="lg:col-span-2">
          {selectedId ? (
            <CohortDetail
              key={selectedId}
              cohortId={selectedId}
              onEdit={openEdit}
              onDelete={setDeleting}
            />
          ) : (
            <Card>
              <div className="flex flex-col items-center py-12 text-gray-400">
                <UsersRound size={40} className="mb-3 opacity-40" />
                <p className="font-medium">Select a cohort to manage its students</p>
              </div>
            </Card>
          )}
        </div>
      </div>

      {/* Create */}
      <Modal open={showCreate} onClose={() => setShowCreate(false)} title="New Cohort">
        <form onSubmit={createForm.handleSubmit((d) => createMut.mutate(d))} className="space-y-4">
          <div>
            <label className="mb-1 block text-sm font-medium text-gray-700">Name</label>
            <Input {...createForm.register('name')} placeholder="BSCS Fall 2024 – Section A" />
            {createForm.formState.errors.name && (
              <p className="mt-1 text-xs text-red-500">{createForm.formState.errors.name.message}</p>
            )}
          </div>
          <div>
            <label className="mb-1 block text-sm font-medium text-gray-700">Description (optional)</label>
            <Input {...createForm.register('description')} placeholder="Morning section, 2024 intake" />
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="secondary" onClick={() => setShowCreate(false)}>Cancel</Button>
            <Button type="submit" loading={createMut.isPending}>Create Cohort</Button>
          </div>
        </form>
      </Modal>

      {/* Edit */}
      <Modal open={!!editing} onClose={() => setEditing(null)} title="Edit Cohort">
        <form
          onSubmit={editForm.handleSubmit((d) => editing && updateMut.mutate({ id: editing.id, body: d }))}
          className="space-y-4"
        >
          <div>
            <label className="mb-1 block text-sm font-medium text-gray-700">Name</label>
            <Input {...editForm.register('name')} />
            {editForm.formState.errors.name && (
              <p className="mt-1 text-xs text-red-500">{editForm.formState.errors.name.message}</p>
            )}
          </div>
          <div>
            <label className="mb-1 block text-sm font-medium text-gray-700">Description (optional)</label>
            <Input {...editForm.register('description')} />
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="secondary" onClick={() => setEditing(null)}>Cancel</Button>
            <Button type="submit" loading={updateMut.isPending}>Save Changes</Button>
          </div>
        </form>
      </Modal>

      {/* Delete */}
      <Modal open={!!deleting} onClose={() => setDeleting(null)} title="Delete Cohort">
        <div className="space-y-4">
          <p className="text-sm text-gray-600">
            Delete <strong>{deleting?.name}</strong>? Only the group is removed — students it was used
            to enroll stay enrolled in their courses.
          </p>
          <div className="flex justify-end gap-2">
            <Button variant="secondary" onClick={() => setDeleting(null)}>Cancel</Button>
            <Button
              variant="danger"
              loading={deleteMut.isPending}
              onClick={() => deleting && deleteMut.mutate(deleting.id)}
            >
              Delete
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
