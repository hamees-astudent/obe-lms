import { useState, useEffect } from 'react';
import { useQuery, useQueries, useMutation } from '@tanstack/react-query';
import {
  GraduationCap,
  Download,
  Eye,
  ChevronDown,
  ChevronUp,
  Search,
  RefreshCw,
  BookOpen,
  X,
} from 'lucide-react';
import api from '@/lib/api';
import { useActivePrograms } from '@/lib/queries';
import { parseApiError } from '@/lib/apiError';
import { toast } from '@/components/ui/Toast';
import Spinner from '@/components/ui/Spinner';
import Modal from '@/components/ui/Modal';
import CopyableId from '@/components/ui/CopyableId';
import { useAuthStore } from '@/store/authStore';
import type {
  TranscriptSummaryResponse,
  TranscriptResponse,
  SemesterResponse,
  UserSummaryResponse,
} from '@/types/api';

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------
type Page<T> = { content: T[]; totalElements: number };

// ---------------------------------------------------------------------------
// PDF Download helper
// ---------------------------------------------------------------------------
async function downloadPdf(id: string, filename: string) {
  const res = await api.get<Blob>(`/transcripts/${id}/pdf`, {
    responseType: 'blob',
  });
  const url = URL.createObjectURL(res.data);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}

// ---------------------------------------------------------------------------
// GPA Badge
// ---------------------------------------------------------------------------
function GpaBadge({ label, value }: { label: string; value: number }) {
  const color =
    value >= 3.5
      ? 'bg-green-100 text-green-700'
      : value >= 2.5
        ? 'bg-blue-100 text-blue-700'
        : value >= 1.5
          ? 'bg-yellow-100 text-yellow-700'
          : 'bg-red-100 text-red-700';
  return (
    <div className={`rounded-lg px-4 py-2 text-center ${color}`}>
      <p className="text-xs font-medium uppercase tracking-wide opacity-70">{label}</p>
      <p className="text-2xl font-bold">{value.toFixed(2)}</p>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Grade Color helper
// ---------------------------------------------------------------------------
function gradeColor(letter: string) {
  if (['A+', 'A', 'A-'].includes(letter)) return 'text-green-700 font-semibold';
  if (['B+', 'B', 'B-'].includes(letter)) return 'text-blue-700 font-semibold';
  if (['C+', 'C', 'C-'].includes(letter)) return 'text-yellow-700 font-semibold';
  return 'text-red-600 font-semibold';
}

// ---------------------------------------------------------------------------
// Transcript Detail Modal
// ---------------------------------------------------------------------------
function TranscriptDetailModal({
  id,
  onClose,
}: {
  id: string;
  onClose: () => void;
}) {
  const [expandedCourse, setExpandedCourse] = useState<string | null>(null);
  const [downloading, setDownloading] = useState(false);

  const detailQ = useQuery({
    queryKey: ['transcripts', id],
    queryFn: () =>
      api.get<TranscriptResponse>(`/transcripts/${id}`).then((r) => r.data),
  });

  const t = detailQ.data;

  async function handleDownload() {
    if (!t) return;
    setDownloading(true);
    try {
      await downloadPdf(
        id,
        `transcript-${t.studentName.replace(/\s+/g, '-')}-${t.semesterName.replace(/\s+/g, '-')}.pdf`,
      );
    } finally {
      setDownloading(false);
    }
  }

  return (
    <Modal open onClose={onClose} title="Transcript" maxWidth="max-w-4xl">
      <div className="space-y-6">
          {detailQ.isLoading && (
            <p className="text-center text-sm text-gray-500 py-10">Loading transcript…</p>
          )}
          {detailQ.isError && (
            <p className="text-center text-sm text-red-600 py-10">Failed to load transcript.</p>
          )}

          {t && (
            <>
              {/* Student info */}
              <div className="rounded-xl border bg-gray-50 p-5 grid grid-cols-1 gap-3 sm:grid-cols-2">
                <div>
                  <p className="text-xs text-gray-500">Student</p>
                  <p className="font-semibold text-gray-900">{t.studentName}</p>
                  <p className="text-sm text-gray-600">{t.studentEmail}</p>
                </div>
                <div>
                  <p className="text-xs text-gray-500">Program</p>
                  <p className="font-semibold text-gray-900">{t.programName}</p>
                </div>
                <div>
                  <p className="text-xs text-gray-500">Semester</p>
                  <p className="font-semibold text-gray-900">{t.semesterName}</p>
                </div>
                <div>
                  <p className="text-xs text-gray-500">Generated At</p>
                  <p className="font-semibold text-gray-900">
                    {new Date(t.generatedAt).toLocaleDateString()}
                  </p>
                </div>
              </div>

              {/* GPA Summary */}
              <div className="flex flex-wrap gap-4">
                <GpaBadge label="SGPA" value={t.snapshotData.sgpa} />
                <GpaBadge label="CGPA" value={t.snapshotData.cgpa} />
                <div className="rounded-lg border px-4 py-2 text-center">
                  <p className="text-xs font-medium uppercase tracking-wide text-gray-500">
                    Credit Hours
                  </p>
                  <p className="text-2xl font-bold text-gray-900">
                    {t.snapshotData.courses.reduce((s, c) => s + c.creditHours, 0)}
                  </p>
                </div>
              </div>

              {/* Courses */}
              <div>
                <h3 className="mb-3 text-sm font-semibold uppercase tracking-wide text-gray-500">
                  Course Results
                </h3>
                {/* Six columns will not fit a phone; scroll the table, not the page. */}
                <div className="overflow-x-auto rounded-xl border scrollbar-thin">
                  <table className="w-full min-w-[34rem] text-sm">
                    <thead className="bg-gray-50 text-xs uppercase text-gray-500">
                      <tr>
                        <th className="px-4 py-3 text-left">Course</th>
                        <th className="px-4 py-3 text-center">Credits</th>
                        <th className="px-4 py-3 text-center">Marks</th>
                        <th className="px-4 py-3 text-center">%</th>
                        <th className="px-4 py-3 text-center">Grade</th>
                        <th className="px-4 py-3 text-center">GP</th>
                        <th className="px-4 py-3 text-center">CLOs</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y">
                      {t.snapshotData.courses.map((c) => (
                        <>
                          <tr key={c.courseId} className="hover:bg-gray-50">
                            <td className="px-4 py-3">
                              <p className="font-medium text-gray-900">{c.courseCode}</p>
                              <p className="text-xs text-gray-500">{c.courseName}</p>
                            </td>
                            <td className="px-4 py-3 text-center text-gray-700">
                              {c.creditHours}
                            </td>
                            <td className="px-4 py-3 text-center text-gray-700">
                              {c.obtainedMarks}/{c.totalMarks}
                            </td>
                            <td className="px-4 py-3 text-center text-gray-700">
                              {c.percentage.toFixed(1)}%
                            </td>
                            <td className={`px-4 py-3 text-center ${gradeColor(c.letterGrade)}`}>
                              {c.letterGrade}
                            </td>
                            <td className="px-4 py-3 text-center text-gray-700">
                              {c.gradePoints.toFixed(2)}
                            </td>
                            <td className="px-4 py-3 text-center">
                              {c.cloAttainments.length > 0 && (
                                <button
                                  onClick={() =>
                                    setExpandedCourse(
                                      expandedCourse === c.courseId ? null : c.courseId,
                                    )
                                  }
                                  className="inline-flex items-center gap-1 text-xs text-primary-600 hover:underline"
                                >
                                  {c.cloAttainments.length}
                                  {expandedCourse === c.courseId ? (
                                    <ChevronUp className="h-3 w-3" />
                                  ) : (
                                    <ChevronDown className="h-3 w-3" />
                                  )}
                                </button>
                              )}
                            </td>
                          </tr>
                          {expandedCourse === c.courseId && (
                            <tr key={`${c.courseId}-clo`} className="bg-primary-50">
                              <td colSpan={7} className="px-6 py-3">
                                <p className="mb-2 text-xs font-semibold uppercase text-primary-700">
                                  CLO Attainments — {c.courseCode}
                                </p>
                                <div className="grid gap-2 sm:grid-cols-2">
                                  {c.cloAttainments.map((clo) => (
                                    <div key={clo.cloId} className="flex items-center gap-3">
                                      <span className="w-20 shrink-0 rounded bg-primary-100 px-2 py-0.5 text-center text-xs font-medium text-primary-700">
                                        {clo.cloCode}
                                      </span>
                                      <div className="flex-1">
                                        <div className="h-2 rounded-full bg-gray-200">
                                          <div
                                            className="h-2 rounded-full bg-primary-500"
                                            style={{
                                              width: `${Math.min(100, clo.attainmentPercentage)}%`,
                                            }}
                                          />
                                        </div>
                                      </div>
                                      <span className="w-10 text-right text-xs text-gray-600">
                                        {clo.attainmentPercentage.toFixed(1)}%
                                      </span>
                                    </div>
                                  ))}
                                </div>
                              </td>
                            </tr>
                          )}
                        </>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>

              {/* PLO Attainments */}
              {t.snapshotData.ploAttainments.length > 0 && (
                <div>
                  <h3 className="mb-3 text-sm font-semibold uppercase tracking-wide text-gray-500">
                    PLO Attainments
                  </h3>
                  <div className="grid gap-3 sm:grid-cols-2">
                    {t.snapshotData.ploAttainments.map((plo) => (
                      <div key={plo.ploId} className="rounded-lg border p-3">
                        <div className="mb-1.5 flex items-center justify-between">
                          <div>
                            <span className="text-xs font-semibold text-gray-700">
                              {plo.ploCode}
                            </span>
                            <span className="ml-2 text-xs text-gray-500">{plo.description}</span>
                          </div>
                          <span className="text-xs font-medium text-gray-700">
                            {plo.attainmentPercentage.toFixed(1)}%
                          </span>
                        </div>
                        <div className="h-2 rounded-full bg-gray-200">
                          <div
                            className={`h-2 rounded-full ${plo.attainmentPercentage >= 70 ? 'bg-green-500' : plo.attainmentPercentage >= 50 ? 'bg-yellow-400' : 'bg-red-400'}`}
                            style={{
                              width: `${Math.min(100, plo.attainmentPercentage)}%`,
                            }}
                          />
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </>
          )}
        </div>

      <div className="mt-6 flex flex-wrap justify-end gap-2 border-t border-gray-100 pt-4">
        <button
          onClick={onClose}
          className="rounded-lg border px-4 py-2.5 text-sm text-gray-700 hover:bg-gray-50"
        >
          Close
        </button>
        <button
          onClick={handleDownload}
          disabled={!t || downloading}
          className="inline-flex items-center gap-2 rounded-lg bg-primary-600 px-4 py-2.5 text-sm font-medium text-white hover:bg-primary-700 disabled:opacity-50"
        >
          <Download className="h-4 w-4" />
          {downloading ? 'Downloading…' : 'Download PDF'}
        </button>
      </div>
    </Modal>
  );
}

// ---------------------------------------------------------------------------
// Transcript Summary Card
// ---------------------------------------------------------------------------
function TranscriptCard({
  t,
  onView,
}: {
  t: TranscriptSummaryResponse;
  onView: (id: string) => void;
}) {
  const [downloading, setDownloading] = useState(false);

  async function handleDownload() {
    setDownloading(true);
    try {
      await downloadPdf(
        t.id,
        `transcript-${t.studentName.replace(/\s+/g, '-')}-${t.semesterName.replace(/\s+/g, '-')}.pdf`,
      );
    } finally {
      setDownloading(false);
    }
  }

  return (
    <div className="rounded-xl border bg-white p-5 shadow-sm hover:shadow-md transition-all duration-200 hover:-translate-y-0.5">
      <div className="flex items-start justify-between gap-4">
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 mb-1">
            <BookOpen className="h-4 w-4 text-primary-500 shrink-0" />
            <h3 className="font-semibold text-gray-900 truncate">{t.semesterName}</h3>
          </div>
          <p className="text-sm text-gray-500 mb-3">{t.programName}</p>
          <div className="flex flex-wrap gap-4 text-sm">
            <div>
              <span className="text-gray-400">SGPA </span>
              <span className={`font-semibold ${t.sgpa >= 2.5 ? 'text-green-600' : 'text-red-600'}`}>
                {t.sgpa.toFixed(2)}
              </span>
            </div>
            <div>
              <span className="text-gray-400">CGPA </span>
              <span className={`font-semibold ${t.cgpa >= 2.5 ? 'text-green-600' : 'text-red-600'}`}>
                {t.cgpa.toFixed(2)}
              </span>
            </div>
            <div>
              <span className="text-gray-400">Credits </span>
              <span className="font-semibold text-gray-700">{t.totalCreditHours}</span>
            </div>
          </div>
        </div>
        <div className="text-right shrink-0">
          <p className="text-xs text-gray-400 mb-3">
            {new Date(t.generatedAt).toLocaleDateString()}
          </p>
          <div className="flex flex-col gap-2">
            <button
              onClick={() => onView(t.id)}
              className="inline-flex items-center gap-1.5 rounded-lg border border-primary-300 px-3 py-1.5 text-xs font-medium text-primary-700 hover:bg-primary-50"
            >
              <Eye className="h-3.5 w-3.5" />
              View
            </button>
            <button
              onClick={handleDownload}
              disabled={downloading}
              className="inline-flex items-center gap-1.5 rounded-lg bg-primary-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-primary-700 disabled:opacity-50"
            >
              <Download className="h-3.5 w-3.5" />
              {downloading ? '…' : 'PDF'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Student view
// ---------------------------------------------------------------------------
function StudentView({ onView }: { onView: (id: string) => void }) {
  const transcriptsQ = useQuery({
    queryKey: ['me', 'transcripts'],
    queryFn: () =>
      api.get<TranscriptSummaryResponse[]>('/me/transcripts').then((r) => r.data),
  });

  const transcripts = transcriptsQ.data ?? [];

  return (
    <div className="space-y-4">
      {transcriptsQ.isLoading && (
        <p className="text-sm text-gray-500">Loading your transcripts…</p>
      )}
      {!transcriptsQ.isLoading && transcripts.length === 0 && (
        <div className="rounded-xl border border-dashed p-10 text-center">
          <GraduationCap className="mx-auto mb-2 h-10 w-10 text-gray-300" />
          <p className="text-sm text-gray-500">
            No transcripts yet. Transcripts are generated when a semester closes.
          </p>
        </div>
      )}
      {transcripts.map((t, idx) => (
        <div key={t.id} className="animate-slide-up" style={{ animationDelay: `${idx * 60}ms` }}>
          <TranscriptCard t={t} onView={onView} />
        </div>
      ))}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Admin/Teacher — By Student tab
// ---------------------------------------------------------------------------
function ByStudentView({ onView }: { onView: (id: string) => void }) {
  const [query, setQuery] = useState('');
  const [selected, setSelected] = useState<UserSummaryResponse | null>(null);

  // Debounced so typing a name does not fire a request per keystroke.
  const [debounced, setDebounced] = useState('');
  useEffect(() => {
    const id = setTimeout(() => setDebounced(query.trim()), 300);
    return () => clearTimeout(id);
  }, [query]);

  const studentsQ = useQuery({
    queryKey: ['admin', 'users', 'student-search', debounced],
    queryFn: () =>
      api
        .get<Page<UserSummaryResponse>>('/admin/users', {
          params: { role: 'STUDENT', q: debounced, size: 10 },
        })
        .then((r) => r.data.content),
    enabled: debounced.length >= 2 && !selected,
  });
  const matches = studentsQ.data ?? [];

  const transcriptsQ = useQuery({
    queryKey: ['admin', 'students', selected?.id, 'transcripts'],
    queryFn: () =>
      api
        .get<TranscriptSummaryResponse[]>(`/admin/students/${selected!.id}/transcripts`)
        .then((r) => r.data),
    enabled: !!selected,
  });
  const transcripts = transcriptsQ.data ?? [];

  return (
    <div className="space-y-4">
      {/* Search by the identifiers staff actually have: name, email, roll number. */}
      <div className="relative">
        <div className="flex items-center gap-2 rounded-lg border bg-white px-3 focus-within:ring-2 focus-within:ring-primary-500">
          <Search className="h-4 w-4 flex-shrink-0 text-gray-400" />
          <input
            type="text"
            value={selected ? `${selected.name} (${selected.email})` : query}
            onChange={(e) => {
              setSelected(null);
              setQuery(e.target.value);
            }}
            placeholder="Search students by name, email or roll number…"
            className="w-full py-2 text-sm focus:outline-none"
          />
          {(selected || query) && (
            <button
              type="button"
              onClick={() => {
                setSelected(null);
                setQuery('');
              }}
              className="flex-shrink-0 text-gray-400 hover:text-gray-600"
              aria-label="Clear search"
            >
              <X className="h-4 w-4" />
            </button>
          )}
          {studentsQ.isFetching && <Spinner size="sm" />}
        </div>

        {!selected && debounced.length >= 2 && (
          <div className="absolute z-10 mt-1 max-h-72 w-full overflow-y-auto rounded-lg border border-gray-200 bg-white shadow-lg">
            {studentsQ.isError ? (
              <p className="px-3 py-2 text-sm text-red-600">
                {parseApiError(studentsQ.error)}
              </p>
            ) : matches.length === 0 && !studentsQ.isFetching ? (
              <p className="px-3 py-2 text-sm text-gray-500">No matching students.</p>
            ) : (
              matches.map((u) => (
                <button
                  key={u.id}
                  type="button"
                  onClick={() => setSelected(u)}
                  className="flex w-full items-center justify-between gap-3 px-3 py-2 text-left hover:bg-gray-50"
                >
                  <span className="min-w-0">
                    <span className="block truncate text-sm text-gray-900">{u.name}</span>
                    <span className="block truncate text-xs text-gray-500">{u.email}</span>
                  </span>
                  {u.studentNumber && (
                    <span className="flex-shrink-0 font-mono text-xs text-gray-400">
                      {u.studentNumber}
                    </span>
                  )}
                </button>
              ))
            )}
          </div>
        )}
      </div>

      {selected && (
        <div className="flex flex-wrap items-center gap-2 rounded-lg bg-gray-50 px-3 py-2 text-sm">
          <span className="font-medium text-gray-800">{selected.name}</span>
          {selected.studentNumber && (
            <span className="font-mono text-xs text-gray-500">{selected.studentNumber}</span>
          )}
          <CopyableId id={selected.id} />
        </div>
      )}

      {selected && transcriptsQ.isLoading && (
        <p className="text-sm text-gray-500">Loading transcripts…</p>
      )}
      {selected && transcriptsQ.isError && (
        <p className="text-sm text-red-600">{parseApiError(transcriptsQ.error)}</p>
      )}
      {selected && !transcriptsQ.isLoading && !transcriptsQ.isError && transcripts.length === 0 && (
        <p className="text-sm text-gray-500">No transcripts found for this student.</p>
      )}
      {transcripts.map((t, idx) => (
        <div key={t.id} className="animate-slide-up" style={{ animationDelay: `${idx * 60}ms` }}>
          <TranscriptCard t={t} onView={onView} />
        </div>
      ))}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Admin — By Semester tab
// ---------------------------------------------------------------------------
function BySemesterView({ onView }: { onView: (id: string) => void }) {
  const [selectedProgramId, setSelectedProgramId] = useState('');
  const [selectedSemesterId, setSelectedSemesterId] = useState('');
  const [loadedSemesterId, setLoadedSemesterId] = useState('');
  const [loadedProgramId, setLoadedProgramId] = useState('');

  const programsQ = useActivePrograms();
  const programs = programsQ.data ?? [];

  const semesterQueries = useQueries({
    queries: programs.map((p) => ({
      queryKey: ['programs', p.id, 'semesters'],
      queryFn: () =>
        api
          .get<SemesterResponse[]>(`/programs/${p.id}/semesters`)
          .then((r) => r.data),
      enabled: programs.length > 0,
    })),
  });

  const semesters: SemesterResponse[] =
    programs.length > 0 && selectedProgramId
      ? (semesterQueries[programs.findIndex((p) => p.id === selectedProgramId)]?.data ?? [])
      : [];

  const transcriptsQ = useQuery({
    queryKey: ['admin', 'transcripts', 'semester', loadedSemesterId],
    queryFn: () =>
      api
        .get<TranscriptSummaryResponse[]>(
          `/admin/transcripts/semester/${loadedSemesterId}`,
        )
        .then((r) => r.data),
    enabled: loadedSemesterId.length === 36,
  });
  const transcripts = transcriptsQ.data ?? [];

  const regenMutation = useMutation({
    mutationFn: () =>
      api.post(
        `/admin/transcripts/semester/${loadedSemesterId}/generate?programId=${loadedProgramId}`,
      ),
    // Regeneration is a background job with no visible result on this page,
    // so the confirmation is the only signal the admin gets.
    onSuccess: () =>
      toast.success('Transcript re-generation started. Refresh in a moment to see updates.'),
  });

  return (
    <div className="space-y-4">
      {/* Selectors */}
      <div className="grid gap-3 sm:grid-cols-3">
        <select
          value={selectedProgramId}
          onChange={(e) => {
            setSelectedProgramId(e.target.value);
            setSelectedSemesterId('');
          }}
          className="rounded-lg border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
        >
          <option value="">Select program…</option>
          {programs.map((p) => (
            <option key={p.id} value={p.id}>
              {p.name} ({p.code})
            </option>
          ))}
        </select>

        <select
          value={selectedSemesterId}
          onChange={(e) => setSelectedSemesterId(e.target.value)}
          disabled={!selectedProgramId}
          className="rounded-lg border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 disabled:opacity-50"
        >
          <option value="">Select semester…</option>
          {semesters.map((s) => (
            <option key={s.id} value={s.id}>
              {s.name}
            </option>
          ))}
        </select>

        <button
          onClick={() => {
            setLoadedSemesterId(selectedSemesterId);
            setLoadedProgramId(selectedProgramId);
          }}
          disabled={!selectedSemesterId}
          className="inline-flex items-center justify-center gap-2 rounded-lg bg-primary-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary-700 disabled:opacity-40"
        >
          <Search className="h-4 w-4" />
          Load Transcripts
        </button>
      </div>

      {/* Results */}
      {loadedSemesterId && transcriptsQ.isLoading && (
        <p className="text-sm text-gray-500">Loading transcripts…</p>
      )}
      {loadedSemesterId && !transcriptsQ.isLoading && (
        <>
          <div className="flex items-center justify-between">
            <p className="text-sm text-gray-600">
              {transcripts.length} transcript{transcripts.length !== 1 ? 's' : ''} found
            </p>
            <button
              onClick={() => regenMutation.mutate()}
              disabled={regenMutation.isPending}
              className="inline-flex items-center gap-2 rounded-lg border border-amber-400 bg-amber-50 px-3 py-1.5 text-xs font-medium text-amber-700 hover:bg-amber-100 disabled:opacity-50"
            >
              <RefreshCw className={`h-3.5 w-3.5 ${regenMutation.isPending ? 'animate-spin' : ''}`} />
              Re-generate All
            </button>
          </div>
          {transcripts.length === 0 && (
            <p className="text-sm text-gray-500">No transcripts for this semester yet.</p>
          )}
          {transcripts.map((t, idx) => (
            <div key={t.id} className="animate-slide-up" style={{ animationDelay: `${idx * 60}ms` }}>
              <TranscriptCard t={t} onView={onView} />
            </div>
          ))}
        </>
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Main Page
// ---------------------------------------------------------------------------
export default function TranscriptsPage() {
  const user = useAuthStore((s) => s.user);
  const isStudent = user?.role === 'STUDENT';
  const isAdmin = user?.role === 'ADMIN';

  const [viewingId, setViewingId] = useState<string | null>(null);
  const [adminTab, setAdminTab] = useState<'student' | 'semester'>('student');

  return (
    <div className="space-y-6">
      {/* Page header */}
      <div className="flex items-center gap-3">
        <GraduationCap className="h-7 w-7 text-primary-600" />
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Transcripts</h1>
          <p className="text-sm text-gray-500">
            {isStudent
              ? 'Your academic transcripts, generated each semester.'
              : 'View and download student transcripts.'}
          </p>
        </div>
      </div>

      {/* Student view */}
      {isStudent && <StudentView onView={setViewingId} />}

      {/* Admin / Teacher view */}
      {!isStudent && (
        <>
          {/* Tabs — admin gets both; teacher only "By Student" */}
          {isAdmin && (
            <div className="flex gap-1 rounded-xl bg-gray-100 p-1 w-fit">
              {(['student', 'semester'] as const).map((tab) => (
                <button
                  key={tab}
                  onClick={() => setAdminTab(tab)}
                  className={`rounded-lg px-4 py-1.5 text-sm font-medium transition-colors ${adminTab === tab ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-500 hover:text-gray-700'}`}
                >
                  {tab === 'student' ? 'By Student' : 'By Semester'}
                </button>
              ))}
            </div>
          )}

          {(adminTab === 'student' || !isAdmin) && (
            <ByStudentView onView={setViewingId} />
          )}
          {isAdmin && adminTab === 'semester' && (
            <BySemesterView onView={setViewingId} />
          )}
        </>
      )}

      {/* Detail modal */}
      {viewingId && (
        <TranscriptDetailModal id={viewingId} onClose={() => setViewingId(null)} />
      )}
    </div>
  );
}
