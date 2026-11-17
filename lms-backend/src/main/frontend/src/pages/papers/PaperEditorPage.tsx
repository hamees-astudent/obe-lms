import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowDown, ArrowUp, ChevronLeft, Download, FileArchive, KeyRound, Pencil, Plus, Printer, Shuffle, Sparkles, Trash2 } from 'lucide-react';
import api from '@/lib/api';
import { downloadBlob } from '@/lib/ai';
import { parseApiError } from '@/lib/apiError';
import { formatDateTime } from '@/lib/datetime';
import { toast } from '@/components/ui/Toast';
import Button from '@/components/ui/Button';
import Badge from '@/components/ui/Badge';
import Modal from '@/components/ui/Modal';
import Spinner from '@/components/ui/Spinner';
import QueryError from '@/components/ui/QueryError';
import BloomBadge from '@/components/ai/BloomBadge';
import MarksMeter, { fmtMarks } from '@/components/ai/MarksMeter';
import { PaperFormModal } from '@/pages/papers/PapersPage';
import {
  QUESTION_TYPE_LABELS,
  isWrittenType,
  type AddPaperItemsResponse,
  type PaperItemResponse,
  type PaperResponse,
  type PaperSection,
  type QuizQuestionResponse,
  type QuizResponse,
  type UUID,
} from '@/types/api';
import RichText from '@/components/ai/RichText';

const SECTIONS: Array<{ key: PaperSection; title: string }> = [
  { key: 'CHOICE', title: 'Section A — Multiple choice' },
  { key: 'SHORT', title: 'Section B — Short answers' },
  { key: 'LONG', title: 'Section C — Long answers' },
];

/** One question paper: its questions within the marks limit, and its printed sets. */
export default function PaperEditorPage() {
  const { pscId, paperId } = useParams<{ pscId: string; paperId: string }>();
  const qc = useQueryClient();
  const [editingDetails, setEditingDetails] = useState(false);
  const [adding, setAdding] = useState(false);
  const [printing, setPrinting] = useState(false);

  const paperQ = useQuery({
    queryKey: ['papers', paperId],
    queryFn: () => api.get<PaperResponse>(`/papers/${paperId}`).then((r) => r.data),
    enabled: !!paperId,
  });
  const paper = paperQ.data;
  const setPaper = (p: PaperResponse) => {
    qc.setQueryData(['papers', paperId], p);
    qc.invalidateQueries({ queryKey: ['offerings', pscId, 'papers'] });
  };

  const updateMarks = useMutation({
    mutationFn: ({ id, marks }: { id: UUID; marks: number }) =>
      api.put<PaperResponse>(`/paper-items/${id}`, { marks }).then((r) => r.data),
    onSuccess: setPaper,
  });
  const removeItem = useMutation({
    mutationFn: (id: UUID) => api.delete<PaperResponse>(`/paper-items/${id}`).then((r) => r.data),
    onSuccess: setPaper,
  });
  const reorder = useMutation({
    mutationFn: (ids: UUID[]) => api.put<PaperResponse>(`/papers/${paperId}/order`, { itemIds: ids }).then((r) => r.data),
    onSuccess: setPaper,
  });

  if (paperQ.isLoading) return <div className="flex justify-center py-16"><Spinner size="lg" /></div>;
  if (paperQ.error || !paper || !pscId) return <QueryError error={paperQ.error} onRetry={() => paperQ.refetch()} />;

  const items = paper.items ?? [];
  const errorOf = updateMarks.error ?? removeItem.error ?? reorder.error;

  function move(section: PaperSection, index: number, delta: number) {
    const sectionItems = items.filter((i) => i.section === section);
    const target = index + delta;
    if (target < 0 || target >= sectionItems.length) return;
    const swapped = [...sectionItems];
    [swapped[index], swapped[target]] = [swapped[target], swapped[index]];
    const order = SECTIONS.flatMap((s) => (s.key === section ? swapped : items.filter((i) => i.section === s.key)));
    reorder.mutate(order.map((i) => i.id));
  }

  return (
    <div className="space-y-5">
      <div className="flex items-center gap-2 text-sm">
        <Link to={`/assessment/${pscId}/papers`} className="flex items-center gap-1 text-primary-600 hover:text-primary-800">
          <ChevronLeft size={16} /> Question papers
        </Link>
      </div>

      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="flex items-center gap-2 text-2xl font-bold text-gray-900">
            {paper.title}
            <button aria-label="Edit paper details" onClick={() => setEditingDetails(true)} className="text-gray-400 hover:text-gray-700">
              <Pencil size={16} />
            </button>
          </h1>
          <p className="mt-1 text-sm text-gray-500">
            {paper.questionCount}/{paper.maxQuestions} questions{paper.durationMinutes ? ` · ${paper.durationMinutes} minutes` : ''}
            {' · '}<Badge variant={paper.status === 'FINAL' ? 'success' : 'default'}>{paper.status === 'FINAL' ? 'Printed' : 'Draft'}</Badge>
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <MarksMeter used={Number(paper.totalMarks)} limit={Number(paper.maxTotalMarks)} />
          <Link to={`/assessment/${pscId}/generate`}><Button variant="secondary"><Sparkles size={15} /> Generate</Button></Link>
          <Button variant="secondary" onClick={() => setAdding(true)}><Plus size={15} /> From quizzes</Button>
          <Button disabled={items.length === 0} onClick={() => setPrinting(true)}><Printer size={15} /> Print sets</Button>
        </div>
      </div>

      {errorOf != null && <p className="text-sm text-red-600">{parseApiError(errorOf)}</p>}

      {(paper.printSets ?? []).length > 0 && <PrintedSets paper={paper} />}

      {items.length === 0 ? (
        <div className="rounded-xl border border-dashed border-gray-200 py-14 text-center text-sm text-gray-400">
          This paper has no questions yet. Generate some, or add questions from your quizzes.
        </div>
      ) : (
        SECTIONS.map((s) => {
          const list = items.filter((i) => i.section === s.key);
          if (list.length === 0) return null;
          const marks = list.reduce((sum, i) => sum + Number(i.marks), 0);
          return (
            <div key={s.key} className="rounded-xl border border-gray-200 bg-white">
              <div className="flex items-center justify-between rounded-t-xl bg-gray-700 px-4 py-2 text-sm font-semibold text-white">
                {s.title} <span>{fmtMarks(marks)} marks</span>
              </div>
              <ul className="divide-y divide-gray-100">
                {list.map((item, index) => (
                  <ItemRow
                    key={item.id}
                    item={item}
                    number={index + 1}
                    first={index === 0}
                    last={index === list.length - 1}
                    onMove={(d) => move(s.key, index, d)}
                    onMarks={(m) => updateMarks.mutate({ id: item.id, marks: m })}
                    onRemove={() => confirm('Remove this question from the paper?') && removeItem.mutate(item.id)}
                  />
                ))}
              </ul>
            </div>
          );
        })
      )}
      {paper.status === 'FINAL' && (
        <p className="text-xs text-gray-500">Editing questions discards the printed sets; print again afterwards.</p>
      )}

      {editingDetails && <PaperFormModal pscId={pscId} paper={paper} onClose={() => { setEditingDetails(false); paperQ.refetch(); }} />}
      {adding && <AddFromQuizzes pscId={pscId} paper={paper} onDone={setPaper} onClose={() => setAdding(false)} />}
      {printing && <PrintModal paper={paper} onDone={setPaper} onClose={() => setPrinting(false)} />}
    </div>
  );
}

function ItemRow({
  item, number, first, last, onMove, onMarks, onRemove,
}: {
  item: PaperItemResponse; number: number; first: boolean; last: boolean;
  onMove: (delta: number) => void; onMarks: (m: number) => void; onRemove: () => void;
}) {
  const q = item.question;
  return (
    <li className="flex items-start gap-3 px-4 py-3">
      <span className="mt-0.5 text-sm font-semibold text-gray-500">{number}.</span>
      <div className="min-w-0 flex-1">
        <RichText text={q.questionText} compact className="text-sm text-gray-900" />
        {!isWrittenType(q.type) ? (
          <ol className="mt-1 grid gap-x-6 gap-y-0.5 text-xs text-gray-600 sm:grid-cols-2">
            {q.options.map((o, i) => (
              <li key={o.id} className={q.correctAnswer.includes(o.id) ? 'font-medium text-green-700' : ''}>
                ({String.fromCharCode(65 + i)}) {o.text}
              </li>
            ))}
          </ol>
        ) : (
          <p className="mt-1 line-clamp-2 text-xs text-gray-500">Model answer: {q.modelAnswer}</p>
        )}
        <div className="mt-1 flex flex-wrap gap-1">
          <Badge variant="default">{QUESTION_TYPE_LABELS[q.type]}</Badge>
          <BloomBadge level={q.bloomLevel} />
        </div>
      </div>
      <div className="flex flex-shrink-0 items-center gap-1">
        <input
          type="number" min={0.5} step={0.5} defaultValue={item.marks} aria-label="Marks"
          onBlur={(e) => { const m = Number(e.target.value); if (m > 0 && m !== Number(item.marks)) onMarks(m); }}
          className="h-8 w-16 rounded border border-gray-300 px-1.5 text-sm"
        />
        <button aria-label="Move up" disabled={first} onClick={() => onMove(-1)} className="p-1 text-gray-400 hover:text-gray-700 disabled:opacity-20"><ArrowUp size={14} /></button>
        <button aria-label="Move down" disabled={last} onClick={() => onMove(1)} className="p-1 text-gray-400 hover:text-gray-700 disabled:opacity-20"><ArrowDown size={14} /></button>
        <button aria-label="Remove" onClick={onRemove} className="p-1 text-gray-400 hover:text-red-500"><Trash2 size={14} /></button>
      </div>
    </li>
  );
}

function AddFromQuizzes({ pscId, paper, onDone, onClose }: {
  pscId: UUID; paper: PaperResponse; onDone: (p: PaperResponse) => void; onClose: () => void;
}) {
  const [quizId, setQuizId] = useState('');
  const [picked, setPicked] = useState<UUID[]>([]);
  const quizzesQ = useQuery({
    queryKey: ['offerings', pscId, 'quizzes'],
    queryFn: () => api.get<QuizResponse[]>(`/offerings/${pscId}/quizzes`).then((r) => r.data),
  });
  const questionsQ = useQuery({
    queryKey: ['quizzes', quizId, 'questions'],
    queryFn: () => api.get<QuizQuestionResponse[]>(`/quizzes/${quizId}/questions`).then((r) => r.data),
    enabled: !!quizId,
  });
  const add = useMutation({
    mutationFn: (fit: boolean) =>
      api.post<AddPaperItemsResponse>(`/papers/${paper.id}/items`, { quizQuestionIds: picked, fitToLimit: fit }).then((r) => r.data),
    onSuccess: (r) => {
      onDone(r.paper);
      toast.success(r.skipped > 0 ? `Added ${r.added}; ${r.skipped} left out to stay within the limit.` : `Added ${r.added}.`);
      onClose();
    },
  });
  const questions = questionsQ.data ?? [];
  const pickedMarks = questions.filter((q) => picked.includes(q.id)).reduce((s, q) => s + q.marks, 0);
  const over = Number(paper.totalMarks) + pickedMarks > Number(paper.maxTotalMarks);

  return (
    <Modal open onClose={onClose} title="Add questions from a quiz" maxWidth="max-w-2xl">
      <div className="space-y-3">
        <select value={quizId} onChange={(e) => { setQuizId(e.target.value); setPicked([]); }} className="h-9 w-full rounded-lg border border-gray-300 px-2 text-sm">
          <option value="">Choose a quiz…</option>
          {(quizzesQ.data ?? []).map((q) => <option key={q.id} value={q.id}>{q.title}</option>)}
        </select>
        {questionsQ.isLoading && <Spinner />}
        <ul className="max-h-80 space-y-1 overflow-y-auto">
          {questions.map((q) => (
            <li key={q.id}>
              <label className="flex items-start gap-2 rounded px-2 py-1.5 text-sm hover:bg-gray-50">
                <input type="checkbox" className="mt-1" checked={picked.includes(q.id)}
                  onChange={() => setPicked(picked.includes(q.id) ? picked.filter((x) => x !== q.id) : [...picked, q.id])} />
                <span className="flex-1">{q.questionText}</span>
                <span className="text-xs text-gray-400">{q.marks}m</span>
              </label>
            </li>
          ))}
        </ul>
        <MarksMeter used={Number(paper.totalMarks) + pickedMarks} limit={Number(paper.maxTotalMarks)} />
        {add.error != null && <p className="text-sm text-red-600">{parseApiError(add.error)}</p>}
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          {over && <Button variant="secondary" disabled={picked.length === 0} loading={add.isPending} onClick={() => add.mutate(true)}>Add what fits</Button>}
          <Button disabled={picked.length === 0 || over} loading={add.isPending} onClick={() => add.mutate(false)}>Add {picked.length}</Button>
        </div>
      </div>
    </Modal>
  );
}

function PrintModal({ paper, onDone, onClose }: { paper: PaperResponse; onDone: (p: PaperResponse) => void; onClose: () => void }) {
  const [sets, setSets] = useState(3);
  const [shuffleQuestions, setShuffleQuestions] = useState(true);
  const [shuffleOptions, setShuffleOptions] = useState(true);
  const print = useMutation({
    mutationFn: () => api.post<PaperResponse>(`/papers/${paper.id}/print-sets`, { sets, shuffleQuestions, shuffleOptions }).then((r) => r.data),
    onSuccess: (p) => { onDone(p); onClose(); },
  });
  return (
    <Modal open onClose={onClose} title="Print question paper">
      <div className="space-y-4 text-sm">
        <p className="text-gray-600">
          Each set gets its own random order so students sitting next to each other hold different papers.
          Sections stay in order (A, B, C); questions are shuffled within them. Every set has its own answer key.
        </p>
        <label className="flex items-center justify-between">
          Number of sets
          <select value={sets} onChange={(e) => setSets(Number(e.target.value))} className="h-9 rounded-lg border border-gray-300 px-2">
            {[1, 2, 3, 4, 5, 6].map((n) => <option key={n} value={n}>{n} ({Array.from({ length: n }, (_, i) => String.fromCharCode(65 + i)).join(', ')})</option>)}
          </select>
        </label>
        <label className="flex items-center gap-2"><input type="checkbox" checked={shuffleQuestions} onChange={(e) => setShuffleQuestions(e.target.checked)} /> Shuffle question order</label>
        <label className="flex items-center gap-2"><input type="checkbox" checked={shuffleOptions} onChange={(e) => setShuffleOptions(e.target.checked)} /> Shuffle multiple-choice options</label>
        {paper.status === 'FINAL' && <p className="text-xs text-amber-700">This replaces the sets printed earlier.</p>}
        {print.error != null && <p className="text-red-600">{parseApiError(print.error)}</p>}
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button loading={print.isPending} onClick={() => print.mutate()}><Shuffle size={14} /> Prepare sets</Button>
        </div>
      </div>
    </Modal>
  );
}

function PrintedSets({ paper }: { paper: PaperResponse }) {
  const [busy, setBusy] = useState<string | null>(null);
  const base = paper.title.replace(/[^A-Za-z0-9 _-]/g, '').trim().replace(/\s+/g, '-') || 'paper';
  async function get(key: string, path: string, name: string) {
    setBusy(key);
    try {
      await downloadBlob(path, name);
    } catch {
      toast.error('Download failed.');
    } finally {
      setBusy(null);
    }
  }
  const sets = paper.printSets ?? [];
  return (
    <div className="rounded-xl border border-green-200 bg-green-50 p-4">
      <div className="mb-2 flex items-center justify-between">
        <p className="text-sm font-medium text-green-800">
          {sets.length} set{sets.length === 1 ? '' : 's'} ready · prepared {formatDateTime(sets[0].printedAt)}
          {sets[0].shuffledQuestions ? ' · questions shuffled' : ''}{sets[0].shuffledOptions ? ' · options shuffled' : ''}
        </p>
        <Button size="sm" variant="secondary" loading={busy === 'zip'}
          onClick={() => get('zip', `/papers/${paper.id}/print-sets.zip`, `${base}-all-sets.zip`)}>
          <FileArchive size={14} /> All sets + keys (ZIP)
        </Button>
      </div>
      <div className="flex flex-wrap gap-2">
        {sets.map((s) => (
          <div key={s.setCode} className="flex items-center gap-1 rounded-lg border border-green-200 bg-white px-2 py-1">
            <span className="text-sm font-bold text-gray-800">Set {s.setCode}</span>
            <Button size="sm" variant="ghost" loading={busy === s.setCode}
              onClick={() => get(s.setCode, `/papers/${paper.id}/print-sets/${s.setCode}/pdf`, `${base}-Set-${s.setCode}.pdf`)}>
              <Download size={13} /> Paper
            </Button>
            <Button size="sm" variant="ghost" loading={busy === s.setCode + 'k'}
              onClick={() => get(s.setCode + 'k', `/papers/${paper.id}/print-sets/${s.setCode}/pdf?answerKey=true`, `${base}-Set-${s.setCode}-answer-key.pdf`)}>
              <KeyRound size={13} /> Key
            </Button>
          </div>
        ))}
      </div>
    </div>
  );
}
