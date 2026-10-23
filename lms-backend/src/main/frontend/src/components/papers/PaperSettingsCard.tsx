import { useEffect, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Settings2 } from 'lucide-react';
import api from '@/lib/api';
import { usePaperSettings } from '@/lib/ai';
import { parseApiError } from '@/lib/apiError';
import { toast } from '@/components/ui/Toast';
import Button from '@/components/ui/Button';
import {
  BLOOM_LABELS,
  BLOOM_LEVELS,
  DIFFICULTIES,
  QUESTION_TYPE_LABELS,
  type MarksTable,
  type PaperSettingsBody,
  type PaperSettingsResponse,
  type QuestionType,
  type UUID,
} from '@/types/api';

const TYPES: QuestionType[] = ['MCQ', 'MSQ', 'SHORT_ANSWER', 'LONG_ANSWER'];

/**
 * The offering's paper limits — so no generated paper runs too long — and the
 * table that prices questions by type × Bloom level × difficulty.
 */
export default function PaperSettingsCard({ pscId }: { pscId: UUID }) {
  const qc = useQueryClient();
  const settingsQ = usePaperSettings(pscId);
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState<PaperSettingsResponse | null>(null);
  useEffect(() => {
    if (settingsQ.data) setForm(settingsQ.data);
  }, [settingsQ.data]);

  const save = useMutation({
    mutationFn: (body: PaperSettingsBody) =>
      api.put<PaperSettingsResponse>(`/offerings/${pscId}/paper-settings`, body).then((r) => r.data),
    onSuccess: (d) => {
      qc.setQueryData(['offerings', pscId, 'paper-settings'], d);
      toast.success('Paper limits saved.');
      setOpen(false);
    },
  });

  if (!form) return null;
  const table = form.marksTable;
  const setTable = (t: MarksTable) => setForm({ ...form, marksTable: t });
  const num = (v: string) => (v === '' ? 0 : Number(v));

  return (
    <div className="rounded-xl border border-gray-200 bg-white p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="flex items-center gap-2 text-sm text-gray-700">
          <Settings2 size={15} className="text-gray-400" />
          Papers may have at most <strong>{form.maxTotalMarks}</strong> marks, <strong>{form.maxQuestions}</strong>{' '}
          questions and <strong>{form.maxDurationMinutes}</strong> minutes. Student practice papers:{' '}
          <strong>{form.mockMaxTotalMarks}</strong> marks.
        </p>
        <Button size="sm" variant="ghost" onClick={() => setOpen((o) => !o)}>{open ? 'Close' : 'Change limits'}</Button>
      </div>

      {open && (
        <div className="mt-4 space-y-4 border-t border-gray-100 pt-4">
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
            {([
              ['maxTotalMarks', 'Max marks per paper'],
              ['maxQuestions', 'Max questions'],
              ['maxDurationMinutes', 'Max minutes'],
              ['mockMaxTotalMarks', 'Max marks, practice'],
            ] as const).map(([key, label]) => (
              <label key={key} className="text-xs">
                <span className="mb-1 block font-medium text-gray-700">{label}</span>
                <input type="number" min={1} value={form[key]}
                  onChange={(e) => setForm({ ...form, [key]: num(e.target.value) })}
                  className="h-9 w-full rounded-lg border border-gray-300 px-2 text-sm" />
              </label>
            ))}
          </div>

          <div>
            <p className="mb-1 text-xs font-semibold uppercase tracking-wide text-gray-500">Marks table</p>
            <p className="mb-2 text-xs text-gray-500">
              A question's marks = base for its type × its Bloom multiplier × its difficulty multiplier, rounded to ½.
            </p>
            <div className="grid gap-3 md:grid-cols-3">
              <Group title="Base marks">
                {TYPES.map((t) => (
                  <Row key={t} label={QUESTION_TYPE_LABELS[t]} value={table.base[t]}
                    onChange={(v) => setTable({ ...table, base: { ...table.base, [t]: v } })} />
                ))}
              </Group>
              <Group title="Bloom multiplier">
                {BLOOM_LEVELS.map((b) => (
                  <Row key={b} label={BLOOM_LABELS[b]} value={table.bloom[b]} step={0.05}
                    onChange={(v) => setTable({ ...table, bloom: { ...table.bloom, [b]: v } })} />
                ))}
              </Group>
              <Group title="Difficulty multiplier">
                {DIFFICULTIES.map((d) => (
                  <Row key={d} label={d.charAt(0) + d.slice(1).toLowerCase()} value={table.difficulty[d]} step={0.05}
                    onChange={(v) => setTable({ ...table, difficulty: { ...table.difficulty, [d]: v } })} />
                ))}
              </Group>
            </div>
          </div>

          {save.error != null && <p className="text-sm text-red-600">{parseApiError(save.error)}</p>}
          <div className="flex justify-end">
            <Button loading={save.isPending} onClick={() => save.mutate(form)}>Save limits</Button>
          </div>
        </div>
      )}
    </div>
  );
}

function Group({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="rounded-lg border border-gray-100 p-2">
      <p className="mb-1 text-xs font-medium text-gray-600">{title}</p>
      <div className="space-y-1">{children}</div>
    </div>
  );
}

function Row({ label, value, onChange, step = 0.5 }: { label: string; value: number; onChange: (v: number) => void; step?: number }) {
  return (
    <label className="flex items-center justify-between gap-2 text-xs text-gray-700">
      {label}
      <input type="number" min={0.05} step={step} value={value} onChange={(e) => onChange(Number(e.target.value))}
        className="h-7 w-20 rounded border border-gray-300 px-1.5" />
    </label>
  );
}
