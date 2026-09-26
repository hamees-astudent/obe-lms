import { Link } from 'react-router-dom';
import { CalendarDays, ChevronRight, FlaskConical } from 'lucide-react';
import Card, { CardHeader } from '@/components/ui/Card';
import Spinner from '@/components/ui/Spinner';
import { useMyTimetable } from '@/lib/queries';
import { courseColour, hhmm, isoToday } from '@/components/timetable/WeekGrid';

/** "HH:mm:ss" of now, comparable with the API's times as strings. */
function nowTime(): string {
  const d = new Date();
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}:00`;
}

/** Dashboard card: today's classes, with the one on now or next marked. */
export default function TodayClasses({ showProgram = false }: { showProgram?: boolean }) {
  const { data, isLoading } = useMyTimetable();
  const today = isoToday();
  const classes = (data?.entries ?? [])
    .filter((e) => e.dayOfWeek === today)
    .sort((a, b) => a.startTime.localeCompare(b.startTime));
  const now = nowTime();
  const current = classes.find((e) => e.endTime > now);

  return (
    <Card>
      <CardHeader
        title="Today's Classes"
        action={
          <Link to="/timetable" className="flex items-center gap-0.5 text-xs font-medium text-primary-600 hover:underline">
            Full timetable <ChevronRight size={14} />
          </Link>
        }
      />
      {isLoading ? (
        <div className="flex justify-center py-6"><Spinner /></div>
      ) : classes.length === 0 ? (
        <div className="flex flex-col items-center py-6 text-center text-gray-400">
          <CalendarDays size={32} className="mb-2 opacity-40" />
          <p className="text-sm">
            {(data?.entries.length ?? 0) === 0 ? 'No timetable published yet.' : 'No classes today.'}
          </p>
        </div>
      ) : (
        <ul className="space-y-2">
          {classes.map((e) => {
            const isNow = e === current && e.startTime <= now;
            const isNext = e === current && !isNow;
            const done = e.endTime <= now;
            return (
              <li
                key={e.id}
                className={`flex items-center gap-3 rounded-lg border px-3 py-2 ${courseColour(e.courseCode)} ${done ? 'opacity-50' : ''}`}
              >
                <div className="w-20 shrink-0 text-xs font-medium">
                  {hhmm(e.startTime)}–{hhmm(e.endTime)}
                </div>
                <div className="min-w-0 flex-1">
                  <p className="flex items-center gap-1 truncate text-sm font-semibold">
                    {e.courseCode} <span className="font-normal">{e.courseName}</span>
                    {e.kind === 'LAB' && <FlaskConical size={12} className="shrink-0" />}
                  </p>
                  <p className="truncate text-xs opacity-75">
                    {e.roomName} · {showProgram ? `${e.programName} · ${e.students} students` : e.teacherName}
                  </p>
                </div>
                {isNow && <span className="rounded-full bg-green-600 px-2 py-0.5 text-[10px] font-semibold text-white">NOW</span>}
                {isNext && <span className="rounded-full bg-primary-600 px-2 py-0.5 text-[10px] font-semibold text-white">NEXT</span>}
              </li>
            );
          })}
        </ul>
      )}
    </Card>
  );
}
