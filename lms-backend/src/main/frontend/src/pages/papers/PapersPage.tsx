import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ChevronLeft, FileText, Plus, Printer, Sparkles, Trash2 } from 'lucide-react';
import api from '@/lib/api';
import { usePaperSettings } from '@/lib/ai';
import { parseApiError } from '@/lib/apiError';
import { formatDateTime } from '@/lib/datetime';
import Button from '@/components/ui/Button';
import Badge from '@/components/ui/Badge';
import Modal from '@/components/ui/Modal';
import Spinner from '@/components/ui/Spinner';
import QueryError from '@/components/ui/QueryError';
import { fmtMarks } from '@/components/ai/MarksMeter';
import PaperSettingsCard from '@/components/papers/PaperSettingsCard';
import type { OfferingSummaryResponse, PaperBody, PaperResponse, UUID } from '@/types/api';

/** An offering's printable question papers. */
export default function PapersPage() {
  const { pscId } = useParams<{ pscId: string }>();
  const qc = useQueryClient();
  const [creating, setCreating] = useState(false);
  const offeringQ = useQuery({
    queryKey: ['offerings', pscId],
    queryFn: () => api.get<OfferingSummaryResponse>(`/offerings/${pscId}`).then((r) => r.data),
    enabled: !!pscId,
  });
  const papersQ = useQuery({
    queryKey: ['offerings', pscId, 'papers'],
    queryFn: () => api.get<PaperResponse[]>(`/offerings/${pscId}/papers`).then((r) => r.data),
    enabled: !!pscId,
  });
  const remove = useMutation({
    mutationFn: (id: UUID) => api.delete(`/papers/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['offerings', pscId, 'papers'] }),
  });
  if (!pscId) return null;

  return (
    <div className="space-y-5">
      <div className="flex items-center gap-2 text-sm">
        <Link to={`/assessment/${pscId}`} className="flex items-center gap-1 text-primary-600 hover:text-primary-800">
          <ChevronLeft size={16} /> {offeringQ.data?.courseCode ?? 'Assessment'}
        </Link>
        <span className="text-gray-300">/</span>
        <span className="text-gray-600">Question papers</span>
      </div>
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="flex items-center gap-2 text-2xl font-bold text-gray-900">
            <Printer className="text-primary-600" size={22} /> Question papers
          </h1>
          <p className="mt-1 text-sm text-gray-500">
            Assemble papers within a marks limit and print them as shuffled sets with answer keys.
          </p>
        </div>
        <div className="flex gap-2">
          <Link to={`/assessment/${pscId}/generate`}>
            <Button variant="secondary"><Sparkles size={15} /> Generate questions</Button>
          </Link>
          <Button onClick={() => setCreating(true)}><Plus size={15} /> New paper</Button>
        </div>
      </div>

      <PaperSettingsCard pscId={pscId} />

      {papersQ.isLoading ? (
        <div className="flex justify-center py-10"><Spinner /></div>
      ) : papersQ.error ? (
        <QueryError error={papersQ.error} onRetry={() => papersQ.refetch()} />
      ) : (papersQ.data ?? []).length === 0 ? (
        <div className="flex flex-col items-center rounded-xl border border-dashed border-gray-200 py-14 text-center">
          <FileText size={36} className="mb-3 text-gray-300" />
          <p className="text-sm text-gray-400">No papers yet. Generate questions, or start an empty paper.</p>
        </div>
      ) : (
        <div className="space-y-3">
          {(papersQ.data ?? []).map((p) => (
            <div key={p.id} className="flex items-center justify-between rounded-xl border border-gray-200 bg-white p-4">
              <Link to={`/assessment/${pscId}/papers/${p.id}`} className="min-w-0 flex-1">
                <p className="font-semibold text-gray-900 hover:text-primary-700">{p.title}</p>
                <p className="mt-1 text-xs text-gray-500">
                  {p.questionCount} questions · {fmtMarks(p.totalMarks)}/{fmtMarks(p.maxTotalMarks)} marks
                  {p.durationMinutes ? ` · ${p.durationMinutes} min` : ''} · updated {formatDateTime(p.updatedAt)}
                </p>
              </Link>
              <div className="flex items-center gap-2">
                <Badge variant={p.status === 'FINAL' ? 'success' : 'default'}>{p.status === 'FINAL' ? 'Printed' : 'Draft'}</Badge>
                <button aria-label="Delete paper" onClick={() => confirm(`Delete "${p.title}"?`) && remove.mutate(p.id)}
                  className="rounded p-2 text-gray-400 hover:text-red-500">
                  <Trash2 size={15} />
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {creating && <PaperFormModal pscId={pscId} onClose={() => setCreating(false)} />}
    </div>
  );
}

/** Create or edit a paper's title, limit, duration and instructions. */
export function PaperFormModal({ pscId, paper, onClose }: { pscId: UUID; paper?: PaperResponse; onClose: () => void }) {
  const qc = useQueryClient();
  const navigate = useNavigate();
  const settingsQ = usePaperSettings(pscId);
  const max = settingsQ.data?.maxTotalMarks ?? 100;
  const [form, setForm] = useState<PaperBody>({
    title: paper?.title ?? '',
    instructions: paper?.instructions ?? 'Attempt all questions. Write your roll number on every page.',
    durationMinutes: paper?.durationMinutes ?? 60,
    maxTotalMarks: paper?.maxTotalMarks ?? Math.min(50, max),
  });
  const save = useMutation({
    mutationFn: () =>
      (paper ? api.put<PaperResponse>(`/papers/${paper.id}`, form) : api.post<PaperResponse>(`/offerings/${pscId}/papers`, form))
        .then((r) => r.data),
    onSuccess: (p) => {
      qc.invalidateQueries({ queryKey: ['offerings', pscId, 'papers'] });
      qc.setQueryData(['papers', p.id], p);
      onClose();
      if (!paper) navigate(`/assessment/${pscId}/papers/${p.id}`);
    },
  });
  return (
    <Modal open onClose={onClose} title={paper ? 'Paper details' : 'New question paper'}>
      <div className="space-y-3">
        <label className="block text-sm">
          <span className="mb-1 block font-medium text-gray-700">Title</span>
          <input value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} placeholder="Midterm examination"
            className="h-9 w-full rounded-lg border border-gray-300 px-3 text-sm" />
        </label>
        <div className="grid grid-cols-2 gap-3">
          <label className="text-sm">
            <span className="mb-1 block font-medium text-gray-700">Marks limit</span>
            <input type="number" min={1} max={max} value={form.maxTotalMarks}
              onChange={(e) => setForm({ ...form, maxTotalMarks: Number(e.target.value) })}
              className="h-9 w-full rounded-lg border border-gray-300 px-3 text-sm" />
            <span className="text-xs text-gray-500">at most {fmtMarks(max)}</span>
          </label>
          <label className="text-sm">
            <span className="mb-1 block font-medium text-gray-700">Duration (minutes)</span>
            <input type="number" min={5} value={form.durationMinutes ?? ''}
              onChange={(e) => setForm({ ...form, durationMinutes: e.target.value ? Number(e.target.value) : undefined })}
              className="h-9 w-full rounded-lg border border-gray-300 px-3 text-sm" />
          </label>
        </div>
        <label className="block text-sm">
          <span className="mb-1 block font-medium text-gray-700">Instructions</span>
          <textarea value={form.instructions ?? ''} rows={3} onChange={(e) => setForm({ ...form, instructions: e.target.value })}
            className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm" />
        </label>
        {save.error != null && <p className="text-sm text-red-600">{parseApiError(save.error)}</p>}
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button disabled={!form.title.trim()} loading={save.isPending} onClick={() => save.mutate()}>
            {paper ? 'Save' : 'Create'}
          </Button>
        </div>
      </div>
    </Modal>
  );
}
