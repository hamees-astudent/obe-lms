import { useState } from 'react';
import { ChevronDown, FileText } from 'lucide-react';
import CitationPassage from '@/components/ai/CitationPassage';
import type { CitationResponse } from '@/types/api';

/**
 * Where an answer or question came from — "Lecture 5 · Slide 12". Click to
 * read the cited passage here; from there the file opens at that page.
 */
export default function CitationChip({ citation, index }: { citation: CitationResponse; index?: number }) {
  const [open, setOpen] = useState(false);

  return (
    <span className={open ? 'block w-full' : 'inline-block max-w-full'}>
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
        title={citation.heading ? `${citation.materialTitle} — ${citation.heading}` : citation.materialTitle}
        className="inline-flex max-w-full items-center gap-1 rounded-full border border-primary-200 bg-primary-50 px-2 py-0.5 text-xs text-primary-700 hover:bg-primary-100"
      >
        {index !== undefined && <span className="font-semibold">[{index}]</span>}
        <FileText size={11} className="flex-shrink-0" />
        <span className="truncate">
          {citation.materialTitle} · {citation.locationLabel}
        </span>
        <ChevronDown size={11} className={`flex-shrink-0 transition ${open ? 'rotate-180' : ''}`} />
      </button>
      {open && (
        <span className="mt-1 block rounded-lg border border-gray-200 bg-white p-3 text-xs leading-relaxed text-gray-700">
          <CitationPassage citation={citation} index={index} />
        </span>
      )}
    </span>
  );
}
