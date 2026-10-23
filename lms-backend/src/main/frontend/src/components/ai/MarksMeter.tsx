/**
 * Marks used against a paper's limit. Turns red past the limit, which the
 * server refuses anyway; the meter is there so the teacher sees it coming.
 */
export default function MarksMeter({ used, limit, label = 'marks' }: { used: number; limit: number; label?: string }) {
  const pct = limit > 0 ? Math.min(100, (used / limit) * 100) : 0;
  const over = used > limit;
  return (
    <div className="min-w-[10rem]">
      <div className="flex justify-between text-xs">
        <span className={over ? 'font-semibold text-red-600' : 'text-gray-600'}>
          {fmtMarks(used)} / {fmtMarks(limit)} {label}
        </span>
        {over && <span className="font-semibold text-red-600">over by {fmtMarks(used - limit)}</span>}
      </div>
      <div className="mt-1 h-2 overflow-hidden rounded-full bg-gray-100" role="meter" aria-valuenow={used} aria-valuemax={limit}>
        <div
          className={`h-full rounded-full transition-all ${over ? 'bg-red-500' : pct > 90 ? 'bg-amber-500' : 'bg-primary-500'}`}
          style={{ width: `${over ? 100 : pct}%` }}
        />
      </div>
    </div>
  );
}

export function fmtMarks(n: number): string {
  return Number.isInteger(n) ? String(n) : n.toFixed(1).replace(/\.0$/, '');
}
