import type { ReactNode } from 'react';
import { FlaskConical } from 'lucide-react';
import type { TimetableEntryResponse, TimetableGridResponse } from '@/types/api';

/** "08:30:00" → "08:30" */
export function hhmm(time: string): string {
  return time.slice(0, 5);
}

/** ISO day of week for today: 1 = Monday … 7 = Sunday. */
export function isoToday(): number {
  return new Date().getDay() || 7;
}

// Literal class names so Tailwind keeps them.
const COLOURS = [
  'border-blue-200 bg-blue-50 text-blue-900',
  'border-emerald-200 bg-emerald-50 text-emerald-900',
  'border-amber-200 bg-amber-50 text-amber-900',
  'border-purple-200 bg-purple-50 text-purple-900',
  'border-rose-200 bg-rose-50 text-rose-900',
  'border-cyan-200 bg-cyan-50 text-cyan-900',
  'border-lime-200 bg-lime-50 text-lime-900',
  'border-indigo-200 bg-indigo-50 text-indigo-900',
];

/** Same course, same colour — wherever it appears. */
export function courseColour(courseCode: string): string {
  let h = 0;
  for (const ch of courseCode) h = (h * 31 + ch.charCodeAt(0)) >>> 0;
  return COLOURS[h % COLOURS.length];
}

type Row = { kind: 'slot'; slotIndex: number; start: string; end: string } | { kind: 'break'; start: string; end: string };

/** The grid's rows: one per period, plus a break row wherever periods are not back to back. */
function buildRows(grid: TimetableGridResponse): Row[] {
  const rows: Row[] = [];
  grid.slots.forEach((s, i) => {
    const prev = grid.slots[i - 1];
    if (prev && prev.end !== s.start) rows.push({ kind: 'break', start: prev.end, end: s.start });
    rows.push({ kind: 'slot', slotIndex: s.index, start: s.start, end: s.end });
  });
  return rows;
}

interface Block {
  firstRow: number;
  lastRow: number;
  entries: TimetableEntryResponse[];
}

/**
 * One day's entries grouped into blocks of overlapping meetings. In a personal
 * timetable each block is one class spanning its periods; in a crowded admin
 * view a block stacks every class meeting at that time.
 */
function blocksForDay(entries: TimetableEntryResponse[], rows: Row[]): Block[] {
  const rowOf = (time: string, edge: 'start' | 'end') =>
    rows.findIndex((r) => r.kind === 'slot' && r[edge] === time);
  const placed = entries
    .map((e) => ({ e, first: rowOf(e.startTime, 'start'), last: rowOf(e.endTime, 'end') }))
    // A meeting edited off the grid (config changed since) still shows, in its nearest row.
    .map((p) => ({ ...p, first: p.first < 0 ? 0 : p.first, last: p.last < 0 ? Math.max(p.first, 0) : p.last }))
    .sort((a, b) => a.first - b.first || a.e.courseCode.localeCompare(b.e.courseCode));

  const blocks: Block[] = [];
  for (const p of placed) {
    const current = blocks[blocks.length - 1];
    if (current && p.first <= current.lastRow) {
      current.entries.push(p.e);
      current.lastRow = Math.max(current.lastRow, p.last);
    } else {
      blocks.push({ firstRow: p.first, lastRow: p.last, entries: [p.e] });
    }
  }
  return blocks;
}

export interface WeekGridProps {
  grid: TimetableGridResponse;
  entries: TimetableEntryResponse[];
  /** Second line of each class card; defaults to room · teacher. */
  detail?: (e: TimetableEntryResponse) => ReactNode;
  onEntryClick?: (e: TimetableEntryResponse) => void;
  /** Tint today's column. */
  highlightToday?: boolean;
}

/** A week of classes: days across, periods down, labs spanning their periods. */
export default function WeekGrid({ grid, entries, detail, onEntryClick, highlightToday = true }: WeekGridProps) {
  const rows = buildRows(grid);
  const today = highlightToday ? isoToday() : -1;
  const describe = detail ?? ((e: TimetableEntryResponse) => `${e.roomName} · ${e.teacherName}`);

  return (
    <div className="overflow-x-auto scrollbar-thin">
      <div
        className="grid min-w-[760px] gap-px overflow-hidden rounded-xl border border-gray-200 bg-gray-200 text-sm"
        style={{
          gridTemplateColumns: `5.5rem repeat(${grid.days.length}, minmax(0, 1fr))`,
          gridTemplateRows: `auto ${rows.map((r) => (r.kind === 'break' ? '2rem' : 'minmax(5.5rem, auto)')).join(' ')}`,
        }}
      >
        {/* Header */}
        <div className="bg-gray-50" style={{ gridRow: 1, gridColumn: 1 }} />
        {grid.days.map((d, i) => (
          <div
            key={d.dayOfWeek}
            style={{ gridRow: 1, gridColumn: i + 2 }}
            className={`px-2 py-2 text-center text-xs font-semibold uppercase tracking-wide ${
              d.dayOfWeek === today ? 'bg-primary-50 text-primary-700' : 'bg-gray-50 text-gray-600'
            }`}
          >
            {d.name}
            {d.dayOfWeek === today && <span className="ml-1 font-normal normal-case">(today)</span>}
          </div>
        ))}

        {/* Time labels and empty cells */}
        {rows.map((r, ri) => (
          <div key={`t${ri}`} style={{ gridRow: ri + 2, gridColumn: 1 }}
               className="flex flex-col justify-center bg-gray-50 px-2 text-xs text-gray-500">
            {r.kind === 'break' ? (
              <span className="italic">Break</span>
            ) : (
              <>
                <span className="font-medium text-gray-700">{hhmm(r.start)}</span>
                <span>{hhmm(r.end)}</span>
              </>
            )}
          </div>
        ))}
        {grid.days.map((d, di) =>
          rows.map((r, ri) => (
            <div
              key={`c${d.dayOfWeek}-${ri}`}
              style={{ gridRow: ri + 2, gridColumn: di + 2 }}
              className={r.kind === 'break' ? 'bg-gray-50' : d.dayOfWeek === today ? 'bg-primary-50/40' : 'bg-white'}
            />
          )),
        )}

        {/* Classes */}
        {grid.days.map((d, di) =>
          blocksForDay(entries.filter((e) => e.dayOfWeek === d.dayOfWeek), rows).map((b) => (
            <div
              key={`b${d.dayOfWeek}-${b.firstRow}`}
              style={{ gridRow: `${b.firstRow + 2} / ${b.lastRow + 3}`, gridColumn: di + 2 }}
              className="flex flex-col gap-1 p-1"
            >
              {b.entries.map((e) => {
                const Tag = onEntryClick ? 'button' : 'div';
                return (
                  <Tag
                    key={e.id}
                    type={onEntryClick ? 'button' : undefined}
                    onClick={onEntryClick ? () => onEntryClick(e) : undefined}
                    title={`${e.courseCode} ${e.courseName}\n${hhmm(e.startTime)}–${hhmm(e.endTime)} · ${e.roomName}\n${e.teacherName} · ${e.programName}`}
                    className={`flex min-h-0 flex-1 flex-col rounded-lg border px-2 py-1.5 text-left ${courseColour(e.courseCode)} ${
                      onEntryClick ? 'transition hover:shadow-md focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500' : ''
                    }`}
                  >
                    <span className="flex items-center gap-1 text-xs font-semibold">
                      {e.courseCode}
                      {e.kind === 'LAB' && (
                        <span className="inline-flex items-center gap-0.5 rounded bg-white/70 px-1 text-[10px] font-medium uppercase">
                          <FlaskConical size={10} /> Lab
                        </span>
                      )}
                    </span>
                    <span className="line-clamp-2 text-xs leading-snug">{e.courseName}</span>
                    <span className="mt-auto truncate pt-0.5 text-[11px] opacity-75">{describe(e)}</span>
                  </Tag>
                );
              })}
            </div>
          )),
        )}
      </div>
    </div>
  );
}
