import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import {
  Plus, Edit2, Users, BookOpen, ChevronDown, ChevronUp, UserPlus, UserMinus,
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
  SemesterResponse,
  OfferingSummaryResponse,
  UserSummaryResponse,
  CourseSummaryResponse,
  EnrollmentResponse,
  Page,
  UUID,
} from '@/types/api';

// ─── Schemas ─────────────────────────────────────────────────────────────────

const createSchema = z.object({
  semesterId:  z.string().uuid('Required'),
  courseId:    z.string().uuid('Required'),
  teacherId:   z.string().uuid('Required'),
  maxCapacity: z.coerce.number().min(0, 'Must be 0 or more'),
});

const updateSchema = z.object({
  teacherId:   z.string().uuid('Required'),
  maxCapacity: z.coerce.number().min(0, 'Must be 0 or more'),
});

type CreateForm = z.infer<typeof createSchema>;
type UpdateForm = z.infer<typeof updateSchema>;

// ─── Enrollment sub-panel ─────────────────────────────────────────────────────

function EnrollmentPanel({
  offeringId,
  students,
}: {
  offeringId: UUID;
  students: UserSummaryResponse[];
}) {
  const qc = useQueryClient();
  const [selectedStudentId, setSelectedStudentId] = useState('');

  const { data: enrollments = [], isLoading } = useQuery<EnrollmentResponse[]>({
    queryKey: ['enrollments', offeringId],
    queryFn: () => api.get(`/offerings/${offeringId}/enrollments`).then((r) => r.data),
  });

  const invalidate = () => qc.invalidateQueries({ queryKey: ['enrollments', offeringId] });

  const enrollMut = useMutation({
    mutationFn: () => api.post('/admin/enrollments', { pscId: offeringId, studentId: selectedStudentId }),
    onSuccess: () => { invalidate(); setSelectedStudentId(''); },
  });

  const dropMut = useMutation({
    mutationFn: (id: UUID) => api.delete(`/admin/enrollments/${id}`),
    onSuccess: () => invalidate(),
  });

  // Students not yet enrolled (active enrollments)
  const enrolledStudentIds = new Set(
    enrollments.filter((e) => e.status === 'ENROLLED').map((e) => e.studentId),
  );
  const unenrolledStudents = students.filter((s) => !enrolledStudentIds.has(s.id));

  return (
    <div className="space-y-3">
      <h5 className="font-medium text-gray-700">Enrolled Students ({enrollments.filter((e) => e.status === 'ENROLLED').length})</h5>

      {/* Enroll student */}
      <div className="flex gap-2">
        <select
          value={selectedStudentId}
          onChange={(e) => setSelectedStudentId(e.target.value)}
          className="flex-1 rounded-lg border border-gray-300 px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
        >
          <option value="">Select student to enroll…</option>
          {unenrolledStudents.map((s) => (
            <option key={s.id} value={s.id}>{s.name} ({s.email})</option>
          ))}
        </select>
        <Button
          size="sm"
          disabled={!selectedStudentId}
          loading={enrollMut.isPending}
          onClick={() => enrollMut.mutate()}
        >
          <UserPlus size={14} className="mr-1" /> Enroll
        </Button>
      </div>

      {isLoading ? (
        <div className="flex justify-center py-3"><Spinner /></div>
      ) : enrollments.length === 0 ? (
        <p className="text-xs text-gray-400 text-center py-2">No students enrolled.</p>
      ) : (
        <div className="space-y-1 max-h-40 overflow-y-auto">
          {enrollments.map((e) => {
            const student = students.find((s) => s.id === e.studentId);
            return (
              <div key={e.id} className="flex items-center justify-between rounded bg-gray-50 px-3 py-1.5 text-sm">
                <span className="text-gray-800">{student?.name ?? e.studentId}</span>
                <div className="flex items-center gap-2">
                  <Badge variant={e.status === 'ENROLLED' ? 'success' : e.status === 'DROPPED' ? 'danger' : 'info'}>
                    {e.status}
                  </Badge>
                  {e.status === 'ENROLLED' && (
                    <button
                      onClick={() => dropMut.mutate(e.id)}
                      className="rounded p-0.5 text-red-400 hover:bg-red-50 hover:text-red-600 transition"
                      title="Drop enrollment"
                    >
                      <UserMinus size={13} />
                    </button>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

// ─── Offering row ─────────────────────────────────────────────────────────────

function OfferingRow({
  offering,
  teachers,
  students,
  onEdit,
}: {
  offering: OfferingSummaryResponse;
  teachers: UserSummaryResponse[];
  students: UserSummaryResponse[];
  onEdit: (o: OfferingSummaryResponse) => void;
}) {
  const [expanded, setExpanded] = useState(false);
  const teacher = teachers.find((t) => t.id === offering.teacherId);

  return (
    <div className="rounded-xl border border-gray-200 bg-white shadow-sm overflow-hidden">
      <div className="flex items-center justify-between px-5 py-4">
        <div className="flex items-center gap-4">
          <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-blue-50 text-blue-700 shrink-0">
            <BookOpen size={16} />
          </div>
          <div>
            <div className="flex items-center gap-2 flex-wrap">
              <span className="font-semibold text-gray-900">{offering.courseName}</span>
              <Badge variant="info">{offering.courseCode}</Badge>
              <span className="text-xs text-gray-500">{offering.creditHours} credits</span>
            </div>
            <p className="text-xs text-gray-500 mt-0.5">
              Teacher: {teacher?.name ?? 'Unknown'} · Capacity: {offering.maxCapacity}
            </p>
          </div>
        </div>
        <div className="flex items-center gap-2 shrink-0">
          <button
            onClick={() => onEdit(offering)}
            className="rounded p-1.5 text-gray-400 hover:bg-gray-100 hover:text-gray-700 transition"
            title="Edit offering"
          >
            <Edit2 size={14} />
          </button>
          <button
            onClick={() => setExpanded((e) => !e)}
            className="rounded p-1.5 text-gray-400 hover:bg-gray-100 transition flex items-center gap-1 text-xs"
          >
            <Users size={14} />
            {expanded ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
          </button>
        </div>
      </div>

      {expanded && (
        <div className="border-t border-gray-100 px-5 py-4">
          <EnrollmentPanel offeringId={offering.id} students={students} />
        </div>
      )}
    </div>
  );
}

// ─── Main page ────────────────────────────────────────────────────────────────

export default function OfferingsPage() {
  const qc = useQueryClient();

  const [selectedProgramId,  setSelectedProgramId]  = useState('');
  const [selectedSemesterId, setSelectedSemesterId] = useState('');
  const [showCreate, setShowCreate] = useState(false);
  const [editing,    setEditing]    = useState<OfferingSummaryResponse | null>(null);

  // ── data ──────────────────────────────────────────────────────────────────
  const { data: programsData } = useQuery<Page<ProgramSummaryResponse>>({
    queryKey: ['programs'],
    queryFn: () => api.get('/programs', { params: { size: 100 } }).then((r) => r.data),
  });
  const programs = programsData?.content ?? [];

  const { data: semesters = [] } = useQuery<SemesterResponse[]>({
    queryKey: ['semesters', selectedProgramId],
    queryFn: () => api.get(`/programs/${selectedProgramId}/semesters`).then((r) => r.data),
    enabled: !!selectedProgramId,
  });

  const { data: offerings = [], isLoading: loadingOfferings } = useQuery<OfferingSummaryResponse[]>({
    queryKey: ['offerings', selectedSemesterId],
    queryFn: () => api.get(`/semesters/${selectedSemesterId}/offerings`).then((r) => r.data),
    enabled: !!selectedSemesterId,
  });

  const { data: teacherPage } = useQuery<Page<UserSummaryResponse>>({
    queryKey: ['users-teachers'],
    queryFn: () => api.get('/admin/users', { params: { role: 'TEACHER', size: 200 } }).then((r) => r.data),
  });
  const teachers = teacherPage?.content ?? [];

  const { data: studentPage } = useQuery<Page<UserSummaryResponse>>({
    queryKey: ['users-students'],
    queryFn: () => api.get('/admin/users', { params: { role: 'STUDENT', size: 500 } }).then((r) => r.data),
  });
  const students = studentPage?.content ?? [];

  const { data: coursesPage } = useQuery<Page<CourseSummaryResponse>>({
    queryKey: ['courses-all'],
    queryFn: () => api.get('/courses', { params: { size: 200 } }).then((r) => r.data),
  });
  const courses = coursesPage?.content ?? [];

  // ── mutations ─────────────────────────────────────────────────────────────
  const invalidate = () => qc.invalidateQueries({ queryKey: ['offerings', selectedSemesterId] });

  const createMut = useMutation({
    mutationFn: (body: CreateForm) => api.post('/admin/offerings', body),
    onSuccess: () => { invalidate(); setShowCreate(false); },
  });

  const updateMut = useMutation({
    mutationFn: ({ id, body }: { id: UUID; body: UpdateForm }) =>
      api.put(`/admin/offerings/${id}`, body),
    onSuccess: () => { invalidate(); setEditing(null); },
  });

  // ── forms ─────────────────────────────────────────────────────────────────
  const createForm = useForm<CreateForm>({
    resolver: zodResolver(createSchema),
    defaultValues: { semesterId: selectedSemesterId, maxCapacity: 30 },
  });

  const editForm = useForm<UpdateForm>({ resolver: zodResolver(updateSchema) });

  function openCreate() {
    createForm.reset({ semesterId: selectedSemesterId, maxCapacity: 30 });
    setShowCreate(true);
  }

  function openEdit(o: OfferingSummaryResponse) {
    editForm.reset({ teacherId: o.teacherId, maxCapacity: o.maxCapacity });
    setEditing(o);
  }

  return (
    <div className="space-y-5 animate-slide-up">
      {/* Header */}
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900">Offerings</h1>
        <Button onClick={openCreate} disabled={!selectedSemesterId}>
          <Plus size={16} className="mr-1" /> Add Offering
        </Button>
      </div>

      {/* Filters */}
      <div className="flex flex-wrap gap-3">
        <select
          value={selectedProgramId}
          onChange={(e) => { setSelectedProgramId(e.target.value); setSelectedSemesterId(''); }}
          className="rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
        >
          <option value="">Select Program…</option>
          {programs.map((p) => (
            <option key={p.id} value={p.id}>{p.name} ({p.code})</option>
          ))}
        </select>

        {selectedProgramId && (
          <select
            value={selectedSemesterId}
            onChange={(e) => setSelectedSemesterId(e.target.value)}
            className="rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
          >
            <option value="">Select Semester…</option>
            {semesters.map((s) => (
              <option key={s.id} value={s.id}>{s.name}</option>
            ))}
          </select>
        )}
      </div>

      {/* Offerings list */}
      {!selectedSemesterId ? (
        <Card>
          <div className="flex flex-col items-center py-12 text-gray-400">
            <BookOpen size={40} className="mb-3 opacity-40" />
            <p className="font-medium">Select a program and semester to view offerings</p>
          </div>
        </Card>
      ) : loadingOfferings ? (
        <div className="flex justify-center py-16"><Spinner size="lg" /></div>
      ) : offerings.length === 0 ? (
        <Card>
          <div className="flex flex-col items-center py-10 text-gray-400">
            <p className="font-medium">No offerings for this semester</p>
            <p className="text-sm">Add an offering to get started.</p>
          </div>
        </Card>
      ) : (
        <div className="space-y-3">
          {offerings.map((o) => (
            <OfferingRow
              key={o.id}
              offering={o}
              teachers={teachers}
              students={students}
              onEdit={openEdit}
            />
          ))}
        </div>
      )}

      {/* Create Modal */}
      <Modal open={showCreate} onClose={() => setShowCreate(false)} title="Add Offering">
        <form onSubmit={createForm.handleSubmit((d) => createMut.mutate(d))} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Semester</label>
            <select
              {...createForm.register('semesterId')}
              className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
            >
              <option value="">Select semester…</option>
              {semesters.map((s) => (
                <option key={s.id} value={s.id}>{s.name}</option>
              ))}
            </select>
            {createForm.formState.errors.semesterId && (
              <p className="text-xs text-red-500 mt-1">{createForm.formState.errors.semesterId.message}</p>
            )}
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Course</label>
            <select
              {...createForm.register('courseId')}
              className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
            >
              <option value="">Select course…</option>
              {courses.map((c) => (
                <option key={c.id} value={c.id}>{c.name} ({c.code})</option>
              ))}
            </select>
            {createForm.formState.errors.courseId && (
              <p className="text-xs text-red-500 mt-1">{createForm.formState.errors.courseId.message}</p>
            )}
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Teacher</label>
            <select
              {...createForm.register('teacherId')}
              className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
            >
              <option value="">Select teacher…</option>
              {teachers.map((t) => (
                <option key={t.id} value={t.id}>{t.name} ({t.email})</option>
              ))}
            </select>
            {createForm.formState.errors.teacherId && (
              <p className="text-xs text-red-500 mt-1">{createForm.formState.errors.teacherId.message}</p>
            )}
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Max Capacity</label>
            <Input type="number" min={0} {...createForm.register('maxCapacity')} />
            {createForm.formState.errors.maxCapacity && (
              <p className="text-xs text-red-500 mt-1">{createForm.formState.errors.maxCapacity.message}</p>
            )}
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="secondary" onClick={() => setShowCreate(false)}>Cancel</Button>
            <Button type="submit" loading={createMut.isPending}>Create Offering</Button>
          </div>
        </form>
      </Modal>

      {/* Edit Modal */}
      <Modal open={!!editing} onClose={() => setEditing(null)} title="Edit Offering">
        <form onSubmit={editForm.handleSubmit((d) => editing && updateMut.mutate({ id: editing.id, body: d }))} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Teacher</label>
            <select
              {...editForm.register('teacherId')}
              className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
            >
              {teachers.map((t) => (
                <option key={t.id} value={t.id}>{t.name} ({t.email})</option>
              ))}
            </select>
            {editForm.formState.errors.teacherId && (
              <p className="text-xs text-red-500 mt-1">{editForm.formState.errors.teacherId.message}</p>
            )}
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Max Capacity</label>
            <Input type="number" min={0} {...editForm.register('maxCapacity')} />
            {editForm.formState.errors.maxCapacity && (
              <p className="text-xs text-red-500 mt-1">{editForm.formState.errors.maxCapacity.message}</p>
            )}
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="secondary" onClick={() => setEditing(null)}>Cancel</Button>
            <Button type="submit" loading={updateMut.isPending}>Save Changes</Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
