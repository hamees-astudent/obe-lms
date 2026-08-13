import { useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import {
  FileText,
  Link2,
  Play,
  Megaphone,
  ClipboardCheck,
  HelpCircle,
  Plus,
  Trash2,
  Eye,
  EyeOff,
  Pencil,
  Download,
  ExternalLink,
  ChevronLeft,
  BookOpen,
  X,
  type LucideIcon,
} from 'lucide-react';
import api from '@/lib/api';
import { toast } from '@/components/ui/Toast';
import { parseApiError } from '@/lib/apiError';
import {
  uploadFile as uploadToServer,
  validateUpload,
  formatBytes,
  openFile,
  MAX_UPLOAD_BYTES,
} from '@/lib/files';
import { useAuthStore } from '@/store/authStore';
import Card from '@/components/ui/Card';
import Button from '@/components/ui/Button';
import Input from '@/components/ui/Input';
import Modal from '@/components/ui/Modal';
import Spinner from '@/components/ui/Spinner';
import Badge from '@/components/ui/Badge';
import type {
  OfferingSummaryResponse,
  CourseMaterialResponse,
  CreateMaterialBody,
  UpdateMaterialBody,
  UUID,
} from '@/types/api';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------
type MaterialType = 'DOCUMENT' | 'URL' | 'VIDEO_LINK' | 'ANNOUNCEMENT';

const TYPE_META: Record<
  string,
  { icon: LucideIcon; color: string; label: string }
> = {
  DOCUMENT:     { icon: FileText,       color: 'bg-blue-50 text-blue-600',    label: 'Document' },
  URL:          { icon: Link2,          color: 'bg-indigo-50 text-indigo-600', label: 'Link' },
  VIDEO_LINK:   { icon: Play,           color: 'bg-red-50 text-red-600',       label: 'Video' },
  ANNOUNCEMENT: { icon: Megaphone,      color: 'bg-amber-50 text-amber-600',   label: 'Announcement' },
  ASSIGNMENT:   { icon: ClipboardCheck, color: 'bg-green-50 text-green-600',   label: 'Assignment' },
  QUIZ:         { icon: HelpCircle,     color: 'bg-purple-50 text-purple-600', label: 'Quiz' },
};

/** Material types a teacher can create directly (ASSIGNMENT/QUIZ are generated). */
const CREATABLE_TYPES = ['ANNOUNCEMENT', 'DOCUMENT', 'URL', 'VIDEO_LINK'] as const;

function formatDuration(secs: number): string {
  const m = Math.floor(secs / 60);
  const s = secs % 60;
  return `${m}:${String(s).padStart(2, '0')}`;
}

// ---------------------------------------------------------------------------
// Type-specific content renderers
// ---------------------------------------------------------------------------
function DocumentContent({ content }: { content: Record<string, unknown> }) {
  const fileId = content.fileId as UUID | undefined;
  const fileName = content.fileName as string | undefined;
  const fileSize = content.fileSize as number | undefined;
  const [loading, setLoading] = useState(false);

  const [downloadError, setDownloadError] = useState('');

  async function handleDownload() {
    if (!fileId) return;
    setDownloadError('');
    setLoading(true);
    try {
      // Always via a pre-signed URL. Linking at /api/files/{id}/download
      // directly sends no Authorization header — the token is held in memory,
      // not in a cookie — so that route always answers 401.
      await openFile(fileId);
    } catch (err) {
      setDownloadError(parseApiError(err, 'Could not open the file.'));
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="flex items-center justify-between rounded-lg bg-gray-50 px-3 py-2">
      <div className="min-w-0">
        <p className="truncate text-sm font-medium text-gray-700">
          {fileName ?? 'File attachment'}
        </p>
        {fileSize != null && (
          <p className="text-xs text-gray-400">{formatBytes(fileSize)}</p>
        )}
        {downloadError && (
          <p className="mt-0.5 text-xs text-red-600">{downloadError}</p>
        )}
      </div>
      <Button
        size="sm"
        variant="secondary"
        onClick={handleDownload}
        loading={loading}
        className="ml-2 shrink-0"
      >
        <Download size={13} />
        Download
      </Button>
    </div>
  );
}

function UrlContent({ content }: { content: Record<string, unknown> }) {
  const url = content.url as string | undefined;
  const linkText = (content.linkText as string | undefined) || url || 'Open link';
  if (!url) return null;
  return (
    <a
      href={url}
      target="_blank"
      rel="noopener noreferrer"
      className="inline-flex items-center gap-1.5 rounded-lg bg-indigo-50 px-3 py-1.5 text-sm font-medium text-indigo-700 hover:bg-indigo-100"
    >
      <ExternalLink size={13} />
      {linkText}
    </a>
  );
}

function VideoContent({ content }: { content: Record<string, unknown> }) {
  const url = content.url as string | undefined;
  const platform = content.platform as string | undefined;
  const durationSecs = content.durationSeconds as number | undefined;
  if (!url) return null;
  return (
    <div className="flex flex-wrap items-center gap-2">
      {platform && <Badge variant="info">{platform}</Badge>}
      {durationSecs != null && durationSecs > 0 && (
        <span className="text-xs text-gray-400">{formatDuration(durationSecs)}</span>
      )}
      <a
        href={url}
        target="_blank"
        rel="noopener noreferrer"
        className="inline-flex items-center gap-1.5 rounded-lg bg-red-50 px-3 py-1.5 text-sm font-medium text-red-700 hover:bg-red-100"
      >
        <Play size={13} />
        Watch
      </a>
    </div>
  );
}

function AnnouncementContent({ content }: { content: Record<string, unknown> }) {
  const body = content.body as string | undefined;
  if (!body) return null;
  return (
    <p className="mt-1 rounded-lg bg-amber-50 px-3 py-2 text-sm leading-relaxed text-amber-800">
      {body}
    </p>
  );
}

function AssignmentContent({ content, pscId }: { content: Record<string, unknown>; pscId: UUID }) {
  const assignmentId = content.assignmentId as UUID | undefined;
  return (
    <Link
      to={assignmentId ? `/assessment/${pscId}?assignment=${assignmentId}` : `/assessment/${pscId}`}
      className="inline-flex items-center gap-1.5 rounded-lg bg-green-50 px-3 py-1.5 text-sm font-medium text-green-700 hover:bg-green-100"
    >
      <ClipboardCheck size={13} />
      View Assignment
    </Link>
  );
}

function QuizContent({ content, pscId }: { content: Record<string, unknown>; pscId: UUID }) {
  const quizId = content.quizId as UUID | undefined;
  return (
    <Link
      to={quizId ? `/assessment/${pscId}?quiz=${quizId}` : `/assessment/${pscId}`}
      className="inline-flex items-center gap-1.5 rounded-lg bg-purple-50 px-3 py-1.5 text-sm font-medium text-purple-700 hover:bg-purple-100"
    >
      <HelpCircle size={13} />
      View Quiz
    </Link>
  );
}

// ---------------------------------------------------------------------------
// Material → CLO mapping
// ---------------------------------------------------------------------------
interface MaterialCloMappingItem {
  materialId: UUID;
  cloId: UUID;
  cloCode: string;
  cloTitle: string;
  weight?: number;
  createdAt: string;
}

interface CourseCloItem {
  id: UUID;
  code: string;
  title: string;
  orderIndex: number;
}

/**
 * Records which CLOs a piece of material teaches toward.
 *
 * This closes the last gap in the OBE chain — assessments could already be
 * mapped to CLOs and CLOs to PLOs, but nothing recorded which lecture, reading
 * or video actually delivers an outcome.
 */
function MaterialCloMappings({
  material,
  courseId,
  canManage,
}: {
  material: CourseMaterialResponse;
  courseId?: UUID;
  canManage: boolean;
}) {
  const queryClient = useQueryClient();
  const [cloId, setCloId] = useState('');
  const [error, setError] = useState('');

  const mappingsQ = useQuery({
    queryKey: ['materials', material.id, 'clo-mappings'],
    queryFn: () =>
      api
        .get<MaterialCloMappingItem[]>(`/materials/${material.id}/clo-mappings`)
        .then((r) => r.data),
  });
  const mappings = mappingsQ.data ?? [];

  const closQ = useQuery({
    queryKey: ['courses', courseId, 'clos'],
    queryFn: () => api.get<CourseCloItem[]>(`/courses/${courseId}/clos`).then((r) => r.data),
    enabled: canManage && !!courseId,
  });
  const clos = closQ.data ?? [];

  const invalidate = () =>
    queryClient.invalidateQueries({ queryKey: ['materials', material.id, 'clo-mappings'] });

  const addMut = useMutation({
    mutationFn: () => api.post(`/materials/${material.id}/clo-mappings`, { cloId }),
    onSuccess: () => {
      invalidate();
      setCloId('');
      setError('');
    },
    onError: (err) => setError(parseApiError(err)),
  });

  const removeMut = useMutation({
    mutationFn: (target: UUID) => api.delete(`/materials/${material.id}/clo-mappings/${target}`),
    onSuccess: invalidate,
    onError: (err) => setError(parseApiError(err)),
  });

  const unmapped = clos.filter((c) => !mappings.some((m) => m.cloId === c.id));

  // Students see the tags only when there are some; staff always get the editor.
  if (!canManage && mappings.length === 0) return null;

  return (
    <div className="mt-2 flex flex-wrap items-center gap-1.5">
      <span className="text-xs font-medium text-gray-500">CLOs:</span>
      {mappings.map((m) => (
        <span
          key={m.cloId}
          title={m.cloTitle}
          className="inline-flex items-center gap-1 rounded-full bg-primary-50 px-2 py-0.5 text-xs font-medium text-primary-700"
        >
          {m.cloCode}
          {canManage && (
            <button
              type="button"
              onClick={() => removeMut.mutate(m.cloId)}
              className="text-primary-300 hover:text-red-500"
              aria-label={`Unmap ${m.cloCode}`}
            >
              <X size={10} />
            </button>
          )}
        </span>
      ))}
      {mappings.length === 0 && (
        <span className="text-xs text-gray-400">none mapped</span>
      )}

      {canManage && unmapped.length > 0 && (
        <>
          <select
            value={cloId}
            onChange={(e) => setCloId(e.target.value)}
            className="h-6 rounded border border-gray-300 bg-white px-1.5 text-xs focus:border-primary-500 focus:outline-none"
          >
            <option value="">Add CLO…</option>
            {unmapped.map((c) => (
              <option key={c.id} value={c.id}>
                {c.code} — {c.title}
              </option>
            ))}
          </select>
          <button
            type="button"
            disabled={!cloId || addMut.isPending}
            onClick={() => addMut.mutate()}
            className="rounded px-1.5 py-0.5 text-xs font-medium text-primary-600 hover:bg-primary-50 disabled:text-gray-300"
          >
            Map
          </button>
        </>
      )}
      {error && <span className="text-xs text-red-600">{error}</span>}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Material card
// ---------------------------------------------------------------------------
interface MaterialCardProps {
  material: CourseMaterialResponse;
  canManage: boolean;
  pscId: UUID;
  courseId?: UUID;
  onEdit: (m: CourseMaterialResponse) => void;
  onDelete: (id: UUID) => void;
  onToggleVisible: (m: CourseMaterialResponse) => void;
}

function MaterialCard({
  material,
  canManage,
  pscId,
  courseId,
  onEdit,
  onDelete,
  onToggleVisible,
}: MaterialCardProps) {
  const meta = TYPE_META[material.type] ?? TYPE_META['DOCUMENT'];
  const Icon = meta.icon;

  return (
    <Card>
      <div className="flex items-start gap-3">
        <div
          className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-lg ${meta.color}`}
        >
          <Icon size={18} />
        </div>
        <div className="min-w-0 flex-1">
          {/* Title row */}
          <div className="flex items-start justify-between gap-2">
            <div className="min-w-0">
              <div className="flex items-center gap-2">
                <p className="font-medium text-gray-900">{material.title}</p>
                {!material.visible && (
                  <span className="rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-700">
                    Hidden
                  </span>
                )}
              </div>
              {material.description && (
                <p className="mt-0.5 text-sm text-gray-500">{material.description}</p>
              )}
            </div>
            {canManage && (
              <div className="flex shrink-0 items-center gap-0.5">
                <button
                  onClick={() => onToggleVisible(material)}
                  title={material.visible ? 'Hide from students' : 'Show to students'}
                  className="rounded p-2 text-gray-400 hover:text-gray-700"
                >
                  {material.visible ? <Eye size={15} /> : <EyeOff size={15} />}
                </button>
                <button
                  onClick={() => onEdit(material)}
                  className="rounded p-2 text-gray-400 hover:text-gray-700"
                >
                  <Pencil size={15} />
                </button>
                <button
                  onClick={() => onDelete(material.id)}
                  className="rounded p-2 text-gray-400 hover:text-red-500"
                >
                  <Trash2 size={15} />
                </button>
              </div>
            )}
          </div>
          {/* Content */}
          <div className="mt-2">
            {material.type === 'DOCUMENT' && (
              <DocumentContent content={material.content} />
            )}
            {material.type === 'URL' && (
              <UrlContent content={material.content} />
            )}
            {material.type === 'VIDEO_LINK' && (
              <VideoContent content={material.content} />
            )}
            {material.type === 'ANNOUNCEMENT' && (
              <AnnouncementContent content={material.content} />
            )}
            {material.type === 'ASSIGNMENT' && (
              <AssignmentContent content={material.content} pscId={pscId} />
            )}
            {material.type === 'QUIZ' && (
              <QuizContent content={material.content} pscId={pscId} />
            )}
          </div>
          <MaterialCloMappings
            material={material}
            courseId={courseId}
            canManage={canManage}
          />
        </div>
      </div>
    </Card>
  );
}

// ---------------------------------------------------------------------------
// Material form (Add / Edit)
// ---------------------------------------------------------------------------
const materialSchema = z
  .object({
    type: z.enum(['DOCUMENT', 'URL', 'VIDEO_LINK', 'ANNOUNCEMENT']),
    title: z.string().min(1, 'Required').max(255, 'Max 255 characters'),
    description: z.string().optional(),
    visible: z.boolean(),
    orderIndex: z.coerce.number().int().min(0).default(0),
    // URL / VIDEO_LINK
    url: z.string().optional(),
    linkText: z.string().optional(),
    // VIDEO_LINK only
    platform: z.enum(['YOUTUBE', 'VIMEO', 'OTHER']).optional(),
    durationSeconds: z.coerce.number().int().min(0).optional(),
    // ANNOUNCEMENT
    body: z.string().optional(),
  })
  .superRefine((d, ctx) => {
    if ((d.type === 'URL' || d.type === 'VIDEO_LINK') && !d.url?.trim()) {
      ctx.addIssue({ code: 'custom', message: 'URL is required', path: ['url'] });
    }
    if (d.url?.trim()) {
      try {
        new URL(d.url);
      } catch {
        ctx.addIssue({
          code: 'custom',
          message: 'Must be a valid URL (include https://)',
          path: ['url'],
        });
      }
    }
    if (d.type === 'ANNOUNCEMENT' && !d.body?.trim()) {
      ctx.addIssue({
        code: 'custom',
        message: 'Body text is required',
        path: ['body'],
      });
    }
    if (d.type === 'VIDEO_LINK' && !d.platform) {
      ctx.addIssue({
        code: 'custom',
        message: 'Platform is required',
        path: ['platform'],
      });
    }
  });

type MaterialFormValues = z.infer<typeof materialSchema>;

function buildContent(values: MaterialFormValues): Record<string, unknown> {
  switch (values.type) {
    case 'URL':
      return { url: values.url ?? '', linkText: values.linkText ?? '' };
    case 'VIDEO_LINK':
      return {
        url: values.url ?? '',
        platform: values.platform ?? 'OTHER',
        durationSeconds: values.durationSeconds ?? 0,
      };
    case 'ANNOUNCEMENT':
      return { body: values.body ?? '' };
    default:
      return {};
  }
}

function formDefaults(m: CourseMaterialResponse): Partial<MaterialFormValues> {
  const base: Partial<MaterialFormValues> = {
    type: m.type as MaterialType,
    title: m.title,
    description: m.description ?? '',
    visible: m.visible,
    orderIndex: m.orderIndex,
  };
  switch (m.type) {
    case 'URL':
      return {
        ...base,
        url: (m.content.url as string) ?? '',
        linkText: (m.content.linkText as string) ?? '',
      };
    case 'VIDEO_LINK':
      return {
        ...base,
        url: (m.content.url as string) ?? '',
        platform: (m.content.platform as 'YOUTUBE' | 'VIMEO' | 'OTHER') ?? 'OTHER',
        durationSeconds: (m.content.durationSeconds as number) ?? 0,
      };
    case 'ANNOUNCEMENT':
      return { ...base, body: (m.content.body as string) ?? '' };
    default:
      return base;
  }
}

interface MaterialFormModalProps {
  pscId: UUID;
  editing: CourseMaterialResponse | null;
  onClose: () => void;
}

function MaterialFormModal({ pscId, editing, onClose }: MaterialFormModalProps) {
  const queryClient = useQueryClient();
  const isEdit = editing !== null;
  const [uploadFile, setUploadFile] = useState<File | null>(null);
  const [uploadError, setUploadError] = useState('');
  const [uploadProgress, setUploadProgress] = useState(0);

  const {
    register,
    handleSubmit,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<MaterialFormValues>({
    resolver: zodResolver(materialSchema),
    defaultValues: isEdit
      ? formDefaults(editing)
      : { type: 'ANNOUNCEMENT', visible: true, orderIndex: 0 },
  });

  const type = watch('type');

  const createMutation = useMutation({
    mutationFn: (body: CreateMaterialBody) =>
      api
        .post<CourseMaterialResponse>(`/offerings/${pscId}/materials`, body)
        .then((r) => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['offerings', pscId, 'materials'] });
      onClose();
    },
  });

  const updateMutation = useMutation({
    mutationFn: (body: UpdateMaterialBody) =>
      api
        .put<CourseMaterialResponse>(`/materials/${editing!.id}`, body)
        .then((r) => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['offerings', pscId, 'materials'] });
      onClose();
    },
  });

  async function onSubmit(values: MaterialFormValues) {
    setUploadError('');

    if (isEdit) {
      const content =
        values.type === 'DOCUMENT' ? editing!.content : buildContent(values);
      updateMutation.mutate({
        title: values.title,
        description: values.description,
        content,
        visible: values.visible,
        orderIndex: values.orderIndex,
      });
      return;
    }

    let content: Record<string, unknown> = buildContent(values);

    if (values.type === 'DOCUMENT') {
      if (!uploadFile) {
        setUploadError('Please select a file to upload.');
        return;
      }
      const invalid = validateUpload(uploadFile);
      if (invalid) {
        setUploadError(invalid);
        return;
      }
      try {
        const up = await uploadToServer(uploadFile, {
          context: 'materials',
          contextId: pscId,
          onProgress: setUploadProgress,
        });
        content = {
          fileId: up.id,
          objectKey: up.objectKey,
          fileName: up.originalName,
          fileSize: up.fileSize,
          mimeType: up.contentType,
        };
      } catch (err) {
        setUploadError(parseApiError(err, 'File upload failed. Please try again.'));
        return;
      }
    }

    createMutation.mutate({
      type: values.type,
      title: values.title,
      description: values.description,
      content,
      visible: values.visible,
      orderIndex: values.orderIndex,
    });
  }

  const pending =
    isSubmitting || createMutation.isPending || updateMutation.isPending;
  const mutErr = createMutation.error || updateMutation.error;

  const textareaBase =
    'w-full rounded-lg border border-gray-300 px-3 py-2 text-sm ' +
    'placeholder:text-gray-400 focus:border-primary-500 focus:outline-none ' +
    'focus:ring-2 focus:ring-primary-500';

  const selectBase =
    'h-9 w-full rounded-lg border border-gray-300 bg-white px-3 text-sm ' +
    'focus:border-primary-500 focus:outline-none focus:ring-2 focus:ring-primary-500';

  return (
    <Modal
      open
      onClose={onClose}
      title={isEdit ? 'Edit Material' : 'Add Material'}
      maxWidth="max-w-xl"
    >
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4 px-6 pb-6 pt-2">
        {/* Type selector — add mode only.
            A segmented radio group rather than a <select>: an <option> cannot
            contain an SVG, so a dropdown could only label these with emoji. */}
        {!isEdit && (
          <fieldset className="flex flex-col gap-1">
            <legend className="mb-1 text-sm font-medium text-gray-700">Type</legend>
            <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
              {CREATABLE_TYPES.map((value) => {
                const meta = TYPE_META[value];
                const Icon = meta.icon;
                const selected = type === value;
                return (
                  <label
                    key={value}
                    className={`flex cursor-pointer flex-col items-center gap-1.5 rounded-lg border-2 px-2 py-3 text-center text-xs font-medium transition-colors ${
                      selected
                        ? 'border-primary-500 bg-primary-50 text-primary-700'
                        : 'border-gray-200 text-gray-600 hover:border-primary-300 hover:bg-gray-50'
                    }`}
                  >
                    <input
                      type="radio"
                      value={value}
                      {...register('type')}
                      className="sr-only"
                    />
                    <Icon size={18} />
                    {meta.label}
                  </label>
                );
              })}
            </div>
          </fieldset>
        )}

        <Input
          label="Title"
          error={errors.title?.message}
          {...register('title')}
        />

        <div className="flex flex-col gap-1">
          <label className="text-sm font-medium text-gray-700">
            Description{' '}
            <span className="font-normal text-gray-400">(optional)</span>
          </label>
          <textarea
            {...register('description')}
            rows={2}
            className={textareaBase}
            placeholder="Brief description…"
          />
        </div>

        {/* ── URL / VIDEO_LINK ── */}
        {(type === 'URL' || type === 'VIDEO_LINK') && (
          <>
            <Input
              label="URL"
              type="url"
              placeholder="https://…"
              error={errors.url?.message}
              {...register('url')}
            />
            {type === 'URL' && (
              <Input
                label="Link text (optional)"
                placeholder="Click here to access…"
                {...register('linkText')}
              />
            )}
            {type === 'VIDEO_LINK' && (
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div className="flex flex-col gap-1">
                  <label className="text-sm font-medium text-gray-700">Platform</label>
                  <select {...register('platform')} className={selectBase}>
                    <option value="YOUTUBE">YouTube</option>
                    <option value="VIMEO">Vimeo</option>
                    <option value="OTHER">Other</option>
                  </select>
                  {errors.platform && (
                    <p className="text-xs text-red-600">{errors.platform.message}</p>
                  )}
                </div>
                <Input
                  label="Duration (seconds)"
                  type="number"
                  min={0}
                  placeholder="e.g. 3600"
                  {...register('durationSeconds')}
                />
              </div>
            )}
          </>
        )}

        {/* ── ANNOUNCEMENT ── */}
        {type === 'ANNOUNCEMENT' && (
          <div className="flex flex-col gap-1">
            <label className="text-sm font-medium text-gray-700">Body</label>
            <textarea
              {...register('body')}
              rows={4}
              className={textareaBase}
              placeholder="Announcement text…"
            />
            {errors.body && (
              <p className="text-xs text-red-600">{errors.body.message}</p>
            )}
          </div>
        )}

        {/* ── DOCUMENT (add mode) ── */}
        {type === 'DOCUMENT' && !isEdit && (
          <div className="flex flex-col gap-1">
            <label className="text-sm font-medium text-gray-700">File</label>
            <input
              type="file"
              onChange={(e) => {
                const file = e.target.files?.[0] ?? null;
                setUploadError(file ? (validateUpload(file) ?? '') : '');
                setUploadFile(file);
              }}
              className="block w-full text-sm text-gray-500 file:mr-3 file:rounded-lg file:border-0 file:bg-primary-50 file:px-3 file:py-1.5 file:text-sm file:font-medium file:text-primary-700 hover:file:bg-primary-100"
            />
            <p className="text-xs text-gray-400">
              Up to {formatBytes(MAX_UPLOAD_BYTES)}
              {uploadFile && !uploadError && ` · ${formatBytes(uploadFile.size)} selected`}
            </p>
            {pending && uploadProgress > 0 && uploadProgress < 100 && (
              <div className="h-1.5 w-full overflow-hidden rounded-full bg-gray-200">
                <div
                  className="h-full rounded-full bg-primary-500 transition-all duration-200"
                  style={{ width: `${uploadProgress}%` }}
                />
              </div>
            )}
            {uploadError && (
              <p className="text-xs text-red-600">{uploadError}</p>
            )}
          </div>
        )}

        {/* ── DOCUMENT (edit mode note) ── */}
        {type === 'DOCUMENT' && isEdit && (
          <div className="rounded-lg bg-gray-50 px-3 py-2 text-sm text-gray-500">
            The attached file cannot be changed. Delete and re-add this material
            to replace the file.
          </div>
        )}

        {/* ── Visibility + order ── */}
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <div className="flex items-center gap-2 rounded-lg border border-gray-200 px-3 py-2">
            <input
              type="checkbox"
              id="mat-visible"
              {...register('visible')}
              className="h-4 w-4 rounded border-gray-300 text-primary-600 focus:ring-primary-500"
            />
            <label
              htmlFor="mat-visible"
              className="cursor-pointer text-sm font-medium text-gray-700"
            >
              Visible to students
            </label>
          </div>
          <Input
            label="Order index"
            type="number"
            min={0}
            {...register('orderIndex')}
          />
        </div>

        {mutErr && (
          <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-600">
            Something went wrong. Please try again.
          </p>
        )}

        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" loading={pending}>
            {isEdit ? 'Save Changes' : 'Add Material'}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

// ---------------------------------------------------------------------------
// Page
// ---------------------------------------------------------------------------
type ModalState =
  | null
  | { mode: 'add' }
  | { mode: 'edit'; material: CourseMaterialResponse };

export default function CourseDetailPage() {
  const { pscId } = useParams<{ pscId: string }>();
  const user = useAuthStore((s) => s.user);
  const canManage =
    user?.role === 'ADMIN' ||
    user?.role === 'TEACHER' ||
    user?.role === 'ASSISTANT';
  const queryClient = useQueryClient();

  const [modal, setModal] = useState<ModalState>(null);
  const [deletingId, setDeletingId] = useState<UUID | null>(null);

  // ── Data ────────────────────────────────────────────────────────────────
  const offeringQ = useQuery({
    queryKey: ['offerings', pscId],
    queryFn: () =>
      api
        .get<OfferingSummaryResponse>(`/offerings/${pscId}`)
        .then((r) => r.data),
    enabled: !!pscId,
  });

  const materialsQ = useQuery({
    queryKey: ['offerings', pscId, 'materials'],
    queryFn: () =>
      api
        .get<CourseMaterialResponse[]>(`/offerings/${pscId}/materials`)
        .then((r) => r.data),
    enabled: !!pscId,
  });

  // ── Mutations ────────────────────────────────────────────────────────────
  const deleteMutation = useMutation({
    mutationFn: (id: UUID) => api.delete(`/materials/${id}`),
    onSuccess: () => {
      toast.success('Material deleted.');
      queryClient.invalidateQueries({ queryKey: ['offerings', pscId, 'materials'] });
      setDeletingId(null);
    },
  });

  const toggleMutation = useMutation({
    mutationFn: (m: CourseMaterialResponse) =>
      api
        .put<CourseMaterialResponse>(`/materials/${m.id}`, {
          title: m.title,
          description: m.description,
          content: m.content,
          visible: !m.visible,
          orderIndex: m.orderIndex,
        } satisfies UpdateMaterialBody)
        .then((r) => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['offerings', pscId, 'materials'] });
    },
  });

  // ── Derived ──────────────────────────────────────────────────────────────
  const offering = offeringQ.data;
  const materials = materialsQ.data ?? [];

  return (
    <div className="space-y-6">
      {/* Breadcrumb */}
      <nav className="flex items-center gap-1.5 text-sm text-gray-500">
        <Link
          to="/courses"
          className="flex items-center gap-1 hover:text-gray-700"
        >
          <ChevronLeft size={14} />
          Courses
        </Link>
        {offering && (
          <>
            <span>/</span>
            <span className="font-medium text-gray-800">
              {offering.courseCode}
            </span>
          </>
        )}
      </nav>

      {/* Header */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div>
          {offeringQ.isLoading ? (
            <Spinner />
          ) : offering ? (
            <>
              <h1 className="text-2xl font-bold text-gray-900">
                {offering.courseCode} — {offering.courseName}
              </h1>
              <p className="mt-1 text-sm text-gray-500">
                {offering.creditHours} credit hrs
                {offering.maxCapacity > 0 &&
                  ` · ${offering.maxCapacity} seats`}
              </p>
            </>
          ) : (
            <p className="text-sm text-red-500">Course not found.</p>
          )}
        </div>
        {canManage && (
          <Button
            onClick={() => setModal({ mode: 'add' })}
            className="shrink-0"
          >
            <Plus size={16} />
            Add Material
          </Button>
        )}
      </div>

      {/* Materials list */}
      {materialsQ.isLoading ? (
        <div className="flex justify-center py-12">
          <Spinner />
        </div>
      ) : materials.length === 0 ? (
        <div className="flex flex-col items-center py-16 text-center">
          <BookOpen size={40} className="mb-3 text-gray-300" />
          <p className="text-sm text-gray-400">
            {canManage
              ? 'No materials yet. Click "Add Material" to get started.'
              : 'No materials have been posted for this course yet.'}
          </p>
        </div>
      ) : (
        <div className="space-y-3">
          {materials.map((m) => (
            <MaterialCard
              key={m.id}
              material={m}
              canManage={canManage}
              pscId={pscId!}
              courseId={offering?.courseId}
              onEdit={(mat) => setModal({ mode: 'edit', material: mat })}
              onDelete={setDeletingId}
              onToggleVisible={(mat) => toggleMutation.mutate(mat)}
            />
          ))}
        </div>
      )}

      {/* Add / Edit modal */}
      {modal && pscId && (
        <MaterialFormModal
          pscId={pscId}
          editing={modal.mode === 'edit' ? modal.material : null}
          onClose={() => setModal(null)}
        />
      )}

      {/* Delete confirmation modal */}
      <Modal
        open={deletingId !== null}
        onClose={() => setDeletingId(null)}
        title="Delete Material"
      >
        <div className="px-6 pb-6 pt-4">
          <p className="text-sm text-gray-600">
            Are you sure you want to delete this material? This cannot be undone.
          </p>
          <div className="mt-4 flex justify-end gap-2">
            <Button variant="secondary" onClick={() => setDeletingId(null)}>
              Cancel
            </Button>
            <Button
              variant="danger"
              loading={deleteMutation.isPending}
              onClick={() => deletingId && deleteMutation.mutate(deletingId)}
            >
              Delete
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
