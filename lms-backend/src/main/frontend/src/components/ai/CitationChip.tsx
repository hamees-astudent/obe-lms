import { useState } from 'react';
import { FileText } from 'lucide-react';
import api from '@/lib/api';
import { presignedUrlByKey } from '@/lib/files';
import { toast } from '@/components/ui/Toast';
import type { CitationResponse, CourseMaterialResponse } from '@/types/api';

/**
 * Where an answer or question came from — "Lecture 5 · Slide 12" — opening
 * the file on click. PDFs open at the cited page.
 */
export default function CitationChip({ citation, index }: { citation: CitationResponse; index?: number }) {
  const [busy, setBusy] = useState(false);

  async function open() {
    setBusy(true);
    try {
      const { data } = await api.get<CourseMaterialResponse>(`/materials/${citation.materialId}`);
      const key = data.content?.objectKey;
      if (typeof key !== 'string') throw new Error('This material has no file');
      const url = await presignedUrlByKey(key);
      const isPdf = String(data.content?.mimeType ?? '').includes('pdf');
      window.open(
        isPdf && citation.locationType === 'PAGE' ? `${url}#page=${citation.locationStart}` : url,
        '_blank',
        'noopener,noreferrer',
      );
    } catch {
      toast.error('Could not open that material.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <button
      type="button"
      onClick={open}
      disabled={busy}
      title={citation.heading ? `${citation.materialTitle} — ${citation.heading}` : citation.materialTitle}
      className="inline-flex max-w-full items-center gap-1 rounded-full border border-primary-200 bg-primary-50 px-2 py-0.5 text-xs text-primary-700 hover:bg-primary-100 disabled:opacity-60"
    >
      {index !== undefined && <span className="font-semibold">[{index}]</span>}
      <FileText size={11} className="flex-shrink-0" />
      <span className="truncate">
        {citation.materialTitle} · {citation.locationLabel}
      </span>
    </button>
  );
}
