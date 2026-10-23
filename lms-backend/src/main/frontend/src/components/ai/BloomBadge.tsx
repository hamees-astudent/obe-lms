import { BLOOM_LABELS, type BloomLevel } from '@/types/api';

const COLORS: Record<BloomLevel, string> = {
  REMEMBER: 'bg-slate-100 text-slate-700',
  UNDERSTAND: 'bg-sky-100 text-sky-700',
  APPLY: 'bg-emerald-100 text-emerald-700',
  ANALYZE: 'bg-amber-100 text-amber-800',
  EVALUATE: 'bg-orange-100 text-orange-800',
  CREATE: 'bg-fuchsia-100 text-fuchsia-700',
};

export default function BloomBadge({ level, title }: { level?: BloomLevel | null; title?: string }) {
  if (!level) return null;
  return (
    <span
      title={title}
      className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${COLORS[level]}`}
    >
      {BLOOM_LABELS[level]}
    </span>
  );
}
