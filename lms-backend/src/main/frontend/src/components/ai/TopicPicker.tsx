import { Check } from 'lucide-react';
import type { TopicResponse, UUID } from '@/types/api';

/** Topics grouped by material, each a toggle; a material's heading selects all of its topics. */
export default function TopicPicker({
  topics,
  selected,
  onChange,
}: {
  topics: TopicResponse[];
  selected: UUID[];
  onChange: (ids: UUID[]) => void;
}) {
  const byMaterial = new Map<UUID, TopicResponse[]>();
  for (const t of topics) {
    const list = byMaterial.get(t.materialId) ?? [];
    list.push(t);
    byMaterial.set(t.materialId, list);
  }
  const toggle = (id: UUID) =>
    onChange(selected.includes(id) ? selected.filter((x) => x !== id) : [...selected, id]);

  return (
    <div className="max-h-64 space-y-3 overflow-y-auto rounded-lg border border-gray-200 p-3">
      {[...byMaterial.entries()].map(([materialId, list]) => {
        const all = list.every((t) => selected.includes(t.id));
        return (
          <div key={materialId}>
            <button
              type="button"
              onClick={() =>
                onChange(all
                  ? selected.filter((id) => !list.some((t) => t.id === id))
                  : [...new Set([...selected, ...list.map((t) => t.id)])])
              }
              className="mb-1 text-xs font-semibold uppercase tracking-wide text-gray-500 hover:text-primary-700"
            >
              {list[0].materialTitle} {all ? '(clear)' : '(all)'}
            </button>
            <div className="flex flex-wrap gap-1.5">
              {list.map((t) => {
                const on = selected.includes(t.id);
                return (
                  <button
                    key={t.id}
                    type="button"
                    title={t.summary}
                    onClick={() => toggle(t.id)}
                    className={`inline-flex items-center gap-1 rounded-full border px-2.5 py-1 text-xs ${
                      on
                        ? 'border-primary-500 bg-primary-50 text-primary-700'
                        : 'border-gray-200 text-gray-700 hover:border-primary-300'
                    }`}
                  >
                    {on && <Check size={11} />}
                    {t.name}
                    <span className="text-gray-400">
                      {t.locationType === 'SLIDE' ? 'sl.' : t.locationType === 'PAGE' ? 'p.' : '§'}
                      {t.locationStart === t.locationEnd ? t.locationStart : `${t.locationStart}–${t.locationEnd}`}
                    </span>
                  </button>
                );
              })}
            </div>
          </div>
        );
      })}
    </div>
  );
}
