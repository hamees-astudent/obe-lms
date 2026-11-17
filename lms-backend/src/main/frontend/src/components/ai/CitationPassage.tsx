import { useState } from 'react';
import { ExternalLink, FileText } from 'lucide-react';
import api from '@/lib/api';
import { fileIdByKey, fileObjectUrl, openInNewTab } from '@/lib/files';
import { toast } from '@/components/ui/Toast';
import type { CitationResponse, CourseMaterialResponse, UUID } from '@/types/api';

/**
 * The passage an answer or question cites: where it is, the text itself, and
 * a button opening the file at that page. Built from spans only, so it can sit
 * inside a paragraph of an answer.
 */
export default function CitationPassage({ citation, index }: { citation: CitationResponse; index?: number }) {
  const [busy, setBusy] = useState(false);

  async function open() {
    setBusy(true);
    try {
      await openMaterialAt(citation);
    } catch {
      toast.error('Could not open that material.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <span className="block">
      <span className="flex items-start gap-1.5 font-medium text-gray-900">
        <FileText size={12} className="mt-0.5 flex-shrink-0 text-primary-600" />
        <span className="min-w-0">
          {index !== undefined && !Number.isNaN(index) && <span className="mr-1 text-primary-700">[{index}]</span>}
          {citation.materialTitle} · {citation.locationLabel}
          {citation.heading && <span className="block font-normal text-gray-500">{citation.heading}</span>}
        </span>
      </span>
      {citation.excerpt ? (
        <span className="mt-2 block max-h-56 overflow-y-auto whitespace-pre-line rounded border-l-2 border-primary-200 bg-gray-50 px-2 py-1.5 text-gray-700">
          {citation.excerpt}
        </span>
      ) : (
        <span className="mt-2 block text-gray-500">Open the material to read this passage.</span>
      )}
      <button
        type="button"
        onClick={open}
        disabled={busy}
        className="mt-2 inline-flex items-center gap-1 text-primary-700 hover:underline disabled:opacity-60"
      >
        <ExternalLink size={11} />
        {busy ? 'Opening…' : `Open ${citation.locationLabel.toLowerCase()}`}
      </button>
    </span>
  );
}

/** Opens the cited material in a new tab; PDFs at the cited page. */
export function openMaterialAt(citation: CitationResponse): Promise<void> {
  return openInNewTab(async () => {
    const { data } = await api.get<CourseMaterialResponse>(`/materials/${citation.materialId}`);
    const content = data.content ?? {};
    const isPdf = String(content.mimeType ?? '').includes('pdf');
    const fragment = isPdf && citation.locationType === 'PAGE' ? `#page=${citation.locationStart}` : '';
    const fileId = typeof content.fileId === 'string' ? (content.fileId as UUID)
      : typeof content.objectKey === 'string' ? await fileIdByKey(content.objectKey)
      : null;
    if (!fileId) throw new Error('This material has no file');
    return { url: await fileObjectUrl(fileId), fragment };
  });
}
