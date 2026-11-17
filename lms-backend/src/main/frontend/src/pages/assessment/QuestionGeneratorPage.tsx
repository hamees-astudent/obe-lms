import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { CheckCircle2, ChevronLeft, RefreshCw, Sparkles, Undo2, XCircle } from 'lucide-react';
import api from '@/lib/api';
import { useCourseClos, useIngestion, usePaperSettings, useTopics } from '@/lib/ai';
import { parseApiError } from '@/lib/apiError';
import { formatDateTime } from '@/lib/datetime';
import { toast } from '@/components/ui/Toast';
import Button from '@/components/ui/Button';
import Badge from '@/components/ui/Badge';
import Spinner from '@/components/ui/Spinner';
import AiModeBanner from '@/components/ai/AiModeBanner';
import BloomBadge from '@/components/ai/BloomBadge';
import CitationChip from '@/components/ai/CitationChip';
import CountSteppers from '@/components/ai/CountSteppers';
import DraftEditModal from '@/components/ai/DraftEditModal';
import MarksMeter, { fmtMarks } from '@/components/ai/MarksMeter';
import TopicPicker from '@/components/ai/TopicPicker';
import IngestionBadge from '@/components/ai/IngestionBadge';
import {
  BLOOM_LABELS,
  BLOOM_LEVELS,
  DIFFICULTIES,
  QUESTION_TYPE_LABELS,
  isWrittenType,
  type BloomLevel,
  type Difficulty,
  type GenerationEstimateResponse,
  type GenerationJobResponse,
  type GenerationRequestBody,
  type OfferingSummaryResponse,
  type PaperResponse,
  type QuestionCounts,
  type QuestionDraftResponse,
  type QuizResponse,
  type SaveDraftsBody,
  type SaveDraftsResponse,
  type UpdateDraftBody,
  type UUID,
} from '@/types/api';
import RichText from '@/components/ai/RichText';

type Step = 'source' | 'configure' | 'review';

/**
 * The teacher's question generator: choose material, set the mix and the
 * marks limit, review what comes back, and save it into a quiz or a paper.
 */
export default function QuestionGeneratorPage() {
  const { pscId } = useParams<{ pscId: string }>();
  const [params, setParams] = useSearchParams();
  const jobId = params.get('job');
  const [step, setStep] = useState<Step>(jobId ? 'review' : 'source');

  const offeringQ = useQuery({
    queryKey: ['offerings', pscId],
    queryFn: () => api.get<OfferingSummaryResponse>(`/offerings/${pscId}`).then((r) => r.data),
    enabled: !!pscId,
  });
  const offering = offeringQ.data;

  const [materialIds, setMaterialIds] = useState<UUID[]>([]);
  const [topicIds, setTopicIds] = useState<UUID[]>([]);

  useEffect(() => {
    if (jobId) setStep('review');
  }, [jobId]);

  if (!pscId) return null;

  return (
    <div className="space-y-5">
      <div className="flex items-center gap-2 text-sm">
        <Link to={`/assessment/${pscId}`} className="flex items-center gap-1 text-primary-600 hover:text-primary-800">
          <ChevronLeft size={16} /> {offering ? offering.courseCode : 'Assessment'}
        </Link>
        <span className="text-gray-300">/</span>
        <span className="text-gray-600">Question generator</span>
      </div>
      <div>
        <h1 className="flex items-center gap-2 text-2xl font-bold text-gray-900">
          <Sparkles className="text-primary-600" size={22} /> Generate questions
        </h1>
        <p className="mt-1 text-sm text-gray-500">
          Questions are written from your uploaded material, each with a suggested CLO, Bloom level and
          marks. Nothing reaches students until you save it into a quiz or paper.
        </p>
      </div>
      <AiModeBanner what="the generator (rule-based questions from your material)" />

      <ol className="flex gap-2 text-xs font-medium">
        {(['source', 'configure', 'review'] as Step[]).map((s, i) => (
          <li
            key={s}
            className={`rounded-full px-3 py-1 ${step === s ? 'bg-primary-600 text-white' : 'bg-gray-100 text-gray-600'}`}
          >
            {i + 1}. {s === 'source' ? 'Material' : s === 'configure' ? 'Configure' : 'Review'}
          </li>
        ))}
      </ol>

      {step === 'source' && (
        <SourceStep
          pscId={pscId}
          materialIds={materialIds}
          topicIds={topicIds}
          onMaterials={setMaterialIds}
          onTopics={setTopicIds}
          onNext={() => setStep('configure')}
          onOpenJob={(id) => setParams({ job: id })}
        />
      )}
      {step === 'configure' && (
        <ConfigureStep
          pscId={pscId}
          courseId={offering?.courseId}
          materialIds={materialIds}
          topicIds={topicIds}
          onBack={() => setStep('source')}
          onStarted={(job) => setParams({ job: job.id })}
        />
      )}
      {step === 'review' && jobId && (
        <ReviewStep
          pscId={pscId}
          courseId={offering?.courseId}
          jobId={jobId}
          onNew={() => {
            setParams({});
            setStep('source');
          }}
        />
      )}
    </div>
  );
}

// ── Step 1: material ─────────────────────────────────────────────────────────

function SourceStep({
  pscId,
  materialIds,
  topicIds,
  onMaterials,
  onTopics,
  onNext,
  onOpenJob,
}: {
  pscId: UUID;
  materialIds: UUID[];
  topicIds: UUID[];
  onMaterials: (ids: UUID[]) => void;
  onTopics: (ids: UUID[]) => void;
  onNext: () => void;
  onOpenJob: (id: UUID) => void;
}) {
  const ingestionQ = useIngestion(pscId);
  const topicsQ = useTopics(pscId);
  const jobsQ = useQuery({
    queryKey: ['offerings', pscId, 'question-jobs'],
    queryFn: () => api.get<GenerationJobResponse[]>(`/offerings/${pscId}/ai/question-jobs`).then((r) => r.data),
  });
  const materials = ingestionQ.data ?? [];
  const ready = materials.filter((m) => m.status === 'READY');
  const topics = (topicsQ.data ?? []).filter((t) => materialIds.length === 0 || materialIds.includes(t.materialId));

  return (
    <div className="grid gap-4 lg:grid-cols-[1fr_18rem]">
      <div className="space-y-4 rounded-xl border border-gray-200 bg-white p-4">
        <div>
          <p className="mb-2 text-sm font-medium text-gray-700">Materials</p>
          {ingestionQ.isLoading ? (
            <Spinner />
          ) : materials.length === 0 ? (
            <p className="text-sm text-gray-500">
              No documents yet. <Link className="text-primary-600 underline" to={`/courses/${pscId}`}>Upload a PDF, PowerPoint or Word file</Link> first.
            </p>
          ) : (
            <ul className="space-y-1">
              {materials.map((m) => (
                <li key={m.materialId} className="flex items-center justify-between rounded-lg px-2 py-1.5 hover:bg-gray-50">
                  <label className="flex items-center gap-2 text-sm">
                    <input
                      type="checkbox"
                      disabled={m.status !== 'READY'}
                      checked={materialIds.includes(m.materialId)}
                      onChange={() =>
                        onMaterials(materialIds.includes(m.materialId)
                          ? materialIds.filter((x) => x !== m.materialId)
                          : [...materialIds, m.materialId])
                      }
                    />
                    {m.title}
                    {!m.visible && <Badge variant="default">Hidden</Badge>}
                  </label>
                  <IngestionBadge status={m.status} error={m.error} />
                </li>
              ))}
            </ul>
          )}
          <p className="mt-1 text-xs text-gray-500">None ticked = all indexed materials.</p>
        </div>
        <div>
          <p className="mb-2 text-sm font-medium text-gray-700">Topics <span className="font-normal text-gray-500">(optional — narrows to these pages/slides)</span></p>
          {topics.length === 0 ? (
            <p className="text-sm text-gray-500">No topics found yet.</p>
          ) : (
            <TopicPicker topics={topics} selected={topicIds} onChange={onTopics} />
          )}
        </div>
        <div className="flex justify-end">
          <Button disabled={ready.length === 0} onClick={onNext}>Next: configure</Button>
        </div>
      </div>

      <div className="rounded-xl border border-gray-200 bg-white p-4">
        <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-gray-500">Recent runs</p>
        {(jobsQ.data ?? []).length === 0 ? (
          <p className="text-xs text-gray-400">None yet.</p>
        ) : (
          <ul className="space-y-1">
            {(jobsQ.data ?? []).slice(0, 10).map((j) => (
              <li key={j.id}>
                <button onClick={() => onOpenJob(j.id)} className="flex w-full items-center justify-between rounded px-2 py-1 text-left text-xs hover:bg-gray-50">
                  <span>{formatDateTime(j.createdAt)} · {j.draftCount} q · {fmtMarks(j.totalMarks)} marks</span>
                  <Badge variant={j.status === 'READY' ? 'success' : j.status === 'FAILED' ? 'danger' : 'info'}>{j.status}</Badge>
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}

// ── Step 2: configure ────────────────────────────────────────────────────────

function ConfigureStep({
  pscId,
  courseId,
  materialIds,
  topicIds,
  onBack,
  onStarted,
}: {
  pscId: UUID;
  courseId?: UUID;
  materialIds: UUID[];
  topicIds: UUID[];
  onBack: () => void;
  onStarted: (job: GenerationJobResponse) => void;
}) {
  const settingsQ = usePaperSettings(pscId);
  const closQ = useCourseClos(courseId);
  const [counts, setCounts] = useState<QuestionCounts>({ MCQ: 10, MSQ: 2, SHORT_ANSWER: 3, LONG_ANSWER: 1 });
  const [difficulty, setDifficulty] = useState<Difficulty>('MEDIUM');
  const [bloomLevels, setBloomLevels] = useState<BloomLevel[]>([]);
  const [cloIds, setCloIds] = useState<UUID[]>([]);
  const [limit, setLimit] = useState<number | null>(null);
  const [instructions, setInstructions] = useState('');
  const maxLimit = settingsQ.data?.maxTotalMarks ?? 100;
  const marksLimit = limit ?? Math.min(50, maxLimit);

  const body: GenerationRequestBody = useMemo(() => ({
    materialIds, topicIds, counts, difficulty, bloomLevels, targetCloIds: cloIds,
    maxTotalMarks: marksLimit, instructions: instructions || undefined,
  }), [materialIds, topicIds, counts, difficulty, bloomLevels, cloIds, marksLimit, instructions]);

  // The server's estimate, so the meter and the limit check use the same marks table.
  const [estimate, setEstimate] = useState<GenerationEstimateResponse | null>(null);
  useEffect(() => {
    const t = setTimeout(() => {
      api.post<GenerationEstimateResponse>(`/offerings/${pscId}/ai/question-jobs/estimate`, body)
        .then((r) => setEstimate(r.data))
        .catch(() => setEstimate(null));
    }, 250);
    return () => clearTimeout(t);
  }, [pscId, body]);

  const start = useMutation({
    mutationFn: () => api.post<GenerationJobResponse>(`/offerings/${pscId}/ai/question-jobs`, body).then((r) => r.data),
    onSuccess: onStarted,
  });

  return (
    <div className="space-y-5 rounded-xl border border-gray-200 bg-white p-4">
      <div className="flex flex-wrap items-end justify-between gap-4 rounded-lg bg-gray-50 p-3">
        <label className="text-sm">
          <span className="mb-1 block font-medium text-gray-700">Marks limit for this paper</span>
          <input
            type="number"
            min={1}
            max={maxLimit}
            value={marksLimit}
            onChange={(e) => setLimit(Number(e.target.value))}
            className="h-9 w-28 rounded-lg border border-gray-300 px-2 text-sm"
          />
          <span className="ml-2 text-xs text-gray-500">course maximum {fmtMarks(maxLimit)}</span>
        </label>
        {estimate && <MarksMeter used={estimate.estimatedMarks} limit={marksLimit} />}
      </div>
      {estimate?.problem && <p className="text-sm text-red-600">{estimate.problem}</p>}

      <div>
        <p className="mb-1 text-sm font-medium text-gray-700">Question mix</p>
        <CountSteppers counts={counts} onChange={setCounts} marksPerQuestion={estimate?.marksPerQuestion} />
      </div>

      <div className="grid gap-4 md:grid-cols-2">
        <div>
          <p className="mb-1 text-sm font-medium text-gray-700">Difficulty</p>
          <div className="flex gap-2">
            {DIFFICULTIES.map((d) => (
              <button key={d} type="button" onClick={() => setDifficulty(d)}
                className={`rounded-lg border px-3 py-1.5 text-sm ${difficulty === d ? 'border-primary-500 bg-primary-50 text-primary-700' : 'border-gray-200 text-gray-600'}`}>
                {d.charAt(0) + d.slice(1).toLowerCase()}
              </button>
            ))}
          </div>
        </div>
        <div>
          <p className="mb-1 text-sm font-medium text-gray-700">Bloom levels <span className="font-normal text-gray-500">(none = by question type)</span></p>
          <div className="flex flex-wrap gap-1.5">
            {BLOOM_LEVELS.map((b) => (
              <button key={b} type="button"
                onClick={() => setBloomLevels(bloomLevels.includes(b) ? bloomLevels.filter((x) => x !== b) : [...bloomLevels, b])}
                className={`rounded-full border px-2.5 py-1 text-xs ${bloomLevels.includes(b) ? 'border-primary-500 bg-primary-50 text-primary-700' : 'border-gray-200 text-gray-600'}`}>
                {BLOOM_LABELS[b]}
              </button>
            ))}
          </div>
        </div>
      </div>

      {(closQ.data ?? []).length > 0 && (
        <div>
          <p className="mb-1 text-sm font-medium text-gray-700">Target CLOs <span className="font-normal text-gray-500">(none = any)</span></p>
          <div className="flex flex-wrap gap-1.5">
            {(closQ.data ?? []).map((c) => (
              <button key={c.id} type="button" title={c.title}
                onClick={() => setCloIds(cloIds.includes(c.id) ? cloIds.filter((x) => x !== c.id) : [...cloIds, c.id])}
                className={`rounded-full border px-2.5 py-1 text-xs ${cloIds.includes(c.id) ? 'border-primary-500 bg-primary-50 text-primary-700' : 'border-gray-200 text-gray-600'}`}>
                {c.code} <span className="text-gray-400">{c.title.length > 40 ? c.title.slice(0, 40) + '…' : c.title}</span>
              </button>
            ))}
          </div>
        </div>
      )}

      <label className="block text-sm">
        <span className="mb-1 block font-medium text-gray-700">Extra instructions <span className="font-normal text-gray-500">(optional, used by the language model)</span></span>
        <input value={instructions} onChange={(e) => setInstructions(e.target.value)} maxLength={1000}
          placeholder="e.g. focus on worked examples; avoid trick questions"
          className="h-9 w-full rounded-lg border border-gray-300 px-3 text-sm" />
      </label>

      {start.error != null && <p className="text-sm text-red-600">{parseApiError(start.error)}</p>}
      <div className="flex justify-between">
        <Button variant="secondary" onClick={onBack}>Back</Button>
        <Button disabled={!estimate?.withinLimit} loading={start.isPending} onClick={() => start.mutate()}>
          <Sparkles size={14} /> Generate {estimate?.questionCount ?? ''} questions
        </Button>
      </div>
    </div>
  );
}

// ── Step 3: review ───────────────────────────────────────────────────────────

function ReviewStep({ pscId, courseId, jobId, onNew }: { pscId: UUID; courseId?: UUID; jobId: UUID; onNew: () => void }) {
  const qc = useQueryClient();
  const navigate = useNavigate();
  const closQ = useCourseClos(courseId);
  const clos = closQ.data ?? [];
  const jobQ = useQuery({
    queryKey: ['question-jobs', jobId],
    queryFn: () => api.get<GenerationJobResponse>(`/ai/question-jobs/${jobId}`).then((r) => r.data),
    refetchInterval: (q) => (q.state.data && ['PENDING', 'RUNNING'].includes(q.state.data.status) ? 2500 : false),
  });
  const job = jobQ.data;
  const drafts = job?.drafts ?? [];
  const [selected, setSelected] = useState<Set<UUID> | null>(null);
  const [editing, setEditing] = useState<QuestionDraftResponse | null>(null);

  // Every draft starts selected except ones already rejected.
  useEffect(() => {
    if (job?.status === 'READY' && selected === null) {
      setSelected(new Set(drafts.filter((d) => d.status !== 'REJECTED').map((d) => d.id)));
    }
  }, [job?.status, drafts, selected]);

  const refresh = () => qc.invalidateQueries({ queryKey: ['question-jobs', jobId] });
  const update = useMutation({
    mutationFn: ({ id, body }: { id: UUID; body: UpdateDraftBody }) =>
      api.put<QuestionDraftResponse>(`/ai/question-drafts/${id}`, body).then((r) => r.data),
    onSuccess: () => { refresh(); setEditing(null); },
  });
  const regenerate = useMutation({
    mutationFn: (id: UUID) => api.post<QuestionDraftResponse>(`/ai/question-drafts/${id}/regenerate`, undefined, { timeout: 180_000 }).then((r) => r.data),
    onSuccess: refresh,
  });

  const limit = Number(job?.config.maxTotalMarks ?? 0);
  const chosen = drafts.filter((d) => selected?.has(d.id));
  const chosenMarks = chosen.reduce((s, d) => s + Number(d.marks), 0);

  if (jobQ.isLoading || !job) return <div className="flex justify-center py-10"><Spinner /></div>;
  if (job.status === 'PENDING' || job.status === 'RUNNING') {
    return (
      <div className="flex flex-col items-center rounded-xl border border-gray-200 bg-white py-16 text-center">
        <Spinner size="lg" />
        <p className="mt-4 text-sm text-gray-600">Writing questions from your material…</p>
        <p className="mt-1 text-xs text-gray-400">With the local model on a CPU this can take a few minutes.</p>
      </div>
    );
  }
  if (job.status === 'FAILED') {
    return (
      <div className="space-y-3 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">
        <p>{job.error}</p>
        <Button variant="secondary" onClick={onNew}>Try again</Button>
      </div>
    );
  }

  function draftBody(d: QuestionDraftResponse, patch: Partial<UpdateDraftBody>): UpdateDraftBody {
    return {
      questionText: d.questionText, options: d.options, correctAnswer: d.correctAnswer,
      modelAnswer: d.modelAnswer, rubric: d.rubric, explanation: d.explanation, marks: Number(d.marks),
      bloomLevel: d.bloomLevel, difficulty: d.difficulty, cloIds: d.suggestedCloIds, status: d.status, ...patch,
    };
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-gray-200 bg-white p-3">
        <div className="text-sm text-gray-600">
          {drafts.length} questions · written by{' '}
          {job.mode === 'LLM' ? 'the local model' : job.mode === 'HYBRID' ? 'the local model and templates' : 'templates (basic mode)'}
        </div>
        <MarksMeter used={chosenMarks} limit={limit} label={`marks selected (${chosen.length} q)`} />
        <Button size="sm" variant="ghost" onClick={onNew}>New run</Button>
      </div>
      {job.notes.length > 0 && (
        <ul className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-2 text-xs text-amber-800">
          {job.notes.map((n) => <li key={n}>{n}</li>)}
        </ul>
      )}

      {drafts.map((d, i) => {
        const on = selected?.has(d.id) ?? false;
        const rejected = d.status === 'REJECTED';
        return (
          <div key={d.id} className={`rounded-xl border bg-white p-4 ${on ? 'border-primary-300' : 'border-gray-200'} ${rejected ? 'opacity-60' : ''}`}>
            <div className="flex items-start gap-3">
              <input
                type="checkbox"
                aria-label={`Include question ${i + 1}`}
                checked={on}
                disabled={rejected}
                onChange={() => {
                  const next = new Set(selected ?? []);
                  if (on) next.delete(d.id); else next.add(d.id);
                  setSelected(next);
                }}
                className="mt-1 h-4 w-4"
              />
              <div className="min-w-0 flex-1">
                <div className="mb-1 flex flex-wrap items-center gap-1.5">
                  <span className="text-xs font-bold text-gray-500">Q{i + 1}</span>
                  <Badge variant={isWrittenType(d.type) ? 'purple' : 'info'}>{QUESTION_TYPE_LABELS[d.type]}</Badge>
                  <BloomBadge level={d.bloomLevel} title={d.bloomSource} />
                  <Badge variant="default">{d.source === 'LLM' ? 'Model' : 'Template'}</Badge>
                  {d.edited && <Badge variant="warning">Edited</Badge>}
                  {d.status === 'ACCEPTED' && <Badge variant="success">Saved</Badge>}
                </div>
                <RichText text={d.questionText} compact className="text-sm font-medium text-gray-900" />
                {!isWrittenType(d.type) ? (
                  <ul className="mt-2 space-y-1">
                    {d.options.map((o, oi) => (
                      <li key={o.id} className={`flex items-center gap-2 text-sm ${d.correctAnswer.includes(o.id) ? 'font-medium text-green-700' : 'text-gray-700'}`}>
                        <span className="w-4 text-xs text-gray-400">{String.fromCharCode(65 + oi)}</span>
                        <RichText text={o.text} compact />
                        {d.correctAnswer.includes(o.id) && <CheckCircle2 size={13} />}
                      </li>
                    ))}
                  </ul>
                ) : (
                  <details className="mt-2 text-sm">
                    <summary className="cursor-pointer text-xs text-primary-600">Model answer and rubric</summary>
                    {d.modelAnswer && <RichText text={d.modelAnswer} className="mt-1 text-gray-700" />}
                    <ul className="mt-1 list-disc pl-5 text-xs text-gray-600">
                      {d.rubric.map((r, ri) => <li key={ri}>{r.criterion} — {fmtMarks(r.marks)}{r.keywords?.length ? ` (${r.keywords.join(', ')})` : ''}</li>)}
                    </ul>
                  </details>
                )}

                <div className="mt-3 flex flex-wrap items-center gap-3 border-t border-gray-100 pt-3 text-xs">
                  <label className="flex items-center gap-1">
                    Marks
                    <input
                      type="number" min={0.5} step={0.5} defaultValue={d.marks}
                      onBlur={(e) => {
                        const m = Number(e.target.value);
                        if (m > 0 && m !== Number(d.marks)) update.mutate({ id: d.id, body: draftBody(d, { marks: m }) });
                      }}
                      className="h-7 w-16 rounded border border-gray-300 px-1.5"
                    />
                  </label>
                  <label className="flex items-center gap-1">
                    Bloom
                    <select
                      value={d.bloomLevel ?? ''}
                      onChange={(e) => update.mutate({ id: d.id, body: draftBody(d, { bloomLevel: e.target.value as BloomLevel }) })}
                      className="h-7 rounded border border-gray-300 px-1"
                    >
                      {BLOOM_LEVELS.map((b) => <option key={b} value={b}>{BLOOM_LABELS[b]}</option>)}
                    </select>
                  </label>
                  <span className="flex flex-wrap items-center gap-1">
                    CLO
                    {clos.map((c) => {
                      const has = d.suggestedCloIds.includes(c.id);
                      const score = d.cloScores.find((s) => s.cloId === c.id)?.score;
                      return (
                        <button
                          key={c.id}
                          type="button"
                          title={`${c.title}${score !== undefined ? ` — similarity ${score.toFixed(2)}` : ''}`}
                          onClick={() => update.mutate({ id: d.id, body: draftBody(d, {
                            cloIds: has ? d.suggestedCloIds.filter((x) => x !== c.id) : [...d.suggestedCloIds, c.id],
                          }) })}
                          className={`rounded-full border px-2 py-0.5 ${has ? 'border-primary-500 bg-primary-50 text-primary-700' : 'border-gray-200 text-gray-500'}`}
                        >
                          {c.code}
                        </button>
                      );
                    })}
                  </span>
                </div>
                {d.cloRationale && <p className="mt-1 text-xs text-gray-500">{d.cloRationale}</p>}
                {d.sources.length > 0 && (
                  <div className="mt-2 flex flex-wrap gap-1.5">
                    {d.sources.map((s) => <CitationChip key={s.chunkId} citation={s} />)}
                  </div>
                )}
              </div>
              <div className="flex flex-shrink-0 flex-col gap-1">
                <Button size="sm" variant="ghost" onClick={() => setEditing(d)}>Edit</Button>
                <Button size="sm" variant="ghost" loading={regenerate.isPending && regenerate.variables === d.id} onClick={() => regenerate.mutate(d.id)}>
                  <RefreshCw size={12} /> New
                </Button>
                {rejected ? (
                  <Button size="sm" variant="ghost" onClick={() => update.mutate({ id: d.id, body: draftBody(d, { status: 'PENDING' }) })}>
                    <Undo2 size={12} /> Restore
                  </Button>
                ) : (
                  <Button size="sm" variant="ghost" onClick={() => {
                    update.mutate({ id: d.id, body: draftBody(d, { status: 'REJECTED' }) });
                    const next = new Set(selected ?? []); next.delete(d.id); setSelected(next);
                  }}>
                    <XCircle size={12} /> Reject
                  </Button>
                )}
              </div>
            </div>
          </div>
        );
      })}

      {(update.error != null || regenerate.error != null) && (
        <p className="text-sm text-red-600">{parseApiError(update.error ?? regenerate.error)}</p>
      )}

      <SaveBar
        pscId={pscId}
        jobId={jobId}
        draftIds={chosen.map((d) => d.id)}
        marks={chosenMarks}
        limit={limit}
        onSaved={(r) => {
          toast.success(`${r.saved} question(s) saved.`);
          refresh();
          navigate(r.target === 'QUIZ' ? `/assessment/${pscId}?quiz=${r.targetId}` : `/assessment/${pscId}/papers/${r.targetId}`);
        }}
      />

      {editing && (
        <DraftEditModal
          draft={editing}
          saving={update.isPending}
          error={update.error != null ? parseApiError(update.error) : undefined}
          onSave={(body) => update.mutate({ id: editing.id, body })}
          onClose={() => setEditing(null)}
        />
      )}
    </div>
  );
}

function SaveBar({
  pscId, jobId, draftIds, marks, limit, onSaved,
}: {
  pscId: UUID; jobId: UUID; draftIds: UUID[]; marks: number; limit: number; onSaved: (r: SaveDraftsResponse) => void;
}) {
  const [target, setTarget] = useState<'QUIZ' | 'PAPER'>('PAPER');
  const [existing, setExisting] = useState('');
  const [title, setTitle] = useState('');
  const quizzesQ = useQuery({
    queryKey: ['offerings', pscId, 'quizzes'],
    queryFn: () => api.get<QuizResponse[]>(`/offerings/${pscId}/quizzes`).then((r) => r.data),
  });
  const papersQ = useQuery({
    queryKey: ['offerings', pscId, 'papers'],
    queryFn: () => api.get<PaperResponse[]>(`/offerings/${pscId}/papers`).then((r) => r.data),
  });
  const save = useMutation({
    mutationFn: (body: SaveDraftsBody) => api.post<SaveDraftsResponse>(`/ai/question-jobs/${jobId}/save`, body).then((r) => r.data),
    onSuccess: onSaved,
  });
  const list = target === 'QUIZ' ? quizzesQ.data ?? [] : papersQ.data ?? [];
  const overLimit = target === 'PAPER' && !existing && marks > limit;

  return (
    <div className="sticky bottom-0 space-y-2 rounded-xl border border-gray-200 bg-white/95 p-3 shadow-lg backdrop-blur">
      <div className="flex flex-wrap items-center gap-2 text-sm">
        <span className="font-medium text-gray-700">Save {draftIds.length} to</span>
        <select value={target} onChange={(e) => { setTarget(e.target.value as 'QUIZ' | 'PAPER'); setExisting(''); }} className="h-9 rounded-lg border border-gray-300 px-2">
          <option value="PAPER">a question paper</option>
          <option value="QUIZ">an online quiz</option>
        </select>
        <select value={existing} onChange={(e) => setExisting(e.target.value)} className="h-9 rounded-lg border border-gray-300 px-2">
          <option value="">New {target === 'QUIZ' ? 'quiz' : 'paper'}…</option>
          {list.map((x) => <option key={x.id} value={x.id}>{x.title}</option>)}
        </select>
        {!existing && (
          <input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="Title"
            className="h-9 min-w-[12rem] flex-1 rounded-lg border border-gray-300 px-3" />
        )}
        <Button
          disabled={draftIds.length === 0 || (!existing && !title.trim()) || overLimit}
          loading={save.isPending}
          onClick={() => save.mutate({
            target,
            draftIds,
            quizId: target === 'QUIZ' && existing ? existing : undefined,
            paperId: target === 'PAPER' && existing ? existing : undefined,
            newQuiz: target === 'QUIZ' && !existing ? { title } : undefined,
            newPaper: target === 'PAPER' && !existing ? { title, maxTotalMarks: limit } : undefined,
          })}
        >
          Save
        </Button>
      </div>
      {overLimit && <p className="text-xs text-red-600">The selection is over the {fmtMarks(limit)}-mark limit — deselect or lower marks.</p>}
      {save.error != null && <p className="text-xs text-red-600">{parseApiError(save.error)}</p>}
    </div>
  );
}
