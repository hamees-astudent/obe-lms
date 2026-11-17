import api from '@/lib/api';
import type { UploadedFileResponse, UUID } from '@/types/api';

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
 * Fetches a stored file through the API and returns a `blob:` URL for it.
 *
 * Same-origin on purpose. A pre-signed storage URL names the storage host the
 * server was configured with — `http://localhost:9000` in a default setup —
 * which on any other machine means the viewer's own computer, so the link
 * breaks. Going through `/api/files/{id}/download` works wherever the app
 * itself loads, and carries the access token like any other request.
 * Uploads are capped at {@link MAX_UPLOAD_BYTES}, so holding one in memory is fine.
 */
export async function fileObjectUrl(fileId: UUID): Promise<string> {
  const res = await api.get<Blob>(`/files/${fileId}/download`, {
    params: { inline: true },
    responseType: 'blob',
    timeout: 120_000,
  });
  return URL.createObjectURL(res.data);
}

/**
 * Opens a new tab and then shows in it the `blob:` URL that `resolve` fetches.
 *
 * The tab is opened first, while the click still counts as a user gesture:
 * opening it after an `await` gets it blocked as a pop-up. Call this before
 * awaiting anything else in the click handler.
 */
export async function openInNewTab(
  resolve: () => Promise<string | { url: string; fragment: string }>,
  fragment = '',
): Promise<void> {
  const tab = window.open('', '_blank');
  try {
    const resolved = await resolve();
    const url = typeof resolved === 'string' ? resolved : resolved.url;
    if (typeof resolved !== 'string') fragment = resolved.fragment;
    if (tab) {
      tab.opener = null;
      tab.location.href = url + fragment;
    } else {
      window.location.assign(url + fragment);
    }
    // The tab has loaded it long before then; free the memory.
    setTimeout(() => URL.revokeObjectURL(url), 10 * 60 * 1000);
  } catch (e) {
    tab?.close();
    throw e;
  }
}

/** Opens a stored file in a new tab, optionally at a fragment such as `#page=12` (PDF viewers honour it). */
export function openFile(fileId: UUID, fragment = ''): Promise<void> {
  return openInNewTab(() => fileObjectUrl(fileId), fragment);
}

export async function fileIdByKey(objectKey: string): Promise<UUID> {
  const { data: file } = await api.get<UploadedFileResponse>('/files/by-key', {
    params: { key: objectKey },
  });
  return file.id;
}

/**
 * Opens a file identified by its storage object key — the reference assignment
 * submissions keep instead of a file id.
 */
export function openFileByKey(objectKey: string, fragment = ''): Promise<void> {
  return openInNewTab(async () => fileObjectUrl(await fileIdByKey(objectKey)), fragment);
}

/**
 * Resolves an object key to a URL the browser can render inline — a scanned
 * exam page next to the marks read off it, say. A `blob:` URL, so it works
 * in a plain `<img src>` on any machine; revoke it when no longer shown.
 */
export async function objectUrlByKey(objectKey: string): Promise<string> {
  return fileObjectUrl(await fileIdByKey(objectKey));
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
