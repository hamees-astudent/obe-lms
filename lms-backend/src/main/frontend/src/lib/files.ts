import api from '@/lib/api';
import type { UploadedFileResponse, PresignedUrlResponse, UUID } from '@/types/api';

/** Keep in step with `spring.servlet.multipart.max-file-size` in application.yml. */
export const MAX_UPLOAD_BYTES = 50 * 1024 * 1024;

export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

/**
 * Rejects a file the server would reject anyway, but before spending the
 * upload. Returns an error message, or null when the file is acceptable.
 */
export function validateUpload(file: File, accept?: string[]): string | null {
  if (file.size === 0) {
    return 'That file is empty.';
  }
  if (file.size > MAX_UPLOAD_BYTES) {
    return `That file is ${formatBytes(file.size)}. The limit is ${formatBytes(MAX_UPLOAD_BYTES)}.`;
  }
  if (accept && accept.length > 0) {
    const ext = `.${file.name.split('.').pop()?.toLowerCase() ?? ''}`;
    if (!accept.map((a) => a.toLowerCase()).includes(ext)) {
      return `Only ${accept.join(', ')} files are accepted.`;
    }
  }
  return null;
}

export interface UploadOptions {
  /** Owning module, e.g. "submissions" or "materials". */
  context?: string;
  /** Owning entity id, so the file can be found again by context. */
  contextId?: UUID;
  /** 0–100 progress callback. */
  onProgress?: (percent: number) => void;
}

/**
 * Uploads one file to `POST /api/files`.
 *
 * Note the deliberate absence of a `Content-Type` header: the request body is
 * `FormData`, and a multipart body is only parseable if the header carries the
 * boundary the browser generates. Setting `multipart/form-data` by hand strips
 * that boundary and the server rejects the upload.
 */
export async function uploadFile(
  file: File,
  { context, contextId, onProgress }: UploadOptions = {},
): Promise<UploadedFileResponse> {
  const formData = new FormData();
  formData.append('file', file);

  const params = new URLSearchParams();
  if (context) params.set('context', context);
  if (contextId) params.set('contextId', contextId);
  const query = params.toString() ? `?${params}` : '';

  const res = await api.post<UploadedFileResponse>(`/files${query}`, formData, {
    onUploadProgress: (event) => {
      if (!onProgress || !event.total) return;
      onProgress(Math.round((event.loaded * 100) / event.total));
    },
  });
  return res.data;
}

/**
 * Opens a stored file in a new tab.
 *
 * Goes through the pre-signed URL endpoint rather than linking straight at
 * `/api/files/{id}/download`: the access token lives in memory and is attached
 * by an axios interceptor, so a plain navigation carries no `Authorization`
 * header and comes back 401.
 */
export async function openFile(fileId: UUID): Promise<void> {
  const { data } = await api.get<PresignedUrlResponse>(`/files/${fileId}/url`);
  window.open(data.url, '_blank', 'noopener,noreferrer');
}

/**
 * Opens a file identified by its storage object key — the reference assignment
 * submissions keep instead of a file id.
 */
export async function openFileByKey(objectKey: string): Promise<void> {
  const { data: file } = await api.get<UploadedFileResponse>('/files/by-key', {
    params: { key: objectKey },
  });
  await openFile(file.id);
}

/**
 * Resolves an object key to a temporary URL the browser can render inline.
 *
 * `openFileByKey` sends the user to a new tab, which is wrong when the file has
 * to be looked at *beside* something else — a scanned exam page next to the
 * marks read off it, say. The URL is pre-signed and short-lived, so it works in
 * a plain `<img src>` without the Authorization header the API otherwise needs.
 */
export async function presignedUrlByKey(objectKey: string): Promise<string> {
  const { data: file } = await api.get<UploadedFileResponse>('/files/by-key', {
    params: { key: objectKey },
  });
  const { data } = await api.get<PresignedUrlResponse>(`/files/${file.id}/url`);
  return data.url;
}

/** Downloads a stored file under a chosen filename. */
export async function downloadFile(fileId: UUID, filename?: string): Promise<void> {
  const res = await api.get<Blob>(`/files/${fileId}/download`, { responseType: 'blob' });
  const url = URL.createObjectURL(res.data);
  try {
    const link = document.createElement('a');
    link.href = url;
    link.download = filename ?? 'download';
    document.body.appendChild(link);
    link.click();
    link.remove();
  } finally {
    URL.revokeObjectURL(url);
  }
}
