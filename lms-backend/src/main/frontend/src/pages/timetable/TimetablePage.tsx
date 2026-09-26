import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { AlertTriangle, CalendarDays, DoorOpen, FlaskConical, RefreshCw, Trash2, Wand2 } from 'lucide-react';
import api from '@/lib/api';
import { useMyTimetable } from '@/lib/queries';
import { useAuthStore } from '@/store/authStore';
import { toast } from '@/components/ui/Toast';
import Badge from '@/components/ui/Badge';
import Button from '@/components/ui/Button';
import Card from '@/components/ui/Card';
import Modal from '@/components/ui/Modal';
import QueryError from '@/components/ui/QueryError';
import Spinner from '@/components/ui/Spinner';
import WeekGrid, { hhmm } from '@/components/timetable/WeekGrid';
import RoomsPanel, { useRooms } from '@/components/timetable/RoomsPanel';
import type {
  CohortSummaryResponse,
  SessionKind,
  TimetableEntryResponse,
  TimetableGridResponse,
  TimetableResponse,
  UnscheduledOfferingResponse,
  UUID,
} from '@/types/api';

const SELECT =
  'h-9 rounded-lg border border-gray-300 bg-white px-3 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500';

function kindLabel(kind: SessionKind) {
  return kind === 'LAB' ? 'Lab' : 'Lecture';
}

function missingLabel(missing: SessionKind[]) {
  const lectures = missing.filter((k) => k === 'LECTURE').length;
  const labs = missing.length - lectures;
  const parts = [];
  if (lectures) parts.push(`${lectures} lecture${lectures === 1 ? '' : 's'}`);
  if (labs) parts.push(`${labs} lab${labs === 1 ? '' : 's'}`);
  return parts.join(' and ');
}

function Stat({ label, value, tone }: { label: string; value: string | number; tone?: 'warn' }) {
  return (
    <Card className="!p-4">
      <p className="text-xs font-medium uppercase tracking-wide text-gray-500">{label}</p>
      <p className={`mt-1 text-2xl font-bold ${tone === 'warn' ? 'text-amber-600' : 'text-gray-900'}`}>{value}</p>
    </Card>
  );
}

// ─── Place / move one meeting ─────────────────────────────────────────────────

type Placing =
  | { mode: 'move'; entry: TimetableEntryResponse }
  | { mode: 'create'; offering: UnscheduledOfferingResponse; kind: SessionKind };

function PlaceModal({
  placing,
  grid,
  onClose,
}: {
  placing: Placing | null;
  grid: TimetableGridResponse;
  onClose: () => void;
}) {
  const qc = useQueryClient();
  const { data: rooms = [] } = useRooms();
  const kind = placing?.mode === 'move' ? placing.entry.kind : placing?.kind ?? 'LECTURE';
  const students = placing?.mode === 'move' ? placing.entry.students : placing?.offering.students ?? 0;
  const length = kind === 'LAB' ? grid.labSlots : 1;

  const [day, setDay] = useState<number>(placing?.mode === 'move' ? placing.entry.dayOfWeek : grid.days[0]?.dayOfWeek);
  const [start, setStart] = useState<string>(placing?.mode === 'move' ? placing.entry.startTime : '');
  const [roomId, setRoomId] = useState<UUID>(placing?.mode === 'move' ? placing.entry.roomId : '');

  // Starts where the whole meeting fits in back-to-back periods.
  const starts = grid.slots.filter((_, i) => {
    if (i + length > grid.slots.length) return false;
    for (let j = i + 1; j < i + length; j++) {
      if (grid.slots[j].start !== grid.slots[j - 1].end) return false;
    }
    return true;
  });
  const suitable = rooms.filter((r) => r.active && r.kind === kind && r.capacity >= students);

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ['admin', 'timetable'] });
    qc.invalidateQueries({ queryKey: ['admin', 'rooms'] });
    qc.invalidateQueries({ queryKey: ['me', 'timetable'] });
  };

  const saveMut = useMutation({
    mutationFn: () =>
      placing?.mode === 'move'
        ? api.put(`/admin/timetable/entries/${placing.entry.id}`, { dayOfWeek: day, startTime: start, roomId })
        : api.post('/admin/timetable/entries', {
            pscId: placing!.offering.pscId, kind, dayOfWeek: day, startTime: start, roomId,
          }),
    onSuccess: () => {
      toast.success(placing?.mode === 'move' ? 'Class moved.' : 'Class placed.');
      invalidate();
      onClose();
    },
  });

  const removeMut = useMutation({
    mutationFn: (id: UUID) => api.delete(`/admin/timetable/entries/${id}`),
    onSuccess: () => {
      toast.success('Class removed from the timetable.');
      invalidate();
      onClose();
    },
  });

  if (!placing) return null;
  const title = placing.mode === 'move'
    ? `${placing.entry.courseCode} ${kindLabel(kind).toLowerCase()}`
    : `Place ${placing.offering.courseCode} ${kindLabel(kind).toLowerCase()}`;
  const subject = placing.mode === 'move' ? placing.entry : placing.offering;

  return (
    <Modal open onClose={onClose} title={title}>
      <div className="space-y-4">
        <div className="rounded-lg bg-gray-50 px-3 py-2 text-sm">
          <p className="font-medium text-gray-900">{subject.courseName}</p>
          <p className="text-xs text-gray-500">
            {subject.programName} · {subject.teacherName} · {students} students
            {placing.mode === 'move' && (
              <> · now {grid.days.find((d) => d.dayOfWeek === placing.entry.dayOfWeek)?.name}{' '}
                {hhmm(placing.entry.startTime)}–{hhmm(placing.entry.endTime)} in {placing.entry.roomName}</>
            )}
          </p>
        </div>

        <div className="grid gap-3 sm:grid-cols-3">
          <div className="flex flex-col gap-1">
            <label htmlFor="place-day" className="text-sm font-medium text-gray-700">Day</label>
            <select id="place-day" className={SELECT} value={day} onChange={(e) => setDay(Number(e.target.value))}>
              {grid.days.map((d) => <option key={d.dayOfWeek} value={d.dayOfWeek}>{d.name}</option>)}
            </select>
          </div>
          <div className="flex flex-col gap-1">
            <label htmlFor="place-start" className="text-sm font-medium text-gray-700">Starts</label>
            <select id="place-start" className={SELECT} value={start} onChange={(e) => setStart(e.target.value)}>
              <option value="">Select…</option>
              {starts.map((s) => <option key={s.index} value={s.start}>{hhmm(s.start)}</option>)}
            </select>
          </div>
          <div className="flex flex-col gap-1">
            <label htmlFor="place-room" className="text-sm font-medium text-gray-700">Room</label>
            <select id="place-room" className={SELECT} value={roomId} onChange={(e) => setRoomId(e.target.value)}>
              <option value="">Select…</option>
              {suitable.map((r) => <option key={r.id} value={r.id}>{r.name} ({r.capacity})</option>)}
            </select>
          </div>
        </div>
        {suitable.length === 0 && (
          <p className="text-xs text-amber-700">
            No active {kind === 'LAB' ? 'lab' : 'lecture room'} seats {students}. Add one under Rooms.
          </p>
        )}
        <p className="text-xs text-gray-500">
          The move is refused if the teacher, any enrolled student or the room is busy then.
        </p>

        <div className="flex items-center justify-between gap-2 pt-2">
          {placing.mode === 'move' ? (
            <Button
              variant="ghost"
              loading={removeMut.isPending}
              onClick={() => {
                if (confirm('Remove this class from the timetable?')) removeMut.mutate(placing.entry.id);
              }}
            >
              <Trash2 size={14} className="mr-1 text-red-500" /> Remove
            </Button>
          ) : <span />}
          <div className="flex gap-2">
            <Button variant="secondary" onClick={onClose}>Cancel</Button>
            <Button disabled={!start || !roomId} loading={saveMut.isPending} onClick={() => saveMut.mutate()}>
              {placing.mode === 'move' ? 'Move class' : 'Place class'}
            </Button>
          </div>
        </div>
      </div>
    </Modal>
  );
}

// ─── Admin ────────────────────────────────────────────────────────────────────

type ViewBy = 'cohort' | 'program' | 'teacher' | 'room';

function AdminTimetable() {
  const qc = useQueryClient();
  const [tab, setTab] = useState<'timetable' | 'rooms'>('timetable');
  const [viewBy, setViewBy] = useState<ViewBy>('cohort');
  const [selected, setSelected] = useState<string>('');
  const [confirmGenerate, setConfirmGenerate] = useState(false);
  const [placing, setPlacing] = useState<Placing | null>(null);

  const timetableQ = useQuery<TimetableResponse>({
    queryKey: ['admin', 'timetable'],
    meta: { errorShownInline: true },
    queryFn: () => api.get('/admin/timetable').then((r) => r.data),
  });
  const cohortsQ = useQuery<CohortSummaryResponse[]>({
    queryKey: ['admin-cohorts', 'list'],
    queryFn: () => api.get('/admin/cohorts').then((r) => r.data),
  });
  const { data: rooms = [] } = useRooms();

  const generateMut = useMutation({
    mutationFn: () => api.post<TimetableResponse>('/admin/timetable/generate').then((r) => r.data),
    onSuccess: (res) => {
      qc.setQueryData(['admin', 'timetable'], res);
      qc.invalidateQueries({ queryKey: ['admin', 'rooms'] });
      qc.invalidateQueries({ queryKey: ['me', 'timetable'] });
      setConfirmGenerate(false);
      if (res.unscheduled.length === 0) {
        toast.success(`Timetable generated: ${res.sessionsPlaced} weekly classes, no clashes.`);
      } else {
        toast.error(`Timetable generated, but ${res.unscheduled.length} offering(s) could not be fully placed.`);
      }
    },
  });

  const clearMut = useMutation({
    mutationFn: () => api.delete('/admin/timetable'),
    onSuccess: () => {
      toast.success('Timetable cleared.');
      qc.invalidateQueries({ queryKey: ['admin', 'timetable'] });
      qc.invalidateQueries({ queryKey: ['admin', 'rooms'] });
      qc.invalidateQueries({ queryKey: ['me', 'timetable'] });
    },
  });

  const data = timetableQ.data;
  const entries = data?.entries ?? [];

  // Options for the "view by" picker, from what the timetable contains.
  const options = useMemo(() => {
    const byKey = new Map<string, string>();
    if (viewBy === 'cohort') {
      const present = new Set(entries.flatMap((e) => e.cohortIds));
      (cohortsQ.data ?? []).filter((c) => present.has(c.id)).forEach((c) => byKey.set(c.id, c.name));
    } else if (viewBy === 'program') {
      entries.forEach((e) => byKey.set(e.programId, e.programName));
    } else if (viewBy === 'teacher') {
      entries.forEach((e) => byKey.set(e.teacherId, e.teacherName));
    } else {
      rooms.forEach((r) => byKey.set(r.id, r.name));
    }
    return [...byKey.entries()]
      .map(([value, label]) => ({ value, label }))
      .sort((a, b) => a.label.localeCompare(b.label, undefined, { numeric: true }));
  }, [viewBy, entries, cohortsQ.data, rooms]);

  const current = options.some((o) => o.value === selected) ? selected : options[0]?.value ?? '';
  const shown = entries.filter((e) =>
    viewBy === 'cohort' ? e.cohortIds.includes(current)
      : viewBy === 'program' ? e.programId === current
        : viewBy === 'teacher' ? e.teacherId === current
          : e.roomId === current,
  );

  const detail = (e: TimetableEntryResponse) =>
    viewBy === 'teacher' ? `${e.roomName} · ${e.programName}`
      : viewBy === 'room' ? `${e.teacherName} · ${e.programName}`
        : `${e.roomName} · ${e.teacherName}`;

  const activeRooms = rooms.filter((r) => r.active).length;

  return (
    <div className="space-y-5 animate-slide-up">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Timetable</h1>
          <p className="text-sm text-gray-500">
            The weekly timetable of every open semester, generated together so no teacher, student or room is
            double-booked.
          </p>
        </div>
        <div className="flex gap-2">
          {entries.length > 0 && (
            <Button
              variant="secondary"
              loading={clearMut.isPending}
              onClick={() => {
                if (confirm('Remove every class of the current term from the timetable?')) clearMut.mutate();
              }}
            >
              Clear
            </Button>
          )}
          <Button onClick={() => setConfirmGenerate(true)} disabled={activeRooms === 0}>
            <Wand2 size={16} className="mr-1" /> {entries.length > 0 ? 'Regenerate' : 'Generate'} Timetable
          </Button>
        </div>
      </div>

      <div className="flex gap-1 border-b border-gray-200">
        {([['timetable', 'Timetable', CalendarDays], ['rooms', 'Rooms', DoorOpen]] as const).map(([key, label, Icon]) => (
          <button
            key={key}
            onClick={() => setTab(key)}
            className={`-mb-px flex items-center gap-1.5 border-b-2 px-4 py-2 text-sm font-medium ${
              tab === key ? 'border-primary-600 text-primary-700' : 'border-transparent text-gray-500 hover:text-gray-800'
            }`}
          >
            <Icon size={15} /> {label}
          </button>
        ))}
      </div>

      {tab === 'rooms' ? (
        <RoomsPanel />
      ) : timetableQ.isError ? (
        <QueryError error={timetableQ.error} onRetry={() => timetableQ.refetch()} />
      ) : timetableQ.isLoading || !data ? (
        <div className="flex justify-center py-16"><Spinner size="lg" /></div>
      ) : (
        <>
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            <Stat label="Offerings this term" value={data.offerings} />
            <Stat label="Weekly classes placed" value={`${data.sessionsPlaced} / ${data.sessionsRequired}`} />
            <Stat label="Not fully placed" value={data.unscheduled.length} tone={data.unscheduled.length ? 'warn' : undefined} />
            <Stat label="Active rooms" value={activeRooms} />
          </div>

          {activeRooms === 0 && (
            <Card>
              <p className="flex items-center gap-2 text-sm text-amber-800">
                <AlertTriangle size={16} /> Add rooms on the Rooms tab before generating the timetable.
              </p>
            </Card>
          )}

          {data.unscheduled.length > 0 && entries.length > 0 && (
            <Card className="border-amber-200 bg-amber-50/60">
              <p className="mb-2 flex items-center gap-2 text-sm font-semibold text-amber-900">
                <AlertTriangle size={16} /> {data.unscheduled.length} offering{data.unscheduled.length === 1 ? ' is' : 's are'} short of weekly classes
              </p>
              <ul className="divide-y divide-amber-100">
                {data.unscheduled.map((u) => (
                  <li key={u.pscId} className="flex flex-wrap items-center justify-between gap-2 py-2 text-sm">
                    <div className="min-w-0">
                      <p className="font-medium text-gray-900">
                        {u.courseCode} {u.courseName} <span className="font-normal text-gray-500">· {u.programName}</span>
                      </p>
                      <p className="text-xs text-gray-600">
                        Missing {missingLabel(u.missing)}. {u.reason ?? 'Regenerate or place it by hand.'}
                      </p>
                    </div>
                    <div className="flex gap-1">
                      {[...new Set(u.missing)].map((k) => (
                        <Button key={k} size="sm" variant="secondary" onClick={() => setPlacing({ mode: 'create', offering: u, kind: k })}>
                          Place {kindLabel(k).toLowerCase()}
                        </Button>
                      ))}
                    </div>
                  </li>
                ))}
              </ul>
            </Card>
          )}

          {entries.length === 0 ? (
            <Card>
              <div className="flex flex-col items-center py-12 text-center text-gray-400">
                <CalendarDays size={40} className="mb-3 opacity-40" />
                <p className="font-medium">No timetable yet</p>
                <p className="text-sm">
                  {data.offerings} offering{data.offerings === 1 ? '' : 's'} in open semesters need{' '}
                  {data.sessionsRequired} weekly classes. Generate the timetable to place them.
                </p>
              </div>
            </Card>
          ) : (
            <Card>
              <div className="mb-4 flex flex-wrap items-end gap-3">
                <div className="flex flex-col gap-1">
                  <label htmlFor="tt-view" className="text-xs font-medium text-gray-600">View by</label>
                  <select id="tt-view" className={SELECT} value={viewBy} onChange={(e) => { setViewBy(e.target.value as ViewBy); setSelected(''); }}>
                    <option value="cohort">Class (cohort)</option>
                    <option value="program">Program</option>
                    <option value="teacher">Teacher</option>
                    <option value="room">Room</option>
                  </select>
                </div>
                <div className="flex min-w-[14rem] flex-col gap-1">
                  <label htmlFor="tt-pick" className="text-xs font-medium text-gray-600">
                    {{ cohort: 'Cohort', program: 'Program', teacher: 'Teacher', room: 'Room' }[viewBy]}
                  </label>
                  <select id="tt-pick" className={SELECT} value={current} onChange={(e) => setSelected(e.target.value)}>
                    {options.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
                  </select>
                </div>
                <p className="ml-auto flex items-center gap-3 text-xs text-gray-500">
                  <span>{shown.length} weekly class{shown.length === 1 ? '' : 'es'}</span>
                  <span className="flex items-center gap-1"><FlaskConical size={12} /> lab = {data.grid.labSlots} periods</span>
                  <span>Click a class to move it</span>
                </p>
              </div>
              {shown.length === 0 ? (
                <p className="py-8 text-center text-sm text-gray-400">No classes for this selection.</p>
              ) : (
                <WeekGrid
                  grid={data.grid}
                  entries={shown}
                  detail={detail}
                  highlightToday={false}
                  onEntryClick={(entry) => setPlacing({ mode: 'move', entry })}
                />
              )}
            </Card>
          )}
        </>
      )}

      <Modal open={confirmGenerate} onClose={() => setConfirmGenerate(false)} title="Generate timetable">
        <div className="space-y-4">
          <p className="text-sm text-gray-600">
            Every offering in the open semesters gets its weekly classes: two lectures for a 3-credit course, and a{' '}
            {data?.grid.labSlots ?? 2}-period lab in a lab for each credit beyond three. Classes are placed so that no
            teacher, student or room is booked twice, in the smallest room that seats the class.
          </p>
          {entries.length > 0 && (
            <p className="flex items-start gap-2 rounded-lg bg-amber-50 px-3 py-2 text-sm text-amber-900">
              <AlertTriangle size={16} className="mt-0.5 shrink-0" />
              This replaces the current timetable ({entries.length} classes), including classes you moved by hand.
            </p>
          )}
          <div className="flex justify-end gap-2">
            <Button variant="secondary" onClick={() => setConfirmGenerate(false)}>Cancel</Button>
            <Button loading={generateMut.isPending} onClick={() => generateMut.mutate()}>
              <RefreshCw size={14} className="mr-1" /> Generate
            </Button>
          </div>
        </div>
      </Modal>

      {data && (
        <PlaceModal
          key={placing ? (placing.mode === 'move' ? placing.entry.id : `${placing.offering.pscId}-${placing.kind}`) : 'none'}
          placing={placing}
          grid={data.grid}
          onClose={() => setPlacing(null)}
        />
      )}
    </div>
  );
}

// ─── Teacher / assistant / student ────────────────────────────────────────────

function MyTimetable() {
  const role = useAuthStore((s) => s.user?.role);
  const isStudent = role === 'STUDENT';
  const { data, isLoading, isError, error, refetch } = useMyTimetable();

  const hours = (data?.entries ?? []).reduce((sum, e) => {
    const [sh, sm] = e.startTime.split(':').map(Number);
    const [eh, em] = e.endTime.split(':').map(Number);
    return sum + (eh * 60 + em - sh * 60 - sm) / 60;
  }, 0);
  const courses = new Set((data?.entries ?? []).map((e) => e.pscId)).size;

  return (
    <div className="space-y-5 animate-slide-up">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">My Timetable</h1>
          <p className="text-sm text-gray-500">
            {isStudent ? 'Your weekly classes this term.' : 'The classes you teach this term, week by week.'}
          </p>
        </div>
        {data && data.entries.length > 0 && (
          <div className="flex gap-2">
            <Badge variant="info">{courses} course{courses === 1 ? '' : 's'}</Badge>
            <Badge variant="purple">{data.entries.length} classes / week</Badge>
            <Badge>{hours.toFixed(1)} hours / week</Badge>
          </div>
        )}
      </div>

      {isError ? (
        <QueryError error={error} onRetry={() => refetch()} />
      ) : isLoading || !data ? (
        <div className="flex justify-center py-16"><Spinner size="lg" /></div>
      ) : data.entries.length === 0 ? (
        <Card>
          <div className="flex flex-col items-center py-12 text-center text-gray-400">
            <CalendarDays size={40} className="mb-3 opacity-40" />
            <p className="font-medium">No timetable yet</p>
            <p className="text-sm">
              {data.offerings === 0
                ? isStudent ? 'You are not enrolled in any course this term.' : 'You have no courses this term.'
                : 'Your classes appear here once the timetable is published.'}
            </p>
          </div>
        </Card>
      ) : (
        <Card>
          <WeekGrid
            grid={data.grid}
            entries={data.entries}
            detail={(e) => (isStudent ? `${e.roomName} · ${e.teacherName}` : `${e.roomName} · ${e.programName}`)}
          />
        </Card>
      )}
    </div>
  );
}

export default function TimetablePage() {
  const isAdmin = useAuthStore((s) => s.user?.role === 'ADMIN');
  return isAdmin ? <AdminTimetable /> : <MyTimetable />;
}
